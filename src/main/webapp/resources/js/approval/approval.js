// 전자결재(사원): 기안 작성(approval-write)과 결재함(approval-box). 양식은 휴가 신청서와 장비 지급 신청서 둘이다 (요구분석서 3.4 전자결재).
// 결재선은 최대 2단계로 순서대로 승인한다. 마지막 결재자가 승인해야 최종 승인이다.
// - 휴가: 최종 승인 때 연차가 차감되고 일정에 나타난다 (근태·연차 화면의 신청과 같은 데이터다).
// - 장비 지급: 최종 승인 때 IT 서비스에 지급 티켓이 만들어진다. 지급이 끝나면 내 장비에 나타난다.
// 임시저장은 검사 없이 입력값을 그대로 저장하고, 반려·회수된 문서는 결재함에서 "재기안"으로 내용을 채워 새로 올린다.
// 결재자 목록, 내 순번인지, 회수·재기안할 수 있는지는 서버가 정해 응답에 담는다. 화면은 받은 대로 그린다.
// 서버 API (시안은 demo/approval.js가 답한다):
//   GET  /approval/api/form-options    기안 작성에 필요한 것
//        → { approvers: [{ empNo, name, dept, rank }], branches: [{ id, name }], equipKinds: [..], leave: { available, pendingDays } | null }
//   GET  /approval/api/box             결재함 → { WAIT: [문서], PROGRESS: [문서], DONE: [문서], REJECTED: [문서], DRAFT: [임시저장] }
//   GET  /approval/api/box/count       메뉴 숫자 → { wait }
//   POST /approval/api/documents       제출 { form: LEAVE|EQUIP, fields, line: [1단계 사번, 2단계 사번 또는 ''], draftId? } → { id, form }
//        fields — LEAVE: { kind: FULL|AM|PM, from, to, reason }  EQUIP: { kind, qty, branchId, reason }
//   POST /approval/api/documents/{id}/approve    { comment } → { id, form, name, status, ticketId }
//   POST /approval/api/documents/{id}/reject     { comment(필수) } → 같은 모양
//   POST /approval/api/documents/{id}/withdraw   회수 → { id, form, status }
//   POST   /approval/api/drafts         임시저장 { form, fields, line } → 임시저장
//   POST   /approval/api/drafts/{id}    고쳐 저장 { form, fields, line } → 임시저장 (없으면 404)
//   POST   /approval/api/drafts/{id}/delete 삭제
// 문서: { id, form, title, name, dept, at, status: REQUESTED|APPROVED|REJECTED|CANCELED, step, line: [{ empNo, name }],
//         history: [{ at, name, action: APPROVE|REJECT|CANCEL, comment }], fields, days(LEAVE), branchName·ticketId(EQUIP),
//         mine, canDecide, canWithdraw, canRedraft, rejectComment }
// 임시저장: { id, form, fields, line: [사번, 사번], lineNames: [이름], branchName, savedAt }
// 읽기 전용 업체는 쓰는 요청이 403으로 거절된다 (버튼의 data-write로 화면도 먼저 막는다).

const EA_FORMS = { LEAVE: '휴가 신청서', EQUIP: '장비 지급 신청서' };
const EA_TABS = { WAIT: '내 순번 대기', PROGRESS: '진행', DONE: '완료', REJECTED: '반려·회수', DRAFT: '임시저장' };
const EA_STATUS = { REQUESTED: '승인 대기', APPROVED: '승인', REJECTED: '반려', CANCELED: '회수' };
const EA_STATUS_CLASS = { REQUESTED: 'waiting', APPROVED: 'paid', REJECTED: 'failed', CANCELED: 'expired' };
const EA_LEAVE_KIND = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };
const EQUIP_QTY_MAX = 10;
const EA_REASON_MAX = 200; // 장비 신청 사유, 장비 문서 결재 의견
const EA_LEAVE_COMMENT_MAX = 100; // 휴가 문서 결재 의견
const EA_ACTION = { APPROVE: '승인', REJECT: '반려', CANCEL: '회수' };

const eaEl = id => document.getElementById(id);
const eaNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const eaCell = (label, ...parts) => {
  const td = eaNode('td', '', eaNode('div', 'cell', ...parts));
  td.dataset.label = label; // 폰에서 값 앞에 붙는 열 이름
  return td;
};
const eaButton = (label, cls, write) => {
  const b = eaNode('button', cls, label);
  b.type = 'button';
  if (write) b.dataset.write = ''; // 읽기 전용 업체에서는 누르지 못하게 표시한다
  return b;
};
const eaDays = n => `${Number.isInteger(n) ? n : n.toFixed(1)}일`;
const eaSpan = f => (f.from ? `${f.from}${f.to && f.to !== f.from ? ` ~ ${f.to}` : ''}` : '');
const eaState = { tab: 'WAIT', open: null, draftId: null, pre: null, box: null, opts: null };

// 사이드 메뉴 "결재함" 옆의 숫자: 내 순번에 와 있는 문서 수
function eaSetBadge(n) {
  const el = eaEl('navApprBoxCount');
  if (!el) return;
  el.textContent = n;
  el.hidden = !n;
}
function eaRefreshBadge() {
  if (!eaEl('navApprBoxCount')) return;
  api.get('/approval/api/box/count').done(r => eaSetBadge(r.wait)); // 숫자를 못 받으면 그대로 둔다
}

// "박관리 ✓ → 최팀장 (결재 중)"처럼 결재선의 진행을 한 줄로
function eaLineText(d) {
  if (!d.line.length) return '-';
  return d.line.map((p, i) => {
    if (d.status === 'APPROVED' || i < d.step) return `${p.name} ✓`;
    if (i === d.step && d.status === 'REQUESTED') return `${p.name} (결재 중)`;
    if (i === d.step && d.status === 'REJECTED') return `${p.name} (반려)`;
    return p.name;
  }).join(' → ');
}

// ---------- 기안 작성 ----------
function eaFillSelects() {
  const o = eaState.opts || { approvers: [], branches: [], equipKinds: [] };
  const fill = (id, withNone) => {
    const sel = eaEl(id);
    const keep = sel.value;
    sel.replaceChildren(...(withNone ? [new Option('(없음)', '')] : [new Option('(고르세요)', '')]),
      ...o.approvers.map(e => new Option(`${e.name} (${e.dept} · ${e.rank})`, e.empNo)));
    if ([...sel.options].some(x => x.value === keep)) sel.value = keep;
  };
  fill('eaAppr1', false);
  fill('eaAppr2', true);
  eaEl('eaEquipBranch').replaceChildren(...o.branches.map(b => new Option(b.name, b.id)));
  eaEl('eaEquipKind').replaceChildren(...o.equipKinds.map(k => new Option(k, k)));
}

// 양식에 따라 보이는 입력칸과 안내를 바꾼다. 입력해 둔 값은 지우지 않는다
function eaSyncForm() {
  const form = eaEl('eaFormKind').value;
  eaEl('eaLeaveFields').hidden = form !== 'LEAVE';
  eaEl('eaEquipFields').hidden = form !== 'EQUIP';
  eaEl('eaFormTitle').textContent = EA_FORMS[form];
  const half = eaEl('eaLeaveKind').value !== 'FULL';
  if (half) eaEl('eaLeaveTo').value = eaEl('eaLeaveFrom').value; // 반차는 하루만
  eaEl('eaLeaveTo').disabled = half;
  const sum = form === 'LEAVE' && eaState.opts ? eaState.opts.leave : null;
  const rows = form === 'LEAVE'
    ? [['남은 연차', sum ? `${eaDays(sum.available)} (승인 대기 ${eaDays(sum.pendingDays)} 제외)` : '-'], ['결재', '최대 2단계, 순서대로 승인합니다'],
      ['최종 승인 후', '연차가 차감되고 일정에 나타납니다'], ['주의', '주말과 등록 휴일은 세지 않고, 오전·오후 반차는 0.5일입니다']]
    : [['결재', '최대 2단계, 순서대로 승인합니다'], ['최종 승인 후', 'IT 서비스에 지급 티켓이 만들어집니다'], ['지급 확인', '내 티켓에서 처리 상태를, 지급되면 내 장비에서 봅니다'],
      ['수령 지점', '업체 관리자가 등록한 지점 중에서 고릅니다']];
  eaEl('eaGuide').replaceChildren(...rows.flatMap(([k, v]) => [eaNode('dt', '', k), eaNode('dd', '', v)]));
}

function eaApplyForm(p) {
  const f = p.fields || {};
  const kinds = (eaState.opts && eaState.opts.equipKinds) || [];
  eaEl('eaFormKind').value = p.form || 'LEAVE';
  eaEl('eaLeaveKind').value = f.kind && ['FULL', 'AM', 'PM'].includes(f.kind) ? f.kind : 'FULL';
  eaEl('eaLeaveFrom').value = (p.form === 'LEAVE' && f.from) || '';
  eaEl('eaLeaveTo').value = (p.form === 'LEAVE' && f.to) || '';
  eaEl('eaLeaveReason').value = (p.form === 'LEAVE' && f.reason) || '';
  eaEl('eaEquipKind').value = p.form === 'EQUIP' && kinds.includes(f.kind) ? f.kind : (kinds[0] || '');
  eaEl('eaEquipQty').value = (p.form === 'EQUIP' && f.qty) || 1;
  if (p.form === 'EQUIP' && f.branchId) eaEl('eaEquipBranch').value = f.branchId;
  eaEl('eaEquipReason').value = (p.form === 'EQUIP' && f.reason) || '';
  eaEl('eaAppr1').value = (p.line && p.line[0]) || '';
  eaEl('eaAppr2').value = (p.line && p.line[1]) || '';
  eaEl('eaError').textContent = '';
  eaSyncForm();
}

// 다른 화면(내 장비, 결재함의 재기안·이어서 쓰기)에서 기안 작성을 열 때 쓴다 (util.js startApprovalWrite)
const eaStartWrite = (form, pre) => startApprovalWrite(form, pre);

// 화면을 열 때 (시안은 proto/app.js showView, JSP는 페이지가 열릴 때). 결재자·지점 목록을 받은 뒤 입력칸을 채운다
function openApprovalWriteView() {
  const pre = takeApprovalPre();
  eaState.draftId = pre && pre.draftId ? pre.draftId : null;
  eaEl('eaError').textContent = '';
  api.get('/approval/api/form-options')
    .done(opts => {
      eaState.opts = opts;
      eaFillSelects();
      eaApplyForm(pre || { form: 'LEAVE', fields: {}, line: [] });
    })
    .fail(xhr => { eaEl('eaError').textContent = apiMessage(xhr, '결재자 목록을 불러오지 못했습니다'); });
}

function eaCollect() {
  const form = eaEl('eaFormKind').value;
  return {
    form,
    fields: form === 'LEAVE'
      ? { kind: eaEl('eaLeaveKind').value, from: eaEl('eaLeaveFrom').value, to: eaEl('eaLeaveTo').value, reason: eaEl('eaLeaveReason').value.trim() }
      : { kind: eaEl('eaEquipKind').value, qty: Number(eaEl('eaEquipQty').value), branchId: eaEl('eaEquipBranch').value, reason: eaEl('eaEquipReason').value.trim() },
    a1: eaEl('eaAppr1').value, a2: eaEl('eaAppr2').value,
  };
}

// 제출 전에 먼저 알려 줄 것. 서버도 같은 검사를 다시 하고, 연차 잔여·날짜 겹침·휴일·지난 날짜는 서버만 본다
function eaCheck(c) {
  const f = c.fields;
  if (c.form === 'LEAVE') {
    if (!f.from) return '시작일을 입력해 주세요';
    if (f.kind === 'FULL' && (!f.to || f.to < f.from)) return '종료일은 시작일 이후여야 합니다';
  } else {
    if (!f.kind) return '장비 종류를 골라 주세요';
    if (!Number.isInteger(f.qty) || f.qty < 1 || f.qty > EQUIP_QTY_MAX) return `수량은 1~${EQUIP_QTY_MAX}대로 입력해 주세요`;
    if (!f.branchId) return '수령 지점을 골라 주세요';
    if (!f.reason) return '신청 사유를 입력해 주세요';
    if (f.reason.length > EA_REASON_MAX) return `사유는 ${EA_REASON_MAX}자까지 쓸 수 있습니다`;
  }
  if (eaState.opts && !eaState.opts.approvers.length) return '결재할 사람이 없습니다. 같은 업체에 부서장·인사 담당·업체 관리자가 본인 말고 한 명 있어야 합니다';
  if (!c.a1) return '1단계 결재자를 골라 주세요';
  if (c.a2 && c.a2 === c.a1) return '2단계 결재자는 1단계와 다른 사람이어야 합니다';
  return '';
}

eaEl('eaFormKind')?.addEventListener('change', eaSyncForm);
eaEl('eaLeaveKind')?.addEventListener('change', eaSyncForm);
eaEl('eaLeaveFrom')?.addEventListener('change', eaSyncForm);

// 임시저장: 검사 없이 지금 입력한 그대로. 저장한 뒤에는 같은 임시저장 문서를 계속 고친다
function eaSaveDraft(body, btn) {
  const id = eaState.draftId;
  btn.disabled = true;
  (id ? api.post(`/approval/api/drafts/${encodeURIComponent(id)}`, body) : api.post('/approval/api/drafts', body))
    .always(() => { btn.disabled = false; })
    .done(d => {
      eaState.draftId = d.id;
      showToast('임시저장했습니다. 결재함의 임시저장에서 이어서 쓸 수 있습니다');
    })
    .fail(xhr => {
      // 다른 곳에서 지운 임시저장이면 새로 저장한다
      if (id && xhr.status === 404) { eaState.draftId = null; eaSaveDraft(body, btn); return; }
      showToast(apiMessage(xhr));
    });
}
eaEl('btnEaDraft')?.addEventListener('click', () => {
  const c = eaCollect();
  eaSaveDraft({ form: c.form, fields: c.fields, line: [c.a1, c.a2] }, eaEl('btnEaDraft'));
});

eaEl('eaForm')?.addEventListener('submit', e => {
  e.preventDefault();
  const err = eaEl('eaError');
  const c = eaCollect();
  const msg = eaCheck(c);
  if (msg) { err.textContent = msg; return; }
  const btn = eaEl('eaForm').querySelector('[type="submit"]');
  if (btn) btn.disabled = true; // 두 번 눌러 두 건이 생기지 않게
  api.post('/approval/api/documents', { form: c.form, fields: c.fields, line: [c.a1, c.a2], draftId: eaState.draftId })
    .done(() => {
      eaState.draftId = null;
      err.textContent = '';
      eaState.tab = 'PROGRESS';
      eaState.open = null;
      showToast(`${EA_FORMS[c.form]}를 제출했습니다. 첫 결재자에게 넘어갔습니다`);
      location.href = pageHref('/approval/list');
    })
    .fail(xhr => { err.textContent = apiMessage(xhr); })
    .always(() => { if (btn) btn.disabled = false; });
});

// ---------- 결재함 ----------
function openApprovalBoxView() {
  eaState.open = null;
  loadApprovalBox();
}

function loadApprovalBox() {
  api.get('/approval/api/box')
    .done(box => { eaState.box = box; renderApprovalBox(); })
    .fail(xhr => showToast(apiMessage(xhr, '결재함을 불러오지 못했습니다')));
}

function renderApprovalBox() {
  const buckets = eaState.box || Object.fromEntries(Object.keys(EA_TABS).map(t => [t, []]));
  Object.keys(EA_TABS).forEach(t => { eaEl(`eaCnt${t}`).textContent = `${(buckets[t] || []).length}건`; });
  document.querySelectorAll('#eaTabs .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.tab === eaState.tab));
  eaSetBadge((buckets.WAIT || []).length);

  const draft = eaState.tab === 'DRAFT';
  eaEl('eaHead').replaceChildren(eaNode('tr', '', ...(draft ? ['문서', '저장 시각'] : ['문서', '기안자', '결재선', '상태', '기안일']).map(t => eaNode('th', '', t))));
  const cols = draft ? 2 : 5;
  const rows = [];
  (buckets[eaState.tab] || []).forEach(d => {
    const key = `${draft ? 'DRAFT' : d.form}:${d.id}`;
    const isOpen = eaState.open === key;
    const tr = eaNode('tr', 'row-link' + (isOpen ? ' is-open' : ''), ...(draft
      ? [eaCell('문서', eaNode('span', '', `${EA_FORMS[d.form]}${eaDraftTitle(d)}`)), eaCell('저장 시각', eaNode('span', '', whenFull(d.savedAt)))]
      : [eaCell('문서', eaNode('span', '', d.title)), eaCell('기안자', eaNode('span', '', d.name), eaNode('small', 'cell-sub', d.dept)),
        eaCell('결재선', eaNode('span', '', eaLineText(d))), eaCell('상태', eaNode('span', `status status-${EA_STATUS_CLASS[d.status]}`, EA_STATUS[d.status])),
        eaCell('기안일', eaNode('span', '', whenFull(d.at)))]));
    tr.addEventListener('click', () => { eaState.open = isOpen ? null : key; renderApprovalBox(); });
    rows.push(tr);
    if (isOpen) {
      const td = eaNode('td', '', eaNode('div', 'slide open', eaNode('div', 'slide-inner', draft ? eaDraftDetail(d) : eaDocDetail(d))));
      td.colSpan = cols;
      rows.push(eaNode('tr', 'row-detail', td));
    }
  });
  if (!rows.length) {
    const td = eaNode('td', '', { WAIT: '내 순번에 온 문서가 없습니다', PROGRESS: '진행 중인 문서가 없습니다', DONE: '완료된 문서가 없습니다', REJECTED: '반려·회수된 문서가 없습니다', DRAFT: '임시저장한 문서가 없습니다' }[eaState.tab]);
    td.colSpan = cols;
    rows.push(eaNode('tr', 'row-empty', td));
  }
  eaEl('eaList').replaceChildren(...rows);
}
document.querySelectorAll('#eaTabs .card-filter').forEach(b => b.addEventListener('click', () => {
  eaState.tab = b.dataset.tab;
  eaState.open = null;
  renderApprovalBox();
}));

const eaDraftTitle = d => (d.form === 'LEAVE'
  ? (d.fields.from ? ` · ${eaSpan(d.fields)}` : '')
  : (d.fields.kind ? ` · ${d.fields.kind} ${d.fields.qty || ''}대` : ''));

// 문서 내용 줄: 양식마다 다르다
function eaDocRows(d) {
  const f = d.fields;
  if (d.form === 'LEAVE') return [['구분', EA_LEAVE_KIND[f.kind]], ['기간', `${eaSpan(f)} (${d.days}일)`], ['사유', f.reason || '-']];
  return [['장비', `${f.kind} ${f.qty}대`], ['수령 지점', d.branchName], ['사유', f.reason],
    ...(d.ticketId ? [['지급 티켓', `${d.ticketId} (내 티켓에서 처리 상태를 봅니다)`]] : [])];
}

function eaDocDetail(d) {
  const info = eaNode('dl', 'detail');
  [['문서', d.title], ['기안자', `${d.name}${d.dept ? ` (${d.dept})` : ''}`], ['기안일', whenFull(d.at)], ...eaDocRows(d), ['결재선', eaLineText(d)], ['상태', EA_STATUS[d.status]]]
    .forEach(([k, v]) => info.append(eaNode('dt', '', k), eaNode('dd', '', v)));
  const log = eaNode('ul', 'bill-history', ...(d.history.length
    ? d.history.map(h => eaNode('li', '', eaNode('span', '', `${whenFull(h.at)} · ${h.name} · ${EA_ACTION[h.action] || h.action}${h.comment ? ` · ${h.comment}` : ''}`)))
    : [eaNode('li', '', eaNode('span', 'muted', '아직 처리된 단계가 없습니다'))]));
  const side = eaNode('section', 'info-card', eaNode('header', 'info-card-head', eaNode('h2', '', '결재 이력')), log);

  if (d.canDecide) {
    const box = eaNode('textarea', 'inq-text');
    box.maxLength = d.form === 'LEAVE' ? EA_LEAVE_COMMENT_MAX : EA_REASON_MAX;
    box.placeholder = '의견 (반려할 때는 사유가 꼭 필요합니다)';
    box.setAttribute('aria-label', '결재 의견');
    const ok = eaButton('승인', 'btn btn-primary', true);
    const no = eaButton('반려', 'btn', true);
    ok.addEventListener('click', () => eaDecide(d, true, box.value.trim(), [ok, no]));
    no.addEventListener('click', () => eaDecide(d, false, box.value.trim(), [ok, no]));
    side.append(eaNode('div', 'inq-body', box, eaNode('div', 'inq-actions', ok, no)));
  } else if (d.canWithdraw) {
    const cancel = eaButton('회수', 'btn', true);
    cancel.addEventListener('click', () => eaCancel(d, cancel));
    side.append(eaNode('div', 'inq-body', eaNode('p', 'inq-hint', '결재가 끝나기 전에는 문서를 회수할 수 있습니다. 회수한 문서는 반려·회수 탭에서 다시 기안합니다.'), eaNode('div', 'inq-actions', cancel)));
  } else if (d.canRedraft) {
    const again = eaButton('재기안', 'btn btn-primary', true);
    again.addEventListener('click', () => eaStartWrite(d.form, { fields: d.fields, line: d.line.map(p => p.empNo) }));
    side.append(eaNode('div', 'inq-body', ...(d.rejectComment ? [eaNode('p', 'notice-warn', `반려 사유: ${d.rejectComment}`)] : []),
      eaNode('p', 'inq-hint', '내용과 결재선을 채운 새 기안 화면이 열립니다. 고친 뒤 다시 제출하세요.'), eaNode('div', 'inq-actions', again)));
  }
  return eaNode('div', 'inq-grid', eaNode('section', 'info-card', eaNode('header', 'info-card-head', eaNode('h2', '', '문서 내용')), info), side);
}

function eaDraftDetail(d) {
  const info = eaNode('dl', 'detail');
  const f = d.fields;
  const rows = d.form === 'LEAVE'
    ? [['구분', EA_LEAVE_KIND[f.kind] || '-'], ['기간', eaSpan(f) || '-'], ['사유', f.reason || '-']]
    : [['장비', f.kind ? `${f.kind} ${f.qty || ''}대` : '-'], ['수령 지점', d.branchName || '-'], ['사유', f.reason || '-']];
  [['양식', EA_FORMS[d.form]], ...rows, ['결재선', d.lineNames.join(' → ') || '-'], ['저장', whenFull(d.savedAt)]]
    .forEach(([k, v]) => info.append(eaNode('dt', '', k), eaNode('dd', '', v)));
  const resume = eaButton('이어서 쓰기', 'btn btn-primary', true);
  const drop = eaButton('삭제', 'btn', true);
  resume.addEventListener('click', () => eaStartWrite(d.form, { fields: d.fields, line: d.line, draftId: d.id }));
  drop.addEventListener('click', () => {
    drop.disabled = true;
    api.post(`/approval/api/drafts/${encodeURIComponent(d.id)}/delete`)
      .done(() => { eaState.open = null; loadApprovalBox(); })
      .fail(xhr => { showToast(apiMessage(xhr)); if (xhr.status === 404) loadApprovalBox(); })
      .always(() => { drop.disabled = false; });
  });
  return eaNode('div', 'inq-grid', eaNode('section', 'info-card', eaNode('header', 'info-card-head', eaNode('h2', '', '임시저장 내용')), info),
    eaNode('section', 'info-card', eaNode('div', 'inq-body', eaNode('p', 'inq-hint', '임시저장은 검사 없이 저장돼 있습니다. 이어서 쓰고 제출할 때 검사합니다.'), eaNode('div', 'inq-actions', resume, drop))));
}

// ---------- 결재 처리 ----------
function eaDecideText(r, approve) {
  const what = `${r.name}님의 ${r.form === 'LEAVE' ? '휴가를' : '장비 지급 신청을'}`;
  if (!approve) return `${what} 반려했습니다`;
  if (r.status === 'APPROVED') return `${what} 최종 승인했습니다${r.ticketId ? '. 지급 티켓을 만들었습니다' : ''}`;
  return `${what} 승인하고 다음 결재자에게 넘겼습니다`;
}

function eaDecide(d, approve, comment, btns) {
  if (!approve && !comment) return showToast('반려 사유를 적어 주세요');
  btns.forEach(b => { b.disabled = true; });
  api.post(`/approval/api/documents/${encodeURIComponent(d.id)}/${approve ? 'approve' : 'reject'}`, { comment })
    .done(r => {
      showToast(eaDecideText(r, approve));
      eaState.open = null;
      loadApprovalBox();
    })
    .fail(xhr => {
      showToast(apiMessage(xhr));
      if (xhr.status === 404 || xhr.status === 409) loadApprovalBox(); // 이미 처리됐거나 회수된 문서: 새로 받아 그린다
    })
    .always(() => btns.forEach(b => { b.disabled = false; }));
}

function eaCancel(d, btn) {
  btn.disabled = true;
  api.post(`/approval/api/documents/${encodeURIComponent(d.id)}/withdraw`)
    .done(() => {
      showToast('신청을 회수했습니다');
      eaState.open = null;
      loadApprovalBox();
    })
    .fail(xhr => {
      showToast(apiMessage(xhr));
      if (xhr.status === 404 || xhr.status === 409) loadApprovalBox();
    })
    .always(() => { btn.disabled = false; });
}
