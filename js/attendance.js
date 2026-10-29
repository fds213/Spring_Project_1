// 근무 > 근태·연차: 로그인한 본인의 출퇴근 기록과 연차 현황만 보여 준다 (요구분석서 v1.5 3.1, 3.9, 3.10).
// 다른 직원의 근태, 근태·연차 관리(인사), 승인은 이 화면에 없다.
//
// 출퇴근 기록: 지문 단말기 시뮬레이터(simulator.js)가 localStorage 의 PUNCH_KEY 에 날짜·단말기 사용자 번호별로 저장한다.
// 로그인한 사람과 단말기 사용자 번호의 연결은 요구분석서 3.10 의 "업체·단말기 사용자 번호·사번 계정의 연결"이다.
// 시안에서는 본인이 화면에서 번호를 연결한다 (실제로는 인사 담당자·서버가 관리하고, 업체 간 같은 번호를 혼동하지 않아야 한다).
//
// 연차: 15일 + 내림(총 완료 경력 연수 ÷ 3). 총 경력 = 이전 직장 경력 + 현재 업체 입사일부터의 근속.
// 이전 경력은 직급별 시연용 임의 데이터다 (실제 경력을 직급으로 판정하는 기능이 아니다).
// 연차를 쓰는 것(휴가)은 결재선(최대 2단계, 순서대로)의 승인을 거친다. 마지막 결재자가 승인해야 연차가 차감되고 일정에
// 반영된다. 승인·반려와 다른 직원의 근태 확인은 근무 > 승인 화면(approval.js)에서 한다.
// 전자결재(휴가 양식)가 아직 없어서 이 화면의 신청 폼이 그 역할을 대신한다.
// 이 파일은 hr.js 뒤, common.js 앞에 로드된다 (staffOf, loadStore, saveStore, pad2, showToast 등은 앞 파일에서 온다).

const PUNCH_KEY = 'ieum-demo-punches';
const LINK_KEY = 'ieum-demo-device-links';
const LEAVE_KEY = 'ieum-demo-leaves-v1'; // { 업체코드: [{ empNo, kind: 'FULL'|'AM'|'PM', from, to, days }] }
const HOLIDAY_KEY = 'ieum-demo-holidays-v1';
const WORK_START_HM = '09:00';
const WORK_END_HM = '18:00';
const ATT_STATUS = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴' };
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// 시연용 직급별 이전 경력 (요구분석서 3.9). 목록에 없는 직급(관리자·팀장 등)은 0년으로 본다
const PREV_CAREER = { 사원: 0, 주임: 2, 대리: 4, 과장: 7, 차장: 10, 부장: 13 };

// 시연용 기본 휴일. 실제로는 인사 담당자가 휴일 관리에서 등록한다 (이 시안에는 그 화면이 없어서 업체별 저장값이 있으면 그것을 쓴다)
const DEFAULT_HOLIDAYS = {
  '2026-01-01': '신정', '2026-02-16': '설날 연휴', '2026-02-17': '설날', '2026-02-18': '설날 연휴',
  '2026-03-01': '삼일절', '2026-03-02': '대체공휴일', '2026-05-05': '어린이날', '2026-05-24': '부처님오신날',
  '2026-05-25': '대체공휴일', '2026-06-03': '지방선거일', '2026-06-06': '현충일', '2026-08-15': '광복절',
  '2026-08-17': '대체공휴일', '2026-09-24': '추석 연휴', '2026-09-25': '추석', '2026-09-26': '추석 연휴',
  '2026-10-03': '개천절', '2026-10-05': '대체공휴일', '2026-10-09': '한글날', '2026-12-25': '성탄절',
};

// ---------- 날짜 도구 (로컬 날짜 기준 'YYYY-MM-DD') ----------
const dayKeyOf = d => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
const dayFromKey = k => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };
const holidayName = k => (loadStore(HOLIDAY_KEY, {})[session.company] || DEFAULT_HOLIDAYS)[k] || '';

// ---------- 로그인한 본인 ----------
const myStaffRecord = () => staffOf(session.company).find(e => e.empNo === session.id) || null;

// 단말기 사용자 번호: 본인이 연결한 값, 없으면 계정에 붙어 있는 값
const deviceIdFor = (code, empNo) => loadStore(LINK_KEY, {})[`${code}:${empNo}`] || '';
function deviceIdOf() {
  return deviceIdFor(session.company, session.id) || session.deviceUserId || '';
}

function setDeviceLink(value) {
  const all = loadStore(LINK_KEY, {});
  const key = `${session.company}:${session.id}`;
  if (value) all[key] = value; else delete all[key];
  saveStore(LINK_KEY, all);
}

// 같은 업체 안에서 한 번호는 한 사람만 쓴다 (다른 사람의 기록을 내 것으로 보면 안 된다)
const deviceIdTaken = id => Object.entries(loadStore(LINK_KEY, {}))
  .some(([k, v]) => v === id && k !== `${session.company}:${session.id}` && k.startsWith(`${session.company}:`));

const punchOf = (deviceId, key) => (deviceId ? (loadStore(PUNCH_KEY, {})[key] || {})[deviceId] || null : null);

// ---------- 연차 ----------
// 현재 연차 주기: 입사일(1주기 시작) 이후 매 기념일에 새로 부여한다. 주기 n 의 총 경력 = 이전 경력 + n년
function leaveCycle(rec) {
  const hire = new Date(rec.hireAt || rec.createdAt || rec.approvedAt || rec.at || Date.now());
  hire.setHours(0, 0, 0, 0);
  const now = new Date();
  const at = n => new Date(hire.getFullYear() + n, hire.getMonth(), hire.getDate());
  let n = Math.max(0, now.getFullYear() - hire.getFullYear());
  while (n > 0 && at(n) > now) n--;
  const prev = PREV_CAREER[rec.rank] ?? 0;
  const career = prev + n;
  return { hire, start: at(n), end: at(n + 1), years: n, prev, career, grant: 15 + Math.floor(career / 3) };
}

// 신청 상태: 승인 대기 → 다음 결재자 → 승인 / 반려. 신청자는 승인 전에 회수할 수 있다.
// 승인 기능이 생기기 전에 저장된 신청은 status 가 없어서 승인된 것으로 본다.
const LEAVE_STATUS = { REQUESTED: '승인 대기', APPROVED: '승인', REJECTED: '반려', CANCELED: '회수' };
const LEAVE_STATUS_CLASS = { REQUESTED: 'waiting', APPROVED: 'paid', REJECTED: 'failed', CANCELED: 'expired' };
const leaveStatus = l => l.status || 'APPROVED';
const allLeaves = () => loadStore(LEAVE_KEY, {})[session.company] || [];
const myLeaves = () => allLeaves().filter(l => l.empNo === session.id);
const isApprovedLeave = l => leaveStatus(l) === 'APPROVED';
const nameOfStaff = empNo => (staffOf(session.company).find(e => e.empNo === empNo) || {}).name || empNo;

// 결재자가 될 수 있는 사람: 부서장(팀장), 인사 담당, 업체 관리자. 본인은 뺀다 (자기 신청을 자기가 승인할 수 없다)
const canApproveRec = e => e.status === 'ACTIVE' && (isHeadOf(e) || (e.roles || []).some(r => r === 'HR' || r === 'ADMIN'));
const approverOptions = () => employeesOf(session.company).filter(e => e.empNo !== session.id && canApproveRec(e));

// 이번 주기에 쓴 연차: 이월하지 않으므로 이전 주기 사용분은 세지 않는다
// 한 직원의 연차 현황. 이번 주기에 시작하는 신청만 센다 (이월 없음). "사용"은 승인된 것만이고, 승인 대기는 따로 센다.
// 대기 중인 것도 잔여를 넘겨 신청하지 못하게 available 에서는 뺀다.
function leaveSummaryFor(code, empNo) {
  const rec = staffOf(code).find(e => e.empNo === empNo);
  if (!rec) return null;
  const cyc = leaveCycle(rec);
  const list = (loadStore(LEAVE_KEY, {})[code] || []).filter(l => {
    const f = dayFromKey(l.from);
    return l.empNo === empNo && f >= cyc.start && f < cyc.end;
  });
  const sumOf = st => list.filter(l => leaveStatus(l) === st).reduce((s, l) => s + l.days, 0);
  const usedDays = sumOf('APPROVED');
  const pendingDays = sumOf('REQUESTED');
  return { cyc, list, usedDays, pendingDays, left: cyc.grant - usedDays, available: cyc.grant - usedDays - pendingDays };
}
const leaveSummary = () => leaveSummaryFor(session.company, session.id);

// ---------- 화면 ----------
const attEl = id => document.getElementById(id);
const attNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const dayText = n => `${Number.isInteger(n) ? n : n.toFixed(1)}일`;
let attMonth = null; // { y, m } 보고 있는 달

function openAttendanceView() {
  const now = new Date();
  attMonth = { y: now.getFullYear(), m: now.getMonth() };
  renderAttendance(); // 헤더의 오늘 출퇴근도 이 화면을 열 때 새로 읽는다 (common.js)
  renderAttendanceView();
}

function renderAttendanceView() {
  if (!attMonth) openAttendanceView();
  const dev = deviceIdOf();
  const today = new Date();
  const sum = leaveSummary();

  // 카드: 오늘 / 이번 달 지각·조퇴 / 남은 연차 (카드의 "이번 달"은 오늘이 속한 달이다)
  const mark = s => (s && s !== 'NORMAL' ? `(${ATT_STATUS[s]})` : '');
  const t = punchOf(dev, dayKeyOf(today));
  attEl('attToday').textContent = !dev ? '단말기 미연결' : !t ? '기록 없음' : t.out ? `${t.in} ~ ${t.out}` : `${t.in} 출근`;
  let late = 0, early = 0;
  const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate();
  for (let d = 1; d <= lastDay && dev; d++) {
    const r = punchOf(dev, dayKeyOf(new Date(today.getFullYear(), today.getMonth(), d)));
    if (r && r.inStatus === 'LATE') late++;
    if (r && r.outStatus === 'EARLY_LEAVE') early++;
  }
  attEl('attLate').textContent = dev ? `${late}회` : '-';
  attEl('attEarly').textContent = dev ? `${early}회` : '-';
  attEl('attLeft').textContent = sum ? dayText(sum.left) : '-';

  renderDeviceLink(dev);

  // 월별 기록
  attEl('attMonthLabel').textContent = `${attMonth.y}년 ${attMonth.m + 1}월`;
  const days = new Date(attMonth.y, attMonth.m + 1, 0).getDate();
  const rows = [];
  for (let d = 1; d <= days; d++) {
    const date = new Date(attMonth.y, attMonth.m, d);
    const key = dayKeyOf(date);
    const wk = date.getDay();
    const holiday = holidayName(key);
    const rest = wk === 0 || wk === 6 || !!holiday;
    const rec = punchOf(dev, key);
    const leave = myLeaves().find(l => isApprovedLeave(l) && l.from <= key && key <= l.to);
    const note = holiday || (wk === 0 || wk === 6 ? '주말' : '') || (leave ? (leave.kind === 'FULL' ? '연차' : leave.kind === 'AM' ? '오전 반차' : '오후 반차') : '');
    const status = rec ? [rec.inStatus && rec.inStatus !== 'NORMAL' ? ATT_STATUS[rec.inStatus] : '', rec.outStatus && rec.outStatus !== 'NORMAL' ? ATT_STATUS[rec.outStatus] : ''].filter(Boolean).join(' · ') || '정상' : '';
    const tr = attNode('tr', (rest ? 'att-rest' : '') + (key === dayKeyOf(today) ? ' att-today' : ''),
      attNode('td', '', `${d}일 (${WEEKDAYS[wk]})`),
      attNode('td', '', rec ? rec.in : '-'),
      attNode('td', '', rec && rec.out ? rec.out : '-'),
      attNode('td', '', status ? attNode('span', `status ${status === '정상' ? 'status-active' : 'status-waiting'}`, status) : '-'),
      attNode('td', 'att-note', note));
    tr.querySelectorAll('td').forEach((td, i) => { td.dataset.label = ['날짜', '출근', '퇴근', '상태', '비고'][i]; });
    rows.push(tr);
  }
  attEl('attList').replaceChildren(...rows);

  fillApprovers();
  renderLeaveInfo(sum);
}

function renderDeviceLink(dev) {
  const box = attEl('attLink');
  box.replaceChildren();
  if (dev) {
    const unlink = attNode('button', 'btn', '연결 해제');
    unlink.type = 'button';
    unlink.addEventListener('click', () => {
      if (readOnly) return showToast(readOnlyMessage());
      setDeviceLink('');
      renderAttendanceView();
      renderAttendance();
    });
    box.append(attNode('p', 'att-link-text', `단말기 사용자 번호 ${dev}번과 연결되어 있습니다. 이 번호로 찍힌 출퇴근만 보입니다.`), unlink);
    return;
  }
  const input = attNode('input', 'input');
  input.id = 'attDeviceId';
  input.inputMode = 'numeric';
  input.maxLength = 4;
  input.placeholder = '예: 0001';
  input.setAttribute('aria-label', '단말기 사용자 번호');
  const error = attNode('p', 'form-error');
  error.setAttribute('role', 'alert');
  const go = attNode('button', 'btn btn-primary', '연결');
  go.type = 'button';
  const connect = () => {
    if (readOnly) return showToast(readOnlyMessage());
    const v = input.value.trim();
    if (!/^\d{4}$/.test(v)) { error.textContent = '단말기 사용자 번호는 숫자 4자리입니다 (시뮬레이터에서 확인)'; return input.focus(); }
    if (deviceIdTaken(v)) { error.textContent = '이미 다른 직원이 연결한 번호입니다'; return input.focus(); }
    setDeviceLink(v);
    renderAttendanceView();
    renderAttendance();
  };
  go.addEventListener('click', connect);
  input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); connect(); } });
  input.addEventListener('input', () => { error.textContent = ''; });
  box.append(
    attNode('p', 'att-link-text', '연결된 단말기 사용자 번호가 없어 출퇴근 기록이 보이지 않습니다. 지문 단말기에 등록된 내 사용자 번호를 연결해 주세요 (시안: 시뮬레이터의 0001~0004).'),
    attNode('div', 'input-action att-link-form', input, go), error);
}

function renderLeaveInfo(sum) {
  const dl = attEl('leaveInfo');
  const hist = attEl('leaveHistory');
  if (!sum) {
    dl.replaceChildren(attNode('dt', '', '안내'), attNode('dd', '', '직원 정보를 찾을 수 없어 연차를 계산할 수 없습니다'));
    hist.replaceChildren();
    return;
  }
  const { cyc } = sum;
  const row = (k, v) => [attNode('dt', '', k), attNode('dd', '', v)];
  dl.replaceChildren(
    ...row('이번 연차 기간', `${ymd(cyc.start)} ~ ${ymd(new Date(cyc.end.getTime() - DAY_MS))}`),
    ...row('부여', `${dayText(cyc.grant)} (15일 + 총 경력 ${cyc.career}년 ÷ 3의 몫 ${Math.floor(cyc.career / 3)}일)`),
    ...row('총 경력', `이전 경력 ${cyc.prev}년 + 현재 업체 근속 ${cyc.years}년`),
    ...row('사용 (승인됨)', dayText(sum.usedDays)),
    ...row('승인 대기', dayText(sum.pendingDays)),
    ...row('남은 연차', dayText(sum.left)),
    ...row('다음 부여', `${ymd(cyc.end)} 입사기념일 (남은 연차는 이월되지 않습니다)`));

  // 신청 내역: 상태와, 대기 중이면 지금 결재할 사람, 반려면 사유. 회수한 것은 보이지 않는다
  const shown = sum.list.filter(l => leaveStatus(l) !== 'CANCELED').sort((x, y) => (x.from < y.from ? 1 : -1));
  hist.replaceChildren(...(shown.length ? shown.map(l => {
    const st = leaveStatus(l);
    const range = `${l.from}${l.to !== l.from ? ` ~ ${l.to}` : ''} · ${LEAVE_LABEL[l.kind]} · ${dayText(l.days)}`;
    const last = (l.history || []).slice(-1)[0];
    const sub = st === 'REQUESTED' ? `${l.step + 1}단계 ${nameOfStaff(l.line[l.step])} 결재 대기`
      : st === 'REJECTED' ? `반려${last && last.comment ? `: ${last.comment}` : ''}` : '';
    const li = attNode('li', '', attNode('span', 'leave-line', attNode('span', '', range), ...(sub ? [attNode('small', 'cell-sub', sub)] : [])),
      attNode('span', `status status-${LEAVE_STATUS_CLASS[st]}`, LEAVE_STATUS[st]));
    if (st === 'REQUESTED') {
      const undo = attNode('button', 'btn', '회수');
      undo.type = 'button';
      undo.addEventListener('click', () => cancelLeave(l.id));
      li.append(undo);
    }
    return li;
  }) : [attNode('li', 'muted', '이번 연차 기간에 신청한 내역이 없습니다')]));
}

// 승인 전의 내 신청을 거둔다. 이미 결재가 시작됐어도 최종 승인·반려 전이면 회수할 수 있다
function cancelLeave(id) {
  if (readOnly) return showToast(readOnlyMessage());
  const all = loadStore(LEAVE_KEY, {});
  const l = (all[session.company] || []).find(x => x.id === id);
  if (!l || l.empNo !== session.id || leaveStatus(l) !== 'REQUESTED') return;
  l.status = 'CANCELED';
  l.history = [...(l.history || []), { step: l.step, by: session.id, name: session.name, action: 'CANCEL', at: new Date().toISOString(), comment: '' }];
  saveStore(LEAVE_KEY, all);
  showToast('신청을 회수했습니다');
  renderAttendanceView();
  if (typeof refreshApprovalBadge === 'function') refreshApprovalBadge();
}

// 결재선 선택지: 결재자가 될 수 있는 사람. 1단계는 꼭, 2단계는 선택
function fillApprovers() {
  const opts = approverOptions();
  const fill = (id, withNone) => {
    const sel = attEl(id);
    const keep = sel.value;
    const options = opts.map(e => { const o = new Option(`${e.name} (${e.dept} · ${e.rank})`, e.empNo); return o; });
    sel.replaceChildren(...(withNone ? [new Option('(없음)', '')] : []), ...options);
    if ([...sel.options].some(o => o.value === keep)) sel.value = keep;
  };
  fill('leaveAppr1', false);
  fill('leaveAppr2', true);
}

attEl('attPrev').addEventListener('click', () => {
  attMonth = attMonth.m === 0 ? { y: attMonth.y - 1, m: 11 } : { y: attMonth.y, m: attMonth.m - 1 };
  renderAttendanceView();
});
attEl('attNext').addEventListener('click', () => {
  attMonth = attMonth.m === 11 ? { y: attMonth.y + 1, m: 0 } : { y: attMonth.y, m: attMonth.m + 1 };
  renderAttendanceView();
});

// ---------- 연차·휴가 신청 ----------
// 신청하면 결재선의 첫 결재자에게 간다 (승인 대기). 결재는 approval.js. 최종 승인 때 연차가 차감되고 일정에 나타난다.
// 요구분석서의 실제 흐름은 전자결재 휴가 양식의 최종 승인 때 서버가 연차 차감·일정 생성을 한 트랜잭션으로 처리하는 것이다.
// 규칙(3.9): 종일 연차는 기간으로, 오전·오후 반차는 하루에 0.5일. 주말과 등록 휴일은 세지 않는다.
// 같은 날짜의 종일 연차·반차, 같은 시간대 반차는 겹칠 수 없다. 남은 연차보다 많이 쓸 수 없다.
const LEAVE_LABEL = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };

// 쉬는 날을 뺀 일하는 날(키 목록)
function workingDaysBetween(fromKey, toKey) {
  const out = [];
  for (let d = dayFromKey(fromKey); dayKeyOf(d) <= toKey; d.setDate(d.getDate() + 1)) {
    if (d.getDay() !== 0 && d.getDay() !== 6 && !holidayName(dayKeyOf(d))) out.push(dayKeyOf(d));
  }
  return out;
}

// 문제가 있으면 사용자에게 보여 줄 문장을, 없으면 저장하고 빈 문자열을 돌려준다
function applyLeave(kind, fromKey, toKey, reason, appr1, appr2) {
  if (readOnly) return readOnlyMessage();
  const sum = leaveSummary();
  const me = myStaffRecord();
  if (!sum || !me) return '직원 정보를 찾을 수 없어 신청할 수 없습니다';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fromKey) || Number.isNaN(dayFromKey(fromKey).getTime())) return '시작일을 입력해 주세요';
  if (kind !== 'FULL') toKey = fromKey; // 반차는 하루만
  if (!/^\d{4}-\d{2}-\d{2}$/.test(toKey) || toKey < fromKey) return '종료일은 시작일 이후여야 합니다';
  if (fromKey < dayKeyOf(new Date())) return '지난 날짜는 신청할 수 없습니다';
  if (dayFromKey(fromKey) >= sum.cyc.end) return `${ymd(sum.cyc.end)}부터는 새 연차 기간입니다. 그때 신청해 주세요`;
  const work = workingDaysBetween(fromKey, toKey);
  if (!work.length) return '선택한 날이 모두 주말이거나 휴일입니다';
  // 결재선: 1단계는 필수, 2단계는 선택이고 1단계와 같은 사람일 수 없다. 서버는 이 사람이 결재자 자격이 있는지 다시 검사한다
  const ok = new Set(approverOptions().map(e => e.empNo));
  if (!ok.size) return '결재할 사람이 없습니다. 같은 업체에 부서장·인사 담당·업체 관리자가 본인 말고 한 명 있어야 합니다';
  if (!appr1 || !ok.has(appr1)) return '1단계 결재자를 골라 주세요';
  if (appr2 && (!ok.has(appr2) || appr2 === appr1)) return '2단계 결재자는 1단계와 다른 사람이어야 합니다';
  for (const l of myLeaves()) {
    if (!['REQUESTED', 'APPROVED'].includes(leaveStatus(l))) continue; // 반려·회수한 신청은 날짜를 막지 않는다
    for (const k of work) {
      if (l.from <= k && k <= l.to && (l.kind === 'FULL' || kind === 'FULL' || l.kind === kind)) {
        return `${k}에 이미 ${LEAVE_LABEL[l.kind]} 신청이 있어 겹칩니다`;
      }
    }
  }
  const days = kind === 'FULL' ? work.length : 0.5;
  if (days > sum.available) return `신청할 수 있는 연차 ${dayText(sum.available)}보다 많습니다 (신청 ${dayText(days)}, 승인 대기 ${dayText(sum.pendingDays)} 포함해서 계산)`;
  const all = loadStore(LEAVE_KEY, {});
  (all[session.company] = all[session.company] || []).push({
    id: `L-${Date.now()}-${Math.floor(Math.random() * 1000)}`, empNo: session.id, name: session.name, dept: me.dept, kind, from: fromKey, to: toKey, days, reason,
    at: new Date().toISOString(), status: 'REQUESTED', step: 0, line: [appr1, ...(appr2 ? [appr2] : [])], history: [],
  });
  saveStore(LEAVE_KEY, all);
  return '';
}

const leaveKind = attEl('leaveKind');
const leaveFrom = attEl('leaveFrom');
const leaveTo = attEl('leaveTo');
const leaveError = attEl('leaveError');
const syncLeaveForm = () => {
  const full = leaveKind.value === 'FULL';
  leaveTo.disabled = !full;
  if (!full || !leaveTo.value || leaveTo.value < leaveFrom.value) leaveTo.value = leaveFrom.value;
  leaveError.textContent = '';
};
leaveKind.addEventListener('change', syncLeaveForm);
leaveFrom.addEventListener('change', syncLeaveForm);
leaveTo.addEventListener('change', () => { leaveError.textContent = ''; });
attEl('leaveForm').addEventListener('submit', e => {
  e.preventDefault();
  const error = applyLeave(leaveKind.value, leaveFrom.value, leaveKind.value === 'FULL' ? leaveTo.value : leaveFrom.value,
    attEl('leaveReason').value.trim().slice(0, 100), attEl('leaveAppr1').value, attEl('leaveAppr2').value);
  leaveError.textContent = error;
  if (error) return;
  attEl('leaveReason').value = '';
  showToast('승인을 요청했습니다. 결재자가 승인하면 연차에 반영됩니다');
  if (typeof refreshApprovalBadge === 'function') refreshApprovalBadge();
  renderAttendanceView();
  if (typeof renderSchedule === 'function') renderSchedule();
});

// 다른 탭의 출퇴근 체크·연결·연차 변경이 이 화면에 바로 보이게 한다 (common.js 의 storage 이벤트가 부른다)
function refreshWorkViews() {
  const key = location.hash.slice(1);
  if (key === 'attendance') renderAttendanceView();
  if (key === 'schedule' && typeof renderSchedule === 'function') renderSchedule();
  if (key === 'work-approval' && typeof renderApproval === 'function') renderApproval();
  if (typeof refreshApprovalBadge === 'function') refreshApprovalBadge();
}
