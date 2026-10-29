// Operator: 업체 관리. Click a company and everything known about it opens under its row.
// It creates no data of its own: it joins what the other screens already keep (applications and their payments,
// the company's accounts from 사원 관리, inquiries).
// Load order: after ops.js and admin-inquiry.js; staffOf comes from hr.js and is only called once a screen is drawn.
// Names start with "firm" because admin-inquiry.js already owns "company..." (companyDetail, renderCompany, ...).

// One company is open at a time; kept across re-renders
let openFirm = null;

function openFirmView() {
  openFirm = null;
  renderFirms();
}

// Every application is a company: a trial creates the company and its first admin right away, so a company in
// its trial or read-only after it is listed too (its state says which). Newest first.
const companyAccounts = () => loadStore(ORDERS_KEY, []).slice().sort((a, b) => new Date(b.at) - new Date(a.at));

// Guest inquiries have no company field; the only link is the contact e-mail typed at signup.
// Company-admin inquiries carry the company code (a foreign key in the real DB). The name is only the fallback
// for older saved records without a code, because two companies can share a name.
function firmInquiries(o) {
  const mail = (o.email || '').toLowerCase();
  return [
    ...loadList(COMPANY_KEY, COMPANY_SAMPLES).filter(q => (q.companyCode ? q.companyCode === o.companyCode : q.company === o.company))
      .map(q => ({ kind: `회원 접수 · ${TYPE_LABEL[typeOf(q)]}`, title: q.title, at: q.at, state: COMPANY_LABEL[companyState(q)] })),
    ...guestList().filter(q => q.email.toLowerCase() === mail)
      .map(q => ({ kind: `비회원 접수 · ${TYPE_LABEL[typeOf(q)]}`, title: q.title, at: q.at, state: GUEST_LABEL[guestState(q)] })),
  ].sort((a, b) => new Date(b.at) - new Date(a.at));
}

// A card body made of short two-line entries; empty gets one quiet sentence instead of a blank card
function miniList(entries, emptyText) {
  if (!entries.length) return h('p', 'mini-empty', emptyText);
  return h('ul', 'mini-list', ...entries.map(([main, sub]) => h('li', '', main, h('span', 'mini-sub', sub))));
}

// The company's accounts as 사원 관리 keeps them (hr.js). The list scrolls inside its card so a 300-person
// company doesn't push everything else off screen; tabindex lets a keyboard user focus and scroll it.
function employeeCard(o) {
  const list = employeesOf(o.companyCode); // accounts with a 사번 (join requests have none yet)
  // one line per account (name left, 사번 and team right): a 2-line row would show only 4 people in the box
  const body = list.length
    ? h('div', 'emp-scroll', h('ul', 'mini-list', ...list.map(e => h('li', 'emp-row', span(e.name), h('span', 'mini-sub', `${e.empNo} · ${e.dept}${statusOf(e) !== 'ACTIVE' ? ` · ${EMP_STATUS[statusOf(e)] || e.status}` : ''}`)))))
    : h('p', 'mini-empty', '등록된 계정이 없습니다');
  if (list.length) {
    body.tabIndex = 0;
    body.setAttribute('role', 'region');
    body.setAttribute('aria-label', `${o.company} 계정 목록`);
  }
  return cardOf(`계정 · ${list.length}명`, body);
}

const PLAN_LABEL = { MONTHLY: '월간', ANNUAL: '연간' };

function firmDetail(o) {
  const st = trialState(o);
  const info = cardOf('업체 정보', dlOf([
    ['업체명', o.company],
    ['업체 코드', o.companyCode],
    ['이용 상태', st.label],
    ['이용 인원', `${o.users}명`],
    ['월 이용료', won(o.total)],
    ['이메일', o.email],
    ['이메일 인증', o.emailVerified === false ? '대기 (로그인 불가)' : '완료'],
    ['체험 신청', whenFull(o.at)],
    ['신청번호', o.no],
  ]));

  // Deliberately NOT shown: the password typed at signup. It is sample data kept in plain text; a
  // company screen has no reason to display it, and 신청 현황 already owns that card.
  const account = cardOf('관리자 계정', dlOf([
    ['이름', o.adminName],
    ['사번', adminNo(o)],
  ]));

  // the trial signup, then every payment try (first payment, extensions, paying again after expiry), newest first
  // sorted newest first; two tries on the same date (a failed try, then the successful one) keep the order they were recorded in
  const history = [
    ...(o.payments || []).map((p, i) => [
      // result: 승인 / 승인 대기 / 반려 (with the reason) / 결제 실패, and 증설 결제·환불 (ops.js payResultOf)
      h('span', '', span(payResultOf(p).label, `status status-${payResultOf(p).cls}`),
        ` ${PAY_KIND[p.kind] || '첫 결제'}${p.plan ? ` ${PLAN_LABEL[p.plan]}` : ''} · ${won(p.amount)}`),
      `${whenFull(p.at)}${p.users ? ` · ${p.users}명` : ''}${p.result === 'REJECTED' && p.reason ? ` · 사유: ${p.reason}` : ''}`, p.at, i + 1,
    ]),
    [h('span', '', span('체험 신청', 'status status-trial'), ' 14일 무료 체험'), `${whenFull(o.at)} · ${o.no}`, o.at, 0],
  ].sort((a, b) => (new Date(b[2]) - new Date(a[2])) || (b[3] - a[3])).map(([main, sub]) => [main, sub]);
  const pay = cardOf('신청·결제 이력', miniList(history, '결제 이력이 없습니다'));

  const inquiries = cardOf('장애·문의 이력', miniList(
    firmInquiries(o).map(q => [q.title, `${q.kind} · ${whenFull(q.at)} · ${q.state}`]),
    '이 업체의 접수 내역이 없습니다'));

  // one column: the admin card with the account list right under it, so both sit in the same grid cell
  return h('div', 'info-grid', info, h('div', 'info-col', account, employeeCard(o)), pay, inquiries);
}

function renderFirms() {
  const rows = companyAccounts().flatMap(o => {
    const isOpen = openFirm === o.companyCode;
    const st = trialState(o);
    const tr = dataRow(isOpen, () => { openFirm = isOpen ? null : o.companyCode; renderFirms(); },
      inqCell('업체', titleBtn(o.company, isOpen), small(o.companyCode)),
      inqCell('관리자', span(o.adminName), small(adminNo(o))),
      inqCell('이용 인원', span(`${o.users}명`)),
      inqCell('월 이용료', span(won(o.total))),
      inqCell('상태', span(st.label, `status status-${st.status.toLowerCase()}`)),
      inqCell('가입일', span(when(o.at))));
    // the numeric columns line up right, like the 신청 현황 table
    tr.children[2].classList.add('num');
    tr.children[3].classList.add('num');
    return isOpen ? [tr, detailTr(6, firmDetail(o))] : [tr];
  });
  document.getElementById('firmList').replaceChildren(...(rows.length ? rows : [emptyTr(6, '아직 가입한 업체가 없습니다')]));
}
