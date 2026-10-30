// IT 서비스(사원): 장애·문의 접수(ticket-new), 내 티켓(ticket-mine), 내 장비(asset-mine) (요구분석서 3.4 IT 티켓·자산, 지점).
// 사원은 본인이 접수한 티켓과 본인에게 지급된 장비만 본다. 티켓을 처리하는 IT 담당자 화면(티켓 처리)과 자산 관리는 이 파일에 없다.
// 접수한 티켓의 지점은 접수 때 값으로 저장되므로, 나중에 자산이 다른 지점으로 옮겨져도 이미 접수한 티켓의 지점은 바뀌지 않는다.
// 장비 지급 신청서(전자결재)가 최종 승인되면 EQUIPMENT 티켓이 만들어진다 (approval/approval.js).
// 서버 API (프론트 제안):
//   GET   /ticket/api/form-options          접수에 필요한 것
//         → { assets: [{ id, name, serial, branchId }](내 장비), branches: [{ id, name }], defaultBranchId }
//   GET   /ticket/api/tickets               내가 접수한 티켓, 최신순 → [티켓]
//   POST  /ticket/api/tickets               { type: INCIDENT|INQUIRY, title, body, branchId, assetId('' = 해당 없음) } → 티켓. 읽기 전용 업체는 403
//   POST  /ticket/api/tickets/{id}/comments { body } → 티켓. 완료된 티켓은 409
//   GET   /asset/api/assets                 내 장비 → { items: [{ id, name, serial, branchName, assignedAt, ticketId }],
//                                                       waiting: [{ id, kind, qty, branchName, status }](지급 대기 중인 장비 지급 티켓) }
// 티켓: { id, type: INCIDENT|INQUIRY|EQUIPMENT, title, body, branchId, branchName, asset: { id, name, serial } | null,
//         status: RECEIVED|ASSIGNED|IN_PROGRESS|RESOLVED|CLOSED|CANCELED|REJECTED (AGENTS.md 티켓 상태), at, kind, qty, history: [{ at, name, text }], comments: [{ at, name, body }], canComment }

const TICKET_TYPE = { INCIDENT: '장애', INQUIRY: '문의', EQUIPMENT: '장비 지급' };
// 티켓 상태 (AGENTS.md): 접수 → 배정 → 처리 중 → 처리 완료 → 종료(요청자 확인), 그 밖에 취소·반려
const TICKET_STATUS = {
  RECEIVED: '접수', ASSIGNED: '배정', IN_PROGRESS: '처리 중', RESOLVED: '처리 완료', CLOSED: '종료', CANCELED: '취소', REJECTED: '반려',
};
const TICKET_CLASS = {
  RECEIVED: 'requested', ASSIGNED: 'requested', IN_PROGRESS: 'waiting', RESOLVED: 'paid', CLOSED: 'paid', CANCELED: 'expired', REJECTED: 'failed',
};
// 내 티켓 요약 카드는 세 묶음으로 센다: 접수(접수·배정), 처리 중, 완료(처리 완료·종료·취소·반려)
const TICKET_GROUP = {
  RECEIVED: 'RECEIVED', ASSIGNED: 'RECEIVED', IN_PROGRESS: 'IN_PROGRESS', RESOLVED: 'DONE', CLOSED: 'DONE', CANCELED: 'DONE', REJECTED: 'DONE',
};
const TICKET_TITLE_MAX = 60;
const TICKET_BODY_MAX = 1000;
const TICKET_COMMENT_MAX = 300;

const itEl = id => document.getElementById(id);
const itNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const itCell = (label, ...parts) => {
  const td = itNode('td', '', itNode('div', 'cell', ...parts));
  td.dataset.label = label; // 폰에서 값 앞에 붙는 열 이름
  return td;
};
const itNew = { opts: null };
const itMine = { filter: 'all', open: null, items: [] };
const itAsset = { items: [], waiting: [] };

// ---------- 장애·문의 접수 ----------
function openTicketNewView() {
  itEl('ticketError').textContent = '';
  api.get('/ticket/api/form-options')
    .done(o => {
      itNew.opts = o;
      itEl('ticketAsset').replaceChildren(new Option('(해당 없음)', ''), ...o.assets.map(a => new Option(`${a.name} (${a.serial})`, a.id)));
      itEl('ticketBranch').replaceChildren(...o.branches.map(b => new Option(b.name, b.id)));
      syncTicketBranch();
    })
    .fail(xhr => { itEl('ticketError').textContent = apiMessage(xhr, '접수에 필요한 정보를 불러오지 못했습니다'); });
}

// 접수 지점 기본값: 고른 자산이 있는 지점. 자산이 없으면 사원의 근무 지점(서버가 정한 기본 지점).
// 실제 장애가 난 곳이 다르면 사원이 바꿔 접수한다
function syncTicketBranch() {
  const o = itNew.opts || { assets: [], defaultBranchId: '' };
  const asset = o.assets.find(a => a.id === itEl('ticketAsset').value);
  const want = asset ? asset.branchId : o.defaultBranchId;
  if ([...itEl('ticketBranch').options].some(x => x.value === want)) itEl('ticketBranch').value = want;
  itEl('ticketBranchHint').textContent = asset ? '고른 자산이 있는 지점으로 채웠습니다. 장애가 난 곳이 다르면 바꿔 주세요' : '근무 지점으로 채웠습니다. 장애가 난 곳이 다르면 바꿔 주세요';
}
itEl('ticketAsset')?.addEventListener('change', syncTicketBranch);

itEl('ticketForm')?.addEventListener('submit', e => {
  e.preventDefault();
  const err = itEl('ticketError');
  const type = itEl('ticketType').value;
  const title = itEl('ticketTitle').value.trim();
  const body = itEl('ticketBody').value.trim();
  const branchId = itEl('ticketBranch').value;
  // 서버도 같은 검사를 다시 한다. 화면은 먼저 알려 줄 뿐이다
  if (!['INCIDENT', 'INQUIRY'].includes(type)) { err.textContent = '구분을 골라 주세요'; return; }
  if (!title) { err.textContent = '제목을 입력해 주세요'; itEl('ticketTitle').focus(); return; }
  if (title.length > TICKET_TITLE_MAX) { err.textContent = `제목은 ${TICKET_TITLE_MAX}자까지 쓸 수 있습니다`; return; }
  if (!body) { err.textContent = '내용을 입력해 주세요'; itEl('ticketBody').focus(); return; }
  if (body.length > TICKET_BODY_MAX) { err.textContent = `내용은 ${TICKET_BODY_MAX}자까지 쓸 수 있습니다`; return; }
  if (!branchId) { err.textContent = '접수 지점을 골라 주세요'; return; }
  const btn = itEl('ticketForm').querySelector('[type="submit"]');
  if (btn) btn.disabled = true; // 두 번 눌러 두 건이 생기지 않게
  api.post('/ticket/api/tickets', { type, title, body, branchId, assetId: itEl('ticketAsset').value })
    .done(t => {
      itEl('ticketForm').reset();
      err.textContent = '';
      itMine.open = t.id; // 접수하면 방금 만든 티켓이 열린 채로 내 티켓으로 간다
      itMine.filter = 'all';
      showToast(`${t.id} 접수했습니다. IT 담당자에게 전달됩니다`);
      location.href = pageHref('/ticket/mine');
    })
    .fail(xhr => { err.textContent = apiMessage(xhr); })
    .always(() => { if (btn) btn.disabled = false; });
});

// ---------- 내 티켓 ----------
function openTicketMineView() { loadTicketMine(); }

function loadTicketMine() {
  api.get('/ticket/api/tickets')
    .done(items => { itMine.items = items; renderTicketMine(); })
    .fail(xhr => showToast(apiMessage(xhr, '티켓 목록을 불러오지 못했습니다')));
}

function renderTicketMine() {
  const mine = itMine.items;
  const count = st => mine.filter(t => st === 'all' || TICKET_GROUP[t.status] === st).length;
  [['all', 'ticketCntAll'], ['RECEIVED', 'ticketCntReceived'], ['IN_PROGRESS', 'ticketCntProgress'], ['DONE', 'ticketCntDone']]
    .forEach(([st, id]) => { itEl(id).textContent = `${count(st)}건`; });
  document.querySelectorAll('#ticketFilters .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === itMine.filter));

  const shown = mine.filter(t => itMine.filter === 'all' || TICKET_GROUP[t.status] === itMine.filter);
  const rows = [];
  shown.forEach(t => {
    const isOpen = itMine.open === t.id;
    const tr = itNode('tr', 'row-link' + (isOpen ? ' is-open' : ''),
      itCell('번호', itNode('span', '', t.id)),
      itCell('구분', itNode('span', '', TICKET_TYPE[t.type] || '-')),
      itCell('제목', itNode('span', '', t.title)),
      itCell('접수 지점', itNode('span', '', t.branchName)),
      itCell('상태', itNode('span', `status status-${TICKET_CLASS[t.status]}`, TICKET_STATUS[t.status])),
      itCell('접수일', itNode('span', '', whenFull(t.at))));
    tr.addEventListener('click', () => { itMine.open = isOpen ? null : t.id; renderTicketMine(); });
    rows.push(tr);
    if (isOpen) {
      const td = itNode('td', '', itNode('div', 'slide open', itNode('div', 'slide-inner', ticketDetail(t))));
      td.colSpan = 6;
      rows.push(itNode('tr', 'row-detail', td));
    }
  });
  if (!rows.length) {
    const td = itNode('td', '', mine.length ? '해당하는 티켓이 없습니다' : '아직 접수한 티켓이 없습니다. 장애·문의 접수에서 접수해 주세요');
    td.colSpan = 6;
    rows.push(itNode('tr', 'row-empty', td));
  }
  itEl('ticketList').replaceChildren(...rows);
}
document.querySelectorAll('#ticketFilters .card-filter').forEach(b => b.addEventListener('click', () => {
  itMine.filter = b.dataset.filter;
  renderTicketMine();
}));

function ticketDetail(t) {
  const info = itNode('dl', 'detail');
  [['번호', t.id], ['구분', TICKET_TYPE[t.type] || '-'], ['접수 지점', t.branchName],
    ['관련 자산', t.asset ? `${t.asset.name} (${t.asset.serial})` : '-'], ['제목', t.title], ['내용', t.body]]
    .forEach(([k, v]) => info.append(itNode('dt', '', k), itNode('dd', '', v)));

  const log = itNode('ul', 'bill-history', ...t.history.map(h => itNode('li', '', itNode('span', '', `${whenFull(h.at)} · ${h.name} · ${h.text}`))));
  const comments = itNode('ul', 'bill-history', ...(t.comments.length
    ? t.comments.map(c => itNode('li', '', itNode('span', '', `${whenFull(c.at)} · ${c.name} · ${c.body}`)))
    : [itNode('li', '', itNode('span', 'muted', '아직 댓글이 없습니다'))]));

  const parts = [itNode('section', 'info-card', itNode('header', 'info-card-head', itNode('h2', '', '접수 내용')), info),
    itNode('section', 'info-card', itNode('header', 'info-card-head', itNode('h2', '', '처리 이력')), log,
      itNode('header', 'info-card-head', itNode('h2', '', '댓글')), comments)];

  if (t.canComment) {
    // 완료되면 더 쓰지 않는다. 처리 중에도 사원이 추가로 알릴 것이 있으면 댓글로 남긴다
    const box = itNode('textarea', 'inq-text');
    box.maxLength = TICKET_COMMENT_MAX;
    box.placeholder = `추가로 알릴 내용 (${TICKET_COMMENT_MAX}자까지)`;
    box.setAttribute('aria-label', '댓글');
    const send = itButton('댓글 남기기', 'btn btn-primary');
    send.addEventListener('click', () => addComment(t.id, box.value, send));
    parts[1].append(itNode('div', 'inq-body', box, itNode('div', 'inq-actions', send)));
  }
  return itNode('div', 'inq-grid', parts[0], itNode('div', '', parts[1], ...(parts[2] ? [itNode('section', 'info-card', parts[2])] : [])));
}

// 쓰는 버튼: 읽기 전용 업체에서는 누르지 못하게 data-write 를 단다
function itButton(label, cls) {
  const b = itNode('button', cls, label);
  b.type = 'button';
  b.dataset.write = '';
  return b;
}

function addComment(id, bodyRaw, btn) {
  const body = bodyRaw.trim();
  if (!body) return showToast('댓글 내용을 입력해 주세요');
  if (body.length > TICKET_COMMENT_MAX) return showToast(`댓글은 ${TICKET_COMMENT_MAX}자까지 쓸 수 있습니다`);
  btn.disabled = true;
  api.post(`/ticket/api/tickets/${encodeURIComponent(id)}/comments`, { body })
    .done(() => loadTicketMine())
    .fail(xhr => { showToast(apiMessage(xhr)); if (xhr.status === 409) loadTicketMine(); })
    .always(() => { btn.disabled = false; });
}


// ---------- 내 장비 ----------
function openAssetMineView() { loadAssetMine(); }

function loadAssetMine() {
  api.get('/asset/api/assets')
    .done(r => { itAsset.items = r.items; itAsset.waiting = r.waiting; renderAssetMine(); })
    .fail(xhr => showToast(apiMessage(xhr, '내 장비를 불러오지 못했습니다')));
}

function renderAssetMine() {
  const { items: assets, waiting } = itAsset;
  itEl('assetCntUse').textContent = `${assets.length}개`;
  itEl('assetCntWait').textContent = `${waiting.length}건`;

  const emptyRow = (cols, label) => {
    const td = itNode('td', '', label);
    td.colSpan = cols;
    return itNode('tr', 'row-empty', td);
  };
  itEl('assetList').replaceChildren(...(assets.length ? assets.map(a => itNode('tr', '',
    itCell('장비', itNode('span', '', a.name)),
    itCell('일련번호', itNode('span', '', a.serial)),
    itCell('배치 지점', itNode('span', '', a.branchName)),
    itCell('지급일', itNode('span', '', ymd(a.assignedAt))),
    itCell('지급 티켓', itNode('span', '', a.ticketId || '-')))) : [emptyRow(5, '지급된 장비가 없습니다')]));
  itEl('assetWaitList').replaceChildren(...(waiting.length ? waiting.map(t => itNode('tr', '',
    itCell('티켓', itNode('span', '', t.id)),
    itCell('신청 장비', itNode('span', '', `${t.kind} ${t.qty}대`)),
    itCell('수령 지점', itNode('span', '', t.branchName)),
    itCell('상태', itNode('span', `status status-${TICKET_CLASS[t.status]}`, TICKET_STATUS[t.status])))) : [emptyRow(4, '지급을 기다리는 신청이 없습니다')]));
}
itEl('btnAssetRequest')?.addEventListener('click', () => {
  startApprovalWrite('EQUIP'); // 전자결재 장비 지급 신청서로 간다 (util.js)
});
