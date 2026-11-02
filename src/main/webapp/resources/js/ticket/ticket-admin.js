// IT 서비스 > 티켓 처리 (views/ticket/admin/list.jsp). IT 담당·업체 관리자 (요구분석서 3.4, 통합 계획서 3장 B·C, 5장 티켓).
// 상태: 접수 → 배정 → 처리 중 → 처리 완료 → 종료(요청자 확인, 내 티켓 화면), 그 밖에 취소·반려. 누를 수 있는 버튼은 서버가 준 actions 로 정한다.
// 상태를 바꾸는 요청에는 받은 version 을 같이 보낸다. 그사이 다른 사람이 바꿨으면 서버가 409 로 거절하고 화면은 새로 받는다.
// 주소·값 이름은 화면이 먼저 정한 제안이다(ticket 모듈 담당이 정하면 그에 맞춘다, AGENTS.md '수정 범위').
// 요청서 범위만 둔다: 상태 카드로 거르는 목록, 배정, 처리 시작, 처리 완료(장비 지급 건은 재고 지급), 반려, 댓글. 검색·페이지 번호는 없다.
// util.js 의 h, span, inqCell, titleBtn, dataRow, detailTr, emptyTr, dlOf, cardOf, writeBtn, textBox, pickSelect, historyList 를 쓴다.
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
  const state = { filter: 'UNASSIGNED', open: null, detail: null, seq: 0, last: null };

  function openTicketAdmin() { load(); }
  window.openTicketAdmin = openTicketAdmin;

  // ---------- 목록 ----------
  function load() {
    const my = ++state.seq;
    api.get('/ticket/admin/api/tickets', { filter: state.filter })
      .done(r => { if (my === state.seq) render(r); }) // 카드를 빨리 바꾸면 늦게 온 앞 응답은 버린다
      .fail(xhr => showToast(apiMessage(xhr, '티켓 목록을 불러오지 못했습니다')));
  }

  const person = p => (p ? `${p.name}${p.dept ? ` (${p.dept})` : ''}` : '-');

  function render(r) {
    state.last = r;
    Object.keys(EMPTY).forEach(k => { $(`taCnt${k}`).textContent = `${(r.counts || {})[k] ?? 0}건`; });
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
        inqCell('구분', span(TYPE[t.type] || '-')),
        inqCell('제목', titleBtn(t.title, isOpen)),
        inqCell('요청자', span(person(t.requester))),
        inqCell('접수 지점', span(t.branchName || '-')),
        inqCell('담당자', span(t.assignee ? t.assignee.name : '미배정', t.assignee ? '' : 'muted')),
        inqCell('상태', span(STATUS[t.status] || t.status, `status status-${STATUS_CLASS[t.status] || 'requested'}`)),
        inqCell('접수일', span(whenFull(t.at))));
      return isOpen ? [tr, detailTr(COLS, d && d.id === t.id ? detailView(d) : span('불러오는 중…', 'muted'))] : [tr];
    });
    $('taList').replaceChildren(...(rows.length ? rows : [emptyTr(COLS, EMPTY[state.filter])]));
  }

  // 카드를 누르면 그 조건으로 다시 받고, 열린 상세는 닫는다
  document.querySelectorAll('#taFilters .card-filter').forEach(b => b.addEventListener('click', () => {
    Object.assign(state, { filter: b.dataset.filter, open: null });
    load();
  }));

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

  // 상태를 바꾼 뒤(또는 409 로 거절된 뒤) 상세를 먼저 새로 받고 목록을 받는다(목록에서 빠진 열린 티켓을 위에 남기려고)
  function refresh() {
    const id = state.open;
    if (!id) { load(); return; }
    api.get(`/ticket/admin/api/tickets/${encodeURIComponent(id)}`)
      .done(d => { if (state.open === id) state.detail = d; })
      .fail(() => { state.open = null; state.detail = null; })
      .always(load);
  }

  function detailView(d) {
    const branch = d.branch ? [d.branch.name, d.branch.address, d.branch.detail].filter(Boolean).join(' · ') : '-';
    const left = cardOf('접수 내용', dlOf([['번호', d.id], ['구분', TYPE[d.type] || '-'], ['요청자', person(d.requester)], ['접수 지점', branch],
      d.type === 'EQUIPMENT' ? ['요청 장비', d.equipmentKind || '-'] : ['관련 자산', d.asset ? `${d.asset.name} (${d.asset.serial})` : '-'],
      ['담당자', d.assignee ? d.assignee.name : '미배정'], ['상태', STATUS[d.status] || d.status], ['제목', d.title], ['내용', d.body || '-']]));

    const right = h('section', 'info-card');
    const actions = actionBlocks(d);
    if (actions.length) right.append(h('header', 'info-card-head', h('h2', '', '처리')), ...actions);
    right.append(h('header', 'info-card-head', h('h2', '', '처리 이력')),
      historyList((d.history || []).map(x => span(`${whenFull(x.at)} · ${x.name} · ${x.text}`)), '아직 이력이 없습니다'),
      h('header', 'info-card-head', h('h2', '', '댓글')),
      historyList((d.comments || []).map(c => span(`${whenFull(c.at)} · ${c.name} · ${c.body}`)), '아직 댓글이 없습니다'));
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
    if (a.assignSelf) row.append(writeBtn('나에게 배정', 'btn btn-primary', b => post(d, 'assign', { assigneeId: null }, b, '나에게 배정했습니다. 이어서 처리 시작을 누르세요')));
    if (a.start) row.append(writeBtn('처리 시작', 'btn btn-primary', b => post(d, 'start', {}, b, '처리를 시작했습니다')));
    if (a.assign) {
      const sel = pickSelect('담당자', '담당자 고르기', (d.assignees || []).map(p => [person(p), p.employeeId]), d.assignee && d.assignee.employeeId);
      row.append(sel, writeBtn(d.assignee ? '재배정' : '배정', 'btn', b => {
        if (!sel.value) { showToast('담당자를 골라 주세요'); sel.focus(); return; }
        post(d, 'assign', { assigneeId: Number(sel.value) }, b, '담당자를 정했습니다');
      }));
    }
    if (row.childElementCount) blocks.push(h('div', 'inq-body', row));

    if (a.resolve && d.type === 'EQUIPMENT' && !(d.stock || []).length) {
      // 장비 지급 건인데 재고가 없으면 처리 완료할 수 없다: 사유를 댓글로 남기고 처리 중으로 둔다(요청서)
      blocks.push(h('div', 'inq-body', h('p', 'muted','지급할 재고 장비가 없습니다. 아래 댓글로 사유를 남기고 처리 중으로 둡니다. 재고가 들어오면 이어서 처리 완료합니다.')));
    } else if (a.resolve) {
      // 처리 완료: 해결 결과는 꼭 적는다. 장비 지급 건은 지급할 재고 장비를 고른다
      const result = textBox(`해결 결과 (${RESULT_MAX}자까지, 요청자에게 보입니다)`, RESULT_MAX, '해결 결과');
      const stock = d.type === 'EQUIPMENT'
        ? pickSelect('지급할 장비', '지급할 장비 고르기', (d.stock || []).map(s => [`${s.name} (${s.serial})`, s.id])) : null;
      blocks.push(h('div', 'inq-body', ...(stock ? [stock] : []), result, h('div', 'inq-actions', writeBtn('처리 완료', 'btn btn-primary', b => {
        if (stock && !stock.value) { showToast('지급할 장비를 골라 주세요'); stock.focus(); return; }
        if (!result.value.trim()) { showToast('해결 결과를 적어 주세요'); result.focus(); return; }
        post(d, 'resolve', { result: result.value.trim(), assetId: stock ? Number(stock.value) : null }, b, '처리 완료했습니다. 요청자가 확인하면 종료됩니다');
      }))));
    }

    if (a.reject) {
      // 반려: 사유를 꼭 적는다. 승인된 장비 지급 건도 지급할 수 없으면 사유를 남겨 반려한다
      const reason = textBox(`반려 사유 (${REASON_MAX}자까지, 요청자에게 보입니다)`, REASON_MAX, '반려 사유');
      blocks.push(h('div', 'inq-body', reason, h('div', 'inq-actions', writeBtn('반려', 'btn btn-danger-line', b => {
        if (!reason.value.trim()) { showToast('반려 사유를 적어 주세요'); reason.focus(); return; }
        post(d, 'reject', { reason: reason.value.trim() }, b, '반려했습니다');
      }))));
    }
    return blocks;
  }

  function commentBlock(d) {
    const box = textBox(`처리 내용이나 요청자에게 알릴 것 (${COMMENT_MAX}자까지)`, COMMENT_MAX, '댓글');
    return h('div', 'inq-body', box, h('div', 'inq-actions', writeBtn('댓글 남기기', 'btn', b => {
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
})();
