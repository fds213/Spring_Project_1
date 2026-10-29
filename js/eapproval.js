// 전자결재(사원): 기안 작성(approval-write)과 결재함(approval-box). 양식은 휴가 신청서와 장비 지급 신청서 둘이다 (요구분석서 3.4 전자결재).
// 결재선은 최대 2단계로 순서대로 승인한다. 마지막 결재자가 승인해야 최종 승인이다.
// - 휴가: 최종 승인 때 연차가 차감되고 일정에 나타난다. 근태·연차 화면의 신청 폼과 같은 저장소(LEAVE_KEY)를 쓰므로 신청은
//   attendance.js applyLeave, 승인·반려는 approval.js decideLeave 가 한다 (같은 규칙을 두 곳에 베끼지 않으려고).
// - 장비 지급: 최종 승인 때 IT 서비스에 지급 티켓이 만들어진다 (it-service.js addTicket). 지급이 끝나면 내 장비에 나타난다.
// 임시저장은 검사 없이 입력값을 그대로 저장하고, 반려·회수된 문서는 결재함에서 "재기안"으로 내용을 채워 새로 올린다.
// 결재자는 결재자 자격(부서장·인사 담당·업체 관리자)이 있는 직원뿐이다 (attendance.js approverOptions). 서버는 이 자격을 다시 검사한다.
// 시안이라 localStorage 에 업체별로 저장한다. 이 파일은 it-service.js 뒤, common.js 앞에 로드된다.

const EA_KEY = 'ieum-demo-approvals-v1'; // 장비 지급 신청서 { 업체코드: [{ id, empNo, name, dept, at, title, fields, line, step, status, history, ticketId }] } (휴가는 LEAVE_KEY)
const EA_DRAFT_KEY = 'ieum-demo-approval-drafts-v1'; // { 업체코드: [{ id, empNo, form, fields, line, savedAt }] }
const EA_FORMS = { LEAVE: '휴가 신청서', EQUIP: '장비 지급 신청서' };
const EA_TABS = { WAIT: '내 순번 대기', PROGRESS: '진행', DONE: '완료', REJECTED: '반려·회수', DRAFT: '임시저장' };
const EQUIP_KINDS = ['노트북', '모니터', '키보드·마우스', '모바일 기기', '기타'];
const EQUIP_QTY_MAX = 10;
const EA_REASON_MAX = 200;
const EA_ACTION = { APPROVE: '승인', REJECT: '반려', CANCEL: '회수' };

const eaEl = id => document.getElementById(id);
const eaNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const eaCell = (label, ...parts) => {
  const td = eaNode('td', '', eaNode('div', 'cell', ...parts));
  td.dataset.label = label; // 폰에서 값 앞에 붙는 열 이름
  return td;
};
const eaState = { tab: 'WAIT', open: null, draftId: null, pre: null };

// ---------- 문서 목록 (휴가와 장비 문서의 저장 모양이 달라서 결재함용 한 가지 모양으로 맞춘다) ----------
const eaEquipDocs = () => loadStore(EA_KEY, {})[session.company] || [];
const eaMyDrafts = () => (loadStore(EA_DRAFT_KEY, {})[session.company] || []).filter(d => d.empNo === session.id);

function eaDocs() {
  const span = l => `${l.from}${l.to !== l.from ? ` ~ ${l.to}` : ''}`;
  const leaves = allLeaves().map(l => ({
    src: 'LEAVE', id: l.id, form: 'LEAVE', empNo: l.empNo, name: l.name || nameOfStaff(l.empNo), dept: l.dept || '', at: l.at,
    title: `휴가 신청서 · ${LEAVE_LABEL[l.kind]} ${span(l)}`, status: leaveStatus(l), step: l.step || 0, line: l.line || [], history: l.history || [],
    rows: [['구분', LEAVE_LABEL[l.kind]], ['기간', `${span(l)} (${l.days}일)`], ['사유', l.reason || '-']],
    fields: { kind: l.kind, from: l.from, to: l.to, reason: l.reason || '' },
  }));
  const equips = eaEquipDocs().map(d => ({
    ...d, src: 'EQUIP', form: 'EQUIP',
    rows: [['장비', `${d.fields.kind} ${d.fields.qty}대`], ['수령 지점', branchNameOf(session.company, d.fields.branchId)], ['사유', d.fields.reason],
      ...(d.ticketId ? [['지급 티켓', `${d.ticketId} (내 티켓에서 처리 상태를 봅니다)`]] : [])],
  }));
  return [...leaves, ...equips].sort((a, b) => new Date(b.at) - new Date(a.at));
}

// 결재함 탭별 문서. "완료·반려"는 내가 올렸거나 내가 결재한 문서까지 보여 준다
function eaBucket(tab) {
  if (!session || session.type !== 'EMPLOYEE') return [];
  const me = session.id;
  const involved = d => d.empNo === me || d.history.some(h => h.by === me);
  const docs = eaDocs();
  if (tab === 'WAIT') return docs.filter(d => d.status === 'REQUESTED' && d.line[d.step] === me && d.empNo !== me);
  if (tab === 'PROGRESS') return docs.filter(d => d.status === 'REQUESTED' && d.empNo === me);
  if (tab === 'DONE') return docs.filter(d => d.status === 'APPROVED' && involved(d));
  if (tab === 'REJECTED') return docs.filter(d => ['REJECTED', 'CANCELED'].includes(d.status) && involved(d));
  return [];
}

// 사이드 메뉴 "결재함" 옆의 숫자: 내 순번에 와 있는 문서 수
function eaRefreshBadge() {
  const el = eaEl('navApprBoxCount');
  if (!el) return;
  const n = eaBucket('WAIT').length;
  el.textContent = n;
  el.hidden = n === 0;
}

// "박관리 ✓ → 최팀장 (결재 중)"처럼 결재선의 진행을 한 줄로
function eaLineText(d) {
  if (!d.line.length) return '-';
  return d.line.map((no, i) => {
    const name = nameOfStaff(no);
    if (d.status === 'APPROVED' || i < d.step) return `${name} ✓`;
    if (i === d.step && d.status === 'REQUESTED') return `${name} (결재 중)`;
    if (i === d.step && d.status === 'REJECTED') return `${name} (반려)`;
    return name;
  }).join(' → ');
}

// ---------- 기안 작성 ----------
const eaOptions = () => approverOptions();

function eaFillSelects() {
  const people = eaOptions();
  const fill = (id, withNone) => {
    const sel = eaEl(id);
    const keep = sel.value;
    sel.replaceChildren(...(withNone ? [new Option('(없음)', '')] : [new Option('(고르세요)', '')]),
      ...people.map(e => new Option(`${e.name} (${e.dept} · ${e.rank})`, e.empNo)));
    if ([...sel.options].some(o => o.value === keep)) sel.value = keep;
  };
  fill('eaAppr1', false);
  fill('eaAppr2', true);
  eaEl('eaEquipBranch').replaceChildren(...branchesOf(session.company).map(b => new Option(b.name, b.id)));
  eaEl('eaEquipKind').replaceChildren(...EQUIP_KINDS.map(k => new Option(k, k)));
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
  const sum = form === 'LEAVE' ? leaveSummary() : null;
  const rows = form === 'LEAVE'
    ? [['남은 연차', sum ? `${dayText(sum.available)} (승인 대기 ${dayText(sum.pendingDays)} 제외)` : '-'], ['결재', '최대 2단계, 순서대로 승인합니다'],
      ['최종 승인 후', '연차가 차감되고 일정에 나타납니다'], ['주의', '주말과 등록 휴일은 세지 않고, 오전·오후 반차는 0.5일입니다']]
    : [['결재', '최대 2단계, 순서대로 승인합니다'], ['최종 승인 후', 'IT 서비스에 지급 티켓이 만들어집니다'], ['지급 확인', '내 티켓에서 처리 상태를, 지급되면 내 장비에서 봅니다'],
      ['수령 지점', '업체 관리자가 등록한 지점 중에서 고릅니다']];
  eaEl('eaGuide').replaceChildren(...rows.flatMap(([k, v]) => [eaNode('dt', '', k), eaNode('dd', '', v)]));
}

function eaApplyForm(p) {
  const f = p.fields || {};
  eaEl('eaFormKind').value = p.form || 'LEAVE';
  eaEl('eaLeaveKind').value = f.kind && ['FULL', 'AM', 'PM'].includes(f.kind) ? f.kind : 'FULL';
  eaEl('eaLeaveFrom').value = (p.form === 'LEAVE' && f.from) || '';
  eaEl('eaLeaveTo').value = (p.form === 'LEAVE' && f.to) || '';
  eaEl('eaLeaveReason').value = (p.form === 'LEAVE' && f.reason) || '';
  eaEl('eaEquipKind').value = p.form === 'EQUIP' && EQUIP_KINDS.includes(f.kind) ? f.kind : EQUIP_KINDS[0];
  eaEl('eaEquipQty').value = (p.form === 'EQUIP' && f.qty) || 1;
  if (p.form === 'EQUIP' && f.branchId) eaEl('eaEquipBranch').value = f.branchId;
  eaEl('eaEquipReason').value = (p.form === 'EQUIP' && f.reason) || '';
  eaEl('eaAppr1').value = (p.line && p.line[0]) || '';
  eaEl('eaAppr2').value = (p.line && p.line[1]) || '';
  eaEl('eaError').textContent = '';
  eaSyncForm();
}

// 다른 화면(내 장비, 결재함의 재기안·이어서 쓰기)에서 기안 작성을 열 때 쓴다
function eaStartWrite(form, pre) {
  eaState.pre = { form, ...pre };
  location.hash = '#approval-write';
  if (location.hash === '#approval-write' && eaEl('view-approval-write').classList.contains('on')) openApprovalWriteView();
}

function openApprovalWriteView() {
  eaFillSelects();
  const pre = eaState.pre;
  eaState.pre = null;
  eaState.draftId = pre && pre.draftId ? pre.draftId : null;
  eaApplyForm(pre || { form: 'LEAVE', fields: {}, line: [] });
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

eaEl('eaFormKind').addEventListener('change', eaSyncForm);
eaEl('eaLeaveKind').addEventListener('change', eaSyncForm);
eaEl('eaLeaveFrom').addEventListener('change', eaSyncForm);

function eaSaveDrafts(change) {
  const all = loadStore(EA_DRAFT_KEY, {});
  const list = (all[session.company] = all[session.company] || []);
  change(list);
  saveStore(EA_DRAFT_KEY, all);
}

// 임시저장: 검사 없이 지금 입력한 그대로. 저장한 뒤에는 같은 임시저장 문서를 계속 고친다
eaEl('btnEaDraft').addEventListener('click', () => {
  if (readOnly) return showToast(readOnlyMessage());
  const c = eaCollect();
  const line = [c.a1, c.a2];
  eaSaveDrafts(list => {
    const old = eaState.draftId && list.find(d => d.id === eaState.draftId && d.empNo === session.id);
    if (old) Object.assign(old, { form: c.form, fields: c.fields, line, savedAt: new Date().toISOString() });
    else {
      eaState.draftId = `D-${Date.now()}`;
      list.push({ id: eaState.draftId, empNo: session.id, form: c.form, fields: c.fields, line, savedAt: new Date().toISOString() });
    }
  });
  showToast('임시저장했습니다. 결재함의 임시저장에서 이어서 쓸 수 있습니다');
});

// 장비 지급 신청서 제출. 문제가 있으면 사용자에게 보여 줄 문장을, 없으면 저장하고 빈 문자열을 돌려준다 (휴가는 applyLeave 가 같은 방식)
function eaApplyEquip(f, a1, a2) {
  if (!EQUIP_KINDS.includes(f.kind)) return '장비 종류를 골라 주세요';
  if (!Number.isInteger(f.qty) || f.qty < 1 || f.qty > EQUIP_QTY_MAX) return `수량은 1~${EQUIP_QTY_MAX}대로 입력해 주세요`;
  if (!branchesOf(session.company).some(b => b.id === f.branchId)) return '수령 지점을 골라 주세요';
  if (!f.reason) return '신청 사유를 입력해 주세요';
  if (f.reason.length > EA_REASON_MAX) return `사유는 ${EA_REASON_MAX}자까지 쓸 수 있습니다`;
  const ok = new Set(eaOptions().map(e => e.empNo));
  if (!ok.size) return '결재할 사람이 없습니다. 같은 업체에 부서장·인사 담당·업체 관리자가 본인 말고 한 명 있어야 합니다';
  if (!a1 || !ok.has(a1)) return '1단계 결재자를 골라 주세요';
  if (a2 && (!ok.has(a2) || a2 === a1)) return '2단계 결재자는 1단계와 다른 사람이어야 합니다';
  const me = myStaffRecord();
  const all = loadStore(EA_KEY, {});
  (all[session.company] = all[session.company] || []).push({
    id: `E-${Date.now()}-${Math.floor(Math.random() * 1000)}`, empNo: session.id, name: session.name, dept: me ? me.dept : '', at: new Date().toISOString(),
    title: `장비 지급 신청서 · ${f.kind} ${f.qty}대`, fields: f, line: [a1, ...(a2 ? [a2] : [])], step: 0, status: 'REQUESTED', history: [],
  });
  saveStore(EA_KEY, all);
  return '';
}

eaEl('eaForm').addEventListener('submit', e => {
  e.preventDefault();
  const err = eaEl('eaError');
  if (readOnly) { err.textContent = readOnlyMessage(); return; }
  const c = eaCollect();
  const msg = c.form === 'LEAVE'
    ? applyLeave(c.fields.kind, c.fields.from, c.fields.to, c.fields.reason, c.a1, c.a2)
    : eaApplyEquip(c.fields, c.a1, c.a2);
  if (msg) { err.textContent = msg; return; }
  if (eaState.draftId) eaSaveDrafts(list => { const i = list.findIndex(d => d.id === eaState.draftId); if (i >= 0) list.splice(i, 1); });
  eaState.draftId = null;
  err.textContent = '';
  eaState.tab = 'PROGRESS';
  eaState.open = null;
  showToast(`${EA_FORMS[c.form]}를 제출했습니다. 첫 결재자에게 넘어갔습니다`);
  location.hash = '#approval-box';
});

// ---------- 결재함 ----------
function openApprovalBoxView() {
  eaState.open = null;
  renderApprovalBox();
}

function renderApprovalBox() {
  const buckets = Object.fromEntries(Object.keys(EA_TABS).map(t => [t, t === 'DRAFT' ? eaMyDrafts() : eaBucket(t)]));
  Object.keys(EA_TABS).forEach(t => { eaEl(`eaCnt${t}`).textContent = `${buckets[t].length}건`; });
  document.querySelectorAll('#eaTabs .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.tab === eaState.tab));
  eaRefreshBadge();

  const draft = eaState.tab === 'DRAFT';
  eaEl('eaHead').replaceChildren(eaNode('tr', '', ...(draft ? ['문서', '저장 시각'] : ['문서', '기안자', '결재선', '상태', '기안일']).map(t => eaNode('th', '', t))));
  const cols = draft ? 2 : 5;
  const rows = [];
  buckets[eaState.tab].forEach(d => {
    const key = `${d.src || 'DRAFT'}:${d.id}`;
    const isOpen = eaState.open === key;
    const tr = eaNode('tr', 'row-link' + (isOpen ? ' is-open' : ''), ...(draft
      ? [eaCell('문서', eaNode('span', '', `${EA_FORMS[d.form]}${eaDraftTitle(d)}`)), eaCell('저장 시각', eaNode('span', '', dayTime(d.savedAt)))]
      : [eaCell('문서', eaNode('span', '', d.title)), eaCell('기안자', eaNode('span', '', d.name), eaNode('small', 'cell-sub', d.dept)),
        eaCell('결재선', eaNode('span', '', eaLineText(d))), eaCell('상태', eaNode('span', `status status-${LEAVE_STATUS_CLASS[d.status]}`, LEAVE_STATUS[d.status])),
        eaCell('기안일', eaNode('span', '', dayTime(d.at)))]));
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
  ? (d.fields.from ? ` · ${d.fields.from}${d.fields.to && d.fields.to !== d.fields.from ? ` ~ ${d.fields.to}` : ''}` : '')
  : (d.fields.kind ? ` · ${d.fields.kind} ${d.fields.qty || ''}대` : ''));

function eaDocDetail(d) {
  const info = eaNode('dl', 'detail');
  [['문서', d.title], ['기안자', `${d.name}${d.dept ? ` (${d.dept})` : ''}`], ['기안일', dayTime(d.at)], ...d.rows, ['결재선', eaLineText(d)], ['상태', LEAVE_STATUS[d.status]]]
    .forEach(([k, v]) => info.append(eaNode('dt', '', k), eaNode('dd', '', v)));
  const log = eaNode('ul', 'bill-history', ...(d.history.length
    ? d.history.map(h => eaNode('li', '', eaNode('span', '', `${dayTime(h.at)} · ${h.name} · ${EA_ACTION[h.action] || h.action}${h.comment ? ` · ${h.comment}` : ''}`)))
    : [eaNode('li', '', eaNode('span', 'muted', '아직 처리된 단계가 없습니다'))]));
  const side = eaNode('section', 'info-card', eaNode('header', 'info-card-head', eaNode('h2', '', '결재 이력')), log);

  const me = session.id;
  if (eaState.tab === 'WAIT') {
    const box = eaNode('textarea', 'inq-text');
    box.maxLength = EA_REASON_MAX;
    box.placeholder = '의견 (반려할 때는 사유가 꼭 필요합니다)';
    box.setAttribute('aria-label', '결재 의견');
    const ok = eaNode('button', 'btn btn-primary', '승인');
    const no = eaNode('button', 'btn', '반려');
    [ok, no].forEach(b => { b.type = 'button'; b.dataset.write = ''; });
    ok.addEventListener('click', () => eaDecide(d, true, box.value.trim()));
    no.addEventListener('click', () => eaDecide(d, false, box.value.trim()));
    side.append(eaNode('div', 'inq-body', box, eaNode('div', 'inq-actions', ok, no)));
  } else if (d.empNo === me && d.status === 'REQUESTED') {
    const cancel = eaNode('button', 'btn', '회수');
    cancel.type = 'button';
    cancel.dataset.write = '';
    cancel.addEventListener('click', () => eaCancel(d));
    side.append(eaNode('div', 'inq-body', eaNode('p', 'inq-hint', '결재가 끝나기 전에는 문서를 회수할 수 있습니다. 회수한 문서는 반려·회수 탭에서 다시 기안합니다.'), eaNode('div', 'inq-actions', cancel)));
  } else if (d.empNo === me && ['REJECTED', 'CANCELED'].includes(d.status)) {
    const last = [...d.history].reverse().find(h => h.comment);
    const again = eaNode('button', 'btn btn-primary', '재기안');
    again.type = 'button';
    again.dataset.write = '';
    again.addEventListener('click', () => {
      if (readOnly) return showToast(readOnlyMessage());
      eaStartWrite(d.form, { fields: d.fields, line: d.line });
    });
    side.append(eaNode('div', 'inq-body', ...(last && d.status === 'REJECTED' ? [eaNode('p', 'notice-warn', `반려 사유: ${last.comment}`)] : []),
      eaNode('p', 'inq-hint', '내용과 결재선을 채운 새 기안 화면이 열립니다. 고친 뒤 다시 제출하세요.'), eaNode('div', 'inq-actions', again)));
  }
  return eaNode('div', 'inq-grid', eaNode('section', 'info-card', eaNode('header', 'info-card-head', eaNode('h2', '', '문서 내용')), info), side);
}

function eaDraftDetail(d) {
  const info = eaNode('dl', 'detail');
  const f = d.fields;
  const rows = d.form === 'LEAVE'
    ? [['구분', LEAVE_LABEL[f.kind] || '-'], ['기간', f.from ? `${f.from}${f.to && f.to !== f.from ? ` ~ ${f.to}` : ''}` : '-'], ['사유', f.reason || '-']]
    : [['장비', f.kind ? `${f.kind} ${f.qty || ''}대` : '-'], ['수령 지점', f.branchId ? branchNameOf(session.company, f.branchId) : '-'], ['사유', f.reason || '-']];
  [['양식', EA_FORMS[d.form]], ...rows, ['결재선', d.line.filter(Boolean).map(nameOfStaff).join(' → ') || '-'], ['저장', dayTime(d.savedAt)]]
    .forEach(([k, v]) => info.append(eaNode('dt', '', k), eaNode('dd', '', v)));
  const resume = eaNode('button', 'btn btn-primary', '이어서 쓰기');
  const drop = eaNode('button', 'btn', '삭제');
  [resume, drop].forEach(b => { b.type = 'button'; });
  resume.dataset.write = '';
  resume.addEventListener('click', () => {
    if (readOnly) return showToast(readOnlyMessage());
    eaStartWrite(d.form, { fields: d.fields, line: d.line, draftId: d.id });
  });
  drop.addEventListener('click', () => {
    eaSaveDrafts(list => { const i = list.findIndex(x => x.id === d.id); if (i >= 0) list.splice(i, 1); });
    eaState.open = null;
    renderApprovalBox();
  });
  return eaNode('div', 'inq-grid', eaNode('section', 'info-card', eaNode('header', 'info-card-head', eaNode('h2', '', '임시저장 내용')), info),
    eaNode('section', 'info-card', eaNode('div', 'inq-body', eaNode('p', 'inq-hint', '임시저장은 검사 없이 저장돼 있습니다. 이어서 쓰고 제출할 때 검사합니다.'), eaNode('div', 'inq-actions', resume, drop))));
}

// ---------- 결재 처리 ----------
function eaDecide(d, approve, comment) {
  if (readOnly) return showToast(readOnlyMessage());
  if (!approve && !comment) return showToast('반려 사유를 적어 주세요');
  if (d.src === 'LEAVE') decideLeave(d.id, approve, comment); // 연차 잔여·날짜 겹침 검사와 최종 승인 처리는 approval.js
  else decideEquip(d.id, approve, comment);
  eaState.open = null;
  renderApprovalBox();
  if (typeof refreshApprovalBadge === 'function') refreshApprovalBadge();
}

function decideEquip(id, approve, comment) {
  const all = loadStore(EA_KEY, {});
  const d = (all[session.company] || []).find(x => x.id === id);
  if (!d || d.status !== 'REQUESTED' || d.line[d.step] !== session.id || d.empNo === session.id) return showToast('이미 처리되었거나 내 순번이 아닙니다');
  const now = new Date().toISOString();
  d.history = [...d.history, { step: d.step, by: session.id, name: session.name, action: approve ? 'APPROVE' : 'REJECT', at: now, comment }];
  if (!approve) {
    d.status = 'REJECTED';
  } else if (d.step < d.line.length - 1) {
    d.step += 1; // 다음 결재자에게 넘어간다
  } else {
    d.status = 'APPROVED';
    d.decidedAt = now;
    // 최종 승인: 신청자 이름으로 IT 서비스에 지급 티켓을 만든다. 승인한 사람이 아니라 신청자의 티켓이다
    const t = addTicket(session.company, {
      type: 'EQUIPMENT', title: `장비 지급 · ${d.fields.kind} ${d.fields.qty}대`, body: d.fields.reason, branchId: d.fields.branchId,
      empNo: d.empNo, name: d.name, dept: d.dept, kind: d.fields.kind, qty: d.fields.qty, docId: d.id,
    });
    d.ticketId = t.id;
  }
  saveStore(EA_KEY, all);
  showToast(!approve ? `${d.name}님의 장비 지급 신청을 반려했습니다` : d.status === 'APPROVED' ? `${d.name}님의 장비 지급 신청을 최종 승인했습니다. 지급 티켓을 만들었습니다` : `${d.name}님의 장비 지급 신청을 승인하고 다음 결재자에게 넘겼습니다`);
}

function eaCancel(d) {
  if (readOnly) return showToast(readOnlyMessage());
  if (d.src === 'LEAVE') {
    cancelLeave(d.id); // 본인이 올린 승인 대기 문서만 회수된다 (attendance.js)
  } else {
    const all = loadStore(EA_KEY, {});
    const x = (all[session.company] || []).find(z => z.id === d.id && z.empNo === session.id && z.status === 'REQUESTED');
    if (!x) return;
    x.status = 'CANCELED';
    x.history = [...x.history, { step: x.step, by: session.id, name: session.name, action: 'CANCEL', at: new Date().toISOString(), comment: '' }];
    saveStore(EA_KEY, all);
    showToast('신청을 회수했습니다');
  }
  eaState.open = null;
  renderApprovalBox();
}

// 다른 탭의 신청·결재가 열려 있는 결재함에 바로 보이게 한다
window.addEventListener('storage', e => {
  if (!session || session.type !== 'EMPLOYEE') return;
  if (![EA_KEY, EA_DRAFT_KEY, LEAVE_KEY].includes(e.key)) return;
  eaRefreshBadge();
  if (eaEl('view-approval-box').classList.contains('on')) renderApprovalBox();
});
