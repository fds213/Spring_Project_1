// IT 서비스(사원): 장애·문의 접수(ticket-new), 내 티켓(ticket-mine), 내 장비(asset-mine) (요구분석서 3.4 IT 티켓·자산, 지점).
// 사원은 본인이 접수한 티켓과 본인에게 지급된 장비만 본다. 티켓을 처리하는 IT 담당자 화면(티켓 처리)과 자산 관리는 이 파일에 없다.
// 이음 운영자에게 보내는 "이음 장애 문의"는 우리 회사 IT 티켓과 다른 것이라 ieum-inquiry.js 에서 따로 한다 (요구분석서 3.7 문의 구분).
//
// 지점은 업체 관리자가 등록하는 주소 목록이다 (3.4 지점). 지점 관리 화면이 아직 없어서 업체별 저장값이 있으면 그것을,
// 없으면 시연용 목록(본사와 지점 2곳)을 쓴다. 접수한 티켓의 지점은 접수 때 값을 복사해 저장하므로, 나중에 자산이 다른 지점으로
// 옮겨져도 이미 접수한 티켓의 지점은 바뀌지 않는다.
// 시안이라 localStorage 에 업체별로 저장하고, 실제 서버는 같은 규칙(본인 것만 조회, 접수 지점 저장, 읽기 전용 차단)을 다시 검사한다.
// 이 파일은 attendance.js 뒤, common.js 앞에 로드된다 (loadStore, saveStore, session, showToast, readOnly, myStaffRecord 등은 앞 파일에서 온다).

const BRANCH_KEY = 'ieum-demo-branches-v1'; // { 업체코드: [{ id, name, address, active }] }
const TICKET_KEY = 'ieum-demo-tickets-v1'; // { 업체코드: [{ id, type, title, body, branchId, assetId, empNo, name, dept, status, at, history, comments, ... }] }
const ASSET_KEY = 'ieum-demo-assets-v1'; // { 업체코드: [{ id, name, serial, branchId, empNo, assignedAt, ticketId }] }

const DEFAULT_BRANCHES = [
  { id: 'B1', name: '본사', address: '서울특별시 중구 세종대로 110' },
  { id: 'B2', name: '부산 지점', address: '부산광역시 해운대구 센텀중앙로 79' },
  { id: 'B3', name: '대전 지점', address: '대전광역시 유성구 대학로 99' },
];
const allBranches = code => loadStore(BRANCH_KEY, {})[code] || DEFAULT_BRANCHES;
// 새로 고르는 목록에서는 사용 중지한 지점이 빠지지만, 이미 쌓인 기록에는 이름이 남는다
const branchesOf = code => allBranches(code).filter(b => b.active !== false);
const branchNameOf = (code, id) => (allBranches(code).find(b => b.id === id) || {}).name || '-';

// 장비 지급 신청서(전자결재)가 최종 승인되면 EQUIPMENT 티켓이 만들어진다 (eapproval.js)
const TICKET_TYPE = { INCIDENT: '장애', INQUIRY: '문의', EQUIPMENT: '장비 지급' };
const TICKET_STATUS = { RECEIVED: '접수', IN_PROGRESS: '처리 중', DONE: '완료' };
const TICKET_CLASS = { RECEIVED: 'requested', IN_PROGRESS: 'waiting', DONE: 'paid' };
const TICKET_TITLE_MAX = 60;
const TICKET_BODY_MAX = 1000;

const itEl = id => document.getElementById(id);
const itNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const itCell = (label, ...parts) => {
  const td = itNode('td', '', itNode('div', 'cell', ...parts));
  td.dataset.label = label; // 폰에서 값 앞에 붙는 열 이름
  return td;
};

const ticketsOf = code => loadStore(TICKET_KEY, {})[code] || [];
const myTickets = () => ticketsOf(session.company).filter(t => t.empNo === session.id);
const assetsOf = code => loadStore(ASSET_KEY, {})[code] || [];
const myAssets = () => assetsOf(session.company).filter(a => a.empNo === session.id);

// 티켓 한 건을 저장한다. 다른 사람의 신청을 승인할 때(전자결재의 장비 지급)도 쓰므로 업체 코드와 대상 직원을 받는다
function addTicket(code, f) {
  const all = loadStore(TICKET_KEY, {});
  const list = (all[code] = all[code] || []);
  const now = new Date().toISOString();
  const t = {
    id: `T-${String(list.length + 1).padStart(4, '0')}`, status: 'RECEIVED', at: now, assetId: '', ...f,
    history: [{ at: now, action: 'CREATE', by: f.empNo, name: f.name, text: '접수했습니다' }], comments: [],
  };
  list.push(t);
  saveStore(TICKET_KEY, all);
  return t;
}

// ---------- 장애·문의 접수 ----------
function openTicketNewView() {
  const assets = myAssets();
  itEl('ticketAsset').replaceChildren(new Option('(해당 없음)', ''), ...assets.map(a => new Option(`${a.name} (${a.serial})`, a.id)));
  itEl('ticketBranch').replaceChildren(...branchesOf(session.company).map(b => new Option(b.name, b.id)));
  syncTicketBranch();
  itEl('ticketError').textContent = '';
}

// 접수 지점 기본값: 고른 자산이 있는 지점. 자산이 없으면 사원의 근무 지점인데, 근무 지점 항목이 아직 없어서 첫 지점을 쓴다.
// 실제 장애가 난 곳이 다르면 사원이 바꿔 접수한다
function syncTicketBranch() {
  const asset = assetsOf(session.company).find(a => a.id === itEl('ticketAsset').value);
  const want = asset ? asset.branchId : (branchesOf(session.company)[0] || {}).id;
  if ([...itEl('ticketBranch').options].some(o => o.value === want)) itEl('ticketBranch').value = want;
  itEl('ticketBranchHint').textContent = asset ? '고른 자산이 있는 지점으로 채웠습니다. 장애가 난 곳이 다르면 바꿔 주세요' : '근무 지점으로 채웠습니다. 장애가 난 곳이 다르면 바꿔 주세요';
}
itEl('ticketAsset').addEventListener('change', syncTicketBranch);

itEl('ticketForm').addEventListener('submit', e => {
  e.preventDefault();
  const err = itEl('ticketError');
  if (readOnly) { err.textContent = readOnlyMessage(); return; }
  const type = itEl('ticketType').value;
  const title = itEl('ticketTitle').value.trim();
  const body = itEl('ticketBody').value.trim();
  const branchId = itEl('ticketBranch').value;
  if (!['INCIDENT', 'INQUIRY'].includes(type)) { err.textContent = '구분을 골라 주세요'; return; }
  if (!title) { err.textContent = '제목을 입력해 주세요'; itEl('ticketTitle').focus(); return; }
  if (title.length > TICKET_TITLE_MAX) { err.textContent = `제목은 ${TICKET_TITLE_MAX}자까지 쓸 수 있습니다`; return; }
  if (!body) { err.textContent = '내용을 입력해 주세요'; itEl('ticketBody').focus(); return; }
  if (body.length > TICKET_BODY_MAX) { err.textContent = `내용은 ${TICKET_BODY_MAX}자까지 쓸 수 있습니다`; return; }
  if (!branchesOf(session.company).some(b => b.id === branchId)) { err.textContent = '접수 지점을 골라 주세요'; return; }
  const me = myStaffRecord();
  const t = addTicket(session.company, { type, title, body, branchId, assetId: itEl('ticketAsset').value, empNo: session.id, name: session.name, dept: me ? me.dept : '' });
  itEl('ticketForm').reset();
  err.textContent = '';
  itMine.open = t.id; // 접수하면 방금 만든 티켓이 열린 채로 내 티켓으로 간다
  itMine.filter = 'all';
  showToast(`${t.id} 접수했습니다. IT 담당자에게 전달됩니다`);
  location.hash = '#ticket-mine';
});

// ---------- 내 티켓 ----------
const itMine = { filter: 'all', open: null };
const dayTime = iso => (typeof whenFull === 'function' ? whenFull(iso) : iso);

function openTicketMineView() { renderTicketMine(); }

function renderTicketMine() {
  const mine = myTickets().slice().sort((a, b) => new Date(b.at) - new Date(a.at));
  const count = st => mine.filter(t => st === 'all' || t.status === st).length;
  [['all', 'ticketCntAll'], ['RECEIVED', 'ticketCntReceived'], ['IN_PROGRESS', 'ticketCntProgress'], ['DONE', 'ticketCntDone']]
    .forEach(([st, id]) => { itEl(id).textContent = `${count(st)}건`; });
  document.querySelectorAll('#ticketFilters .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === itMine.filter));

  const shown = mine.filter(t => itMine.filter === 'all' || t.status === itMine.filter);
  const rows = [];
  shown.forEach(t => {
    const isOpen = itMine.open === t.id;
    const tr = itNode('tr', 'row-link' + (isOpen ? ' is-open' : ''),
      itCell('번호', itNode('span', '', t.id)),
      itCell('구분', itNode('span', '', TICKET_TYPE[t.type] || '-')),
      itCell('제목', itNode('span', '', t.title)),
      itCell('접수 지점', itNode('span', '', branchNameOf(session.company, t.branchId))),
      itCell('상태', itNode('span', `status status-${TICKET_CLASS[t.status]}`, TICKET_STATUS[t.status])),
      itCell('접수일', itNode('span', '', dayTime(t.at))));
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
  const asset = assetsOf(session.company).find(a => a.id === t.assetId);
  const info = itNode('dl', 'detail');
  [['번호', t.id], ['구분', TICKET_TYPE[t.type] || '-'], ['접수 지점', branchNameOf(session.company, t.branchId)],
    ['관련 자산', asset ? `${asset.name} (${asset.serial})` : '-'], ['제목', t.title], ['내용', t.body]]
    .forEach(([k, v]) => info.append(itNode('dt', '', k), itNode('dd', '', v)));

  const log = itNode('ul', 'bill-history', ...t.history.map(h => itNode('li', '', itNode('span', '', `${dayTime(h.at)} · ${h.name || h.by} · ${h.text}`))));
  const comments = itNode('ul', 'bill-history', ...(t.comments.length
    ? t.comments.map(c => itNode('li', '', itNode('span', '', `${dayTime(c.at)} · ${c.name} · ${c.body}`)))
    : [itNode('li', '', itNode('span', 'muted', '아직 댓글이 없습니다'))]));

  const parts = [itNode('section', 'info-card', itNode('header', 'info-card-head', itNode('h2', '', '접수 내용')), info),
    itNode('section', 'info-card', itNode('header', 'info-card-head', itNode('h2', '', '처리 이력')), log,
      itNode('header', 'info-card-head', itNode('h2', '', '댓글')), comments)];

  if (t.status !== 'DONE') {
    // 완료되면 더 쓰지 않는다. 처리 중에도 사원이 추가로 알릴 것이 있으면 댓글로 남긴다
    const box = itNode('textarea', 'inq-text');
    box.maxLength = 300;
    box.placeholder = '추가로 알릴 내용 (300자까지)';
    box.setAttribute('aria-label', '댓글');
    const send = itNode('button', 'btn btn-primary', '댓글 남기기');
    send.type = 'button';
    send.dataset.write = '';
    send.addEventListener('click', () => addComment(t.id, box.value));
    parts[1].append(itNode('div', 'inq-body', box, itNode('div', 'inq-actions', send)));

    // 시연 전용: 실제로는 IT 담당자가 티켓 처리 화면에서 상태를 바꾼다 (이 시안에는 그 화면이 없다)
    const demo = itNode('div', 'inq-body', itNode('p', 'inq-hint', '시연 전용: 실제로는 IT 담당자가 티켓 처리 화면에서 바꿉니다.'));
    const acts = itNode('div', 'inq-actions');
    if (t.status === 'RECEIVED') acts.append(demoButton('처리 중으로 바꾸기', () => setTicketStatus(t.id, 'IN_PROGRESS')));
    acts.append(demoButton(t.type === 'EQUIPMENT' ? '장비 지급 완료로 처리' : '완료로 바꾸기', () => setTicketStatus(t.id, 'DONE')));
    demo.append(acts);
    parts.push(demo);
  }
  return itNode('div', 'inq-grid', parts[0], itNode('div', '', parts[1], ...(parts[2] ? [itNode('section', 'info-card', parts[2])] : [])));
}

function demoButton(label, onClick) {
  const b = itNode('button', 'btn', label);
  b.type = 'button';
  b.addEventListener('click', onClick);
  return b;
}

// 내 티켓 한 건을 새로 읽어 바꾼다 (다른 탭의 변경을 덮어쓰지 않으려고)
function withMyTicket(id, change) {
  const all = loadStore(TICKET_KEY, {});
  const t = (all[session.company] || []).find(x => x.id === id && x.empNo === session.id);
  if (!t) return null;
  change(t);
  saveStore(TICKET_KEY, all);
  return t;
}

function addComment(id, bodyRaw) {
  if (readOnly) return showToast(readOnlyMessage());
  const body = bodyRaw.trim();
  if (!body) return showToast('댓글 내용을 입력해 주세요');
  withMyTicket(id, t => { if (t.status !== 'DONE') t.comments.push({ at: new Date().toISOString(), by: session.id, name: session.name, body }); });
  renderTicketMine();
}

function setTicketStatus(id, status) {
  const t = withMyTicket(id, x => {
    if (x.status === 'DONE') return;
    x.status = status;
    x.history.push({ at: new Date().toISOString(), by: 'IT', name: 'IT 담당자(시연)', text: status === 'DONE' ? '완료했습니다' : '처리를 시작했습니다' });
  });
  if (t && status === 'DONE' && t.type === 'EQUIPMENT') grantEquipment(t);
  renderTicketMine();
  if (itEl('view-asset-mine').classList.contains('on')) renderAssetMine();
}

// 장비 지급 티켓이 완료되면 신청한 수량만큼 자산이 신청자에게 지급된다 (실제로는 IT 담당자가 자산 관리에서 지급한다)
function grantEquipment(t) {
  const all = loadStore(ASSET_KEY, {});
  const list = (all[session.company] = all[session.company] || []);
  const now = new Date().toISOString();
  for (let i = 0; i < (t.qty || 1); i++) {
    list.push({ id: `A-${Date.now()}-${i}`, name: t.kind || '장비', serial: `SN-${String(Date.now()).slice(-6)}${i + 1}`, branchId: t.branchId, empNo: t.empNo, assignedAt: now, ticketId: t.id });
  }
  saveStore(ASSET_KEY, all);
}

// ---------- 내 장비 ----------
function openAssetMineView() { renderAssetMine(); }

function renderAssetMine() {
  const assets = myAssets();
  const waiting = myTickets().filter(t => t.type === 'EQUIPMENT' && t.status !== 'DONE');
  itEl('assetCntUse').textContent = `${assets.length}개`;
  itEl('assetCntWait').textContent = `${waiting.length}건`;

  const emptyRow = (cols, label) => {
    const td = itNode('td', '', label);
    td.colSpan = cols;
    return itNode('tr', 'row-empty', td);
  };
  itEl('assetList').replaceChildren(...(assets.length ? assets.slice().sort((a, b) => new Date(b.assignedAt) - new Date(a.assignedAt)).map(a => itNode('tr', '',
    itCell('장비', itNode('span', '', a.name)),
    itCell('일련번호', itNode('span', '', a.serial)),
    itCell('배치 지점', itNode('span', '', branchNameOf(session.company, a.branchId))),
    itCell('지급일', itNode('span', '', ymd(a.assignedAt))),
    itCell('지급 티켓', itNode('span', '', a.ticketId || '-')))) : [emptyRow(5, '지급된 장비가 없습니다')]));
  itEl('assetWaitList').replaceChildren(...(waiting.length ? waiting.map(t => itNode('tr', '',
    itCell('티켓', itNode('span', '', t.id)),
    itCell('신청 장비', itNode('span', '', `${t.kind || '장비'} ${t.qty || 1}대`)),
    itCell('수령 지점', itNode('span', '', branchNameOf(session.company, t.branchId))),
    itCell('상태', itNode('span', `status status-${TICKET_CLASS[t.status]}`, TICKET_STATUS[t.status])))) : [emptyRow(4, '지급을 기다리는 신청이 없습니다')]));
}
itEl('btnAssetRequest').addEventListener('click', () => {
  if (readOnly) return showToast(readOnlyMessage());
  if (typeof eaStartWrite === 'function') eaStartWrite('EQUIP'); // 전자결재 장비 지급 신청서로 간다 (eapproval.js)
});

// 다른 탭의 변경(티켓 처리, 장비 지급)을 열려 있는 화면에 바로 보여 준다
window.addEventListener('storage', e => {
  if (!session || session.type !== 'EMPLOYEE') return;
  if (![TICKET_KEY, ASSET_KEY].includes(e.key)) return;
  if (itEl('view-ticket-mine').classList.contains('on')) renderTicketMine();
  if (itEl('view-asset-mine').classList.contains('on')) renderAssetMine();
});
