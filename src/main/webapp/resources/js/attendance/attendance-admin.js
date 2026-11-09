// 인사 관리 > 근태·연차 관리 (views/attendance/admin/list.jsp). 업체 관리자·인사 담당 (요구분석서 3.4·3.9).
// 직원별 오늘 출퇴근과 그 달 출근일·지각·조퇴, 오늘 기준 남은 연차를 본다. 기록을 고치는 기능은 없다(단말기 기록이 원본).
// 연차 추가 부여(+)·되돌리기(-)는 재직 중이고 입사일이 있는 다른 직원 줄의 [연차]로 연다. 권한·본인·남은 일수는 서버가 다시 막는다.
//
//   GET /attendance/admin/api/overview?month=YYYY-MM → { month, today, counts: { serving, punched, lateToday },
//        items: [{ employeeId, employeeNo, name, dept, serving, deviceNo, todayIn, todayOut, todayLate, todayEarly,
//                  workDays, late, early, leaveLeft (입사일이 없으면 null) }] }
//   GET  /attendance/admin/api/leaves/{employeeId}/grants → { grant(보유), left(남은), items: [{ id, days(±), note, at, by }] } 최근 순
//   POST /attendance/admin/api/leaves/{employeeId}/grants { days(±0.5 단위), note } → { employeeId, left }
//        거절: 400 값, 403 권한·본인, 404 다른 업체, 409 퇴사·입사일 없음·되돌리면 남은 연차가 0 아래
// util.js 의 h, span, inqCell, emptyTr, writeBtn, historyList, whenFull, showToast 를 쓴다.

const aaState = { month: null }; // { y, m } 보고 있는 달

const aaMonthParam = () => `${aaState.month.y}-${pad2(aaState.month.m + 1)}`;

function openAttendanceAdmin() {
  if (!aaState.month) {
    const now = new Date();
    aaState.month = { y: now.getFullYear(), m: now.getMonth() };
  }
  document.getElementById('aaMonth').textContent = `${aaState.month.y}년 ${aaState.month.m + 1}월`;
  api.get('/attendance/admin/api/overview', { month: aaMonthParam() })
    .done(r => { if (r.month === aaMonthParam()) renderAttendanceAdmin(r); }) // 달을 빨리 넘기면 늦게 온 앞 달 응답은 버린다
    .fail(xhr => showToast(apiMessage(xhr, '근태를 불러오지 못했습니다')));
}

function renderAttendanceAdmin(r) {
  document.getElementById('aaServing').textContent = `${r.counts.serving}명`;
  document.getElementById('aaPunched').textContent = `${r.counts.punched}명`;
  document.getElementById('aaLate').textContent = `${r.counts.lateToday}명`;

  const rows = r.items.map(p => {
    const today = !p.todayIn ? '-' : p.todayOut ? `${p.todayIn} ~ ${p.todayOut}` : `${p.todayIn} 출근`;
    const marks = [p.todayLate ? '지각' : '', p.todayEarly ? '조퇴' : ''].filter(Boolean).join(' · ');
    const tr = h('tr', p.serving ? '' : 'is-inactive',
      inqCell('사번', p.employeeNo),
      inqCell('이름', p.name, ...(p.serving ? [] : [span(' 재직 아님', 'cell-sub')])),
      inqCell('부서', p.dept || '-'),
      inqCell('단말기 번호', p.deviceNo || '미연결'),
      inqCell('오늘', today, ...(marks ? [span(marks, 'status status-waiting')] : [])),
      inqCell('출근일', `${p.workDays}일`),
      inqCell('지각', `${p.late}회`),
      inqCell('조퇴', `${p.early}회`),
      inqCell('남은 연차', p.leaveLeft == null ? '-' : `${p.leaveLeft}일`),
      inqCell('관리', grantCell(p)));
    return tr;
  });
  document.getElementById('aaList').replaceChildren(...(rows.length ? rows : [emptyTr(10, '직원이 없습니다')]));
}

// ---------- 연차 추가 부여 ----------

const aaGrant = { target: null }; // 창을 연 직원 { employeeId, name, employeeNo }
const aaMe = () => document.getElementById('view-attendance-admin').dataset.me;

/** 관리 칸: 재직 중이고 입사일이 있는 다른 직원만 [연차]. 본인은 '본인', 그 밖은 '-' */
function grantCell(p) {
  if (String(p.employeeId) === aaMe()) return span('본인', 'cell-sub');
  if (!p.serving || p.leaveLeft == null) return '-';
  const b = writeBtn('연차', 'btn', () => openGrant(p));
  b.setAttribute('aria-label', `${p.name} 연차 추가 부여`);
  return h('div', 'row-actions', b); // 칸 안에서 버튼이 늘어나지 않게(다른 표의 관리 칸과 같게)
}

/** 부여 창을 열고 그 직원의 보유·남은 일수와 부여 내역을 읽는다 */
function openGrant(p) {
  aaGrant.target = { employeeId: p.employeeId, name: p.name, employeeNo: p.employeeNo };
  document.getElementById('aaGrantForm').reset();
  document.getElementById('aaGrantKind').dispatchEvent(new Event('change')); // 꾸민 드롭다운(select.js)을 처음 값으로
  document.getElementById('aaGrantError').textContent = '';
  document.getElementById('aaGrantWho').textContent = `${p.name} (${p.employeeNo})`;
  document.getElementById('aaGrantHistory').replaceChildren(span('불러오는 중…', 'muted'));
  const dialog = document.getElementById('aaGrantDialog');
  if (!dialog.open) dialog.showModal();
  loadGrantHistory();
}

/** 보유·남은 일수와 부여 내역(최근 순)을 다시 그린다 */
function loadGrantHistory() {
  const t = aaGrant.target;
  api.get(`/attendance/admin/api/leaves/${t.employeeId}/grants`)
    .done(r => {
      if (aaGrant.target !== t) return; // 그사이 다른 직원 창을 열었다
      const sum = r.left == null ? '' : ` · 보유 ${r.grant}일 · 남은 ${r.left}일`;
      document.getElementById('aaGrantWho').textContent = `${t.name} (${t.employeeNo})${sum}`;
      document.getElementById('aaGrantHistory').replaceChildren(historyList(r.items.map(g => [
        h('span', 'aa-grant-note', `${g.days > 0 ? '+' : ''}${g.days}일 · ${g.note}`),
        span(`${whenFull(g.at)}${g.by ? ` · ${g.by}` : ''}`, 'cell-sub')]), '아직 추가 부여가 없습니다'));
    })
    .fail(xhr => {
      if (aaGrant.target !== t) return;
      document.getElementById('aaGrantHistory').replaceChildren(span(apiMessage(xhr, '부여 내역을 불러오지 못했습니다'), 'muted'));
    });
}

/** 입력을 확인하고 보낸다: 일수는 0.5일 단위 30일까지, 사유 필수. 되돌리기는 음수로 보낸다 */
document.getElementById('aaGrantForm').addEventListener('submit', ev => {
  ev.preventDefault();
  const t = aaGrant.target;
  const error = document.getElementById('aaGrantError');
  const sign = Number(document.getElementById('aaGrantKind').value);
  const days = Number(document.getElementById('aaGrantDays').value);
  const note = document.getElementById('aaGrantNote').value.trim();
  error.textContent = !(days > 0) ? '일수를 넣어 주세요'
    : days * 2 !== Math.round(days * 2) ? '일수는 0.5일 단위로 넣어 주세요'
    : days > 30 ? '한 번에 30일까지 바꿀 수 있습니다'
    : !note ? '사유를 넣어 주세요' : '';
  if (error.textContent || !t) return;
  const btn = document.getElementById('aaGrantSubmit');
  btn.disabled = true;
  api.post(`/attendance/admin/api/leaves/${t.employeeId}/grants`, { days: sign * days, note })
    .done(r => {
      showToast(`${t.name}님 연차를 ${days}일 ${sign > 0 ? '더 줬습니다' : '되돌렸습니다'} (남은 ${r.left}일)`);
      document.getElementById('aaGrantDays').value = '';
      document.getElementById('aaGrantNote').value = '';
      loadGrantHistory();
      openAttendanceAdmin(); // 표의 남은 연차도 새로
    })
    .fail(xhr => { error.textContent = apiMessage(xhr, '연차를 바꾸지 못했습니다'); })
    .always(() => { btn.disabled = false; });
});
document.getElementById('aaGrantClose').addEventListener('click', () => document.getElementById('aaGrantDialog').close());
['aaGrantDays', 'aaGrantNote'].forEach(id =>
  document.getElementById(id).addEventListener('input', () => { document.getElementById('aaGrantError').textContent = ''; }));

function moveAdminMonth(step) {
  const d = new Date(aaState.month.y, aaState.month.m + step, 1);
  aaState.month = { y: d.getFullYear(), m: d.getMonth() };
  openAttendanceAdmin();
}
document.getElementById('aaPrev').addEventListener('click', () => moveAdminMonth(-1));
document.getElementById('aaNext').addEventListener('click', () => moveAdminMonth(1));
