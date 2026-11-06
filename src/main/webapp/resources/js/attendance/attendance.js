// 근무 > 근태 (views/attendance/my.jsp): 로그인한 본인의 출퇴근 기록만 보여 준다 (요구분석서 3.1, 3.10).
// 다른 직원의 근태 기록, 근태·연차 관리(인사)는 이 화면에 없다(다른 직원 것은 내가 처리할 정정 요청만). 연차는 연차 화면(leave.js)에 있다.
//
// 출퇴근 기록: 지문 단말기가 보낸 기록을 단말기 사용자 번호로 찾는다. 로그인한 사람과 단말기 사용자 번호의 연결은 요구분석서 3.10 의
// "업체·단말기 사용자 번호·사번 계정의 연결"이다. 이 화면에서는 본인이 번호를 연결한다 (원래는 인사 담당자·서버가 관리할 일이다).
// 승인된 연차가 있는 날은 기록의 비고에 연차 구분을 적는다.
//
// 근태 정정(퇴근 누락 1차): '퇴근 누락' 날의 비고에 [정정 요청]. 요청하면 '정정 대기'와 [취소], 반려되면 반려 사유와 [다시 요청],
// 승인되면 그날 퇴근이 채워지고 '정정 반영'. 처리자(요청자 부서장, 없거나 본인이면 업체 관리자)에게는 '처리할 근태 정정' 목록이 보인다.
//   GET  /attendance/api/corrections?month=YYYY-MM → { month, items: [{ id, date, in, outTime, reason, status, at, decidedBy, decidedAt, rejectReason }] } 최근 순
//   POST /attendance/api/corrections { date, outTime(HH:mm), reason } · POST …/{id}/cancel
//   GET  /attendance/api/corrections/inbox → { items: [{ id, name, employeeNo, dept, date, in, outTime, reason, at, … }] } 오래된 순
//   POST …/{id}/approve · POST …/{id}/reject { reason }   거절은 { message } (400 값, 403 권한, 404 없음, 409 상태)

const ATT_STATUS = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴', NO_OUT: '퇴근 누락' }; // NO_OUT: 지난 날 출근만 있고 퇴근이 없음
const ATT_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
// 기록 비고에 쓰는 연차 구분 이름
const ATT_LEAVE_NOTE = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };

const attEl = id => document.getElementById(id);
const attNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const attState = { month: null, placed: '' }; // month = { y, m } 보고 있는 달, placed = 목록 위치를 맞춘 달

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
  loadAttInbox();
}

const attMonthParam = () => `${attState.month.y}-${pad2(attState.month.m + 1)}`;

// 그 달 기록과 내 정정 요청을 같이 받는다. 정정 목록을 못 받아도 기록은 그린다
function loadAttRecords() {
  const month = attMonthParam();
  const corr = api.get('/attendance/api/corrections', { month }).then(c => c, () => $.Deferred().resolve({ items: [] }).promise());
  $.when(api.get('/attendance/api/records', { month }), corr)
    .done((r, c) => { if (r.month === attMonthParam()) renderAttRecords(r, c.items); }) // 달을 빨리 넘기면 늦게 온 앞 달 응답은 버린다
    .fail(xhr => showToast(apiMessage(xhr, '출퇴근 기록을 불러오지 못했습니다')));
}

function renderAttRecords(r, corrections = []) {
  // 날짜마다 가장 최근 정정 요청 (목록이 최근 순이라 처음 나온 것)
  const corrByDate = {};
  corrections.forEach(c => { if (!corrByDate[c.date]) corrByDate[c.date] = c; });
  const dev = r.deviceId;
  // 카드: 오늘 / 이번 달 지각·조퇴 (카드의 "이번 달"은 오늘이 속한 달이다)
  const t = r.today.punch;
  // 기록이 있으면 연결을 끊었어도 기록을 보여 준다(표·헤더와 같은 말). 없을 때만 미연결 안내
  attEl('attToday').textContent = t ? (t.out ? `${t.in} ~ ${t.out}` : `${t.in} 출근`) : dev ? '기록 없음' : '단말기 미연결';
  attEl('attLate').textContent = `${r.thisMonth.late}회`;
  attEl('attEarly').textContent = `${r.thisMonth.early}회`;
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
      attNode('td', 'att-note', attNode('div', 'att-note-box', ...(note ? [note] : []), ...corrParts(day, rec, corrByDate[day.date]))));
    tr.querySelectorAll('td').forEach((td, i) => { td.dataset.label = ['날짜', '출근', '퇴근', '상태', '비고'][i]; });
    return tr;
  });
  attEl('attList').replaceChildren(...rows);
  placeAttList();
}

// ---------- 근태 정정 (퇴근 누락) ----------

const attCorr = { day: null, reject: null }; // 요청 창을 연 날 { date, in }, 반려 창을 연 요청
const attDayLabel = date => `${Number(date.slice(5, 7))}월 ${Number(date.slice(8))}일`;

/** 비고 칸의 정정 표시: 반영·대기(+취소)·반려(+다시 요청), 퇴근 누락이면 [정정 요청] */
function corrParts(day, rec, c) {
  if (c && c.status === 'APPROVED') return [span(`정정 반영 ${c.outTime}`, 'status status-active')];
  if (c && c.status === 'REQUESTED') {
    return [span(`정정 대기 ${c.outTime}`, 'status status-waiting'),
      writeBtn('취소', 'btn', b => cancelCorrection(c, b))];
  }
  if (!rec || rec.outStatus !== 'NO_OUT') return [];
  const parts = [];
  if (c && c.status === 'REJECTED') parts.push(span('정정 반려', 'status status-rejected'), span(`사유: ${c.rejectReason}`, 'cell-sub'));
  const again = c && c.status === 'REJECTED';
  const go = writeBtn(again ? '다시 요청' : '정정 요청', 'btn', () => openCorrection(day, rec));
  go.setAttribute('aria-label', `${attDayLabel(day.date)} 퇴근 누락 정정 ${again ? '다시 요청' : '요청'}`);
  parts.push(go);
  return parts;
}

/** 정정 요청 창을 연다 (그날 출근 시각을 보여 준다) */
function openCorrection(day, rec) {
  attCorr.day = { date: day.date, in: rec.in };
  attEl('attCorrForm').reset();
  attEl('attCorrError').textContent = '';
  attEl('attCorrWho').textContent = `${attDayLabel(day.date)} (${ATT_WEEKDAYS[day.weekday]}) · 출근 ${rec.in} · 퇴근 기록 없음`;
  attEl('attCorrDialog').showModal();
  attEl('attCorrOut').focus();
}

attEl('attCorrForm').addEventListener('submit', ev => {
  ev.preventDefault();
  const d = attCorr.day;
  const out = attEl('attCorrOut').value;
  const reason = attEl('attCorrReason').value.trim();
  const error = attEl('attCorrError');
  // 서버도 같은 검사를 다시 한다
  error.textContent = !out ? '퇴근 시각을 넣어 주세요'
    : out < d.in.slice(0, 5) ? `퇴근 시각은 출근 시각(${d.in.slice(0, 5)})보다 늦어야 합니다`
    : !reason ? '사유를 넣어 주세요' : '';
  if (error.textContent) return;
  const btn = attEl('attCorrSubmit');
  btn.disabled = true;
  api.post('/attendance/api/corrections', { date: d.date, outTime: out, reason })
    .done(() => {
      attEl('attCorrDialog').close();
      showToast(`${attDayLabel(d.date)} 퇴근을 ${out}으로 정정해 달라고 요청했습니다`);
      loadAttRecords();
    })
    .fail(xhr => { error.textContent = apiMessage(xhr, '정정을 요청하지 못했습니다'); })
    .always(() => { btn.disabled = false; });
});
attEl('attCorrCancel').addEventListener('click', () => attEl('attCorrDialog').close());

/** 처리 전인 내 요청을 취소한다 */
function cancelCorrection(c, btn) {
  btn.disabled = true;
  api.post(`/attendance/api/corrections/${c.id}/cancel`)
    .done(() => showToast(`${attDayLabel(c.date)} 정정 요청을 취소했습니다`))
    .fail(xhr => showToast(apiMessage(xhr, '취소하지 못했습니다')))
    .always(() => loadAttRecords()); // 그새 처리됐을 수 있어 실패해도 다시 받는다
}

/** 처리할 근태 정정 (부서장·업체 관리자). 대기 요청이 없으면 칸을 숨긴다 */
function loadAttInbox() {
  api.get('/attendance/api/corrections/inbox')
    .done(r => renderAttInbox(r.items))
    .fail(() => { attEl('attInbox').hidden = true; });
}

/** 처리할 대기 요청 표: 요청자·날짜·출근·요청 퇴근·사유·요청일과 [승인]·[반려] */
function renderAttInbox(items) {
  attEl('attInbox').hidden = !items.length;
  attEl('attInboxList').replaceChildren(...items.map(c => h('tr', '',
    inqCell('요청자', c.name, ...(c.dept ? [small(c.dept)] : [])),
    inqCell('날짜', attDayLabel(c.date)),
    inqCell('출근', c.in),
    inqCell('요청 퇴근', c.outTime),
    inqCell('사유', h('span', 'att-corr-reason', c.reason)),
    inqCell('요청일', whenFull(c.at)),
    inqCell('관리', h('div', 'row-actions',
      writeBtn('승인', 'btn btn-primary', b => approveCorrection(c, b)),
      writeBtn('반려', 'btn', () => openReject(c)))))));
}

/** 승인: 요청한 퇴근 시각이 그날 근태에 들어간다 */
function approveCorrection(c, btn) {
  btn.disabled = true;
  api.post(`/attendance/api/corrections/${c.id}/approve`)
    .done(() => showToast(`${c.name}님 ${attDayLabel(c.date)} 퇴근을 ${c.outTime}으로 정정했습니다`))
    .fail(xhr => showToast(apiMessage(xhr, '승인하지 못했습니다')))
    .always(() => loadAttInbox());
}

/** 반려 창을 연다 */
function openReject(c) {
  attCorr.reject = c;
  attEl('attRejectForm').reset();
  attEl('attRejectError').textContent = '';
  attEl('attRejectWho').textContent = `${c.name} · ${attDayLabel(c.date)} · 요청 퇴근 ${c.outTime}`;
  attEl('attRejectDialog').showModal();
  attEl('attRejectReason').focus();
}

attEl('attRejectForm').addEventListener('submit', ev => {
  ev.preventDefault();
  const c = attCorr.reject;
  const reason = attEl('attRejectReason').value.trim();
  const error = attEl('attRejectError');
  error.textContent = reason ? '' : '반려 사유를 넣어 주세요';
  if (error.textContent) return;
  const btn = attEl('attRejectSubmit');
  btn.disabled = true;
  api.post(`/attendance/api/corrections/${c.id}/reject`, { reason })
    .done(() => {
      attEl('attRejectDialog').close();
      showToast(`${c.name}님 ${attDayLabel(c.date)} 정정 요청을 반려했습니다`);
      loadAttInbox();
    })
    .fail(xhr => { error.textContent = apiMessage(xhr, '반려하지 못했습니다'); })
    .always(() => { btn.disabled = false; });
});
attEl('attRejectCancel').addEventListener('click', () => attEl('attRejectDialog').close());
['attCorrOut', 'attCorrReason', 'attRejectReason'].forEach(id =>
  attEl(id).addEventListener('input', () => { attEl(id.startsWith('attReject') ? 'attRejectError' : 'attCorrError').textContent = ''; }));

// 목록 위치: 달을 새로 열 때만 맞춘다(탭으로 돌아와 다시 받을 때는 보던 자리 그대로).
// 오늘이 있는 달이면 오늘 줄이 표 머리줄 바로 아래에 오게, 다른 달이면 1일부터
function placeAttList() {
  const key = attMonthParam();
  if (attState.placed === key) return;
  attState.placed = key;
  const wrap = document.querySelector('.att-table');
  const today = attEl('attList').querySelector('.att-today');
  if (!today) { wrap.scrollTop = 0; return; }
  const head = wrap.querySelector('thead');
  wrap.scrollTop += today.getBoundingClientRect().top - wrap.getBoundingClientRect().top - (head ? head.offsetHeight : 0);
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
document.addEventListener('visibilitychange', () => { if (!document.hidden && attState.month) { loadAttRecords(); loadAttInbox(); } });
