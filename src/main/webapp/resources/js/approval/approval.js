// 전자결재(사원): 기안 작성(approval-write)과 결재함(approval-box). 양식은 휴가 신청서와 장비 지급 신청서 둘이다 (요구분석서 3.4 전자결재).
// 결재선은 최대 2단계로 순서대로 승인한다. 마지막 결재자가 승인해야 최종 승인이다.
// - 휴가: 최종 승인 때 연차가 차감되고 일정에 나타난다 (연차 화면의 신청 내역과 같은 데이터다).
// - 장비 지급: 최종 승인 때 IT 서비스에 지급 티켓이 만들어진다. 지급이 끝나면 내 장비에 나타난다.
// 반려된 문서는 결재함에서 "재기안"으로 내용을 채워 새로 올린다. 임시저장은 핵심 뒤 추가(11/9까지, 요구분석서 3장 범위 표)라 아직 만들지 않았다.
// 결재자 목록, 내 순번인지, 재기안할 수 있는지는 서버가 정해 응답에 담는다. 화면은 받은 대로 그린다.
// 읽기 전용 업체는 쓰는 요청이 409로 거절된다(공통 검사 ReadOnlyInterceptor). 버튼의 data-write로 화면도 먼저 막는다.

const EA_FORMS = { DOC_LEAVE: '휴가 신청서', DOC_EQUIPMENT: '장비 지급 신청서' };
const EA_TABS = { WAIT: '내 순번 대기', PROGRESS: '진행', DONE: '완료', REJECTED: '반려' };
const EA_STATUS = { IN_PROGRESS: '진행', APPROVED: '완료', REJECTED: '반려' };
const EA_STATUS_CLASS = { IN_PROGRESS: 'waiting', APPROVED: 'paid', REJECTED: 'failed' };
const EA_LEAVE_TYPE = { LV_FULL: '연차', LV_AM: '오전 반차', LV_PM: '오후 반차' };
const EQUIP_QTY_MAX = 10;
const EA_REASON_MAX = 200; // 장비 신청 사유, 장비 문서 결재 의견
const EA_LEAVE_COMMENT_MAX = 100; // 휴가 문서 결재 의견
const EA_ACTION = { APR_SUBMIT: '제출', APR_APPROVE: '승인', APR_REJECT: '반려' };
const EA_ACTION_STATUS = { APR_SUBMIT: 'approved', APR_APPROVE: 'paid', APR_REJECT: 'failed' }; // 결재 이력 표시 색 (common.css 상태 색)
const EA_ACTION_CLASS = { APR_SUBMIT: 'submit', APR_APPROVE: 'approve', APR_REJECT: 'reject' };

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
const eaSpan = f => (f.fromDate ? `${f.fromDate}${f.toDate && f.toDate !== f.fromDate ? ` ~ ${f.toDate}` : ''}` : '');
const eaState = { tab: 'WAIT', open: null, pre: null, box: null, opts: null, initial: undefined, holidays: {} };


// "박관리 ✓ → 최팀장 (결재 중)"처럼 결재선의 진행을 한 줄로
function eaLineText(d) {
  if (!d.steps.length) return '-';
  const current = d.statusCode === 'IN_PROGRESS' ? d.steps.find(p => p.statusCode === 'WAITING') : null;
  return d.steps.map(p => {
    if (p.statusCode === 'APPROVED') return `${p.employeeName} ✓`;
    if (p.statusCode === 'REJECTED') return `${p.employeeName} (반려)`;
    return p === current ? `${p.employeeName} (결재 중)` : p.employeeName;
  }).join(' → ');
}

// ---------- 기안 작성 ----------
// 양식(eaFormKind)과 휴가 구분(eaLeaveKind)의 값은 숨긴 select에 두고, 화면에는 탭·버튼으로 보인다
const eaToday = () => { const d = new Date(); return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`; };

// 결재자 한 사람의 표시: 체험 신청으로 만든 업체 관리자는 부서·직급이 없어 있는 값만 괄호에 넣는다
const eaApproverOption = e => {
  const sub = [e.departmentName, e.gradeName].filter(Boolean).join(' · ');
  return new Option(sub ? `${e.employeeName} (${sub})` : e.employeeName, e.employeeId);
};

// 2단계 목록: 1단계에서 고른 사람은 뺀다 (같은 사람을 두 번 고르지 않게)
function eaFillAppr2() {
  const o = eaState.opts || { approvers: [] };
  const a1 = eaEl('eaAppr1').value;
  const sel = eaEl('eaAppr2');
  const keep = sel.value;
  sel.replaceChildren(new Option('2단계 없음', ''), ...o.approvers.filter(e => String(e.employeeId) !== a1).map(eaApproverOption));
  sel.value = [...sel.options].some(x => x.value === keep) ? keep : '';
}

function eaFillSelects() {
  const o = eaState.opts || { approvers: [], branches: [], equipKinds: [] };
  const a1 = eaEl('eaAppr1');
  const keep = a1.value;
  const pickHint = new Option('1단계 결재자 선택', '');
  pickHint.dataset.placeholder = ''; // 안내 문구: 펼친 목록에는 넣지 않는다(select.js). '2단계 없음'은 고를 수 있는 항목이다
  a1.replaceChildren(pickHint, ...o.approvers.map(eaApproverOption));
  if ([...a1.options].some(x => x.value === keep)) a1.value = keep;
  eaFillAppr2();
  eaEl('eaEquipBranch').replaceChildren(...o.branches.map(b => new Option(b.branchName, b.branchId)));
  eaEl('eaEquipKind').replaceChildren(...o.equipKinds.map(k => new Option(k.codeName, k.code)));
  // 고를 사람·지점이 없으면 제출을 누르기 전에 미리 알린다
  const hint = eaEl('eaApprHint');
  const none = eaState.opts && !o.approvers.length;
  hint.classList.toggle('is-warn', !!none);
  hint.textContent = none
    ? '결재할 사람이 없습니다. 같은 업체에 본인 말고 재직 중인 직원이 한 명 있어야 합니다.'
    : '같은 업체의 재직 중인 직원 중에서 고릅니다. 결재자는 이 문서만 승인·반려할 수 있습니다.';
  eaEl('eaBranchHintRow').hidden = !(eaState.opts && !o.branches.length);
}

// ---- 등록 휴일: 신청 일수에서 빼려고 고른 기간의 달마다 일정 API(/schedule/api/schedules)의 holidays를 받아 둔다 ----
// 일정 화면과 서버의 휴가 일수 계산이 같은 휴일(AttendanceService.getHolidays)을 쓴다. 서버는 제출할 때 다시 센다.
// eaState.holidays['YYYY-MM'] = 그달 휴일 날짜 Set(받는 중이면 'loading', 못 받으면 'failed')
const eaYmd = d => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
function eaMonthsOf(from, to) {
  const months = [];
  for (let d = new Date(`${from.slice(0, 7)}-01T00:00:00`), end = new Date(`${to.slice(0, 7)}-01T00:00:00`); d <= end && months.length < 13; d.setMonth(d.getMonth() + 1)) {
    months.push(eaYmd(d).slice(0, 7));
  }
  return months;
}
function eaLoadHolidays(from, to) {
  if (!from || !to || to < from) return;
  eaMonthsOf(from, to).filter(m => !eaState.holidays[m]).forEach(m => {
    eaState.holidays[m] = 'loading';
    api.get('/schedule/api/schedules', { month: m })
      .done(r => { eaState.holidays[m] = new Set((r.holidays || []).map(h => h.date)); eaSyncForm(); })
      .fail(() => { eaState.holidays[m] = 'failed'; eaSyncForm(); });
  });
}
// 고른 기간의 휴일을 다 받았는지: 'ok' | 'loading' | 'failed'
function eaHolidayState(from, to) {
  const v = eaMonthsOf(from, to).map(m => eaState.holidays[m]);
  if (v.some(x => x === 'failed')) return 'failed';
  return v.every(x => x instanceof Set) ? 'ok' : 'loading';
}
const eaIsHoliday = key => { const v = eaState.holidays[key.slice(0, 7)]; return v instanceof Set && v.has(key); };

// 주말만 뺀 날 수 (휴일 목록을 못 받았을 때도 주말만 고른 신청은 막으려고 따로 센다)
function eaWeekdayCount(from, to) {
  if (!from || !to || to < from) return 0;
  let n = 0;
  for (let d = new Date(`${from}T00:00:00`), end = new Date(`${to}T00:00:00`); d <= end; d.setDate(d.getDate() + 1)) {
    if (d.getDay() !== 0 && d.getDay() !== 6) n++;
  }
  return n;
}

// 신청 일수: 주말과 (받아 둔) 등록 휴일을 뺀 날 수. 반차는 그날이 쉬는 날이 아니면 0.5일
function eaRequestDays(f) {
  const half = f.leaveType !== 'LV_FULL';
  const from = f.fromDate;
  const to = half ? f.fromDate : f.toDate;
  if (!from || !to || to < from) return 0;
  let n = 0;
  for (let d = new Date(`${from}T00:00:00`), end = new Date(`${to}T00:00:00`); d <= end; d.setDate(d.getDate() + 1)) {
    if (d.getDay() !== 0 && d.getDay() !== 6 && !eaIsHoliday(eaYmd(d))) n++;
  }
  return half ? (n ? 0.5 : 0) : n;
}

// [제출] 옆 한 줄 요약: 무엇을 누구에게 내는지. 아직 안 고른 것이 있으면 무엇을 고르면 되는지 알려 준다
const EA_WEEK = ['일', '월', '화', '수', '목', '금', '토'];
const eaMd = v => { const d = new Date(`${v}T00:00:00`); return `${d.getMonth() + 1}월 ${d.getDate()}일(${EA_WEEK[d.getDay()]})`; };
function eaSummary() {
  const el = eaEl('eaSummary');
  if (!el) return;
  const c = eaCollect();
  const f = c.fields;
  const pick = id => { const s = eaEl(id); return s.value ? s.options[s.selectedIndex].text.replace(/ \(.*\)$/, '') : ''; };
  const line = [pick('eaAppr1'), pick('eaAppr2')].filter(Boolean).join(' → ');
  const todo = [];
  let what = null;
  if (c.form === 'DOC_LEAVE') {
    const half = f.leaveType !== 'LV_FULL';
    if (!f.fromDate || (!half && !f.toDate)) todo.push('기간');
    else if (!eaRequestDays(f)) { el.classList.add('is-todo'); el.textContent = '고른 날이 모두 주말이나 휴일입니다. 기간을 다시 골라 주세요'; return; }
    else {
      const span = !half && f.toDate !== f.fromDate ? `${eaMd(f.fromDate)} ~ ${eaMd(f.toDate)}` : eaMd(f.fromDate);
      what = [span, ` ${EA_LEAVE_TYPE[f.leaveType]} `, eaNode('strong', '', eaDays(eaRequestDays(f)))];
    }
    if (!f.reason) todo.push('사유');
  } else {
    if (!f.purpose) todo.push('사유');
    const kind = pick('eaEquipKind');
    const branch = pick('eaEquipBranch');
    if (kind) what = [`${kind} `, eaNode('strong', '', `${f.quantity || 0}대`), ...(branch ? [` · ${branch} 수령`] : [])];
  }
  if (!c.a1) todo.push('1단계 결재자');
  el.classList.toggle('is-todo', !!todo.length);
  if (todo.length) { el.textContent = `아직 비어 있음: ${todo.join(' · ')}`; return; }
  el.replaceChildren(...(what || []), ` · ${line} 결재`);
}

// 양식에 따라 보이는 입력칸과 안내를 바꾼다. 입력해 둔 값은 지우지 않는다
function eaSyncForm() {
  const form = eaEl('eaFormKind').value;
  const leave = form === 'DOC_LEAVE';
  eaEl('eaLeaveFields').hidden = !leave;
  eaEl('eaEquipFields').hidden = leave;
  // 탭·라디오 묶음: Tab은 고른 버튼에만 멈추고, 묶음 안에서는 화살표로 옮긴다(eaArrowKeys)
  document.querySelectorAll('.ea-tab').forEach(t => { t.setAttribute('aria-selected', t.dataset.form === form); t.tabIndex = t.dataset.form === form ? 0 : -1; });

  const kind = eaEl('eaLeaveKind').value;
  document.querySelectorAll('#eaLeaveSeg [data-kind]').forEach(b => { b.setAttribute('aria-checked', b.dataset.kind === kind); b.tabIndex = b.dataset.kind === kind ? 0 : -1; });
  const half = kind !== 'LV_FULL';
  const from = eaEl('eaLeaveFrom');
  const to = eaEl('eaLeaveTo');
  from.min = eaToday(); // 지난 날짜는 달력에서 고르지 못하게 (서버도 거절한다)
  to.min = from.value || eaToday();
  if (half) to.value = from.value; // 반차는 하루만
  to.disabled = half;

  // 남은 연차와 이번 신청 일수
  const sum = eaState.opts ? eaState.opts.leave : null;
  eaEl('eaLeftCard').hidden = !leave;
  eaEl('eaLeft').textContent = sum ? (Number.isInteger(sum.available) ? sum.available : sum.available.toFixed(1)) : '-';
  eaEl('eaLeftSub').textContent = sum ? `승인 대기 ${eaDays(sum.pendingDays)} 제외` : '';
  const days = eaEl('eaDays');
  const lf = { leaveType: kind, fromDate: from.value, toDate: half ? from.value : to.value };
  const picked = from.value && lf.toDate && lf.toDate >= from.value;
  if (leave && picked) eaLoadHolidays(lf.fromDate, lf.toDate);
  const hs = picked ? eaHolidayState(lf.fromDate, lf.toDate) : 'ok';
  const n = eaRequestDays(lf);
  // 휴일을 받는 중이거나 못 받았으면 그 사실을 함께 적는다 (서버가 제출할 때 휴일을 빼고 다시 센다)
  const note = hs === 'loading' ? ' (휴일 확인 중)' : hs === 'failed' ? ' (주말 제외 · 휴일은 제출할 때 빠집니다)' : half ? '' : ' (주말·휴일 제외)';
  days.classList.remove('is-over');
  if (!from.value || (!half && !to.value)) days.replaceChildren();
  else if (!n) days.textContent = hs === 'loading' ? '휴일 확인 중…' : '고른 날이 모두 주말이나 휴일입니다';
  else {
    const left = sum && hs !== 'loading' ? sum.available - n : null;
    days.replaceChildren('신청 ', eaNode('strong', '', eaDays(n)), note,
      ...(left === null ? [] : left < 0 ? [' · ', eaNode('em', '', `남은 연차보다 ${eaDays(-left)} 많습니다`)] : [` · 사용 후 남는 연차 ${eaDays(left)}`]));
    days.classList.toggle('is-over', left !== null && left < 0);
  }

  const rows = leave
    ? ['최종 승인되면 연차가 차감되고 일정에 나타납니다.', '주말과 등록 휴일은 세지 않고, 오전·오후 반차는 0.5일입니다.', '결재자는 순서대로 승인하고, 반려되면 결재함에서 재기안합니다.']
    : ['최종 승인되면 IT 서비스에 지급 티켓이 만들어집니다.', '처리 상태는 내 티켓에서, 지급된 장비는 내 장비에서 봅니다.', '수령 지점은 업체 관리자가 등록한 지점 중에서 고릅니다.'];
  eaEl('eaGuide').replaceChildren(...rows.map(t => eaNode('li', '', t)));
  eaEl('eaEquipCount').textContent = `${eaEl('eaEquipReason').value.length} / ${EA_REASON_MAX}`;
  eaSummary();
}

function eaApplyForm(p) {
  const f = p.fields || {};
  const kinds = (eaState.opts && eaState.opts.equipKinds) || [];
  eaEl('eaFormKind').value = p.form || 'DOC_LEAVE';
  eaEl('eaLeaveKind').value = f.leaveType && ['LV_FULL', 'LV_AM', 'LV_PM'].includes(f.leaveType) ? f.leaveType : 'LV_FULL';
  eaEl('eaLeaveFrom').value = (p.form === 'DOC_LEAVE' && f.fromDate) || '';
  eaEl('eaLeaveTo').value = (p.form === 'DOC_LEAVE' && f.toDate) || '';
  eaEl('eaLeaveReason').value = (p.form === 'DOC_LEAVE' && f.reason) || '';
  eaEl('eaEquipKind').value = p.form === 'DOC_EQUIPMENT' && kinds.some(k => k.code === f.assetType) ? f.assetType : ((kinds[0] && kinds[0].code) || '');
  eaEl('eaEquipQty').value = (p.form === 'DOC_EQUIPMENT' && f.quantity) || 1;
  if (p.form === 'DOC_EQUIPMENT' && f.branchId) eaEl('eaEquipBranch').value = f.branchId;
  eaEl('eaEquipReason').value = (p.form === 'DOC_EQUIPMENT' && f.purpose) || '';
  eaEl('eaAppr1').value = (p.line && p.line[0]) || '';
  eaFillAppr2();
  eaEl('eaAppr2').value = (p.line && p.line[1]) || '';
  eaEl('eaError').textContent = '';
  eaSyncForm();
  eaState.initial = eaSnapshot(); // [취소] 때 쓰던 내용이 있는지 견줄 처음 값
}

// [취소] 때 견줄 값: 지금 보고 있는 양식만이 아니라 두 양식의 칸을 모두 모은다
// (장비 탭에 사유를 쓰고 휴가 탭으로 돌아와 [취소]를 눌러도 확인 창이 뜨게)
const EA_INPUT_IDS = ['eaLeaveKind', 'eaLeaveFrom', 'eaLeaveTo', 'eaLeaveReason', 'eaEquipKind', 'eaEquipQty', 'eaEquipBranch', 'eaEquipReason', 'eaAppr1', 'eaAppr2'];
const eaSnapshot = () => JSON.stringify(EA_INPUT_IDS.map(id => eaEl(id).value.trim()));

// 다른 화면(내 장비, 결재함의 재기안)에서 기안 작성을 열 때 쓴다 (util.js startApprovalWrite)
const eaStartWrite = (form, pre) => startApprovalWrite(form, pre);

// 화면을 열 때 (JSP 페이지가 열리면 부른다). 결재자·지점 목록을 받은 뒤 입력칸을 채운다
function openApprovalWriteView() {
  eaInitFiles();
  const pre = takeApprovalPre();
  eaState.pre = pre;
  eaEl('eaError').textContent = '';
  eaSyncForm();
  api.get('/approval/api/form-options')
    .done(opts => {
      eaState.opts = opts;
      eaFillSelects();
      eaApplyForm(pre || { form: 'DOC_LEAVE', fields: {}, line: [] });
    })
    .fail(xhr => { eaEl('eaError').textContent = apiMessage(xhr, '결재자 목록을 불러오지 못했습니다'); });
}

function eaCollect() {
  const form = eaEl('eaFormKind').value;
  return {
    form,
    fields: form === 'DOC_LEAVE'
      ? { leaveType: eaEl('eaLeaveKind').value, fromDate: eaEl('eaLeaveFrom').value, toDate: eaEl('eaLeaveTo').value, reason: eaEl('eaLeaveReason').value.trim() }
      : { assetType: eaEl('eaEquipKind').value, quantity: Number(eaEl('eaEquipQty').value), branchId: eaEl('eaEquipBranch').value, purpose: eaEl('eaEquipReason').value.trim() },
    a1: eaEl('eaAppr1').value, a2: eaEl('eaAppr2').value,
  };
}

// 제출 전에 먼저 알려 줄 것. 서버도 같은 검사를 다시 하고, 연차 잔여·날짜 겹침·휴일·지난 날짜는 서버만 본다
function eaCheck(c) {
  const f = c.fields;
  if (c.form === 'DOC_LEAVE') {
    if (!f.fromDate) return '시작일을 입력해 주세요';
    if (f.leaveType === 'LV_FULL' && (!f.toDate || f.toDate < f.fromDate)) return '종료일은 시작일 이후여야 합니다';
    // 주말만 고른 것은 휴일 목록과 상관없이 막고, 휴일까지 겹쳐 0일인 것은 휴일 목록을 받은 뒤에 막는다
    const lastDay = f.leaveType === 'LV_FULL' ? f.toDate : f.fromDate;
    if (!eaWeekdayCount(f.fromDate, lastDay) || (!eaRequestDays(f) && eaHolidayState(f.fromDate, lastDay) === 'ok')) return '고른 날이 모두 주말이나 휴일입니다. 기간을 다시 골라 주세요';
    if (!f.reason) return '사유를 입력해 주세요'; // 서버도 사유가 없으면 거절한다
  } else {
    if (!f.assetType) return '장비 종류를 골라 주세요';
    if (!Number.isInteger(f.quantity) || f.quantity < 1 || f.quantity > EQUIP_QTY_MAX) return `수량은 1~${EQUIP_QTY_MAX}대로 입력해 주세요`;
    if (!f.branchId) return eaState.opts && !eaState.opts.branches.length ? '등록된 지점이 없어 신청할 수 없습니다. 업체 관리자에게 지점 등록을 요청해 주세요' : '수령 지점을 골라 주세요';
    if (!f.purpose) return '신청 사유를 입력해 주세요';
    if (f.purpose.length > EA_REASON_MAX) return `사유는 ${EA_REASON_MAX}자까지 쓸 수 있습니다`;
  }
  if (eaState.opts && !eaState.opts.approvers.length) return '결재할 사람이 없습니다. 같은 업체에 본인 말고 재직 중인 직원이 한 명 있어야 합니다';
  if (!c.a1) return '1단계 결재자를 골라 주세요';
  if (c.a2 && c.a2 === c.a1) return '2단계 결재자는 1단계와 다른 사람이어야 합니다';
  return '';
}

// 확인 창(<dialog>)을 열고, 누른 버튼이 '확인'(value="ok")이면 true. Esc로 닫으면 false
function eaConfirm(id) {
  const dlg = eaEl(id);
  return new Promise(resolve => {
    let ok = false;
    dlg.querySelectorAll('button').forEach(b => { b.onclick = () => { ok = b.value === 'ok'; dlg.close(); }; });
    dlg.addEventListener('close', () => resolve(ok), { once: true });
    dlg.showModal();
  });
}

document.querySelectorAll('.ea-tab').forEach(t => t.addEventListener('click', () => {
  eaEl('eaFormKind').value = t.dataset.form;
  eaEl('eaError').textContent = '';
  eaSyncForm();
}));
document.querySelectorAll('#eaLeaveSeg [data-kind]').forEach(b => b.addEventListener('click', () => {
  eaEl('eaLeaveKind').value = b.dataset.kind;
  eaSyncForm();
}));
// 양식 탭(role="tab")과 구분 버튼(role="radio")은 화면 읽기 프로그램에 탭·라디오로 읽히므로, 화살표로 옮겨 고르게 한다
function eaArrowKeys(box, selector) {
  box?.addEventListener('keydown', e => {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
    if (!step) return;
    e.preventDefault();
    const btns = [...box.querySelectorAll(selector)];
    const next = btns[(btns.indexOf(document.activeElement) + step + btns.length) % btns.length];
    next.click();
    next.focus();
  });
}
eaArrowKeys(document.querySelector('#view-approval-write .ea-tabs'), '.ea-tab');
eaArrowKeys(eaEl('eaLeaveSeg'), '[data-kind]');
eaEl('eaLeaveFrom')?.addEventListener('change', () => {
  // 시작일을 종료일 뒤로 옮기면 종료일을 시작일에 맞춘다 (빈 종료일도 시작일로 채운다)
  const from = eaEl('eaLeaveFrom').value;
  const to = eaEl('eaLeaveTo');
  if (from && (!to.value || to.value < from)) to.value = from;
  eaSyncForm();
});
eaEl('eaLeaveTo')?.addEventListener('change', eaSyncForm);
eaEl('eaAppr1')?.addEventListener('change', () => { eaFillAppr2(); eaSummary(); });
eaEl('eaEquipReason')?.addEventListener('input', () => { eaEl('eaEquipCount').textContent = `${eaEl('eaEquipReason').value.length} / ${EA_REASON_MAX}`; });
['eaAppr2', 'eaEquipKind', 'eaEquipBranch'].forEach(id => eaEl(id)?.addEventListener('change', eaSummary));
['eaEquipQty', 'eaEquipReason', 'eaLeaveReason'].forEach(id => eaEl(id)?.addEventListener('input', eaSummary));
eaEl('eaForm')?.addEventListener('input', () => { eaEl('eaError').textContent = ''; });

eaEl('eaCancel')?.addEventListener('click', async () => {
  const dirty = eaState.initial !== undefined && eaSnapshot() !== eaState.initial;
  if (dirty && !(await eaConfirm('eaLeaveDialog'))) return;
  location.href = pageHref('/approval/list');
});

eaEl('eaForm')?.addEventListener('submit', async e => {
  e.preventDefault();
  const err = eaEl('eaError');
  const c = eaCollect();
  if (eaState.pre?.originalDocumentId && c.form !== eaState.pre.form) {
    err.textContent = '재기안은 원래 문서와 같은 양식으로 제출해 주세요.';
    return;
  }
  const msg = eaCheck(c);
  if (msg) { err.textContent = msg; return; }
  const btn = eaEl('eaForm').querySelector('[type="submit"]');
  if (btn.disabled) return;
  // 제출하면 회수할 수 없어 한 번 묻는다
  const names = [c.a1, c.a2].filter(Boolean).map(id => eaState.opts.approvers.find(a => String(a.employeeId) === id)).filter(Boolean).map(a => a.employeeName);
  eaEl('eaSubmitTitle').textContent = `${EA_FORMS[c.form]}를 제출할까요?`;
  eaEl('eaSubmitText').textContent = `${names.length > 1 ? `${names.join(' → ')} 순서로` : `${names[0]}님이`} 결재합니다. 제출하면 고치거나 회수할 수 없습니다.`;
  if (!(await eaConfirm('eaSubmitDialog'))) return;
  btn.disabled = true; // 두 번 눌러 두 건이 생기지 않게
  api.post('/approval/api/documents', { documentType: c.form, fields: c.fields, line: [c.a1, c.a2], originalDocumentId: eaState.pre?.originalDocumentId || null, fileIds: eaState.files.map(f => f.id) })
    .done(() => {
      err.textContent = '';
      eaState.initial = eaSnapshot();
      toastAndGo(`${EA_FORMS[c.form]}를 제출했습니다. 첫 결재자에게 넘어갔습니다`, pageHref('/approval/list'));
    })
    // 성공하면 버튼을 풀지 않는다: 결재함으로 넘어가기 전에 한 번 더 눌러 같은 문서가 두 번 올라가지 않게. 실패했을 때만 다시 누를 수 있게 푼다
    .fail(xhr => { err.textContent = apiMessage(xhr); btn.disabled = false; });
});

// ---------- 첨부 ----------
// 고르는 즉시 서버에 올려(POST /approval/api/files) 번호를 받아 두고, 제출할 때 번호(fileIds)만 보낸다
const EA_FILE_MAX = 5; // 서버 ApprovalService.FILE_MAX와 같게
eaState.files = [];

// 올린 파일 목록을 그린다 (빼기 버튼 포함)
function eaRenderFiles() {
  eaEl('eaFiles').disabled = eaState.uploading || eaState.files.length >= EA_FILE_MAX;
  eaEl('eaFileList').replaceChildren(...eaState.files.map(f => {
    const del = eaButton('빼기', 'btn', false);
    del.addEventListener('click', () => { eaState.files = eaState.files.filter(x => x.id !== f.id); eaRenderFiles(); });
    return eaNode('li', '', `${f.name} (${Math.ceil(f.size / 1024)}KB) `, del);
  }));
}

// 파일을 고르면 바로 서버에 올린다
function eaInitFiles() {
  eaEl('eaFiles').addEventListener('change', e => {
    const picked = [...e.target.files];
    e.target.value = ''; // 같은 파일을 다시 고를 수 있게
    eaEl('eaFileError').textContent = '';
    if (!picked.length) return;
    if (eaState.files.length + picked.length > EA_FILE_MAX) {
      eaEl('eaFileError').textContent = `첨부파일은 ${EA_FILE_MAX}개까지입니다`;
      return;
    }
    const form = new FormData();
    picked.forEach(file => form.append('files', file));
    eaState.uploading = true;
    eaRenderFiles();
    $.ajax({ url: pageHref('/approval/api/files'), method: 'POST', data: form, processData: false, contentType: false, dataType: 'json' })
      .done(res => { eaState.files.push(...res.data); })
      .fail(xhr => { eaEl('eaFileError').textContent = apiMessage(xhr, '파일을 올리지 못했습니다'); })
      .always(() => { eaState.uploading = false; eaRenderFiles(); });
  });
}

// ---------- 결재함 ----------
function openApprovalBoxView() {
  eaState.open = null;
  eaState.boxFirst = true; // 처음 열 때만 보여 줄 탭을 고른다
  loadApprovalBox();
}

function loadApprovalBox() {
  api.get('/approval/api/box')
    .done(box => {
      eaState.box = box;
      // 처음 열 때 내 순번 대기가 0건이면 문서가 있는 첫 탭(진행 → 반려 → 완료)을 연다.
      // 결재할 일이 거의 없는 직원이 빈 목록부터 보지 않게. 승인·반려 뒤 다시 받을 때는 보던 탭 그대로 둔다
      if (eaState.boxFirst) {
        eaState.boxFirst = false;
        if (!(box.WAIT || []).length) eaState.tab = ['PROGRESS', 'REJECTED', 'DONE'].find(t => (box[t] || []).length) || 'WAIT';
      }
      renderApprovalBox();
    })
    .fail(xhr => showToast(apiMessage(xhr, '결재함을 불러오지 못했습니다')));
}

function renderApprovalBox() {
  const buckets = eaState.box || Object.fromEntries(Object.keys(EA_TABS).map(t => [t, []]));
  Object.keys(EA_TABS).forEach(t => { eaEl(`eaCnt${t}`).textContent = `${(buckets[t] || []).length}건`; });
  document.querySelectorAll('#eaTabs .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.tab === eaState.tab));
  IEUM.refreshBadges(); // 메뉴의 결재함 숫자 (common.js /badges)

  eaEl('eaHead').replaceChildren(eaNode('tr', '', ...['문서', '기안자', '결재선', '상태', '기안일'].map(t => eaNode('th', '', t))));
  const cols = 5;
  const rows = [];
  (buckets[eaState.tab] || []).forEach(d => {
    const key = `${d.documentType}:${d.approvalDocumentId}`;
    const isOpen = eaState.open === key;
    const tr = eaNode('tr', 'row-link' + (isOpen ? ' is-open' : ''),
      eaCell('문서', eaNode('span', '', d.title)), eaCell('기안자', eaNode('span', '', d.employeeName), ...(d.departmentName ? [eaNode('small', 'cell-sub', d.departmentName)] : [])),
      eaCell('결재선', eaNode('span', '', eaLineText(d))), eaCell('상태', eaNode('span', `status status-${EA_STATUS_CLASS[d.statusCode]}`, EA_STATUS[d.statusCode]), ...(d.redrafted ? [eaNode('small', 'cell-sub', '재기안 완료')] : [])),
      eaCell('기안일', eaNode('span', '', whenFull(d.submittedAt))));
    tr.addEventListener('click', () => { eaState.open = isOpen ? null : key; renderApprovalBox(); });
    rows.push(tr);
    if (isOpen) {
      const td = eaNode('td', '', eaNode('div', 'slide open', eaNode('div', 'slide-inner', eaDocDetail(d))));
      td.colSpan = cols;
      rows.push(eaNode('tr', 'row-detail', td));
    }
  });
  if (!rows.length) {
    const td = eaNode('td', '', { WAIT: '내 순번에 온 문서가 없습니다', PROGRESS: '진행 중인 문서가 없습니다', DONE: '완료된 문서가 없습니다', REJECTED: '반려된 문서가 없습니다' }[eaState.tab]);
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

// 문서 내용 줄: 양식마다 다르다
function eaDocRows(d) {
  const f = d.fields;
  if (d.documentType === 'DOC_LEAVE') return [['구분', EA_LEAVE_TYPE[f.leaveType]], ['기간', `${eaSpan(f)} (${d.requestedDays}일)`], ['사유', f.reason || '-']];
  return [['장비', `${d.assetTypeName} ${f.quantity}대`], ['수령 지점', d.branchName], ['사유', f.purpose],
    ...(d.ticketId ? [['지급 티켓', `${d.ticketId} (내 티켓에서 처리 상태를 봅니다)`]] : [])];
}

function eaDocDetail(d) {
  const info = eaNode('dl', 'detail');
  [['문서', d.title], ['기안자', `${d.employeeName}${d.departmentName ? ` (${d.departmentName})` : ''}`], ['기안일', whenFull(d.submittedAt)], ...eaDocRows(d), ['결재선', eaLineText(d)], ['상태', `${EA_STATUS[d.statusCode]}${d.redrafted ? ' · 재기안 완료' : ''}`]]
    .forEach(([k, v]) => info.append(eaNode('dt', '', k), eaNode('dd', '', v)));
      // 첨부: 이름을 누르면 내려받는다 (결재함에 보이는 문서만 서버가 내준다)
  if (d.files && d.files.length) {
    info.append(eaNode('dt', '', '첨부'), eaNode('dd', '', ...d.files.map(f => {
      const a = eaNode('a', '', f.name);
      a.href = pageHref(f.url);
      return eaNode('div', '', a);
    })));
  }
  // 결재 이력: 누가 · [제출·승인·반려] 표시, 의견은 아랫줄, 시각은 흐리게 (오래된 것부터)
  const log = eaNode('ol', 'ea-timeline', ...(d.history.length
    ? d.history.map(h => eaNode('li', `ea-tl-${EA_ACTION_CLASS[h.actionCode] || 'submit'}`,
        eaNode('div', 'ea-tl-head', eaNode('strong', '', h.employeeName), eaNode('span', `status status-${EA_ACTION_STATUS[h.actionCode] || 'waiting'}`, EA_ACTION[h.actionCode] || h.actionCode)),
        ...(h.opinion ? [eaNode('p', 'ea-tl-opinion', h.opinion)] : []),
        eaNode('time', 'ea-tl-time', whenFull(h.createdAt))))
    : [eaNode('li', 'ea-tl-empty', '아직 처리된 단계가 없습니다')]));
  const side = eaNode('section', 'info-card ea-history', eaNode('header', 'info-card-head', eaNode('h2', '', '결재 이력')), log);

  if (d.canDecide) {
    const box = eaNode('textarea', 'inq-text');
    box.maxLength = d.documentType === 'DOC_LEAVE' ? EA_LEAVE_COMMENT_MAX : EA_REASON_MAX;
    box.placeholder = '의견 (반려할 때는 사유가 꼭 필요합니다)';
    box.setAttribute('aria-label', '결재 의견');
    const ok = eaButton('승인', 'btn btn-primary', true);
    const no = eaButton('반려', 'btn', true);
    ok.addEventListener('click', async () => {
      // 승인은 되돌릴 수 없어 한 번 묻는다. 마지막 결재자면 최종 승인 때 일어나는 일까지 알린다
      const last = d.steps.filter(p => p.statusCode === 'WAITING').length <= 1;
      const what = d.documentType === 'DOC_LEAVE'
        ? `${d.employeeName}님의 연차가 ${d.requestedDays}일 차감되고 일정에 휴가가 들어갑니다.`
        : '지급 티켓이 만들어져 IT 담당자에게 넘어갑니다.';
      eaEl('eaDecideTitle').textContent = last ? '최종 승인할까요?' : '승인할까요?';
      eaEl('eaDecideText').textContent = last ? `${what} 승인하면 되돌릴 수 없습니다.` : '다음 결재자에게 넘어갑니다. 승인하면 되돌릴 수 없습니다.';
      if (await eaConfirm('eaDecideDialog')) eaDecide(d, true, box.value.trim(), [ok, no]);
    });
    no.addEventListener('click', () => eaDecide(d, false, box.value.trim(), [ok, no]));
    side.append(eaNode('div', 'inq-body', box, eaNode('div', 'inq-actions', ok, no)));
  } else if (d.canRedraft) {
    const again = eaButton('재기안', 'btn btn-primary', true);
    again.addEventListener('click', () => eaStartWrite(d.documentType, { fields: d.fields, line: d.steps.map(p => p.approverEmployeeId), originalDocumentId: d.approvalDocumentId }));
    side.append(eaNode('div', 'inq-body', ...(d.rejectOpinion ? [eaNode('p', 'notice-warn', `반려 사유: ${d.rejectOpinion}`)] : []),
      eaNode('p', 'inq-hint', '내용과 결재선을 채운 새 기안 화면이 열립니다. 고친 뒤 다시 제출하세요.'), eaNode('div', 'inq-actions', again)));
  }
  return eaNode('div', 'inq-grid', eaNode('section', 'info-card', eaNode('header', 'info-card-head', eaNode('h2', '', '문서 내용')), info), side);
}

// ---------- 결재 처리 ----------
function eaDecideText(r, approve) {
  const what = `${r.employeeName}님의 ${r.documentType === 'DOC_LEAVE' ? '휴가를' : '장비 지급 신청을'}`;
  if (!approve) return `${what} 반려했습니다`;
  if (r.statusCode === 'APPROVED') return `${what} 최종 승인했습니다${r.ticketId ? '. 지급 티켓을 만들었습니다' : ''}`;
  return `${what} 승인하고 다음 결재자에게 넘겼습니다`;
}

function eaDecide(d, approve, opinion, btns) {
  if (!approve && !opinion) return showToast('반려 사유를 적어 주세요');
  btns.forEach(b => { b.disabled = true; });
  api.post(`/approval/api/documents/${encodeURIComponent(d.approvalDocumentId)}/${approve ? 'approve' : 'reject'}`, { opinion })
    .done(r => {
      showToast(eaDecideText(r, approve));
      eaState.open = null;
      loadApprovalBox();
    })
    .fail(xhr => {
      showToast(apiMessage(xhr));
      if (xhr.status === 404 || xhr.status === 409) loadApprovalBox(); // 이미 처리된 문서: 새로 받아 그린다
    })
    .always(() => btns.forEach(b => { b.disabled = false; }));
}
