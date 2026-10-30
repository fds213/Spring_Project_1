// 근무 > 근태·연차 (조각 views/attendance/my.html): 로그인한 본인의 출퇴근 기록과 연차 현황만 보여 준다 (요구분석서 v1.5 3.1, 3.9, 3.10).
// 다른 직원의 근태, 근태·연차 관리(인사)는 이 화면에 없다.
//
// 출퇴근 기록: 지문 단말기가 보낸 기록을 단말기 사용자 번호로 찾는다. 로그인한 사람과 단말기 사용자 번호의 연결은 요구분석서 3.10 의
// "업체·단말기 사용자 번호·사번 계정의 연결"이다. 시안에서는 본인이 화면에서 번호를 연결한다 (실제로는 인사 담당자·서버가 관리한다).
// 연차: 15일 + 내림(총 완료 경력 연수 ÷ 3). 계산과 신청 규칙(겹침·잔여·주말·휴일)은 서버가 정하고, 화면은 받은 값을 보여 준다.
// 연차를 쓰는 것(휴가)은 전자결재의 휴가 신청서로 신청한다(요구분석서 3장 범위). 최종 승인 때 연차가 차감되고 여기 내역에 들어온다.
// 이 화면은 기록과 연차 현황·내역만 보여 주고, 신청·회수는 전자결재(기안 작성·결재함)에서 한다.
//
// 서버 API (시안은 demo/attendance.js가 답한다):
//   GET    /attendance/api/records?month=YYYY-MM   한 달 출퇴근 기록
//          → { month, deviceId, today: { date, punch }, thisMonth: { late, early }, days: [{ date, weekday, holiday, punch, leave }] }
//            punch = { in, out, inStatus, outStatus } | null (상태 NORMAL|LATE|EARLY_LEAVE, 퇴근 전이면 out·outStatus 는 ''),
//            leave = 그날의 승인된 연차 FULL|AM|PM | null, thisMonth = 오늘이 속한 달의 지각·조퇴 횟수, deviceId 는 연결 없으면 ''
//   POST   /attendance/api/device-link  { deviceId: '0001' } → { deviceId }  숫자 4자리가 아니면 400, 같은 업체의 다른 직원이 쓰면 409
//   POST   /attendance/api/device-link/delete → { deviceId }  (계정에 원래 붙은 번호가 있으면 그 번호, 없으면 '')
//   GET    /attendance/api/leaves      → { summary, items }
//          summary = { start, end (ISO. end 는 다음 부여일), years, prev, career, grant, usedDays, pendingDays, left, available } | null (직원 정보 없음)
//          items = 이번 연차 기간의 휴가 신청서(회수한 것 제외), 시작일 늦은 순
//          한 건 = { id, kind, from, to, days, reason, at, status: REQUESTED|APPROVED|REJECTED, step, line: [{ empNo, name }], comment(반려 사유) }
// 쓰기는 이용 기간이 끝난 업체(읽기 전용)면 403이다.

const ATT_STATUS = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴' };
const ATT_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 연차 구분·신청 상태 이름
const LEAVE_LABEL = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };
const LEAVE_STATUS = { REQUESTED: '승인 대기', APPROVED: '승인', REJECTED: '반려', CANCELED: '회수' };
const LEAVE_STATUS_CLASS = { REQUESTED: 'waiting', APPROVED: 'paid', REJECTED: 'failed', CANCELED: 'expired' };
const dayText = n => `${Number.isInteger(n) ? n : n.toFixed(1)}일`;

const attEl = id => document.getElementById(id);
const attNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const attState = { month: null }; // month = { y, m } 보고 있는 달

// 화면을 열 때 (시안은 proto/app.js showView, JSP는 페이지가 열릴 때)
function openAttendanceView() {
  const now = new Date();
  attState.month = { y: now.getFullYear(), m: now.getMonth() };
  attRefreshHeader();
  renderAttendanceView();
}

// 헤더의 오늘 출퇴근 (공통 틀이 그린다. 이 화면을 열 때와 단말기 연결을 바꿨을 때 새로 그리게 한다)
function attRefreshHeader() {
  if (typeof renderAttendance === 'function') renderAttendance();
}

// 화면 전체를 서버에서 다시 받아 그린다 (다른 탭에서 바뀌었을 때도 이것을 부른다)
function renderAttendanceView() {
  if (!attState.month) return openAttendanceView();
  loadAttRecords();
  loadAttLeaves();
}

const attMonthParam = () => `${attState.month.y}-${pad2(attState.month.m + 1)}`;

function loadAttRecords() {
  api.get('/attendance/api/records', { month: attMonthParam() })
    .done(r => { if (r.month === attMonthParam()) renderAttRecords(r); }) // 달을 빨리 넘기면 늦게 온 앞 달 응답은 버린다
    .fail(xhr => showToast(apiMessage(xhr, '출퇴근 기록을 불러오지 못했습니다')));
}

function loadAttLeaves() {
  api.get('/attendance/api/leaves')
    .done(renderLeaveInfo)
    .fail(xhr => showToast(apiMessage(xhr, '연차 현황을 불러오지 못했습니다')));
}

function renderAttRecords(r) {
  const dev = r.deviceId;
  // 카드: 오늘 / 이번 달 지각·조퇴 (카드의 "이번 달"은 오늘이 속한 달이다)
  const t = r.today.punch;
  attEl('attToday').textContent = !dev ? '단말기 미연결' : !t ? '기록 없음' : t.out ? `${t.in} ~ ${t.out}` : `${t.in} 출근`;
  attEl('attLate').textContent = dev ? `${r.thisMonth.late}회` : '-';
  attEl('attEarly').textContent = dev ? `${r.thisMonth.early}회` : '-';

  renderDeviceLink(dev);

  // 월별 기록
  attEl('attMonthLabel').textContent = `${attState.month.y}년 ${attState.month.m + 1}월`;
  const rows = r.days.map(day => {
    const wk = day.weekday;
    const weekend = wk === 0 || wk === 6;
    const rest = weekend || !!day.holiday;
    const rec = day.punch;
    const note = day.holiday || (weekend ? '주말' : '') || (day.leave ? LEAVE_LABEL[day.leave] : '');
    const status = rec ? [rec.inStatus && rec.inStatus !== 'NORMAL' ? ATT_STATUS[rec.inStatus] : '', rec.outStatus && rec.outStatus !== 'NORMAL' ? ATT_STATUS[rec.outStatus] : ''].filter(Boolean).join(' · ') || '정상' : '';
    const tr = attNode('tr', (rest ? 'att-rest' : '') + (day.date === r.today.date ? ' att-today' : ''),
      attNode('td', '', `${Number(day.date.slice(8))}일 (${ATT_WEEKDAYS[wk]})`),
      attNode('td', '', rec ? rec.in : '-'),
      attNode('td', '', rec && rec.out ? rec.out : '-'),
      attNode('td', '', status ? attNode('span', `status ${status === '정상' ? 'status-active' : 'status-waiting'}`, status) : '-'),
      attNode('td', 'att-note', note));
    tr.querySelectorAll('td').forEach((td, i) => { td.dataset.label = ['날짜', '출근', '퇴근', '상태', '비고'][i]; });
    return tr;
  });
  attEl('attList').replaceChildren(...rows);
}

// 단말기 연결 상자: 연결돼 있으면 해제 버튼, 없으면 번호 입력
function renderDeviceLink(dev) {
  const box = attEl('attLink');
  box.replaceChildren();
  if (dev) {
    const unlink = attNode('button', 'btn', '연결 해제');
    unlink.type = 'button';
    unlink.addEventListener('click', () => {
      unlink.disabled = true;
      api.post('/attendance/api/device-link/delete')
        .done(() => { loadAttRecords(); attRefreshHeader(); })
        .fail(xhr => { showToast(apiMessage(xhr)); unlink.disabled = false; });
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
    if (go.disabled) return;
    const v = input.value.trim();
    // 서버도 같은 검사를 다시 한다 (다른 직원이 쓰는 번호인지는 서버만 안다)
    if (!/^\d{4}$/.test(v)) { error.textContent = '단말기 사용자 번호는 숫자 4자리입니다 (시뮬레이터에서 확인)'; return input.focus(); }
    go.disabled = true;
    api.post('/attendance/api/device-link', { deviceId: v })
      .done(() => { loadAttRecords(); attRefreshHeader(); })
      .fail(xhr => { error.textContent = apiMessage(xhr); go.disabled = false; input.focus(); });
  };
  go.addEventListener('click', connect);
  input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); connect(); } });
  input.addEventListener('input', () => { error.textContent = ''; });
  box.append(
    attNode('p', 'att-link-text', '연결된 단말기 사용자 번호가 없어 출퇴근 기록이 보이지 않습니다. 지문 단말기에 등록된 내 사용자 번호를 연결해 주세요 (시안: 시뮬레이터의 0001~0004).'),
    attNode('div', 'input-action att-link-form', input, go), error);
}

// 연차 현황과 이번 연차 기간의 신청 내역
function renderLeaveInfo(r) {
  const sum = r.summary;
  const dl = attEl('leaveInfo');
  const hist = attEl('leaveHistory');
  attEl('attLeft').textContent = sum ? dayText(sum.left) : '-';
  if (!sum) {
    dl.replaceChildren(attNode('dt', '', '안내'), attNode('dd', '', '직원 정보를 찾을 수 없어 연차를 계산할 수 없습니다'));
    hist.replaceChildren();
    return;
  }
  const row = (k, v) => [attNode('dt', '', k), attNode('dd', '', v)];
  dl.replaceChildren(
    ...row('이번 연차 기간', `${ymd(sum.start)} ~ ${ymd(new Date(new Date(sum.end).getTime() - DAY_MS))}`),
    ...row('부여', `${dayText(sum.grant)} (15일 + 총 경력 ${sum.career}년 ÷ 3의 몫 ${Math.floor(sum.career / 3)}일)`),
    ...row('총 경력', `이전 경력 ${sum.prev}년 + 현재 업체 근속 ${sum.years}년`),
    ...row('사용 (승인됨)', dayText(sum.usedDays)),
    ...row('승인 대기', dayText(sum.pendingDays)),
    ...row('남은 연차', dayText(sum.left)),
    ...row('다음 부여', `${ymd(sum.end)} 입사기념일 (남은 연차는 이월되지 않습니다)`));

  // 신청 내역: 상태와, 대기 중이면 지금 결재할 사람, 반려면 사유 (회수는 결재함에서)
  hist.replaceChildren(...(r.items.length ? r.items.map(l => {
    const range = `${l.from}${l.to !== l.from ? ` ~ ${l.to}` : ''} · ${LEAVE_LABEL[l.kind]} · ${dayText(l.days)}`;
    const now = l.line[l.step];
    const sub = l.status === 'REQUESTED' ? `${l.step + 1}단계 ${now ? now.name : '-'} 결재 대기`
      : l.status === 'REJECTED' ? `반려${l.comment ? `: ${l.comment}` : ''}` : '';
    const li = attNode('li', '', attNode('span', 'leave-line', attNode('span', '', range), ...(sub ? [attNode('small', 'cell-sub', sub)] : [])),
      attNode('span', `status status-${LEAVE_STATUS_CLASS[l.status]}`, LEAVE_STATUS[l.status]));
    return li;
  }) : [attNode('li', 'muted', '이번 연차 기간에 신청한 내역이 없습니다')]));
}

const attMoveMonth = step => {
  const { y, m } = attState.month;
  const d = new Date(y, m + step, 1);
  attState.month = { y: d.getFullYear(), m: d.getMonth() };
  loadAttRecords();
};
attEl('attPrev').addEventListener('click', () => attMoveMonth(-1));
attEl('attNext').addEventListener('click', () => attMoveMonth(1));
