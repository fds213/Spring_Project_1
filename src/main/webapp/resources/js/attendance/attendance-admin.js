// 인사 관리 > 근태·연차 관리 (views/attendance/admin/list.jsp). 업체 관리자·인사 담당 (요구분석서 3.4·3.9).
// 직원별 오늘 출퇴근과 그 달 출근일·지각·조퇴, 오늘 기준 남은 연차를 본다. 기록을 고치는 기능은 없다(단말기 기록이 원본).
//
//   GET /attendance/admin/api/overview?month=YYYY-MM → { month, today, counts: { serving, punched, lateToday },
//        items: [{ employeeId, employeeNo, name, dept, serving, deviceNo, todayIn, todayOut, todayLate, todayEarly,
//                  workDays, late, early, leaveLeft (입사일이 없으면 null) }] }
// util.js 의 h, inqCell, emptyTr, showToast 를 쓴다.

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
      inqCell('남은 연차', p.leaveLeft == null ? '-' : `${p.leaveLeft}일`));
    return tr;
  });
  document.getElementById('aaList').replaceChildren(...(rows.length ? rows : [emptyTr(9, '직원이 없습니다')]));
}

function moveAdminMonth(step) {
  const d = new Date(aaState.month.y, aaState.month.m + step, 1);
  aaState.month = { y: d.getFullYear(), m: d.getMonth() };
  openAttendanceAdmin();
}
document.getElementById('aaPrev').addEventListener('click', () => moveAdminMonth(-1));
document.getElementById('aaNext').addEventListener('click', () => moveAdminMonth(1));
