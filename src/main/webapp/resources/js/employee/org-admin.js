// 업체 관리 > 부서 관리 · 직급 관리 (views/employee/admin/dept.jsp, rank.jsp. 업체 관리자만).
// 업체의 부서와 직급을 추가·이름 변경·삭제·순서 바꾸기 한다. 이 목록이 계정 생성·가입 활성화·사원 관리에 그대로 쓰인다.
// 규칙 (서버가 지키고, 화면은 먼저 알려 준다):
//   - 이름은 1~20자, 목록 안에서 겹치지 않는다(빈칸·대소문자 무시). '관리자'는 첫 관리자 표시라 직급 이름으로 못 쓴다.
//   - 부서는 30개, 직급은 15개까지, 하나 이상 남긴다.
//   - 사람이 없을 때만 지운다: 계정(사용 중, 자리 대기, 승인되고 만들기 전, 비활성, 차단)과 아직 열린 가입 요청. 삭제한 계정은 세지 않는다.
//     부서를 지우면 그 부서의 가입 활성화도 꺼진다.
//   - 이름을 바꿔도 사람은 그대로 그 부서·직급에 있다 (게시판·일정·휴가 기록, 연차 경력도 따라간다).
//   - 직급은 위(낮은 직급)에서 아래(높은 직급) 순서다. 화살표로 순서를 바꾼다.
//   - 부서 형태 (공통 코드 DEPT_TYPE: IT 부서, 인사 부서, 운영 부서, 없음 = 일반): 부서마다 고른다. IT 부서 사람은 IT 담당 메뉴, 인사 부서
//     사람은 인사 담당 메뉴를 다음 화면부터 받는다. 여러 부서가 같은 형태여도 된다. 이름을 바꾸면 따라가고, 지우면 없어진다.
//   - 읽기 전용 업체(이용 기간 끝남): 여기 버튼은 모두 쓰기라 흐리게 보이고(data-write) 서버가 403으로 거절한다.
// 서버 API ({kind} = depts | ranks, {name}은 서버가 정하는 부서·직급 식별값(이름 또는 id)):
//   GET    /employee/admin/api/{kind}               → { max, items: [{ name, members: { all, inUse, retired, blocked, requests },
//                                                      type, head (부서만: 부서 형태, 부서장 이름 | null), careerYears (직급만) }] }
//   POST   /employee/admin/api/{kind}               { name } → { name }                 409: 이미 있음, 개수 초과
//   POST   /employee/admin/api/{kind}/{name}        { name } → { from, to, mySub }      mySub: 바뀐 내 헤더 표시("부서 · 직급") | null
//   POST   /employee/admin/api/{kind}/{name}/delete → { name }                          409: 사람이 있음, 마지막 하나
//   POST   /employee/admin/api/{kind}/{name}/move   { dir: -1 | 1 } → { ok }
//   POST   /employee/admin/api/depts/{name}/type    { type: DEPT_IT | DEPT_HR | DEPT_OPS | '' } → { name, type }
// 같은 모듈의 employee/employee.js에서 cellOf, writeButton, askConfirm, DEPT_TYPE, deptTypeLabel, viewShown, readOnlyUi를 쓴다.
(() => {
  const ORG = {
    dept: { path: 'depts', word: '부서', topic: '부서는', obj: '부서를', prefix: 'dept' },
    rank: { path: 'ranks', word: '직급', topic: '직급은', obj: '직급을', prefix: 'rank' },
  };
  const $o = id => document.getElementById(id);
  const data = { dept: null, rank: null }; // 마지막으로 받은 목록 { max, items }
  const editing = { dept: null, rank: null }; // 이름을 바꾸려고 연 줄의 이름
  const url = (kind, name, tail) => `/employee/admin/api/${ORG[kind].path}`
    + (name != null ? `/${encodeURIComponent(name)}` : '') + (tail ? `/${tail}` : '');
  const names = kind => (data[kind] ? data[kind].items.map(x => x.name) : []);
  const norm = s => s.replace(/\s+/g, '').toLowerCase();
  const showError = (kind, text) => { $o(`${ORG[kind].prefix}Error`).textContent = text; };

  function load(kind) {
    api.get(url(kind))
      .done(d => { data[kind] = d; render(kind); })
      .fail(xhr => showToast(apiMessage(xhr, `${ORG[kind].word} 목록을 불러오지 못했습니다`)));
  }

  // 서버도 같은 검사를 다시 한다. 화면은 먼저 알려 줄 뿐이다
  function nameError(kind, raw, except) {
    const name = raw.trim();
    if (!name) return `${ORG[kind].word} 이름을 입력해 주세요`;
    if (name.length > 20) return '이름은 20자까지 쓸 수 있습니다';
    if (kind === 'rank' && norm(name) === norm('관리자')) return "'관리자'는 최초 관리자 표시에 쓰여 직급 이름으로 쓸 수 없습니다";
    if (names(kind).some(x => x !== except && norm(x) === norm(name))) return `이미 있는 ${ORG[kind].word}입니다`;
    return '';
  }

  // ---------- 동작 ----------
  function add(kind) {
    const input = $o(`${ORG[kind].prefix}NewName`);
    const max = data[kind] ? data[kind].max : Infinity;
    const error = names(kind).length >= max ? `${ORG[kind].topic} ${max}개까지 만들 수 있습니다` : nameError(kind, input.value);
    showError(kind, error);
    if (error) return input.focus();
    const btn = $o(`${ORG[kind].prefix}AddForm`).querySelector('[type="submit"]');
    if (btn) btn.disabled = true; // 두 번 눌러 두 번 더해지지 않게
    api.post(url(kind), { name: input.value.trim() })
      .done(r => {
        input.value = '';
        showToast(`${ORG[kind].word} '${r.name}'을(를) 추가했습니다`);
      })
      .fail(xhr => { showError(kind, apiMessage(xhr)); input.focus(); })
      .always(() => { if (btn) btn.disabled = false; load(kind); });
  }

  function move(kind, name, dir, b) {
    b.disabled = true;
    api.post(url(kind, name, 'move'), { dir })
      .fail(xhr => showToast(apiMessage(xhr)))
      .always(() => load(kind));
  }

  function saveRename(kind, from, input, b) {
    const to = input.value.trim();
    if (to === from) { editing[kind] = null; showError(kind, ''); return render(kind); }
    const error = nameError(kind, to, from);
    showError(kind, error);
    if (error) return input.focus();
    if (b.disabled) return; // 보내는 중
    b.disabled = true;
    api.post(url(kind, from), { name: to })
      .done(r => {
        // 내 헤더의 "부서 · 직급"도 새 이름으로 (다른 사람은 다음 화면부터)
        if (r.mySub) { const sub = $o('userSub'); if (sub) sub.textContent = r.mySub; }
        editing[kind] = null;
        showToast(`'${r.from}'을(를) '${r.to}'(으)로 바꿨습니다. 소속 직원과 기록도 함께 바뀌었습니다`);
        load(kind);
      })
      .fail(xhr => {
        showError(kind, apiMessage(xhr));
        b.disabled = false;
        // 다른 탭에서 지웠으면 목록을 다시 받는다
        if (xhr.status === 404) { editing[kind] = null; load(kind); } else input.focus();
      });
  }

  function remove(kind, item) {
    const { word } = ORG[kind];
    const { name } = item;
    if (names(kind).length <= 1) return showToast(`${ORG[kind].topic} 하나 이상 있어야 합니다`);
    const m = item.members;
    if (m.all) {
      const parts = [m.inUse && `사용 중 ${m.inUse}명`, m.retired && `비활성 ${m.retired}명`, m.blocked && `차단 ${m.blocked}명`, m.requests && `가입 요청 ${m.requests}건`].filter(Boolean).join(', ');
      return askConfirm(`'${name}' ${ORG[kind].obj} 지울 수 없습니다`,
        `${parts}이 이 ${word}에 있습니다. 비활성 계정은 사원 관리에서 삭제하고(차단 계정은 차단 해제 뒤 비활성화), 가입 요청은 가입 승인에서 처리한 뒤 다시 해 주세요.`, '확인', null);
    }
    askConfirm(`'${name}' ${ORG[kind].obj} 삭제할까요?`,
      `목록에서 사라지고, 계정 생성에서도 더 이상 고를 수 없습니다.${kind === 'dept' ? ' 이 부서의 가입 활성화도 꺼집니다.' : ''}`, '삭제', () => {
        // 서버가 다시 본다: 그사이 누가 들어왔을 수 있다
        api.post(`${url(kind, name)}/delete`)
          .done(r => showToast(`${word} '${r.name}'을(를) 삭제했습니다`))
          .fail(xhr => showToast(apiMessage(xhr)))
          .always(() => load(kind));
      }, true);
  }

  // 부서 형태: 부서 줄마다 고르는 칸. 이 화면의 다른 것처럼 쓰기 동작이다 (data-write, 읽기 전용이면 서버가 거절한다)
  function typeSelect(item) {
    const sel = document.createElement('select');
    sel.className = 'input org-type';
    sel.setAttribute('aria-label', `${item.name} 부서 형태`);
    sel.setAttribute('data-write', '');
    if (readOnlyUi()) sel.disabled = true;
    sel.append(new Option('일반', ''), ...Object.entries(DEPT_TYPE).map(([k, v]) => new Option(v, k)));
    sel.value = item.type || '';
    sel.addEventListener('change', () => {
      const type = sel.value;
      sel.disabled = true;
      api.post(url('dept', item.name, 'type'), { type })
        .done(r => {
          item.type = r.type;
          const what = { DEPT_IT: ' 이 부서 직원에게 IT 담당 메뉴가 보입니다', DEPT_HR: ' 이 부서 직원에게 인사 담당 메뉴가 보입니다' }[r.type] || '';
          showToast(`'${r.name}'의 부서 형태를 ${deptTypeLabel(r.type)}(으)로 바꿨습니다.${what}`);
        })
        .fail(xhr => { sel.value = item.type || ''; showToast(apiMessage(xhr)); })
        .always(() => { sel.disabled = readOnlyUi(); });
    });
    return sel;
  }

  // ---------- 표 ----------
  const btn = (label, aria, onClick, cls) => {
    const b = writeButton(label, aria, onClick);
    if (cls) b.classList.add(cls);
    return b;
  };
  function render(kind) {
    const d = data[kind];
    if (!d) return;
    const list = d.items;
    const { word } = ORG[kind];
    if (editing[kind] && !list.some(x => x.name === editing[kind])) editing[kind] = null;
    $o(`${ORG[kind].prefix}Count`).textContent = `${list.length}개 / 최대 ${d.max}개`;
    $o(`${ORG[kind].prefix}List`).replaceChildren(...list.map((item, i) => {
      const { name } = item;
      const tr = document.createElement('tr');
      const order = document.createElement('div');
      order.className = 'org-move';
      order.append(
        btn('↑', `${name} 위로`, ev => move(kind, name, -1, ev.currentTarget)),
        btn('↓', `${name} 아래로`, ev => move(kind, name, 1, ev.currentTarget)),
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
        const save = btn('저장', `${name} 이름 저장`, () => saveRename(kind, name, input, save), 'btn-primary');
        input.addEventListener('keydown', e => {
          if (e.key === 'Enter') { e.preventDefault(); saveRename(kind, name, input, save); }
          if (e.key === 'Escape') { editing[kind] = null; showError(kind, ''); render(kind); }
        });
        nameCell = input;
        actions.append(
          save,
          btn('취소', `${name} 이름 변경 취소`, () => { editing[kind] = null; showError(kind, ''); render(kind); }),
        );
        setTimeout(() => { input.focus(); input.select(); });
      } else {
        nameCell = document.createElement('strong');
        nameCell.textContent = name;
        actions.append(
          btn('이름 변경', `${name} 이름 변경`, () => { editing[kind] = name; showError(kind, ''); render(kind); }),
          btn('삭제', `${name} 삭제`, () => remove(kind, item)),
        );
      }
      const m = item.members;
      const people = document.createElement('span');
      people.textContent = [`${m.inUse}명`, m.retired && `비활성 ${m.retired}`, m.blocked && `차단 ${m.blocked}`, m.requests && `요청 ${m.requests}`].filter(Boolean).join(' · ');
      tr.append(cellOf('순서', order), cellOf(word, nameCell));
      if (kind === 'dept') tr.append(cellOf('부서 형태', typeSelect(item)));
      tr.append(cellOf('인원', people));
      if (kind === 'rank') {
        // 연차 계산에서 이 직급의 이전 경력으로 치는 연수 (서버가 준 careerYears, 새 직급은 0년)
        const c = document.createElement('span');
        c.textContent = `${item.careerYears}년`;
        if (!item.careerYears) c.className = 'muted';
        tr.append(cellOf('연차 경력', c));
      }
      if (kind === 'dept') {
        const h = document.createElement('span');
        h.textContent = item.head || '-';
        if (!item.head) h.className = 'muted';
        tr.append(cellOf('부서장', h));
      }
      tr.append(cellOf('관리', actions));
      return tr;
    }));
  }

  for (const kind of ['dept', 'rank']) {
    $o(`${ORG[kind].prefix}AddForm`)?.addEventListener('submit', e => { e.preventDefault(); add(kind); });
    $o(`${ORG[kind].prefix}NewName`)?.addEventListener('input', () => showError(kind, ''));
  }


  // 화면을 열 때 (JSP 페이지가 열리면 부른다)
  window.openOrgAdmin = kind => {
    editing[kind] = null;
    showError(kind, '');
    $o(`${ORG[kind].prefix}NewName`).value = '';
    load(kind);
  };
})();
