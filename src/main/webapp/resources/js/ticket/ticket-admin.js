// IT 서비스 > 티켓 처리 (views/ticket/admin/list.jsp). IT 담당·업체 관리자 (요구분석서 3.4, 통합 계획서 3장 B·C, 5장 티켓).
// 상태: 접수 → 배정 → 처리 중 → 처리 완료 → 종료(요청자 확인), 그 밖에 취소·반려. 누를 수 있는 버튼은 서버가 준 actions 로 정한다.
// 상태를 바꾸는 요청에는 받은 version 을 같이 보낸다. 그사이 다른 사람이 바꿨으면 서버가 409 로 거절하고 화면은 새로 받는다.
//
// 서버 API (화면이 먼저 정한 제안. ticket 모듈 담당이 정하면 그에 맞춘다):
//   GET  /ticket/admin/api/tickets?filter=&type=&q=&page=
//        filter: UNASSIGNED(미배정) | MINE(내 담당) | IN_PROGRESS(처리 중) | RESOLVED(확인 대기) | ALL
//        → { page, pages, counts: { UNASSIGNED, MINE, IN_PROGRESS, RESOLVED, ALL },
//            items: [{ id, type, title, requester: { name, dept }, branchName, assignee: { name } | null, status, at }] }
//   GET  /ticket/admin/api/tickets/{id}
//        → { id, type, title, body, status, version, at, requester: { name, dept }, branch: { name, address, detail },
//            asset: { name, serial } | null, equipmentKind (장비 지급 건) | null, assignee: { employeeId, name } | null,
//            history: [{ at, name, text }], comments: [{ at, name, body }],
//            actions: { assignSelf, assign, start, resolve, reject, comment },
//            assignees: [{ employeeId, name, dept }] (assign 일 때), stock: [{ id, name, serial }] (장비 지급 건 resolve 일 때) }
//   POST /ticket/admin/api/tickets/{id}/assign   { assigneeId (없으면 나), version }
//   POST /ticket/admin/api/tickets/{id}/start    { version }
//   POST /ticket/admin/api/tickets/{id}/resolve  { result, assetId (장비 지급 건), version }
//   POST /ticket/admin/api/tickets/{id}/reject   { reason, version }
//   POST /ticket/admin/api/tickets/{id}/comments { body }
// util.js 의 h, span, inqCell, emptyTr, whenFull, showToast 를 쓴다.
(() => {
  const TYPE = { INCIDENT: '장애', INQUIRY: '문의', EQUIPMENT: '장비 지급' };
  const STATUS = { RECEIVED: '접수', ASSIGNED: '배정', IN_PROGRESS: '처리 중', RESOLVED: '처리 완료', CLOSED: '종료', CANCELED: '취소', REJECTED: '반려' };
  const STATUS_CLASS = { RECEIVED: 'requested', ASSIGNED: 'requested', IN_PROGRESS: 'waiting', RESOLVED: 'paid', CLOSED: 'paid', CANCELED: 'expired', REJECTED: 'failed' };
  const EMPTY = {
    UNASSIGNED: '배정을 기다리는 티켓이 없습니다', MINE: '내가 맡은 티켓이 없습니다', IN_PROGRESS: '처리 중인 티켓이 없습니다',
    RESOLVED: '요청자 확인을 기다리는 티켓이 없습니다', ALL: '티켓이 없습니다',
  };
  const RESULT_MAX = 500;
  const REASON_MAX = 300;
  const COMMENT_MAX = 300;
  const COLS = 8;
  const $ = id => document.getElementById(id);
  const state = { filter: 'UNASSIGNED', type: '', q: '', page: 1, open: null, detail: null, seq: 0 };

  function openTicketAdmin() { load(); }
  window.openTicketAdmin = openTicketAdmin;

  // ---------- 목록 ----------
  function load() {
    const my = ++state.seq;
    api.get('/ticket/admin/api/tickets', { filter: state.filter, type: state.type, q: state.q, page: state.page })
      .done(r => { if (my === state.seq) render(r); }) // 조건을 빨리 바꾸면 늦게 온 앞 응답은 버린다
      .fail(xhr => showToast(apiMessage(xhr, '티켓 목록을 불러오지 못했습니다')));
  }

  function render(r) {
    Object.keys(EMPTY).forEach(k => { $(`taCnt${k}`).textContent = `${(r.counts || {})[k] ?? 0}건`; });
    document.querySelectorAll('#taFilters .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === state.filter));

    const rows = [];
    (r.items || []).forEach(t => {
      const isOpen = state.open === t.id;
      const tr = h('tr', 'row-link' + (isOpen ? ' is-open' : ''),
        inqCell('번호', span(t.id)),
        inqCell('구분', span(TYPE[t.type] || '-')),
        inqCell('제목', span(t.title)),
        inqCell('요청자', span(t.requester ? `${t.requester.name}${t.requester.dept ? ` (${t.requester.dept})` : ''}` : '-')),
        inqCell('접수 지점', span(t.branchName || '-')),
        inqCell('담당자', span(t.assignee ? t.assignee.name : '미배정', t.assignee ? '' : 'muted')),
        inqCell('상태', span(STATUS[t.status] || t.status, `status status-${STATUS_CLASS[t.status] || 'requested'}`)),
        inqCell('접수일', span(whenFull(t.at))));
      tr.addEventListener('click', () => toggle(t.id));
      rows.push(tr);
      if (isOpen) {
        const td = h('td', '', h('div', 'slide open', h('div', 'slide-inner', state.detail && state.detail.id === t.id ? detailView(state.detail) : span('불러오는 중…', 'muted'))));
        td.colSpan = COLS;
        rows.push(h('tr', 'row-detail', td));
      }
    });
    $('taList').replaceChildren(...(rows.length ? rows : [emptyTr(COLS, state.q || state.type ? '찾는 티켓이 없습니다' : EMPTY[state.filter])]));
    renderPager(r.page || 1, r.pages || 1);
    state.last = r;
  }

  function renderPager(page, pages) {
    const nav = $('taPager');
    const btn = (label, p, opts = {}) => {
      const b = h('button', '', label);
      b.type = 'button';
      if (opts.aria) b.setAttribute('aria-label', opts.aria);
      if (opts.current && p === page) b.setAttribute('aria-current', 'page');
      b.disabled = !!opts.disabled;
      b.addEventListener('click', () => { state.page = p; state.open = null; load(); });
      return b;
    };
    // 지금 페이지 앞뒤 두 쪽만 번호로 보인다 (페이지가 많아도 버튼이 넘치지 않게)
    const from = Math.max(1, page - 2);
    const to = Math.min(pages, page + 2);
    const items = [btn('이전', page - 1, { disabled: page <= 1, aria: '이전 페이지' })];
    for (let p = from; p <= to; p++) items.push(btn(String(p), p, { current: true, aria: `${p}페이지` }));
    items.push(btn('다음', page + 1, { disabled: page >= pages, aria: '다음 페이지' }));
    nav.replaceChildren(...items);
    nav.hidden = pages <= 1;
  }

  document.querySelectorAll('#taFilters .card-filter').forEach(b => b.addEventListener('click', () => {
    state.filter = b.dataset.filter;
    state.page = 1;
    state.open = null;
    load();
  }));
  $('taSearchForm').addEventListener('submit', e => {
    e.preventDefault();
    state.type = $('taType').value;
    state.q = $('taSearch').value.trim();
    state.page = 1;
    state.open = null;
    load();
  });
  $('taType').addEventListener('change', () => $('taSearchForm').requestSubmit());

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
      .fail(xhr => { showToast(apiMessage(xhr, '티켓을 불러오지 못했습니다')); state.open = null; render(state.last); });
  }

  // 상태를 바꾼 뒤(또는 409 로 거절된 뒤) 목록과 열린 상세를 새로 받는다
  function refresh() {
    const id = state.open;
    state.detail = null;
    load();
    if (id) loadDetail(id);
  }

  function detailView(d) {
    const info = h('dl', 'detail');
    const branch = d.branch ? [d.branch.name, d.branch.address, d.branch.detail].filter(Boolean).join(' · ') : '-';
    [['번호', d.id], ['구분', TYPE[d.type] || '-'], ['요청자', d.requester ? `${d.requester.name}${d.requester.dept ? ` (${d.requester.dept})` : ''}` : '-'],
      ['접수 지점', branch],
      d.type === 'EQUIPMENT' ? ['요청 장비', d.equipmentKind || '-'] : ['관련 자산', d.asset ? `${d.asset.name} (${d.asset.serial})` : '-'],
      ['담당자', d.assignee ? d.assignee.name : '미배정'], ['상태', STATUS[d.status] || d.status], ['제목', d.title], ['내용', d.body || '-']]
      .forEach(([k, v]) => info.append(h('dt', '', k), h('dd', '', v)));

    const left = h('section', 'info-card', h('header', 'info-card-head', h('h2', '', '접수 내용')), info);
    const right = h('section', 'info-card');
    const actions = actionBlocks(d);
    if (actions.length) right.append(h('header', 'info-card-head', h('h2', '', '처리')), ...actions);
    right.append(h('header', 'info-card-head', h('h2', '', '처리 이력')),
      h('ul', 'bill-history', ...((d.history || []).length ? d.history.map(x => h('li', '', span(`${whenFull(x.at)} · ${x.name} · ${x.text}`)))
        : [h('li', '', span('아직 이력이 없습니다', 'muted'))])),
      h('header', 'info-card-head', h('h2', '', '댓글')),
      h('ul', 'bill-history', ...((d.comments || []).length ? d.comments.map(c => h('li', '', span(`${whenFull(c.at)} · ${c.name} · ${c.body}`)))
        : [h('li', '', span('아직 댓글이 없습니다', 'muted'))])));
    if (d.actions && d.actions.comment) right.append(commentBlock(d));
    // 상세 안을 눌러도 줄이 접히지 않게
    const grid = h('div', 'inq-grid', left, h('div', '', right));
    grid.addEventListener('click', e => e.stopPropagation());
    return grid;
  }

  // 서버가 준 actions 마다 처리 칸을 만든다
  function actionBlocks(d) {
    const a = d.actions || {};
    const blocks = [];
    const row = h('div', 'inq-actions');
    if (a.assignSelf) row.append(button('나에게 배정', 'btn btn-primary', b => post(d, 'assign', { assigneeId: null }, b, '나에게 배정했습니다')));
    if (a.start) row.append(button('처리 시작', 'btn btn-primary', b => post(d, 'start', {}, b, '처리를 시작했습니다')));
    if (a.assign) {
      const sel = h('select', 'input ta-assignee', h('option', '', '담당자 고르기'), ...(d.assignees || []).map(p => {
        const o = h('option', '', `${p.name}${p.dept ? ` (${p.dept})` : ''}`);
        o.value = p.employeeId;
        if (d.assignee && d.assignee.employeeId === p.employeeId) o.selected = true;
        return o;
      }));
      sel.firstChild.value = '';
      sel.setAttribute('aria-label', '담당자');
      row.append(sel, button(d.assignee ? '재배정' : '배정', 'btn', b => {
        if (!sel.value) { showToast('담당자를 골라 주세요'); sel.focus(); return; }
        post(d, 'assign', { assigneeId: Number(sel.value) }, b, '담당자를 정했습니다');
      }));
    }
    if (row.childElementCount) blocks.push(h('div', 'inq-body', row));

    if (a.resolve) {
      // 처리 완료: 해결 결과는 꼭 적는다. 장비 지급 건은 지급할 재고 장비를 고른다
      const result = textarea(`해결 결과 (${RESULT_MAX}자까지, 요청자에게 보입니다)`, RESULT_MAX, '해결 결과');
      const parts = [result];
      let stock = null;
      if (d.type === 'EQUIPMENT') {
        stock = h('select', 'input', h('option', '', (d.stock || []).length ? '지급할 장비 고르기' : '지급할 재고 장비가 없습니다'),
          ...(d.stock || []).map(s => { const o = h('option', '', `${s.name} (${s.serial})`); o.value = s.id; return o; }));
        stock.firstChild.value = '';
        stock.setAttribute('aria-label', '지급할 장비');
        parts.unshift(stock);
      }
      parts.push(h('div', 'inq-actions', button('처리 완료', 'btn btn-primary', b => {
        if (stock && !stock.value) { showToast('지급할 장비를 골라 주세요'); stock.focus(); return; }
        if (!result.value.trim()) { showToast('해결 결과를 적어 주세요'); result.focus(); return; }
        post(d, 'resolve', { result: result.value.trim(), assetId: stock ? Number(stock.value) : null }, b, '처리 완료했습니다. 요청자가 확인하면 종료됩니다');
      })));
      blocks.push(h('div', 'inq-body', ...parts));
    }

    if (a.reject) {
      // 반려: 사유를 꼭 적는다. 승인된 장비 지급 건도 지급할 수 없으면 사유를 남겨 반려한다
      const reason = textarea(`반려 사유 (${REASON_MAX}자까지, 요청자에게 보입니다)`, REASON_MAX, '반려 사유');
      blocks.push(h('div', 'inq-body', reason, h('div', 'inq-actions', button('반려', 'btn btn-danger-line', b => {
        if (!reason.value.trim()) { showToast('반려 사유를 적어 주세요'); reason.focus(); return; }
        post(d, 'reject', { reason: reason.value.trim() }, b, '반려했습니다');
      }))));
    }
    return blocks;
  }

  function commentBlock(d) {
    const box = textarea(`처리 내용이나 요청자에게 알릴 것 (${COMMENT_MAX}자까지)`, COMMENT_MAX, '댓글');
    return h('div', 'inq-body', box, h('div', 'inq-actions', button('댓글 남기기', 'btn', b => {
      if (!box.value.trim()) { box.focus(); return; }
      b.disabled = true;
      api.post(`/ticket/admin/api/tickets/${encodeURIComponent(d.id)}/comments`, { body: box.value.trim() })
        .done(() => { showToast('댓글을 남겼습니다'); refresh(); })
        .fail(xhr => { showToast(apiMessage(xhr)); b.disabled = false; });
    })));
  }

  // 상태 바꾸기: version 을 같이 보낸다. 409(다른 사람이 먼저 바꿈)면 새로 받는다
  function post(d, action, body, btn, done) {
    btn.disabled = true;
    api.post(`/ticket/admin/api/tickets/${encodeURIComponent(d.id)}/${action}`, { ...body, version: d.version })
      .done(() => { showToast(done); refresh(); })
      .fail(xhr => {
        showToast(xhr.status === 409 ? apiMessage(xhr, '다른 사람이 먼저 바꿨습니다. 새로 불러왔습니다') : apiMessage(xhr));
        if (xhr.status === 409) refresh(); else btn.disabled = false;
      });
  }

  // 쓰는 버튼: 읽기 전용 업체에서는 흐리게(data-write)
  function button(label, cls, onClick) {
    const b = h('button', cls, label);
    b.type = 'button';
    b.dataset.write = '';
    b.addEventListener('click', () => onClick(b));
    return b;
  }
  function textarea(placeholder, max, label) {
    const t = h('textarea', 'inq-text');
    t.maxLength = max;
    t.placeholder = placeholder;
    t.setAttribute('aria-label', label);
    return t;
  }
})();
