// 업체 관리 > 부서 관리 · 직급 관리 (company admin only, user asked 2026-10-29).
// Add, rename, delete and reorder the company's departments and ranks. The lists are the ones the rest of the
// groupware already reads (hr.js deptsOf / ranksOf: 계정 생성, 가입 활성화, 사원 관리), so a change shows up there at once.
// Rules:
//   - A name is 1~20 characters and unique in its list (spaces and letter case ignored). '관리자' is not a rank:
//     it marks the company's first admin.
//   - Up to 30 departments and 15 ranks; at least one of each stays.
//   - Delete only when nobody is in it: no account (in use, waiting for a seat, approved and not created yet,
//     or deactivated) and no join request still open. Deleted accounts (사원 관리 > 삭제) don't count.
//     Deleting a department also turns its 가입 활성화 off.
//   - Rename keeps everyone where they are. The prototype keeps names, not ids, so it rewrites every stored copy
//     of the old name (staff records and join requests, 가입 활성화, 부서 게시판 posts, 부서 일정, 휴가 기록,
//     공지 작성 부서). The real server keeps department and rank ids, so a rename changes one row.
//   - Ranks are listed from the lowest (top) to the highest (bottom); the arrows change that order.
//   - Read-only company (period over): every button here is a write action and is refused (data-write).
// Real server (proposed): GET/POST /company/admin/depts, PUT/DELETE /company/admin/depts/{id},
// PUT /company/admin/depts/order, and the same under /company/admin/ranks. 409 when the name is taken or the
// department/rank is still in use.
// Uses loadStore, saveStore (ops.js), staffOf, saveStaff, statusOf, deptsOf, ranksOf, DEPT_KEY, RANK_KEY,
// JOIN_OPEN_KEY, cellOf, writeButton, askConfirm (hr.js), session, readOnly, showToast (common.js / ops.js).
(() => {
  const ORG = {
    dept: { key: DEPT_KEY, of: deptsOf, word: '부서', topic: '부서는', obj: '부서를', max: 30, prefix: 'dept' },
    rank: { key: RANK_KEY, of: ranksOf, word: '직급', topic: '직급은', obj: '직급을', max: 15, prefix: 'rank' },
  };
  const $o = id => document.getElementById(id);
  const editing = { dept: null, rank: null }; // the name whose row is open for renaming

  const listOf = kind => ORG[kind].of(session.company).slice();
  function saveList(kind, list) {
    const all = loadStore(ORG[kind].key, {});
    all[session.company] = list;
    saveStore(ORG[kind].key, all);
  }
  const norm = s => s.replace(/\s+/g, '').toLowerCase();

  // Who is in a department or rank: accounts and open join requests, by state
  const LIVE = ['ACTIVE', 'WAITING', 'APPROVED', 'RETIRED', 'REQUESTED'];
  function membersOf(kind, name) {
    const people = staffOf(session.company).filter(e => e[kind] === name && LIVE.includes(statusOf(e)));
    const count = st => people.filter(e => st.includes(statusOf(e))).length;
    return { all: people.length, inUse: count(['ACTIVE', 'WAITING', 'APPROVED']), retired: count(['RETIRED']), requests: count(['REQUESTED']) };
  }

  function nameError(kind, raw, except) {
    const name = raw.trim();
    if (!name) return `${ORG[kind].word} 이름을 입력해 주세요`;
    if (name.length > 20) return '이름은 20자까지 쓸 수 있습니다';
    if (kind === 'rank' && norm(name) === norm('관리자')) return "'관리자'는 최초 관리자 표시에 쓰여 직급 이름으로 쓸 수 없습니다";
    if (listOf(kind).some(x => x !== except && norm(x) === norm(name))) return `이미 있는 ${ORG[kind].word}입니다`;
    return '';
  }

  // ---------- 이름 변경: every stored copy of the old name ----------
  function renameRefs(kind, from, to) {
    const code = session.company;
    const staff = staffOf(code);
    staff.forEach(e => { if (e[kind] === from) e[kind] = to; });
    saveStaff(code, staff);
    if (kind === 'dept') {
      const open = loadStore(JOIN_OPEN_KEY, {});
      (open[code] || []).forEach(x => { if (x.scope === from) x.scope = to; });
      saveStore(JOIN_OPEN_KEY, open);
      // { company: [{ dept, ... }] }: 부서 일정 (schedule.js) and 휴가 기록 (attendance.js)
      for (const key of ['ieum-demo-schedules-v1', 'ieum-demo-leaves-v1']) {
        const all = loadStore(key, {});
        if (Array.isArray(all[code])) { all[code].forEach(x => { if (x.dept === from) x.dept = to; }); saveStore(key, all); }
      }
      // per-company stores of notice.js: board posts by department, notices with the writer's department
      const board = loadStore(`ieum-board-demo-v2:${code}`, null);
      if (board && Array.isArray(board.posts)) {
        board.posts.forEach(p => { if (p.dept === from) p.dept = to; });
        saveStore(`ieum-board-demo-v2:${code}`, board);
      }
      const notices = loadStore(`ieum-notice-demo-v3:${code}`, null);
      if (notices && Array.isArray(notices.list)) {
        notices.list.forEach(n => { if (n.writerDept === from) n.writerDept = to; });
        saveStore(`ieum-notice-demo-v3:${code}`, notices);
      }
    }
    // the signed-in admin's own session in this tab: its department and the header line ("부서 · 직급").
    // Other tabs and people pick the new name up from the staff records (notice.js and hr.js read those first)
    let changed = false;
    if (kind === 'dept' && session.dept === from) { session.dept = to; changed = true; }
    const parts = String(session.sub || '').split(' · ');
    if (parts.includes(from)) { session.sub = parts.map(x => (x === from ? to : x)).join(' · '); changed = true; }
    if (changed) {
      try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)); } catch { /* storage blocked */ }
      $o('userSub').textContent = session.sub;
    }
  }

  // ---------- actions ----------
  function add(kind) {
    const input = $o(`${ORG[kind].prefix}NewName`);
    const list = listOf(kind);
    const error = list.length >= ORG[kind].max ? `${ORG[kind].topic} ${ORG[kind].max}개까지 만들 수 있습니다` : nameError(kind, input.value);
    $o(`${ORG[kind].prefix}Error`).textContent = error;
    if (error) return input.focus();
    const name = input.value.trim();
    saveList(kind, [...list, name]);
    input.value = '';
    showToast(`${ORG[kind].word} '${name}'을(를) 추가했습니다`);
    render(kind);
  }

  function move(kind, i, dir) {
    const list = listOf(kind);
    const j = i + dir;
    if (j < 0 || j >= list.length) return;
    [list[i], list[j]] = [list[j], list[i]];
    saveList(kind, list);
    render(kind);
  }

  function saveRename(kind, from, input) {
    const to = input.value.trim();
    const errorEl = $o(`${ORG[kind].prefix}Error`);
    if (to === from) { editing[kind] = null; errorEl.textContent = ''; return render(kind); }
    const error = nameError(kind, to, from);
    errorEl.textContent = error;
    if (error) return input.focus();
    const list = listOf(kind);
    const i = list.indexOf(from);
    if (i < 0) { editing[kind] = null; return render(kind); } // removed in another tab
    list[i] = to;
    saveList(kind, list);
    renameRefs(kind, from, to);
    editing[kind] = null;
    showToast(`'${from}'을(를) '${to}'(으)로 바꿨습니다. 소속 직원과 기록도 함께 바뀌었습니다`);
    render(kind);
  }

  function remove(kind, name) {
    const word = ORG[kind].word;
    if (listOf(kind).length <= 1) return showToast(`${ORG[kind].topic} 하나 이상 있어야 합니다`);
    const m = membersOf(kind, name);
    if (m.all) {
      const parts = [m.inUse && `사용 중 ${m.inUse}명`, m.retired && `비활성 ${m.retired}명`, m.requests && `가입 요청 ${m.requests}건`].filter(Boolean).join(', ');
      return askConfirm(`'${name}' ${ORG[kind].obj} 지울 수 없습니다`,
        `${parts}이 이 ${word}에 있습니다. 비활성 계정은 사원 관리에서 삭제하고, 가입 요청은 가입 승인에서 처리한 뒤 다시 해 주세요.`, '확인', null);
    }
    askConfirm(`'${name}' ${ORG[kind].obj} 삭제할까요?`,
      `목록에서 사라지고, 계정 생성에서도 더 이상 고를 수 없습니다.${kind === 'dept' ? ' 이 부서의 가입 활성화도 꺼집니다.' : ''}`, '삭제', () => {
        // checked again: someone may have joined it in the meantime
        if (membersOf(kind, name).all) { showToast(`그사이 이 ${word}에 사람이 생겨 삭제하지 않았습니다`); return render(kind); }
        saveList(kind, listOf(kind).filter(x => x !== name));
        if (kind === 'dept') {
          const open = loadStore(JOIN_OPEN_KEY, {});
          if (open[session.company]) { open[session.company] = open[session.company].filter(x => x.scope !== name); saveStore(JOIN_OPEN_KEY, open); }
        }
        showToast(`${word} '${name}'을(를) 삭제했습니다`);
        render(kind);
      }, true);
  }

  // ---------- table ----------
  const btn = (label, aria, onClick, cls) => {
    const b = writeButton(label, aria, onClick);
    if (cls) b.classList.add(cls);
    return b;
  };
  function render(kind) {
    if (!session || session.type !== 'EMPLOYEE') return;
    const list = listOf(kind);
    const word = ORG[kind].word;
    if (editing[kind] && !list.includes(editing[kind])) editing[kind] = null;
    $o(`${ORG[kind].prefix}Count`).textContent = `${list.length}개 / 최대 ${ORG[kind].max}개`;
    const heads = kind === 'dept' ? staffOf(session.company).filter(e => e.head && statusOf(e) === 'ACTIVE') : [];
    $o(`${ORG[kind].prefix}List`).replaceChildren(...list.map((name, i) => {
      const tr = document.createElement('tr');
      const order = document.createElement('div');
      order.className = 'org-move';
      order.append(
        btn('↑', `${name} 위로`, () => move(kind, i, -1)),
        btn('↓', `${name} 아래로`, () => move(kind, i, 1)),
      );
      order.firstChild.disabled = i === 0;
      order.lastChild.disabled = i === list.length - 1;

      let nameCell;
      const actions = document.createElement('div');
      actions.className = 'row-actions';
      if (editing[kind] === name) {
        const input = document.createElement('input');
        input.className = 'input org-rename';
        input.value = name;
        input.maxLength = 20;
        input.setAttribute('aria-label', `${name} 새 이름`);
        input.addEventListener('keydown', e => {
          if (e.key === 'Enter') { e.preventDefault(); saveRename(kind, name, input); }
          if (e.key === 'Escape') { editing[kind] = null; $o(`${ORG[kind].prefix}Error`).textContent = ''; render(kind); }
        });
        nameCell = input;
        actions.append(
          btn('저장', `${name} 이름 저장`, () => saveRename(kind, name, input), 'btn-primary'),
          btn('취소', `${name} 이름 변경 취소`, () => { editing[kind] = null; $o(`${ORG[kind].prefix}Error`).textContent = ''; render(kind); }),
        );
        setTimeout(() => { input.focus(); input.select(); });
      } else {
        nameCell = document.createElement('strong');
        nameCell.textContent = name;
        actions.append(
          btn('이름 변경', `${name} 이름 변경`, () => { editing[kind] = name; $o(`${ORG[kind].prefix}Error`).textContent = ''; render(kind); }),
          btn('삭제', `${name} 삭제`, () => remove(kind, name)),
        );
      }
      const m = membersOf(kind, name);
      const people = document.createElement('span');
      people.textContent = [`${m.inUse}명`, m.retired && `비활성 ${m.retired}`, m.requests && `요청 ${m.requests}`].filter(Boolean).join(' · ');
      tr.append(cellOf('순서', order), cellOf(word, nameCell), cellOf('인원', people));
      if (kind === 'dept') {
        const head = heads.find(e => e.dept === name);
        const h = document.createElement('span');
        h.textContent = head ? head.name : '-';
        if (!head) h.className = 'muted';
        tr.append(cellOf('부서장', h));
      }
      tr.append(cellOf('관리', actions));
      return tr;
    }));
  }

  for (const kind of ['dept', 'rank']) {
    $o(`${ORG[kind].prefix}AddForm`).addEventListener('submit', e => { e.preventDefault(); add(kind); });
    $o(`${ORG[kind].prefix}NewName`).addEventListener('input', () => { $o(`${ORG[kind].prefix}Error`).textContent = ''; });
  }
  // another tab changed the lists or the staff: redraw the page that is open (not while a name is being typed)
  window.addEventListener('storage', e => {
    if (![DEPT_KEY, RANK_KEY, EMP_KEY].includes(e.key) || !session) return;
    const view = location.hash.slice(1);
    if (view === 'admin-dept' && !editing.dept) render('dept');
    if (view === 'admin-rank' && !editing.rank) render('rank');
  });

  // common.js showView calls this when either page opens
  window.openOrgAdmin = kind => {
    editing[kind] = null;
    $o(`${ORG[kind].prefix}Error`).textContent = '';
    $o(`${ORG[kind].prefix}NewName`).value = '';
    render(kind);
  };
})();
