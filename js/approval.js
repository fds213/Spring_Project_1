// 근무 > 승인: 부서장·인사 담당·업체 관리자가 쓰는 화면 (요구분석서 v1.5 3.1 전자결재 "최대 2단계 순차 결재, 승인·반려",
// 3.9 연차·반차 규칙, 3.10 근태). 두 가지를 한다.
//   1) 휴가 승인: 결재선에서 "내 순번"인 신청만 보이고, 승인하면 다음 단계로, 마지막 단계면 최종 승인이 된다.
//      반려는 사유를 적어야 하고 그 자리에서 끝난다. 최종 승인 때 연차 잔여와 날짜 겹침을 다시 검사한다.
//   2) 직원 근태 확인: 부서장은 같은 부서 직원, 인사 담당·업체 관리자는 업체 전체 직원의 하루 출퇴근과 이번 달 지각·조퇴를 본다.
//      본인의 것은 근태·연차 화면에서 보므로 여기서는 뺀다. 다른 업체 직원은 보이지 않는다 (staffOf 가 업체별이다).
// 시안이라 localStorage 에서 읽고 쓰고, 실제 서버는 같은 규칙을 모두 다시 검사한다 (화면은 안내만 한다).
// attendance.js 의 도구(leaveStatus, LEAVE_LABEL, leaveSummaryFor, workingDaysBetween, deviceIdFor, punchOf, dayKeyOf ...)를 쓴다.

const apprEl = id => document.getElementById(id);
const apprNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };

let apprDay = ''; // 근태 현황에서 보고 있는 날짜

// 이 사람이 승인 화면을 쓸 수 있는가 (메뉴의 data-roles 와 같은 기준)
const canUseApproval = () => !!session && session.type === 'EMPLOYEE' && ['HEAD', 'HR', 'ADMIN'].some(r => userRoles.includes(r));

// 근태를 볼 수 있는 직원: 인사 담당·업체 관리자는 전체, 부서장은 자기 부서. 본인과 사용 중이 아닌 계정은 뺀다
function visiblePeople() {
  const all = userRoles.includes('ADMIN') || userRoles.includes('HR');
  const dept = (myStaffRecord() || {}).dept;
  return employeesOf(session.company).filter(e => e.status === 'ACTIVE' && e.empNo !== session.id && (all || e.dept === dept));
}

// 내 순번인 신청: 결재선의 현재 단계가 나이고 아직 끝나지 않은 것
const mineToDecide = () => allLeaves().filter(l => leaveStatus(l) === 'REQUESTED' && l.line[l.step] === session.id && l.empNo !== session.id);

// 내가 처리한 신청 기록 (승인·반려한 단계)
function myDecisions() {
  return allLeaves().flatMap(l => (l.history || []).filter(x => x.by === session.id && (x.action === 'APPROVE' || x.action === 'REJECT')).map(x => ({ l, x })))
    .sort((p, q) => (p.x.at < q.x.at ? 1 : -1));
}

// 메뉴 배지: 내 순번인 신청 수
function refreshApprovalBadge() {
  const badge = apprEl('approvalCount');
  if (!badge) return;
  const n = canUseApproval() ? mineToDecide().length : 0;
  badge.textContent = n;
  badge.hidden = n === 0;
}

function openApprovalView() {
  apprDay = dayKeyOf(new Date());
  renderApproval();
}

function renderApproval() {
  if (!apprDay) apprDay = dayKeyOf(new Date());
  refreshApprovalBadge();
  if (!canUseApproval()) return;
  const waiting = mineToDecide();
  const decisions = myDecisions();
  apprEl('apprCntWait').textContent = `${waiting.length}건`;
  apprEl('apprCntDone').textContent = `${decisions.length}건`;
  renderApprovalList(waiting);
  renderPeopleAttendance();
  renderApprovalHistory(decisions);
}

// ---------- 휴가 승인 대기 ----------
function renderApprovalList(waiting) {
  const cell = (label, ...parts) => { const td = apprNode('td', '', ...parts); td.dataset.label = label; return td; };
  const rows = waiting.slice().sort((a, b) => (a.at < b.at ? -1 : 1)).map(l => {
    const reason = apprNode('input', 'input appr-reason');
    reason.placeholder = '반려 사유 (반려할 때만)';
    reason.maxLength = 100;
    reason.setAttribute('aria-label', '반려 사유');
    const ok = apprNode('button', 'btn btn-primary', '승인');
    ok.type = 'button';
    ok.addEventListener('click', () => decideLeave(l.id, true, ''));
    const no = apprNode('button', 'btn', '반려');
    no.type = 'button';
    no.addEventListener('click', () => decideLeave(l.id, false, reason.value.trim()));
    const line = l.line.map((e, i) => `${i + 1}단계 ${nameOfStaff(e)}${i < l.step ? ' ✓' : i === l.step ? ' (내 차례)' : ''}`).join(' → ');
    return apprNode('tr', '',
      cell('신청자', apprNode('div', 'cell', apprNode('span', '', l.name), apprNode('small', 'cell-sub', l.dept || ''))),
      cell('구분·기간', apprNode('div', 'cell', apprNode('span', '', LEAVE_LABEL[l.kind]), apprNode('small', 'cell-sub', `${l.from}${l.to !== l.from ? ` ~ ${l.to}` : ''}`))),
      cell('일수', `${l.days}일`),
      cell('사유', l.reason || '-'),
      cell('결재선', apprNode('small', 'cell-sub', line)),
      cell('처리', apprNode('div', 'appr-actions', reason, apprNode('div', 'row', ok, no))));
  });
  if (!rows.length) {
    const td = apprNode('td', '', '내 순번인 휴가 신청이 없습니다');
    td.colSpan = 6;
    rows.push(apprNode('tr', 'row-empty', td));
  }
  apprEl('apprList').replaceChildren(...rows);
}

// 승인·반려. 저장값을 새로 읽어 내 순번이 맞는지 다시 확인한다 (다른 탭에서 이미 처리했거나 회수했을 수 있다)
function decideLeave(id, approve, comment) {
  if (readOnly) return showToast(readOnlyMessage());
  if (!canUseApproval()) return;
  if (!approve && !comment) return showToast('반려 사유를 적어 주세요');
  const all = loadStore(LEAVE_KEY, {});
  const l = (all[session.company] || []).find(x => x.id === id);
  if (!l || leaveStatus(l) !== 'REQUESTED' || l.line[l.step] !== session.id || l.empNo === session.id) {
    renderApproval();
    return showToast('이미 처리되었거나 내 순번이 아닙니다');
  }
  const now = new Date().toISOString();
  const entry = { step: l.step, by: session.id, name: session.name, action: approve ? 'APPROVE' : 'REJECT', at: now, comment };

  if (approve && l.step === l.line.length - 1) {
    // 마지막 단계의 승인 = 최종 승인: 연차 잔여와 날짜 겹침을 다시 검사한다 (신청 뒤에 다른 신청이 승인됐을 수 있다)
    const sum = leaveSummaryFor(session.company, l.empNo);
    const others = (all[session.company] || []).filter(x => x.empNo === l.empNo && x.id !== l.id && leaveStatus(x) === 'APPROVED');
    const clash = workingDaysBetween(l.from, l.to).some(k => others.some(x => x.from <= k && k <= x.to && (x.kind === 'FULL' || l.kind === 'FULL' || x.kind === l.kind)));
    if (!sum || sum.left < l.days) return showToast(`${l.name}님의 남은 연차(${sum ? sum.left : 0}일)가 부족해 승인할 수 없습니다. 반려해 주세요`);
    if (clash) return showToast('이미 승인된 다른 휴가와 날짜가 겹쳐 승인할 수 없습니다. 반려해 주세요');
  }

  l.history = [...(l.history || []), entry];
  if (!approve) {
    l.status = 'REJECTED';
  } else if (l.step < l.line.length - 1) {
    l.step += 1; // 다음 결재자에게 넘어간다
  } else {
    l.status = 'APPROVED';
    l.decidedAt = now;
  }
  saveStore(LEAVE_KEY, all);
  showToast(!approve ? `${l.name}님의 휴가를 반려했습니다` : l.status === 'APPROVED' ? `${l.name}님의 휴가를 최종 승인했습니다` : `${l.name}님의 휴가를 승인하고 다음 결재자에게 넘겼습니다`);
  renderApproval();
}

// ---------- 직원 근태 현황 ----------
function renderPeopleAttendance() {
  const people = visiblePeople();
  const day = apprDay;
  const d = dayFromKey(day);
  apprEl('apprDate').value = day;
  apprEl('apprScope').textContent = (userRoles.includes('ADMIN') || userRoles.includes('HR'))
    ? '업체 전체 직원의 출퇴근 기록입니다 (본인 제외)'
    : `${(myStaffRecord() || {}).dept || '-'} 직원의 출퇴근 기록입니다 (본인 제외)`;
  const hol = holidayName(day);
  const rest = d.getDay() === 0 || d.getDay() === 6 || !!hol;
  const monthDays = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  const leaves = allLeaves().filter(l => leaveStatus(l) === 'APPROVED');
  const STATUS_TEXT = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴' };

  let issues = 0;
  const rows = people.map(e => {
    const dev = deviceIdFor(session.company, e.empNo) || e.deviceUserId || '';
    const rec = punchOf(dev, day);
    let late = 0, early = 0;
    for (let n = 1; n <= monthDays && dev; n++) {
      const r = punchOf(dev, dayKeyOf(new Date(d.getFullYear(), d.getMonth(), n)));
      if (r && r.inStatus === 'LATE') late++;
      if (r && r.outStatus === 'EARLY_LEAVE') early++;
    }
    const lv = leaves.find(l => l.empNo === e.empNo && l.from <= day && day <= l.to);
    const flags = rec ? [rec.inStatus === 'LATE' ? '지각' : '', rec.outStatus === 'EARLY_LEAVE' ? '조퇴' : ''].filter(Boolean) : [];
    if (flags.length) issues++;
    const note = !dev ? '단말기 미연결' : lv ? LEAVE_LABEL[lv.kind] : hol || (d.getDay() === 0 || d.getDay() === 6 ? '주말' : (!rec && !rest ? '기록 없음' : ''));
    const tr = apprNode('tr', rest ? 'att-rest' : '',
      apprNode('td', '', apprNode('div', 'cell', apprNode('span', '', e.name), apprNode('small', 'cell-sub', `${e.dept} · ${e.rank}`))),
      apprNode('td', '', rec ? rec.in : '-'),
      apprNode('td', '', rec && rec.out ? rec.out : '-'),
      apprNode('td', '', rec ? apprNode('span', `status ${flags.length ? 'status-waiting' : 'status-active'}`, flags.length ? flags.join(' · ') : STATUS_TEXT.NORMAL) : '-'),
      apprNode('td', 'att-note', note),
      apprNode('td', '', dev ? `${late}회 · ${early}회` : '-'));
    tr.querySelectorAll('td').forEach((td, i) => { td.dataset.label = ['직원', '출근', '퇴근', '상태', '비고', '이번 달 지각·조퇴'][i]; });
    return tr;
  });
  apprEl('apprCntPeople').textContent = `${people.length}명`;
  apprEl('apprCntIssue').textContent = `${issues}명`;
  if (!rows.length) {
    const td = apprNode('td', '', '확인할 수 있는 직원이 없습니다');
    td.colSpan = 6;
    rows.push(apprNode('tr', 'row-empty', td));
  }
  apprEl('apprAtt').replaceChildren(...rows);
}

function renderApprovalHistory(decisions) {
  apprEl('apprHistory').replaceChildren(...(decisions.length ? decisions.slice(0, 10).map(({ l, x }) => {
    const approved = x.action === 'APPROVE';
    return apprNode('li', '',
      apprNode('span', 'leave-line',
        apprNode('span', '', `${l.name} · ${l.from}${l.to !== l.from ? ` ~ ${l.to}` : ''} · ${LEAVE_LABEL[l.kind]} ${l.days}일`),
        apprNode('small', 'cell-sub', `${whenFull(x.at)}${x.comment ? ` · 사유: ${x.comment}` : ''}`)),
      apprNode('span', `status status-${approved ? 'paid' : 'failed'}`, approved ? '승인' : '반려'));
  }) : [apprNode('li', 'muted', '아직 처리한 내역이 없습니다')]));
}

apprEl('apprPrev').addEventListener('click', () => { const d = dayFromKey(apprDay); d.setDate(d.getDate() - 1); apprDay = dayKeyOf(d); renderApproval(); });
apprEl('apprNext').addEventListener('click', () => { const d = dayFromKey(apprDay); d.setDate(d.getDate() + 1); apprDay = dayKeyOf(d); renderApproval(); });
apprEl('apprToday').addEventListener('click', () => { apprDay = dayKeyOf(new Date()); renderApproval(); });
apprEl('apprDate').addEventListener('change', e => { if (/^\d{4}-\d{2}-\d{2}$/.test(e.target.value)) { apprDay = e.target.value; renderApproval(); } });
