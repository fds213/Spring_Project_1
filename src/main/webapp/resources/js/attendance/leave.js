// 근무 > 연차 (views/attendance/leave.jsp): 로그인한 본인의 연차 현황과 휴가 신청 내역만 보여 준다 (요구분석서 3.9).
// 다른 직원의 연차, 근태·연차 관리(인사)는 이 화면에 없다. 출퇴근 기록은 근태 화면(attendance.js)에 있다.
//
// 연차: 모든 직원 15일, 입사할 때 한 번 부여한다(경력·입사기념일로 바뀌지 않음). 계산과 신청 규칙(겹침·잔여·주말·휴일)은 서버가 정하고, 화면은 받은 값을 보여 준다.
// 연차를 쓰는 것(휴가)은 전자결재의 휴가 신청서로 신청한다(요구분석서 3장 범위). 최종 승인 때 연차가 차감되고 여기 내역에 들어온다.
// 이 화면은 연차 현황·내역만 보여 주고, 신청은 전자결재(기안 작성)에서 한다.

// 연차 구분·신청 상태 이름
const LEAVE_LABEL = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };
const LEAVE_STATUS = { REQUESTED: '승인 대기', APPROVED: '승인', REJECTED: '반려' };
const LEAVE_STATUS_CLASS = { REQUESTED: 'waiting', APPROVED: 'paid', REJECTED: 'failed' };
const dayText = n => `${Number.isInteger(n) ? n : n.toFixed(1)}일`;

const leaveEl = id => document.getElementById(id);
const leaveNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };

// 화면을 열 때 (JSP 페이지가 열리면 부른다). 다른 탭에서 바뀌었을 때도 이것을 부른다
function openLeaveView() {
  api.get('/attendance/api/leaves')
    .done(renderLeaveView)
    .fail(xhr => showToast(apiMessage(xhr, '연차 현황을 불러오지 못했습니다')));
}

// 다른 탭(전자결재)에서 신청·결재하고 돌아오면 다시 받는다
document.addEventListener('visibilitychange', () => { if (!document.hidden) openLeaveView(); });

// 카드, 연차 현황, 신청 내역
function renderLeaveView(r) {
  const sum = r.summary;
  const dl = leaveEl('leaveInfo');
  const hist = leaveEl('leaveHistory');
  leaveEl('leaveLeft').textContent = sum ? dayText(sum.left) : '-';
  leaveEl('leaveGrant').textContent = sum ? dayText(sum.grant) : '-';
  leaveEl('leaveUsed').textContent = sum ? dayText(sum.usedDays) : '-';
  leaveEl('leavePending').textContent = sum ? dayText(sum.pendingDays) : '-';
  if (!sum) {
    dl.replaceChildren(leaveNode('dt', '', '안내'), leaveNode('dd', '', r.reason === 'NO_HIRE_DATE'
      ? '입사일이 등록되지 않아 연차를 계산할 수 없습니다. 인사 담당에게 문의하세요' : '직원 정보를 찾을 수 없어 연차를 계산할 수 없습니다'));
    hist.replaceChildren();
    return;
  }
  const row = (k, v) => [leaveNode('dt', '', k), leaveNode('dd', '', v)];
  dl.replaceChildren(
    ...row('부여', `${dayText(sum.grant)} (${ymd(sum.start)} 입사 때 부여, 모든 직원 같음)`),
    ...row('사용 (승인됨)', dayText(sum.usedDays)),
    ...row('승인 대기', dayText(sum.pendingDays)),
    ...row('남은 연차', dayText(sum.left)));

  // 신청 내역: 상태와, 대기 중이면 지금 결재할 사람, 반려면 사유
  hist.replaceChildren(...(r.items.length ? r.items.map(l => {
    const range = `${l.from}${l.to !== l.from ? ` ~ ${l.to}` : ''} · ${LEAVE_LABEL[l.kind]} · ${dayText(l.days)}`;
    const now = l.line[l.step];
    const sub = l.status === 'REQUESTED' ? `${l.step + 1}단계 ${now ? now.name : '-'} 결재 대기`
      : l.status === 'REJECTED' ? `반려${l.comment ? `: ${l.comment}` : ''}` : '';
    return leaveNode('li', '', leaveNode('span', 'leave-line', leaveNode('span', '', range), ...(sub ? [leaveNode('small', 'cell-sub', sub)] : [])),
      leaveNode('span', `status status-${LEAVE_STATUS_CLASS[l.status]}`, LEAVE_STATUS[l.status]));
  }) : [leaveNode('li', 'muted', '신청한 휴가가 없습니다')]));
}
