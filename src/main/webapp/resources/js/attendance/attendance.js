// 근무 > 근태 (views/attendance/my.jsp): 로그인한 본인의 출퇴근 기록만 보여 준다 (요구분석서 3.1, 3.10).
// 다른 직원의 근태, 근태·연차 관리(인사)는 이 화면에 없다. 연차는 연차 화면(leave.js)에 있다.
//
// 출퇴근 기록: 지문 단말기가 보낸 기록을 단말기 사용자 번호로 찾는다. 로그인한 사람과 단말기 사용자 번호의 연결은 요구분석서 3.10 의
// "업체·단말기 사용자 번호·사번 계정의 연결"이다. 이 화면에서는 본인이 번호를 연결한다 (원래는 인사 담당자·서버가 관리할 일이다).
// 승인된 연차가 있는 날은 기록의 비고에 연차 구분을 적는다.

const ATT_STATUS = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴' };
const ATT_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 기록 비고에 쓰는 연차 구분 이름
const ATT_LEAVE_NOTE = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };

const attEl = id => document.getElementById(id);
const attNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const attState = { month: null }; // month = { y, m } 보고 있는 달

// 화면을 열 때 (JSP 페이지가 열리면 부른다)
function openAttendanceView() {
  const now = new Date();
  attState.month = { y: now.getFullYear(), m: now.getMonth() };
  renderAttendanceView();
}

// 화면 전체를 서버에서 다시 받아 그린다 (다른 탭에서 바뀌었을 때도 이것을 부른다)
function renderAttendanceView() {
  if (!attState.month) return openAttendanceView();
  loadAttRecords();
}

const attMonthParam = () => `${attState.month.y}-${pad2(attState.month.m + 1)}`;

function loadAttRecords() {
  api.get('/attendance/api/records', { month: attMonthParam() })
    .done(r => { if (r.month === attMonthParam()) renderAttRecords(r); }) // 달을 빨리 넘기면 늦게 온 앞 달 응답은 버린다
    .fail(xhr => showToast(apiMessage(xhr, '출퇴근 기록을 불러오지 못했습니다')));
}

function renderAttRecords(r) {
  const dev = r.deviceId;
  // 카드: 오늘 / 이번 달 지각·조퇴 (카드의 "이번 달"은 오늘이 속한 달이다)
  const t = r.today.punch;
  attEl('attToday').textContent = !dev ? '단말기 미연결' : !t ? '기록 없음' : t.out ? `${t.in} ~ ${t.out}` : `${t.in} 출근`;
  attEl('attLate').textContent = dev ? `${r.thisMonth.late}회` : '-';
  attEl('attEarly').textContent = dev ? `${r.thisMonth.early}회` : '-';
  // 헤더의 오늘 출퇴근도 같이 맞춘다 (header.jspf 와 같은 문구). 시뮬레이터에서 찍고 돌아오면 새로고침 없이 바뀐다
  const head = document.querySelector('.attend-time');
  if (head) head.textContent = !t ? '출근 기록 없음' : t.out ? `출근 ${t.in} · 퇴근 ${t.out}` : `오늘 출근 ${t.in}`;

  renderDeviceLink(dev);

  // 월별 기록
  attEl('attMonthLabel').textContent = `${attState.month.y}년 ${attState.month.m + 1}월`;
  const rows = r.days.map(day => {
    const wk = day.weekday;
    const weekend = wk === 0 || wk === 6;
    const rest = weekend || !!day.holiday;
    const rec = day.punch;
    const note = day.holiday || (weekend ? '주말' : '') || (day.leave ? ATT_LEAVE_NOTE[day.leave] : '');
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
        .done(() => loadAttRecords())
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
      .done(() => loadAttRecords())
      .fail(xhr => { error.textContent = apiMessage(xhr); go.disabled = false; input.focus(); });
  };
  go.addEventListener('click', connect);
  input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); connect(); } });
  input.addEventListener('input', () => { error.textContent = ''; });
  box.append(
    attNode('p', 'att-link-text', '연결된 단말기 사용자 번호가 없어 출퇴근 기록이 보이지 않습니다. 지문 단말기에 등록된 내 사용자 번호를 연결해 주세요.'),
    attNode('div', 'input-action att-link-form', input, go), error);
}

const attMoveMonth = step => {
  const { y, m } = attState.month;
  const d = new Date(y, m + step, 1);
  attState.month = { y: d.getFullYear(), m: d.getMonth() };
  loadAttRecords();
};
attEl('attPrev').addEventListener('click', () => attMoveMonth(-1));
attEl('attNext').addEventListener('click', () => attMoveMonth(1));
// 다른 탭(지문 시뮬레이터)에서 찍고 이 탭으로 돌아오면 기록을 다시 받는다 (기능명세서 1.9: 변경 반영)
document.addEventListener('visibilitychange', () => { if (!document.hidden && attState.month) loadAttRecords(); });
