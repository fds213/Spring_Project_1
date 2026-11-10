// IT 서비스 > 티켓 처리 (views/ticket/admin/list.jsp). IT 담당·업체 관리자 (요구분석서 3.4, 통합 계획서 3장 B·C, 5장 티켓).
// 상태: 접수 → 배정 → 처리 중 → 처리 완료 → 종료(요청자 확인, 내 티켓 화면), 그 밖에 취소·반려. 누를 수 있는 버튼은 서버가 준 actions 로 정한다.
// 상태를 바꾸는 요청에는 받은 version 을 같이 보낸다. 그사이 다른 사람이 바꿨으면 서버가 409 로 거절하고 화면은 새로 받는다.
// 주소·값 이름은 ticket 모듈 담당이 확정한 규격이다(11/3). 버튼은 actions 가 true 인 것만 보이고, 화면이 역할·상태로 다시 계산하지 않는다.
// 요청서 범위만 둔다: 상태 카드로 거르는 목록, 배정, 처리 시작, 처리 완료(장비 지급 건은 재고 지급), 반려, 댓글.
// 목록은 제목 검색과 쪽 번호(한 쪽 20건)로 받는다(#202): GET /ticket/admin/api/tickets?filter=&keyword=&page= → { counts, items, page, pages, total }.
// 카드 건수는 검색어 기준이고, 쪽 번호는 1부터다. 범위를 벗어난 쪽은 서버가 맞춰 준 page 를 따른다.
// util.js 의 h, span, text, inqCell, titleBtn, dataRow, detailTr, emptyTr, dlOf, cardOf, writeBtn, textBox, pickSelect, historyList, assetLabel, assetTypeName 과 이름표 TICKET_TYPE, TICKET_STATUS, TICKET_CLASS 를 쓴다.
(() => {
  const EMPTY = {
    UNASSIGNED: '배정을 기다리는 티켓이 없습니다', MINE: '내가 맡은 티켓이 없습니다', IN_PROGRESS: '처리 중인 티켓이 없습니다',
    RESOLVED: '요청자 확인을 기다리는 티켓이 없습니다', ALL: '티켓이 없습니다',
  };
  const RESULT_MAX = 500;
  const REASON_MAX = 300;
  const COMMENT_MAX = 300;
  const COLS = 8;
  const $ = id => document.getElementById(id);
  const SEARCH_DELAY = 250; // 타자를 멈추고 0.25초 뒤에 한 번만 묻는다(게시판 검색과 같다)
  const state = { filter: 'UNASSIGNED', q: '', page: 1, open: null, detail: null, seq: 0, last: null, draft: {} };
  // 열린 티켓에 쓰던 값(해결 결과·반려 사유·댓글·고른 장비·고른 담당자). 상세를 다시 그려도(댓글 저장, 409) 지워지지 않게 들고 있다
  const draftOf = id => (state.draft.id === id ? state.draft : (state.draft = { id }));
  function keep(el, id, key) {
    const v = draftOf(id)[key];
    // 고르기 칸은 그 값이 아직 목록에 있을 때만(그사이 다른 사람이 지급한 재고 장비는 빠진다)
    if (v && (el.tagName !== 'SELECT' || [...el.options].some(o => o.value === v))) el.value = v;
    el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', () => { draftOf(id)[key] = el.value; });
    return el;
  }
  const forget = (id, ...keys) => { if (state.draft.id === id) keys.forEach(k => delete state.draft[k]); };

  // 알림에서 오면 주소의 ?filter= 카드로 연다(티켓 배정 알림 → MINE)
  // 주소의 ?q=(검색어)·?page=(쪽)도 읽는다: 새로 고침하거나 다른 화면에서 뒤로 와도 보던 자리로 돌아온다
  function openTicketAdmin() {
    const qs = new URLSearchParams(location.search);
    const want = qs.get('filter');
    if (want && EMPTY[want]) state.filter = want;
    state.q = (qs.get('q') || '').trim();
    state.page = Math.max(1, parseInt(qs.get('page'), 10) || 1);
    $('taSearch').value = state.q;
    load();
  }
  window.openTicketAdmin = openTicketAdmin;

  // 지금 조건을 주소에 적는다. 기록을 쌓지 않게 replaceState 로 바꾸기만 한다(뒤로 가기는 앞 화면으로 간다)
  function syncUrl() {
    const qs = new URLSearchParams({ filter: state.filter });
    if (state.q) qs.set('q', state.q);
    if (state.page > 1) qs.set('page', state.page);
    history.replaceState(null, '', `${location.pathname}?${qs}`);
  }

  // ---------- 목록 ----------
  function load() {
    syncUrl();
    const my = ++state.seq;
    api.get('/ticket/admin/api/tickets', { filter: state.filter, keyword: state.q, page: state.page })
      .done(r => { if (my === state.seq) render(r); }) // 카드·검색·쪽을 빨리 바꾸면 늦게 온 앞 응답은 버린다
      .fail(xhr => { state.pagerFocus = false; showToast(apiMessage(xhr, '티켓 목록을 불러오지 못했습니다')); });
  }

  const person = p => (p ? `${p.name}${p.dept ? ` (${p.dept})` : ''}` : '-');

  function render(r) {
    state.last = r;
    // 처리하다 건수가 줄어 마지막 쪽이 없어지면(또는 주소의 쪽이 너무 크면) 서버가 맞춘 쪽을 따르고 주소도 고친다
    if (r.page && r.page !== state.page) { state.page = r.page; syncUrl(); }
    const counts = r.counts || {};
    Object.keys(EMPTY).forEach(k => { $(`taCnt${k}`).textContent = `${counts[k] ?? 0}건`; });
    // 카드마다 건수가 있으니 여기는 검색어에 맞는 전체 건수(카드 '전체'와 같다)
    $('taSearchInfo').textContent = state.q ? `'${state.q}' 검색 결과 ${counts.ALL ?? 0}건` : '';
    renderPager(r.pages || 1);
    document.querySelectorAll('#taFilters .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === state.filter));

    const items = (r.items || []).slice();
    // 열어 둔 티켓이 처리하면서 이 조건에서 빠져도(미배정 → 나에게 배정 등) 맨 위에 남겨 이어서 처리하게 한다
    const d = state.detail;
    if (state.open && d && d.id === state.open && !items.some(t => t.id === state.open)) {
      items.unshift({ id: d.id, type: d.type, title: d.title, requester: d.requester, branchName: d.branch && d.branch.name, assignee: d.assignee, status: d.status, at: d.at });
    }
    const rows = items.flatMap(t => {
      const isOpen = state.open === t.id;
      const tr = dataRow(isOpen, () => toggle(t.id),
        inqCell('번호', span(t.id)),
        inqCell('구분', span(TICKET_TYPE[t.type] || '-')),
        inqCell('제목', titleBtn(t.title, isOpen)),
        inqCell('요청자', span(person(t.requester))),
        inqCell('접수 지점', span(t.branchName || '-')),
        inqCell('담당자', span(t.assignee ? t.assignee.name : '미배정', t.assignee ? '' : 'muted')),
        inqCell('상태', span(TICKET_STATUS[t.status] || t.status, `status status-${TICKET_CLASS[t.status] || 'requested'}`)),
        inqCell('접수일', span(whenFull(t.at))));
      return isOpen ? [tr, detailTr(COLS, d && d.id === t.id ? detailView(d) : span('불러오는 중…', 'muted'))] : [tr];
    });
    $('taList').replaceChildren(...(rows.length ? rows : [emptyTr(COLS, state.q ? '검색어에 맞는 티켓이 없습니다' : EMPTY[state.filter])]));
  }

  // 쪽 번호: [이전] 1 … 4 5 6 … 12 [다음]. 지금 쪽 앞뒤 2쪽과 처음·끝 쪽을 보인다
  function renderPager(pages) {
    const nav = $('taPager');
    const refocus = state.pagerFocus; // 한 번만 쓴다: 실패하거나 한 쪽뿐이어도 다음에 엉뚱하게 초점이 옮겨 가지 않게
    state.pagerFocus = false;
    nav.hidden = pages <= 1;
    if (pages <= 1) { nav.replaceChildren(); return; }
    const go = page => {
      Object.assign(state, { page, open: null, pagerFocus: true });
      load();
      $('taFilters').scrollIntoView({ block: 'start' });
    };
    const btn = (label, page, aria, { current = false, disabled = false } = {}) => {
      const b = h('button', '', label);
      b.type = 'button';
      b.setAttribute('aria-label', aria);
      if (current) b.setAttribute('aria-current', 'page');
      b.disabled = disabled;
      b.addEventListener('click', () => go(page));
      return b;
    };
    const items = [btn('이전', state.page - 1, '이전 페이지', { disabled: state.page === 1 })];
    let last = 0;
    for (let p = 1; p <= pages; p++) {
      if (p !== 1 && p !== pages && Math.abs(p - state.page) > 2) continue;
      if (p - last > 1) items.push(span('…', 'pager-gap'));
      items.push(btn(String(p), p, `${p}페이지`, { current: p === state.page }));
      last = p;
    }
    items.push(btn('다음', state.page + 1, '다음 페이지', { disabled: state.page === pages }));
    nav.replaceChildren(...items);
    // 쪽 단추를 눌러 다시 그렸으면 키보드 초점을 새 지금 쪽으로 옮긴다(눌렀던 단추가 사라져 초점을 잃지 않게)
    if (refocus) nav.querySelector('[aria-current="page"]')?.focus({ preventScroll: true });
  }

  // 카드를 누르면 그 조건의 첫 쪽을 다시 받고, 열린 상세는 닫는다(검색어는 그대로 둔다)
  document.querySelectorAll('#taFilters .card-filter').forEach(b => b.addEventListener('click', () => {
    Object.assign(state, { filter: b.dataset.filter, page: 1, open: null });
    load();
  }));

  // 제목 검색: 타자를 멈추면 첫 쪽부터 다시 받는다. Enter는 기다리지 않는다. 다 지우면 전체 목록
  let searchTimer;
  const runSearch = () => {
    const q = $('taSearch').value.trim();
    if (q === state.q) return; // 띄어쓰기만 늘었으면 다시 받지 않는다
    Object.assign(state, { q, page: 1, open: null });
    load();
  };
  $('taSearch').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, SEARCH_DELAY); });
  $('taSearchForm').addEventListener('submit', e => { e.preventDefault(); clearTimeout(searchTimer); runSearch(); });

  // ---------- 상세 ----------
  function toggle(id) {
    state.open = state.open === id ? null : id;
    state.detail = null;
    if (state.last) render(state.last);
    if (state.open) loadDetail(id);
  }

  function loadDetail(id) {
    api.get(`/ticket/admin/api/tickets/${encodeURIComponent(id)}`)
      .done(d => { if (state.open === id) { state.detail = d; render(state.last); } })
      .fail(xhr => {
        if (state.open !== id) return; // 그사이 다른 줄을 열었다
        showToast(apiMessage(xhr, '티켓을 불러오지 못했습니다'));
        state.open = null;
        render(state.last);
      });
  }

  // 상태를 바꾼 뒤(또는 409 로 거절된 뒤) 상세를 먼저 새로 받고 목록을 받는다(목록에서 빠진 열린 티켓을 위에 남기려고)
  function refresh() {
    const id = state.open;
    if (!id) { load(); return; }
    api.get(`/ticket/admin/api/tickets/${encodeURIComponent(id)}`)
      .done(d => { if (state.open === id) state.detail = d; })
      .fail(() => { if (state.open === id) { state.open = null; state.detail = null; } })
      .always(load);
  }

  // 요청자가 장애 접수 때 붙인 사진(#197): 작게 보이고, 누르면 새 탭에서 원래 크기로 연다. 관리자용 주소라 서버가 IT·관리자인지 다시 본다
  function photoLink(d) {
    const img = h('img');
    img.src = pageHref(`/ticket/admin/api/tickets/${encodeURIComponent(d.id)}/photos/${encodeURIComponent(d.photoId)}`);
    img.alt = '요청자가 붙인 사진';
    img.loading = 'lazy';
    const a = h('a', 'tk-photo-link', img);
    a.href = img.src;
    a.target = '_blank';
    a.rel = 'noopener';
    return a;
  }

  function detailView(d) {
    const branch = d.branch ? [d.branch.name, d.branch.address, d.branch.detail].filter(Boolean).join(' · ') : '-';
    const left = cardOf('접수 내용', dlOf([['번호', d.id], ['구분', TICKET_TYPE[d.type] || '-'], ['요청자', person(d.requester)], ['접수 지점', branch],
      d.type === 'TK_EQUIPMENT' ? ['요청 장비', d.equipmentKind ? assetTypeName(d.equipmentKind) : '-'] : ['관련 자산', d.asset ? assetLabel(d.asset) : '-'],
      ['담당자', d.assignee ? d.assignee.name : '미배정'], ['상태', TICKET_STATUS[d.status] || d.status], ['제목', d.title], ['내용', text(d.body || '-')],
      ...(d.photoId ? [['사진', photoLink(d)]] : [])]));

    const right = h('section', 'info-card');
    const actions = actionBlocks(d);
    if (actions.length) right.append(h('header', 'info-card-head', h('h2', '', '처리')), ...actions);
    right.append(h('header', 'info-card-head', h('h2', '', '처리 이력')),
      historyList((d.history || []).map(x => text(`${whenFull(x.at)} · ${x.name} · ${x.text}`)), '아직 이력이 없습니다'),
      h('header', 'info-card-head', h('h2', '', '댓글')),
      historyList((d.comments || []).map(c => text(`${whenFull(c.at)} · ${c.name} · ${c.body}`)), '아직 댓글이 없습니다'));
    if (d.actions && d.actions.comment) right.append(commentBlock(d));
    const grid = h('div', 'inq-grid', left, h('div', '', right));
    grid.addEventListener('click', e => e.stopPropagation()); // 상세 안을 눌러도 줄이 접히지 않게
    return grid;
  }

  // 서버가 준 actions 마다 처리 칸을 만든다
  function actionBlocks(d) {
    const a = d.actions || {};
    const blocks = [];
    const row = h('div', 'inq-actions');
    if (a.assignSelf) row.append(writeBtn('나에게 배정', 'btn btn-primary', b => post(d, 'assign', {}, b, '나에게 배정했습니다. 이어서 처리 시작을 누르세요', ['assignee'])));
    if (a.start) row.append(writeBtn('처리 시작', 'btn btn-primary', b => post(d, 'start', {}, b, '처리를 시작했습니다')));
    if (a.assign) {
      const sel = keep(pickSelect('담당자', '담당자 고르기', (d.assignees || []).map(p => [person(p), p.employeeId]), d.assignee && d.assignee.employeeId), d.id, 'assignee');
      row.append(sel, writeBtn(d.assignee ? '재배정' : '배정', 'btn', b => {
        if (!sel.value) { showToast('담당자를 골라 주세요'); sel.focus(); return; }
        post(d, 'assign', { assigneeId: Number(sel.value) }, b, '담당자를 정했습니다', ['assignee']);
      }));
    }
    if (row.childElementCount) blocks.push(h('div', 'inq-body', row));

    if (a.resolve && d.type === 'TK_EQUIPMENT' && !(d.stock || []).length) {
      // 장비 지급 건인데 재고가 없으면 처리 완료할 수 없다: 사유를 댓글로 남기고 처리 중으로 둔다(요청서)
      blocks.push(h('div', 'inq-body', h('p', 'muted','지급할 재고 장비가 없습니다. 아래 댓글로 사유를 남기고 처리 중으로 둡니다. 재고가 들어오면 이어서 처리 완료합니다.')));
    } else if (a.resolve) {
      // 처리 완료: 해결 결과는 꼭 적는다. 장비 지급 건은 지급할 재고 장비를 고른다
      const result = keep(textBox(`해결 결과 (${RESULT_MAX}자까지, 요청자에게 보입니다)`, RESULT_MAX, '해결 결과'), d.id, 'result');
      const stock = d.type === 'TK_EQUIPMENT'
        ? keep(pickSelect('지급할 장비', '지급할 장비 고르기', (d.stock || []).map(s => [assetLabel(s), s.id])), d.id, 'stock') : null;
      blocks.push(h('div', 'inq-body', ...(stock ? [stock] : []), result, h('div', 'inq-actions', writeBtn('처리 완료', 'btn btn-primary', b => {
        if (stock && !stock.value) { showToast('지급할 장비를 골라 주세요'); stock.focus(); return; }
        if (!result.value.trim()) { showToast('해결 결과를 적어 주세요'); result.focus(); return; }
        post(d, 'resolve', { result: result.value.trim(), ...(stock ? { assetId: Number(stock.value) } : {}) }, b, '처리 완료했습니다. 요청자가 확인하면 종료됩니다', ['result', 'stock']);
      }))));
    }

    if (a.reject) {
      // 반려: 사유를 꼭 적는다. 일반 장애·문의 건만이고, 장비 지급 건은 서버가 reject 를 주지 않는다(재고가 없으면 처리 중으로 둔다)
      const reason = keep(textBox(`반려 사유 (${REASON_MAX}자까지, 요청자에게 보입니다)`, REASON_MAX, '반려 사유'), d.id, 'reason');
      blocks.push(h('div', 'inq-body', reason, h('div', 'inq-actions', writeBtn('반려', 'btn btn-danger-line', b => {
        if (!reason.value.trim()) { showToast('반려 사유를 적어 주세요'); reason.focus(); return; }
        post(d, 'reject', { reason: reason.value.trim() }, b, '반려했습니다', ['reason']);
      }))));
    }
    return blocks;
  }

  function commentBlock(d) {
    const box = keep(textBox(`처리 내용이나 요청자에게 알릴 것 (${COMMENT_MAX}자까지)`, COMMENT_MAX, '댓글'), d.id, 'comment');
    return h('div', 'inq-body', box, h('div', 'inq-actions', writeBtn('댓글 남기기', 'btn', b => {
      if (!box.value.trim()) { box.focus(); return; }
      b.disabled = true;
      api.post(`/ticket/admin/api/tickets/${encodeURIComponent(d.id)}/comments`, { body: box.value.trim() })
        .done(() => { forget(d.id, 'comment'); showToast('댓글을 남겼습니다'); refresh(); })
        .fail(xhr => { showToast(apiMessage(xhr)); if (xhr.status === 409) refresh(); else b.disabled = false; });
    })));
  }

  // 상태 바꾸기: version 을 같이 보낸다. 409(다른 사람이 먼저 바꿈)면 새로 받는다(쓰던 값은 남긴다). 성공하면 그 동작에 쓴 값(used)만 지운다
  function post(d, action, body, btn, done, used = []) {
    btn.disabled = true;
    api.post(`/ticket/admin/api/tickets/${encodeURIComponent(d.id)}/${action}`, { ...body, version: d.version })
      .done(() => { forget(d.id, ...used); showToast(done); refresh(); })
      .fail(xhr => {
        showToast(xhr.status === 409 ? apiMessage(xhr, '다른 사람이 먼저 바꿨습니다. 새로 불러왔습니다') : apiMessage(xhr));
        if (xhr.status === 409) refresh(); else btn.disabled = false;
      });
  }
})();
