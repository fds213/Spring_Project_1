// 근무 > 연차 (views/attendance/leave.jsp): 로그인한 본인의 연차 현황과 이번 연차 기간의 휴가 신청 내역만 보여 준다 (요구분석서 3.9).
// 다른 직원의 연차, 근태·연차 관리(인사)는 이 화면에 없다. 출퇴근 기록은 근태 화면(attendance.js)에 있다.
//
// 연차: 15일 + 내림(총 완료 경력 연수 ÷ 3). 계산과 신청 규칙(겹침·잔여·주말·휴일)은 서버가 정하고, 화면은 받은 값을 보여 준다.
// 연차를 쓰는 것(휴가)은 전자결재의 휴가 신청서로 신청한다(요구분석서 3장 범위). 최종 승인 때 연차가 차감되고 여기 내역에 들어온다.
// 이 화면은 연차 현황·내역만 보여 주고, 신청·회수는 전자결재(기안 작성·결재함)에서 한다.

// 연차 구분·신청 상태 이름
const LEAVE_LABEL = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };
const LEAVE_STATUS = { REQUESTED: '승인 대기', APPROVED: '승인', REJECTED: '반려', CANCELED: '회수' };
const LEAVE_STATUS_CLASS = { REQUESTED: 'waiting', APPROVED: 'paid', REJECTED: 'failed', CANCELED: 'expired' };
const dayText = n => `${Number.isInteger(n) ? n : n.toFixed(1)}일`;

const leaveEl = id => document.getElementById(id);
const leaveNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };

// 화면을 열 때 (JSP 페이지가 열리면 부른다). 다른 탭에서 바뀌었을 때도 이것을 부른다
function openLeaveView() {
  api.get('/attendance/api/leaves')
    .done(renderLeaveView)
    .fail(xhr => showToast(apiMessage(xhr, '연차 현황을 불러오지 못했습니다')));
}

// 카드, 연차 현황, 이번 연차 기간의 신청 내역
function renderLeaveView(r) {
  const sum = r.summary;
  const dl = leaveEl('leaveInfo');
  const hist = leaveEl('leaveHistory');
  leaveEl('leaveLeft').textContent = sum ? dayText(sum.left) : '-';
  leaveEl('leaveGrant').textContent = sum ? dayText(sum.grant) : '-';
  leaveEl('leaveUsed').textContent = sum ? dayText(sum.usedDays) : '-';
  leaveEl('leavePending').textContent = sum ? dayText(sum.pendingDays) : '-';
  if (!sum) {
    dl.replaceChildren(leaveNode('dt', '', '안내'), leaveNode('dd', '', '직원 정보를 찾을 수 없어 연차를 계산할 수 없습니다'));
    hist.replaceChildren();
    return;
  }
  const row = (k, v) => [leaveNode('dt', '', k), leaveNode('dd', '', v)];
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
    return leaveNode('li', '', leaveNode('span', 'leave-line', leaveNode('span', '', range), ...(sub ? [leaveNode('small', 'cell-sub', sub)] : [])),
      leaveNode('span', `status status-${LEAVE_STATUS_CLASS[l.status]}`, LEAVE_STATUS[l.status]));
  }) : [leaveNode('li', 'muted', '이번 연차 기간에 신청한 내역이 없습니다')]));
}
