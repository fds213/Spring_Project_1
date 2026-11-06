// IT 서비스(사원): 장애·문의 접수(ticket-new), 내 티켓(ticket-mine), 내 장비(asset-mine) (요구분석서 3.4 IT 티켓·자산, 지점).
// 사원은 본인이 접수한 티켓과 본인에게 지급된 장비만 본다. 아직 접수 상태인 장애·문의는 본인이 취소할 수 있다(#177). 티켓을 처리하는 IT 담당자 화면(티켓 처리)과 자산 관리는 이 파일에 없다.
// 접수한 티켓의 지점은 접수 때 값으로 저장되므로, 나중에 자산이 다른 지점으로 옮겨져도 이미 접수한 티켓의 지점은 바뀌지 않는다.
// 장비 지급 신청서(전자결재)가 최종 승인되면 TK_EQUIPMENT 티켓이 만들어진다 (approval/approval.js).

// 종류·상태 이름표(TICKET_TYPE, TICKET_STATUS, TICKET_CLASS)와 장비 종류(assetTypeName)는 util.js
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
const itNew = { opts: null, initial: undefined };
const itMine = { filter: 'all', open: null, items: [] };
const itAsset = { items: [], waiting: [] };

// ---------- 장애·문의 접수 ----------
// 구분(ticketType)의 값은 숨긴 select에 두고, 화면에는 설명이 붙은 버튼 두 개로 보인다
const TICKET_INPUT_IDS = ['ticketType', 'ticketAsset', 'ticketBranch', 'ticketTitle', 'ticketBody'];
const itSnapshot = () => JSON.stringify(TICKET_INPUT_IDS.map(id => itEl(id).value.trim())); // [취소] 때 쓰던 내용이 있는지 견줄 값

function openTicketNewView() {
  itEl('ticketError').textContent = '';
  syncTicketType();
  api.get('/ticket/api/form-options')
    .done(o => {
      itNew.opts = o;
      itEl('ticketAsset').replaceChildren(new Option('관련 자산 없음', ''), ...o.assets.map(a => new Option(assetLabel(a), a.id)));
      // 지점이 하나도 없으면 빈 칸 대신 '등록된 지점 없음'을 보인다 (고를 수 없는 안내 문구)
      const none = new Option('등록된 지점 없음', '');
      none.disabled = true;
      itEl('ticketBranch').replaceChildren(...(o.branches.length ? o.branches.map(b => new Option(b.name, b.id)) : [none]));
      syncTicketBranch();
      itNew.initial = itSnapshot();
    })
    .fail(xhr => { itEl('ticketError').textContent = apiMessage(xhr, '접수에 필요한 정보를 불러오지 못했습니다'); });
  // 진행 중인 내 티켓 수: 접수·배정·처리 중 (내 티켓 화면과 같은 자료). 받은 뒤에 카드를 보인다. 못 받아도 접수는 할 수 있어 카드를 숨긴 채 둔다
  api.get('/ticket/api/tickets').done(items => {
    itEl('ticketOpenCard').hidden = false;
    const open = (items || []).filter(t => TICKET_GROUP[t.status] !== 'DONE');
    const waiting = (items || []).filter(t => t.status === 'RESOLVED').length; // 처리 완료, 내 [확인]을 기다림
    itEl('ticketOpenCount').textContent = open.length;
    itEl('ticketOpenCard').classList.toggle('is-alert', open.length > 0 || waiting > 0);
    itEl('ticketOpenSub').textContent = waiting ? `처리 완료 ${waiting}건이 내 [확인]을 기다립니다`
      : open.length ? '같은 문제면 새로 접수하지 말고 내 티켓에 댓글로 남겨 주세요' : '지금 처리 중인 티켓이 없습니다';
  }).fail(() => { itEl('ticketOpenCard').hidden = true; });
}

// 구분 버튼과 숨긴 select를 맞추고, 내용 칸 예시를 구분에 맞게 바꾼다
function syncTicketType() {
  const type = itEl('ticketType').value;
  document.querySelectorAll('.tk-type').forEach(b => {
    b.setAttribute('aria-checked', b.dataset.type === type);
    b.tabIndex = b.dataset.type === type ? 0 : -1; // 라디오 묶음: Tab은 고른 버튼에만 멈추고, 묶음 안에서는 화살표로 옮긴다
  });
  const incident = type === 'TK_INCIDENT';
  itEl('ticketTitle').placeholder = incident ? '예: 노트북 화면이 켜지지 않아요' : '예: 공용 프린터 연결 방법이 궁금해요';
  itEl('ticketBody').placeholder = incident
    ? '언제부터, 어떤 증상인지, 오류 메시지가 있으면 그대로 적어 주세요'
    : '무엇을 하려는지, 어디서 막혔는지 적어 주세요';
  syncTicketSummary();
}

// 접수 지점 기본값: 고른 자산이 있는 지점. 자산이 없으면 사원의 근무 지점(서버가 정한 기본 지점).
// 실제 장애가 난 곳이 다르면 사원이 바꿔 접수한다.
// 채울 지점이 목록에 없으면(근무 지점이 없음, 자산의 지점이 사용 중지) 첫 지점을 대신 고르지 않고 비워 둔다. 비운 채 접수하면 제출 검사가 막는다
function syncTicketBranch() {
  const o = itNew.opts || { assets: [], branches: [], defaultBranchId: '' };
  // 서버의 ID 는 숫자, select 값은 글자라서 글자로 맞춰 비교한다
  const asset = o.assets.find(a => String(a.id) === itEl('ticketAsset').value);
  const want = String(asset ? asset.branchId : o.defaultBranchId ?? '');
  const sel = itEl('ticketBranch');
  const found = [...sel.options].some(x => x.value === want);
  if (found) sel.value = want; else sel.selectedIndex = -1;
  const hint = itEl('ticketBranchHint');
  hint.classList.toggle('is-warn', !found);
  hint.textContent = !o.branches.length ? '등록된 지점이 없습니다. 업체 관리자에게 지점 등록을 요청해 주세요'
    : !found ? '접수 지점을 골라 주세요'
    : asset ? '고른 자산이 있는 지점으로 채웠습니다. 장애가 난 곳이 다르면 바꿔 주세요' : '근무 지점으로 채웠습니다. 장애가 난 곳이 다르면 바꿔 주세요';
  syncTicketSummary();
}

// [접수] 옆 한 줄 요약: 비어 있는 칸, 다 채우면 무엇을 어디로 접수하는지
function syncTicketSummary() {
  const el = itEl('ticketSummary');
  if (!el) return;
  itEl('ticketTitleCount').textContent = `${itEl('ticketTitle').value.length} / ${TICKET_TITLE_MAX}`;
  itEl('ticketBodyCount').textContent = `${itEl('ticketBody').value.length} / ${TICKET_BODY_MAX}`;
  const sel = itEl('ticketBranch');
  const branch = sel.value ? sel.options[sel.selectedIndex].text : '';
  const todo = [];
  if (!branch) todo.push('접수 지점');
  if (!itEl('ticketTitle').value.trim()) todo.push('제목');
  if (!itEl('ticketBody').value.trim()) todo.push('내용');
  el.classList.toggle('is-todo', !!todo.length);
  if (todo.length) { el.textContent = `아직 비어 있음: ${todo.join(' · ')}`; return; }
  const a = itEl('ticketAsset');
  const asset = a.value ? a.options[a.selectedIndex].text : '';
  el.replaceChildren(itNode('strong', '', TICKET_TYPE[itEl('ticketType').value]), ` · ${branch}${asset ? ` · ${asset}` : ''} → IT 담당자에게 전달`);
}

document.querySelectorAll('.tk-type').forEach(b => b.addEventListener('click', () => {
  itEl('ticketType').value = b.dataset.type;
  syncTicketType();
}));
// 구분 버튼(role="radio")은 화면 읽기 프로그램에 라디오로 읽히므로, 라디오처럼 화살표로 옮겨 고르게 한다
itEl('view-ticket-new')?.querySelector('.tk-types')?.addEventListener('keydown', e => {
  const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
  if (!step) return;
  e.preventDefault();
  const btns = [...document.querySelectorAll('.tk-type')];
  const next = btns[(btns.indexOf(document.activeElement) + step + btns.length) % btns.length];
  next.click();
  next.focus();
});
itEl('ticketAsset')?.addEventListener('change', syncTicketBranch);
itEl('ticketBranch')?.addEventListener('change', () => {
  itEl('ticketBranchHint').textContent = '';
  syncTicketSummary();
});
['ticketTitle', 'ticketBody'].forEach(id => itEl(id)?.addEventListener('input', () => {
  itEl('ticketError').textContent = '';
  syncTicketSummary();
}));

// 확인 창(<dialog>)을 열고, 누른 버튼이 '확인'(value="ok")이면 true. Esc로 닫으면 false
function itConfirm(id) {
  const dlg = itEl(id);
  return new Promise(resolve => {
    let ok = false;
    dlg.querySelectorAll('button').forEach(b => { b.onclick = () => { ok = b.value === 'ok'; dlg.close(); }; });
    dlg.addEventListener('close', () => resolve(ok), { once: true });
    dlg.showModal();
  });
}
itEl('ticketCancel')?.addEventListener('click', async () => {
  const dirty = itNew.initial !== undefined && itSnapshot() !== itNew.initial;
  if (dirty && !(await itConfirm('ticketLeaveDialog'))) return;
  location.href = pageHref('/ticket/mine');
});

itEl('ticketForm')?.addEventListener('submit', e => {
  e.preventDefault();
  const err = itEl('ticketError');
  const type = itEl('ticketType').value;
  const title = itEl('ticketTitle').value.trim();
  const body = itEl('ticketBody').value.trim();
  const branchId = itEl('ticketBranch').value;
  // 서버도 같은 검사를 다시 한다. 화면은 먼저 알려 줄 뿐이다
  if (!['TK_INCIDENT', 'TK_QUESTION'].includes(type)) { err.textContent = '구분을 골라 주세요'; return; }
  if (!branchId) { err.textContent = '접수 지점을 골라 주세요'; itEl('ticketBranch').focus(); return; }
  if (!title) { err.textContent = '제목을 입력해 주세요'; itEl('ticketTitle').focus(); return; }
  if (title.length > TICKET_TITLE_MAX) { err.textContent = `제목은 ${TICKET_TITLE_MAX}자까지 쓸 수 있습니다`; return; }
  if (!body) { err.textContent = '내용을 입력해 주세요'; itEl('ticketBody').focus(); return; }
  if (body.length > TICKET_BODY_MAX) { err.textContent = `내용은 ${TICKET_BODY_MAX}자까지 쓸 수 있습니다`; return; }
  const btn = itEl('ticketForm').querySelector('[type="submit"]');
  if (btn.disabled) return;
  btn.disabled = true; // 두 번 눌러 두 건이 생기지 않게
  const assetId = itEl('ticketAsset').value;
  api.post('/ticket/api/tickets', { type, title, body, branchId: Number(branchId), assetId: assetId ? Number(assetId) : null })
    .done(t => {
      err.textContent = '';
      itNew.initial = itSnapshot(); // 접수했으니 나갈 때 묻지 않는다
      toastAndGo(`${t.id}번 티켓을 접수했습니다. IT 담당자에게 전달됩니다`, pageHref('/ticket/mine'));
    })
    // 성공하면 버튼을 풀지 않는다: 내 티켓 화면으로 넘어가기 전에 한 번 더 눌러 같은 티켓이 두 건 생기지 않게. 실패했을 때만 다시 누를 수 있게 푼다
    .fail(xhr => { err.textContent = apiMessage(xhr); btn.disabled = false; });
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
    ['관련 자산', t.asset ? assetLabel(t.asset) : '-'], ['제목', t.title], ['내용', text(t.body)]]
    .forEach(([k, v]) => info.append(itNode('dt', '', k), itNode('dd', '', v)));

  // 접수 직후에는 이력이 없다: 티켓 처리 화면(ticket-admin.js)과 같은 안내를 보인다
  const log = historyList((t.history || []).map(x => text(`${whenFull(x.at)} · ${x.name} · ${x.text}`)), '아직 이력이 없습니다');
  const comments = itNode('ul', 'bill-history', ...(t.comments.length
    ? t.comments.map(c => itNode('li', '', text(`${whenFull(c.at)} · ${c.name} · ${c.body}`)))
    : [itNode('li', '', itNode('span', 'muted', '아직 댓글이 없습니다'))]));

  const parts = [itNode('section', 'info-card', itNode('header', 'info-card-head', itNode('h2', '', '접수 내용')), info),
    itNode('section', 'info-card', itNode('header', 'info-card-head', itNode('h2', '', '처리 이력')), log,
      itNode('header', 'info-card-head', itNode('h2', '', '댓글')), comments)];

  // 처리 완료된 티켓: 해결 결과를 보고 확인하면 종료된다(요청서. 다시 요청은 이번 범위가 아니다)
  if (t.canClose) {
    const ok = itButton('확인(종료)', 'btn btn-primary');
    ok.addEventListener('click', () => closeTicket(t, ok));
    parts[1].prepend(itNode('header', 'info-card-head', itNode('h2', '', '처리 결과 확인')),
      itNode('p', 'muted inq-body', '처리 이력의 해결 결과를 확인해 주세요. 확인하면 종료됩니다.'),
      itNode('div', 'inq-body', itNode('div', 'inq-actions', ok)));
  }
  // 아직 접수 상태인 장애·문의는 요청자가 취소할 수 있다(#177). 서버가 canCancel 을 준 티켓에만 보인다(장비 지급 티켓은 없음)
  if (t.canCancel) parts[1].prepend(...cancelBox(t));
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
  return itNode('div', 'inq-grid', parts[0], itNode('div', '', parts[1]));
}

// 쓰는 버튼: 읽기 전용 업체에서는 누르지 못하게 data-write 를 단다
function itButton(label, cls) {
  const b = itNode('button', cls, label);
  b.type = 'button';
  b.dataset.write = '';
  return b;
}

// 확인(종료): ticket 모듈 담당이 확정한 규격(11/3). 서버가 canClose 를 준 처리 완료 티켓에만 보인다.
// POST /ticket/api/tickets/{id}/close { version }. 그사이 상태가 바뀌었으면 409
function closeTicket(t, btn) {
  btn.disabled = true;
  api.post(`/ticket/api/tickets/${encodeURIComponent(t.id)}/close`, { version: t.version })
    .done(() => { showToast(`${t.id}번 티켓을 종료했습니다`); loadTicketMine(); })
    .fail(xhr => { showToast(apiMessage(xhr)); if (xhr.status === 409) loadTicketMine(); else btn.disabled = false; });
}

// 접수 취소 칸: 되돌릴 수 없어서 한 번 더 묻는다. [접수 취소] → [취소하기]·[그대로 두기]
// 버튼이 바뀔 때 포커스를 새 버튼으로 옮기고, 안내 문장이 바뀐 것은 화면 낭독기에도 읽힌다(aria-live)
const TICKET_CANCEL_NOTE = '아직 담당자가 맡기 전입니다. 잘못 접수했거나 문제가 해결됐으면 취소할 수 있습니다.';
function cancelBox(t) {
  const note = itNode('p', 'muted inq-body', TICKET_CANCEL_NOTE);
  note.setAttribute('aria-live', 'polite');
  const actions = itNode('div', 'inq-actions');
  const ask = focus => {
    const btn = itButton('접수 취소', 'btn btn-danger-line');
    btn.addEventListener('click', () => {
      note.textContent = `${t.id}번 티켓을 취소할까요? 취소하면 되돌릴 수 없습니다.`;
      const yes = itButton('취소하기', 'btn btn-danger-line');
      const no = itButton('그대로 두기', 'btn');
      yes.addEventListener('click', () => cancelTicket(t, yes, no));
      no.addEventListener('click', () => {
        note.textContent = TICKET_CANCEL_NOTE;
        ask(true);
      });
      actions.replaceChildren(yes, no);
      yes.focus();
    });
    actions.replaceChildren(btn);
    if (focus) btn.focus();
  };
  ask(false);
  return [itNode('header', 'info-card-head', itNode('h2', '', '접수 취소')), note, itNode('div', 'inq-body', actions)];
}

// 접수 취소: ticket 모듈 담당이 정한 규격(#177). POST /ticket/api/tickets/{id}/cancel { version } — version 은 늘 보낸다.
// 오류는 상태 코드로만 나눈다(문구는 보여 주기만): 409 는 그새 담당자가 맡았거나 바뀐 것이라 목록을 다시 받는다
function cancelTicket(t, yes, no) {
  yes.disabled = true;
  no.disabled = true;
  api.post(`/ticket/api/tickets/${encodeURIComponent(t.id)}/cancel`, { version: t.version })
    .done(() => { showToast(`${t.id}번 티켓을 취소했습니다`); loadTicketMine(); })
    .fail(xhr => {
      showToast(apiMessage(xhr));
      if (xhr.status === 409 || xhr.status === 404) loadTicketMine();
      else { yes.disabled = false; no.disabled = false; }
    });
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

  itEl('assetList').replaceChildren(...(assets.length ? assets.map(a => itNode('tr', '',
    itCell('장비', itNode('span', '', a.name)),
    itCell('일련번호', itNode('span', '', a.serial || '-')),
    itCell('배치 지점', itNode('span', '', a.branchName)),
    itCell('지급일', itNode('span', '', ymd(a.assignedAt))),
    itCell('지급 티켓', itNode('span', '', a.ticketId || '-')))) : [emptyRow(5, '지급된 장비가 없습니다')]));
  itEl('assetWaitList').replaceChildren(...(waiting.length ? waiting.map(t => itNode('tr', '',
    itCell('티켓', itNode('span', '', t.id)),
    itCell('신청 장비', itNode('span', '', `${assetTypeName(t.kind)} ${t.qty}대`)),
    itCell('수령 지점', itNode('span', '', t.branchName)),
    itCell('상태', itNode('span', `status status-${TICKET_CLASS[t.status]}`, TICKET_STATUS[t.status])))) : [emptyRow(4, '지급을 기다리는 신청이 없습니다')]));
}
itEl('btnAssetRequest')?.addEventListener('click', () => {
  startApprovalWrite('DOC_EQUIPMENT'); // 전자결재 장비 지급 신청서로 간다 (util.js)
});
