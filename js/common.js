// Prototype only: switches views by URL hash. The real project uses one JSP per page.
const VIEWS = {
  'home': { crumb: '홈', title: '홈' },
  'notice': { crumb: '홈', title: '공지사항' },
  'approval-write': { crumb: '전자결재', title: '기안 작성' },
  'approval-box': { crumb: '전자결재', title: '결재함' },
  'ticket-new': { crumb: 'IT 서비스', title: '장애·문의 접수' },
  'ticket-mine': { crumb: 'IT 서비스', title: '내 티켓' },
  'asset-mine': { crumb: 'IT 서비스', title: '내 장비' },
  'ticket-all': { crumb: 'IT 서비스', title: '티켓 처리' },
  'asset-all': { crumb: 'IT 서비스', title: '자산 관리' },
  'attendance': { crumb: '근무', title: '근태·연차' },
  'schedule': { crumb: '근무', title: '일정' },
  'chat': { crumb: '소통', title: '채팅' },
  'board-dept': { crumb: '소통', title: '부서 게시판' },
  'location': { crumb: '소통', title: '지도' },
  'org': { crumb: '회사', title: '조직도' },
  'hr-employee': { crumb: '인사 관리', title: '사원 관리' },
  'hr-join': { crumb: '인사 관리', title: '가입 승인' },
  'hr-attendance': { crumb: '인사 관리', title: '근태·연차 관리' },
  'hr-holiday': { crumb: '인사 관리', title: '휴일 관리' },
  'admin-dept': { crumb: '업체 관리', title: '부서 관리' },
  'admin-role': { crumb: '업체 관리', title: '역할 관리' },
  'admin-billing': { crumb: '업체 관리', title: '이용 결제' },
};

// Role-based menu: an item shows when the user has any role in its data-roles.
// In the real JSP the server decides this with sec:authorize; the select below only simulates it.
let userRoles = ['EMPLOYEE'];

function canSee(item) {
  const need = (item.dataset.roles || '').split(' ').filter(Boolean);
  return need.length === 0 || need.some(r => userRoles.includes(r));
}
let readOnly = false;

function applyRoles() {
  document.querySelectorAll('.nav-item[data-view]').forEach(a => { a.hidden = !canSee(a); });
  // hide a group heading when none of its items are visible
  document.querySelectorAll('.nav-section').forEach(s => {
    s.hidden = ![...s.querySelectorAll('.nav-item')].some(a => !a.hidden);
  });
}

// Sign-in. The app always opens on the login screen, and everyone uses the same three fields:
// IEUM itself is a company with its own code (ieum), and its accounts are the operators. The account
// decides which menus the same 3-column layout shows: company employees get the groupware, IEUM
// operators get the operator screens (ops.js). Prototype only: the accounts below live in this file and
// the session in sessionStorage, so closing the tab signs out. The real project uses Spring Security form login.
const DEMO_ACCOUNTS = [
  // Only IEUM's own operator account is built in (sample companies removed 2026-10-28). Company accounts come
  // from a trial signup or the built-in demo companies ieum0001/ieum0002 (findTrialAccount, ops.js demoCompanies)
  // and from employees who joined through 계정 만들기 (hr.js).
  { type: 'OPS', company: 'ieum', id: '20260001', pw: 'ieum1234',
    companyName: '이음 운영', name: '운영자', sub: '이음 운영팀', roles: 'EMPLOYEE' },
];

// A company that signed up for the free trial (trial.js) signs in with its company code, the first admin's
// 사번 (<signup year>0001) and password once its e-mail is verified (requirements v1.4: the account exists from
// the signup, the operator issues nothing).
function findTrialAccount(company, id, pw) {
  const o = loadStore(ORDERS_KEY, []).find(x => x.companyCode === company && adminNo(x) === id && x.adminPw === pw);
  if (!o) return null;
  return { type: 'EMPLOYEE', company, id, companyName: o.company, name: o.adminName, sub: '업체 관리자', roles: 'EMPLOYEE ADMIN' };
}
const MODES = {
  EMPLOYEE: { views: VIEWS, home: 'home' },
  OPS: { views: OPS_VIEWS, home: 'ops-company' },
};
const SESSION_KEY = 'ieum-demo-session';

function loadSession() {
  try { return JSON.parse(sessionStorage.getItem(SESSION_KEY)); } catch { return null; }
}
let session = loadSession();

// The groupware runs only in the installed IEUM app (runningAsApp, from install.js); a browser tab gets
// the download screen. Prototype only: add ?preview to the address to look at it in a normal tab,
// since the preview pane and quick checks can't install apps.
const inApp = runningAsApp || new URLSearchParams(location.search).has('preview');

function showAppOnly() {
  document.getElementById('appShell').hidden = true;
  document.getElementById('loginScreen').hidden = true;
  document.getElementById('appOnlyScreen').hidden = false;
  document.title = 'IEUM 앱 다운로드';
}

function showLogin() {
  document.body.dataset.mode = '';
  document.getElementById('appShell').hidden = true;
  document.getElementById('loginScreen').hidden = false;
  document.title = '로그인';
}

// Fill the header and pick the menu for the signed-in account
function enterApp() {
  const mode = session.type;
  document.body.dataset.mode = mode;
  document.querySelectorAll('[data-mode]').forEach(el => { el.hidden = el.dataset.mode !== mode; });
  document.getElementById('companyName').textContent = session.companyName;
  document.getElementById('userAvatar').textContent = session.name[0];
  document.getElementById('userName').textContent = session.name;
  document.getElementById('userSub').textContent = session.sub;
  document.getElementById('brandLink').href = '#' + MODES[mode].home;
  document.getElementById('homeGreeting').textContent = `안녕하세요, ${session.name}님`;
  // roles come with the account; the prototype's role picker starts from them
  userRoles = session.roles.split(' ');
  const picker = document.getElementById('demoRole');
  picker.value = [...picker.options].some(opt => opt.value === session.roles) ? session.roles : 'EMPLOYEE';
  refreshReadOnly();
  renderAttendance();
  renderTrialBar();
  refreshJoinBadge();
  document.getElementById('loginScreen').hidden = true;
  document.getElementById('appShell').hidden = false;
}

// Strip under the header: a company in its trial, a paid company in the last 7 days of its period (there is no
// automatic renewal, so someone has to extend it), or a company whose period is over (read-only).
// The pay button only for roles that can reach 업체 관리 > 이용 결제.
function renderTrialBar() {
  const bar = document.getElementById('trialBar');
  const order = session && session.type === 'EMPLOYEE' ? findCompanyOrder(session.company) : null;
  const st = order ? trialState(order) : null;
  if (!st || (st.status === 'PAID' && st.daysLeft > 7)) { bar.hidden = true; return; }
  const text = document.getElementById('trialText');
  text.replaceChildren();
  const strong = document.createElement('strong');
  const more = document.createElement('span'); // the longer tail; phones show only the bold part
  more.className = 'trial-more';
  const admin = userRoles.includes('ADMIN');
  if (st.status === 'TRIAL') {
    strong.textContent = st.daysLeft === 0 ? `오늘 무료 체험이 끝납니다 (${st.end})` : `무료 체험 중 · ${st.dday} (${st.end} 만료)`;
    more.textContent = st.urgent ? ' · 결제하지 않으면 체험이 끝난 뒤 읽기 전용으로 바뀝니다'
      : ' · 결제하면 그날부터 유료로 이어서 씁니다';
  } else if (st.status === 'PAID') {
    strong.textContent = st.daysLeft === 0 ? `오늘 유료 이용 기간이 끝납니다 (${st.end})` : `유료 이용 만료 ${st.dday} (${st.end})`;
    more.textContent = ' · 자동으로 결제되지 않습니다. 연장하지 않으면 만료 다음 날부터 읽기 전용입니다';
  } else {
    strong.textContent = `${st.paid ? '이용 기간' : '체험'}이 끝나 읽기 전용입니다 (${st.end} 만료)`;
    more.textContent = admin ? ' · 결제하면 바로 이어서 쓸 수 있습니다' : ' · 업체 관리자가 결제하면 바로 이어서 쓸 수 있습니다';
  }
  text.append(strong, more);
  bar.classList.toggle('is-urgent', st.urgent);
  bar.classList.toggle('is-expired', st.status === 'EXPIRED');
  const pay = document.getElementById('trialPay');
  pay.textContent = st.status === 'PAID' ? '연장하기' : '결제하기';
  pay.hidden = !admin;
  bar.hidden = false;
}

// 업체 관리 > 이용 결제 (requirements v1.5 PAY-01·02, SUB-01·03·05). No automatic renewal: the admin pays
// directly, choosing monthly or annual each time (quoteOf in ops.js has the rules and the price).
// - trial, or read-only after it: 첫 결제 with headcount and billing e-mail, starts today
// - paid and running: 연장, adds to the current expiry date; the headcount may change and the new one starts
//   the day after the current period (seatChanges in ops.js), never below the accounts in use
// - paid period over (read-only): 다시 결제, starts today, headcount and e-mail can change
// Steps: 1. 요금제 선택 → 2. 결제 (Toss test payment in the real project, mock here) → 3. 완료.
// In the real project the server rebuilds the quote from the order, checks the approved Toss amount against it
// and applies a payment only once even when the confirm request repeats.
let billStep = 1; // which step is showing; back to 1 whenever the page is opened
let billQuote = null; // the quote made at step 1 → 2, so step 2 pays exactly what was shown

const MODE_TEXT = { FIRST: '첫 결제', EXTEND: '연장', RESTART: '다시 결제' };
const PAY_KIND = { FIRST: '첫 결제', EXTEND: '연장', RESTART: '다시 결제', RENEW: '정기 결제' };

const fillDetail = (id, rows) => document.getElementById(id).replaceChildren(...rows.flatMap(([k, v]) => {
  const dt = document.createElement('dt'); dt.textContent = k;
  const dd = document.createElement('dd'); dd.textContent = v;
  return [dt, dd];
}));
const checkedPlan = () => document.querySelector('input[name="payPlan"]:checked').value;

function renderBilling() {
  applyDueSeats(); // a headcount chosen at an extension whose day has come
  const order = findCompanyOrder(session.company);
  const card = (id, v) => { document.getElementById(id).textContent = v; };
  if (!order) {
    ['billStatus', 'billPlan', 'billEnd', 'billUsersNow'].forEach(id => card(id, '-'));
    fillDetail('billInfo', [['안내', '이 업체의 신청 정보가 없습니다']]);
    document.getElementById('billPay').hidden = true;
    document.getElementById('billHistoryBox').hidden = true;
    return;
  }
  const st = trialState(order);
  const sub = subscriptionOf(order);
  card('billStatus', st.status === 'PAID' ? '이용 중' : st.label);
  card('billPlan', sub ? sub.planLabel : '무료 체험');
  // the D-day sits in the label so the date fits on one line in the card
  card('billEndLabel', `${sub ? '만료일' : '체험 만료일'} · ${st.status === 'EXPIRED' ? '만료됨' : st.dday}`);
  card('billEnd', st.end);
  const change = nextSeatChange(order);
  card('billUsersNow', change ? `${order.users}명 → ${plannedUsers(order)}명` : `${order.users}명`);
  fillDetail('billInfo', [
    ['업체명', order.company],
    ['업체 코드', order.companyCode],
    ['이용 인원', change ? `${order.users}명 (${ymd(change.from)}부터 ${change.users}명)` : `${order.users}명`],
    ['1인 월 이용료', won(PRICE_PER_USER)],
    ['결제 안내 이메일', order.billEmail || order.email || '-'],
    ['체험 기간', `${ymd(order.at)} ~ ${st.trialEnd} (14일)`],
    ...(sub ? [
      ['요금제', sub.planLabel],
      ['이용 기간', `${st.serviceFrom} ~ ${st.end}`],
      ['결제 방식', '자동 결제 없음 · 만료 전에 직접 연장'],
    ] : []),
    ['계정 한도', sub ? `사용 중 ${order.users}명 (구매 인원)` : `관리자 포함 ${TRIAL_SEATS}명 (결제하면 해제)`],
  ]);
  renderPayHistory(order);

  const mode = quoteOf(order, 'MONTHLY', order.users).mode;
  document.getElementById('billPay').hidden = false;
  document.getElementById('billPayTitle').textContent = MODE_TEXT[mode];
  document.getElementById('billHint').textContent = {
    FIRST: st.status === 'EXPIRED' ? '체험이 끝나 읽기 전용입니다. 결제하면 오늘부터 바로 이어서 쓸 수 있습니다'
      : '체험 기간 중 언제든 결제할 수 있고, 결제한 날부터 유료 이용이 시작됩니다. 남은 체험 기간은 이어지지 않습니다',
    EXTEND: `만료일(${st.end}) 다음 날부터 이어지도록 직접 연장합니다. 자동 결제는 없고, 해지·환불은 제공하지 않습니다`,
    RESTART: '이용 기간이 끝나 읽기 전용입니다. 결제하면 오늘부터 다시 시작하고, 계정과 데이터는 그대로입니다',
  }[mode];
  renderBillSteps(order, mode);
}

function renderBillSteps(order, mode) {
  document.querySelectorAll('#billSteps li').forEach(li => {
    const k = Number(li.dataset.step);
    li.className = k === billStep ? 'on' : k < billStep ? 'done-step' : '';
  });
  [1, 2, 3].forEach(n => { document.getElementById(`billStep${n}`).hidden = n !== billStep; });
  if (billStep === 1) {
    // the billing e-mail only when a new period starts today; the headcount every time
    document.getElementById('payEmailBox').hidden = mode === 'EXTEND';
    // filled once per visit, so a re-render (e.g. a change from another tab) doesn't wipe what is being typed
    const users = document.getElementById('payUsers');
    if (users.dataset.for !== order.no) {
      users.value = plannedUsers(order);
      document.getElementById('payEmail').value = order.billEmail || order.email || '';
      users.dataset.for = order.no;
    }
    const min = `최소 ${activeStaff(session.company).length}명(지금 사용 중인 계정 수)`;
    document.getElementById('payUsersHint').textContent = mode === 'EXTEND'
      ? `지금 ${order.users}명 · 바꾼 인원은 새 기간(${ymd(dayAfter(subscriptionOf(order).expire))})부터 적용 · ${min}`
      : min + (mode === 'FIRST' ? ' · 결제하면 체험 중 10명 제한이 풀립니다' : '');
    renderPayPreview(order);
  } else if (billStep === 2) {
    const q = billQuote;
    const failed = (order.payments || []).filter(p => p.result === 'FAILED').length;
    fillDetail('billOrder', [
      ['구분', MODE_TEXT[q.mode]],
      ['요금제', `${PLANS[q.plan].label} (${q.months}개월)`],
      ['이용 인원', q.mode === 'EXTEND' && q.users !== order.users ? `${q.users}명 (${ymd(q.from)}부터)` : `${q.users}명`],
      ['이용 기간', `${ymd(q.from)} ~ ${ymd(q.to)}`],
      ['결제 금액', `${won(q.amount)} (${q.formula})`],
      ['할인', q.pct ? `${q.pct}% · ${q.why}` : q.why],
      ['결제 실패 이력', failed ? `${failed}회` : '없음'],
    ]);
    document.getElementById('billError').textContent = '';
    document.getElementById('billBusy').textContent = '';
  }
}

// step 1: both plans' prices and the chosen plan's amount and period, updated while the headcount is typed
function renderPayPreview(order) {
  const n = Number(document.getElementById('payUsers').value);
  const ok = Number.isInteger(n) && n >= 1 && n <= 500;
  const users = ok ? n : plannedUsers(order);
  const month = quoteOf(order, 'MONTHLY', users);
  const year = quoteOf(order, 'ANNUAL', users);
  document.getElementById('planPriceMONTHLY').textContent = `${won(month.amount)} / 월`;
  document.getElementById('planPriceANNUAL').textContent = `${won(year.amount)} / 년`;
  document.getElementById('planNoteANNUAL').textContent = `12개월 · ${year.pct}% 할인 (${year.why})`;
  const q = checkedPlan() === 'ANNUAL' ? year : month;
  fillDetail('payPreview', [
    ['결제 금액', ok ? `${won(q.amount)} (${q.formula})` : '-'],
    ['이용 기간', `${ymd(q.from)} ~ ${ymd(q.to)}${q.mode === 'EXTEND' ? ' (지금 만료일 다음 날부터 이어서)' : ' (오늘 결제하면)'}`],
  ]);
}
document.getElementById('payUsers').addEventListener('input', () => {
  document.getElementById('payUsersError').textContent = '';
  const order = findCompanyOrder(session.company);
  if (order) renderPayPreview(order);
});
document.querySelectorAll('input[name="payPlan"]').forEach(r => r.addEventListener('change', () => {
  const order = findCompanyOrder(session.company);
  if (order) renderPayPreview(order);
}));
document.getElementById('payEmail').addEventListener('input', () => { document.getElementById('payEmailError').textContent = ''; });

// step 1 → 2: check the headcount (and the e-mail for a new period) and fix the quote to pay
document.getElementById('btnPayNext').addEventListener('click', () => {
  const order = findCompanyOrder(session.company);
  if (!order) return;
  const usersEl = document.getElementById('payUsers');
  const emailEl = document.getElementById('payEmail');
  const n = Number(usersEl.value);
  const accounts = activeStaff(session.company).length;
  const usersError = !Number.isInteger(n) || n < 1 || n > 500 ? '이용 인원은 1~500명으로 입력해 주세요'
    : n < accounts ? `지금 계정이 ${accounts}명이라 ${accounts}명보다 적게 정할 수 없습니다` : '';
  document.getElementById('payUsersError').textContent = usersError;
  if (usersError) return usersEl.focus();
  const quote = quoteOf(order, checkedPlan(), n);
  if (quote.mode !== 'EXTEND') {
    const email = emailEl.value.trim();
    const emailError = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? '' : '이메일 형식을 확인해 주세요';
    document.getElementById('payEmailError').textContent = emailError;
    if (emailError) return emailEl.focus();
    quote.email = email;
  }
  billQuote = quote;
  billStep = 2;
  document.getElementById('billAgree').checked = false; // agreed again for every payment
  renderBilling();
});
document.getElementById('btnPayBack').addEventListener('click', () => { billStep = 1; renderBilling(); });
document.getElementById('btnPayFinish').addEventListener('click', () => {
  billStep = 1;
  renderBilling();
  document.querySelector('.content').scrollTop = 0;
});
// opening the page (or another page) starts again from step 1 with fresh values
window.addEventListener('hashchange', () => {
  billStep = 1;
  billQuote = null;
  delete document.getElementById('payUsers').dataset.for;
});

// Mock payment: buttons lock while "processing" so a double click can't record two payments
function payBill(success) {
  const ok = document.getElementById('btnBillOk');
  const fail = document.getElementById('btnBillFail');
  const error = document.getElementById('billError');
  if (ok.disabled) return;
  if (!document.getElementById('billAgree').checked) {
    error.textContent = '이용 조건에 동의해야 결제할 수 있습니다';
    return;
  }
  error.textContent = '';
  ok.disabled = fail.disabled = true;
  document.getElementById('billBusy').textContent = '결제를 처리하고 있습니다…';
  setTimeout(() => {
    ok.disabled = fail.disabled = false;
    document.getElementById('billBusy').textContent = '';
    const orders = loadStore(ORDERS_KEY, []);
    const order = orders.find(o => o.companyCode === session.company);
    if (!order || !billQuote) return;
    // the state changed in another tab since step 1 (e.g. it paid there): quote again instead of paying
    const now = quoteOf(order, billQuote.plan, billQuote.users);
    if (now.mode !== billQuote.mode || now.from !== billQuote.from || now.amount !== billQuote.amount) {
      billStep = 1;
      renderBilling();
      showToast('결제 상태가 바뀌어 금액을 다시 계산했습니다. 확인하고 다시 진행해 주세요');
      return;
    }
    const q = billQuote;
    const at = new Date().toISOString();
    order.payments = order.payments || [];
    order.payments.push({ at, kind: q.mode, plan: q.plan, months: q.months, pct: q.pct, users: q.users, amount: q.amount,
      from: q.from, to: q.to, result: success ? 'OK' : 'FAILED' });
    if (success) {
      if (q.mode === 'EXTEND') {
        order.subMonths += q.months;
        if (q.users !== plannedUsers(order)) {
          order.seatChanges = [...(order.seatChanges || []), { from: q.from, users: q.users }];
        }
      } else {
        // first payment or after the paid period ended: a new run from today, with the headcount chosen at step 1
        order.status = 'PAID';
        order.paidAt = order.paidAt || at;
        order.serviceFrom = q.from;
        order.subAnchorAt = q.from;
        order.subMonths = q.months;
        order.users = q.users;
        order.total = q.users * PRICE_PER_USER;
        // the billing address is kept apart: order.email stays the verified signup address (password reset)
        order.billEmail = q.email || order.billEmail;
        delete order.seatChanges;
      }
      if (q.mode !== 'EXTEND') order.plan = q.plan; // an extension's plan shows once its period starts
    }
    saveStore(ORDERS_KEY, orders);
    if (success) {
      billStep = 3;
      showPayDone(order, q);
    }
    refreshReadOnly(); // paying ends read-only mode: everything can be used again
    renderBilling();
    renderTrialBar();
    if (!success) error.textContent = '모의 결제에 실패했습니다. 결제는 반영되지 않았고, 다시 시도할 수 있습니다.';
  }, 800);
}
document.getElementById('btnBillOk').addEventListener('click', () => payBill(true));
document.getElementById('btnBillFail').addEventListener('click', () => payBill(false));

function showPayDone(order, q) {
  document.getElementById('payDoneText').textContent = {
    FIRST: `유료 이용 신청이 끝났습니다. ${ymd(q.from)}부터 ${ymd(q.to)}까지 ${PLANS[q.plan].label} 요금제로 이어서 쓰고, ` +
      `체험 중 ${TRIAL_SEATS}명이던 계정 한도도 풀렸습니다. 계정과 데이터는 그대로입니다.`,
    EXTEND: `연장이 끝났습니다. 만료일이 ${ymd(q.to)}로 늘었습니다.` +
      (q.users !== order.users ? ` 이용 인원은 ${ymd(q.from)}부터 ${q.users}명입니다.` : ''),
    RESTART: `다시 결제했습니다. 오늘부터 ${ymd(q.to)}까지 쓸 수 있고, 계정과 데이터는 그대로입니다.`,
  }[q.mode] + ' 자동으로 결제되지 않으니 만료 전에 직접 연장해 주세요.';
  fillDetail('payDone', [
    ['구분', MODE_TEXT[q.mode]],
    ['요금제', PLANS[q.plan].label],
    ['결제 금액', `${won(q.amount)} (${q.formula})`],
    ['이용 기간', `${ymd(q.from)} ~ ${ymd(q.to)}`],
    ['만료일', ymd(addMonths(order.subAnchorAt, order.subMonths))],
  ]);
}

// Payment history, newest first
function renderPayHistory(order) {
  const pays = order.payments || [];
  document.getElementById('billHistoryBox').hidden = !pays.length;
  document.getElementById('billHistory').replaceChildren(...pays.slice().reverse().map(p => {
    const li = document.createElement('li');
    const what = document.createElement('span');
    const plan = p.plan ? ` ${PLANS[p.plan].label}` : '';
    what.textContent = `${ymd(p.at)} · ${PAY_KIND[p.kind] || '첫 결제'}${plan} · ${won(p.amount)}${p.users ? ` (${p.users}명)` : ''}` +
      (p.result === 'OK' && p.from && p.to ? ` · ${ymd(p.from)} ~ ${ymd(p.to)}` : '');
    const result = document.createElement('span');
    result.className = `status status-${p.result === 'OK' ? 'paid' : 'failed'}`;
    result.textContent = p.result === 'OK' ? '성공' : '실패';
    li.append(what, result);
    return li;
  }));
}

// data-fill="업체코드 사번 비밀번호"
document.querySelectorAll('[data-fill]').forEach(b => b.addEventListener('click', () => {
  const [company, id, pw] = b.dataset.fill.split(' ');
  document.getElementById('loginCompany').value = company;
  document.getElementById('loginId').value = id;
  document.getElementById('loginPw').value = pw;
  document.getElementById('loginError').textContent = '';
}));

// A company whose trial ended unpaid, or whose paid period ended without an extension, is read-only
// (requirements v1.5 DEM-03·SUB-04): everyone still signs in and sees everything there is, but nothing can be
// added or changed until the admin pays; paying lets them carry on with the same accounts and data. Screens mark the actions that write with data-write. In the real project the server
// refuses every write from such a company (POST/PUT/DELETE, chat messages) except the payment itself;
// the screen only greys the buttons out and says why.
function isReadOnly(company) {
  const order = findCompanyOrder(company);
  return !!order && trialState(order).status === 'EXPIRED';
}

// Re-check after sign-in, a payment, or a change from another tab
function refreshReadOnly() {
  readOnly = !!session && session.type === 'EMPLOYEE' && isReadOnly(session.company);
  document.body.classList.toggle('is-readonly', readOnly);
  document.querySelectorAll('[data-write]').forEach(el => {
    if (readOnly) el.setAttribute('aria-disabled', 'true'); else el.removeAttribute('aria-disabled');
  });
  applyRoles();
}

const readOnlyMessage = () => userRoles.includes('ADMIN')
  ? '이용 기간이 끝나 읽기 전용입니다. 이용 결제에서 결제하면 바로 이어서 쓸 수 있습니다.'
  : '이용 기간이 끝나 읽기 전용입니다. 업체 관리자가 결제하면 바로 이어서 쓸 수 있습니다.';

// Any write action while read-only: stop it before its own handler runs and say why
document.addEventListener('click', e => {
  if (!readOnly || !e.target.closest('[data-write]')) return;
  e.preventDefault();
  e.stopImmediatePropagation();
  showToast(readOnlyMessage());
}, true);

function signOut(message) {
  session = null;
  readOnly = false;
  document.body.classList.remove('is-readonly');
  resetEmployeePage();
  try { sessionStorage.removeItem(SESSION_KEY); } catch { /* nothing to remove */ }
  setMenu(false);
  history.replaceState(null, '', location.pathname + location.search);
  document.getElementById('loginError').textContent = message || '';
  document.getElementById('loginError').classList.remove('is-ok');
  showLogin();
}

document.getElementById('loginForm').addEventListener('submit', e => {
  e.preventDefault();
  const company = document.getElementById('loginCompany').value.trim().toLowerCase();
  const id = document.getElementById('loginId').value.trim().toLowerCase();
  const pw = document.getElementById('loginPw').value;
  const error = document.getElementById('loginError');
  if (!company || !id || !pw) {
    error.textContent = '업체 코드, 사번, 비밀번호를 모두 입력해 주세요';
    return;
  }
  let acc = DEMO_ACCOUNTS.find(a => a.company === company && a.id === id && a.pw === pw) || findTrialAccount(company, id, pw);
  if (!acc) {
    // an employee who joined through 계정 만들기 (hr.js): signs in once approved; otherwise says why not
    const staff = staffSignIn(company, id, pw);
    if (staff && staff.error) { error.textContent = staff.error; return; }
    acc = staff && staff.account;
  }
  if (!acc) {
    error.textContent = '업체 코드, 사번 또는 비밀번호가 맞지 않습니다'; // never say which part was wrong
    return;
  }
  // the company hasn't verified its signup e-mail yet (requirements v1.4 ACC-06): no sign-in until it does.
  // The prototype links back to the verification step; the real mail link does the same job
  const order = acc.type === 'EMPLOYEE' ? findCompanyOrder(acc.company) : null;
  if (order && order.emailVerified === false) {
    const link = document.createElement('a');
    link.href = `trial.html?verify=${order.companyCode}`;
    link.textContent = '인증하러 가기';
    error.replaceChildren('신청할 때 적은 이메일을 아직 인증하지 않았습니다. 인증 메일의 링크를 누른 뒤 로그인해 주세요. ', link);
    return;
  }
  // an account the company deactivated (사원 관리) can't sign in; the real server checks the same at login
  if (acc.type === 'EMPLOYEE' && staffOf(acc.company).some(e => e.empNo === acc.id && e.status === 'RETIRED')) {
    error.textContent = '사용이 중지된 계정입니다. 업체 관리자에게 문의해 주세요';
    return;
  }
  error.textContent = '';
  document.getElementById('loginPw').value = '';
  const { pw: _, ...info } = acc;
  session = info;
  try { sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)); } catch { /* storage blocked: signed in for this page only */ }
  enterApp();
  history.replaceState(null, '', '#' + MODES[session.type].home);
  showView();
  showPaymentReminder();
});

// Payment reminder: the admin gets a pop-up on D-7, D-3, D-1 and the last day of the trial, and of a paid
// period too (no automatic renewal, so it has to extend by hand), once per day. Once the trial or the paid
// period is over (read-only) everyone gets one a day (decided 2026-10-28): the admin with a way to pay,
// employees with "ask your admin" and only a 확인 button. Once per person per day. The real server also
// sends the reminders as notifications (bell) and e-mail on the same days.
function showPaymentReminder() {
  if (!session || session.type !== 'EMPLOYEE') return;
  const admin = userRoles.includes('ADMIN');
  const order = findCompanyOrder(session.company);
  const st = order && trialState(order);
  if (!st || (st.status !== 'EXPIRED' && (!admin || !REMIND_DAYS.includes(st.daysLeft)))) return;
  const key = `ieum-demo-reminded-${session.company}-${session.id}`;
  const today = ymd(Date.now());
  if (loadStore(key, '') === today) return;
  saveStore(key, today);

  let dialog = document.getElementById('payReminder');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'payReminder';
    dialog.className = 'modal';
    dialog.innerHTML = '<h2></h2><p></p><div class="modal-actions"><button type="button" class="btn" value="later">나중에</button><button type="button" class="btn btn-primary" value="pay">결제하러 가기</button></div>';
    dialog.querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
      dialog.close();
      if (b.value === 'pay') location.hash = 'admin-billing';
    }));
    document.body.append(dialog);
  }
  // employees can't pay: one 확인 button instead of 나중에 / 결제하러 가기
  dialog.querySelector('[value="later"]').textContent = admin ? '나중에' : '확인';
  dialog.querySelector('[value="pay"]').hidden = !admin;
  dialog.querySelector('[value="pay"]').textContent = st.status === 'PAID' ? '연장하러 가기' : '결제하러 가기';
  if (st.status === 'EXPIRED') {
    const what = st.paid ? '유료 이용 기간' : '무료 체험';
    dialog.querySelector('h2').textContent = `${st.paid ? '이용 기간' : '체험'}이 끝나 읽기 전용입니다`;
    dialog.querySelector('p').textContent = `${what}이 ${st.end}에 끝나, 지금은 지난 내용을 보기만 할 수 있습니다. ` + (admin
      ? '결제하면 오늘부터 바로 이어서 쓸 수 있고, 계정과 데이터는 그대로입니다.'
      : '업체 관리자에게 결제를 요청해 주세요. 결제하면 계정과 데이터 그대로 바로 이어서 쓸 수 있습니다.');
  } else if (st.status === 'PAID') {
    dialog.querySelector('h2').textContent = st.daysLeft === 0 ? '오늘 유료 이용 기간이 끝납니다' : `유료 이용 기간이 ${st.daysLeft}일 남았습니다`;
    dialog.querySelector('p').textContent = `이용 기간은 ${st.end}까지이고, 자동으로 결제되지 않습니다. 그 전에 연장하지 않으면 ` +
      '다음 날부터 읽기 전용으로 바뀝니다. 연장하면 지금 만료일부터 이어집니다.';
  } else {
    dialog.querySelector('h2').textContent = st.daysLeft === 0 ? '오늘 무료 체험이 끝납니다' : `무료 체험이 ${st.daysLeft}일 남았습니다`;
    dialog.querySelector('p').textContent = `체험은 ${st.end}에 끝나고, 그때까지 결제하지 않으면 읽기 전용으로 바뀌어 새로 쓰거나 바꿀 수 없습니다. ` +
      '결제하면 그날부터 유료 이용이 시작되고, 계정과 데이터는 그대로 이어집니다.';
  }
  if (!dialog.open) dialog.showModal();
}

document.getElementById('btnLogout').addEventListener('click', () => signOut());

function showView() {
  if (!inApp) return showAppOnly();
  if (!session) return showLogin();
  // an employee deactivated while signed in is sent back to the login screen with the reason (hr.js)
  const blocked = staffBlockedMessage();
  if (blocked) return signOut(blocked);
  const { views } = MODES[session.type];
  const home = MODES[session.type].home;
  let key = location.hash.slice(1);
  const link = document.querySelector(`.nav-item[data-view="${key}"]`);
  // not a screen of this account type, or no access with the current roles: back to its first screen
  // a write page opened by its address while read-only: back home with the reason
  const writeBlocked = readOnly && link && link.hasAttribute('data-write');
  if (writeBlocked) showToast(readOnlyMessage());
  if (!views[key] || (link && link.hidden) || writeBlocked) {
    key = home;
    history.replaceState(null, '', '#' + home);
  }
  document.querySelectorAll('.nav-item').forEach(a => a.classList.toggle('on', a.dataset.view === key));
  document.querySelectorAll('.view').forEach(v => v.classList.remove('on'));

  const built = document.getElementById('view-' + key);
  if (built) {
    built.classList.add('on');
  } else {
    const current = document.createElement('span');
    current.className = 'crumb-current';
    current.textContent = views[key].title;
    document.getElementById('placeholderCrumb').replaceChildren(views[key].crumb + ' > ', current);
    document.getElementById('placeholderTitle').textContent = views[key].title;
    document.getElementById('view-placeholder').classList.add('on');
  }
  if (session.type === 'OPS') opsViewShown(key);
  if (key === 'admin-billing') renderBilling();
  if (key === 'hr-employee') renderEmployees();
  if (key === 'hr-join') renderJoin();
  document.querySelector('.content').scrollTop = 0;
  // The installed app window already prefixes the app name ("IEUM - 홈"), so the page title is just the view
  document.title = views[key].title;
}

// Today's attendance for the signed-in user, recorded by the fingerprint reader simulator under the
// account's reader user number (deviceUserId; no built-in account has one since the sample companies were
// removed on 2026-10-28). In the real JSP these values come from the server.
const ATTEND_LABEL = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴' };

function renderAttendance() {
  let rec = null;
  try {
    const d = new Date();
    const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const mine = session && session.deviceUserId;
    rec = mine ? (JSON.parse(localStorage.getItem('ieum-demo-punches')) || {})[day]?.[mine] || null : null;
  } catch { /* storage blocked: show "no record" */ }

  const header = document.getElementById('attendHeader');
  const card = document.getElementById('attendCard');
  if (!rec) {
    header.textContent = '출근 기록 없음';
    card.textContent = '기록 없음';
    return;
  }
  const mark = status => (status && status !== 'NORMAL' ? `(${ATTEND_LABEL[status]})` : '');
  header.textContent = rec.out ? `출근 ${rec.in} · 퇴근 ${rec.out}` : `오늘 출근 ${rec.in}`;
  card.textContent = `${rec.in} 출근${mark(rec.inStatus)}` + (rec.out ? ` · ${rec.out} 퇴근${mark(rec.outStatus)}` : '');
}

// the simulator runs in another tab; localStorage changes there fire this event here. A payment or a
// new signup in another tab changes the trial strip too
window.addEventListener('storage', e => {
  if (e.key === 'ieum-demo-punches') renderAttendance();
  if (e.key === EMP_KEY && session && session.type === 'EMPLOYEE') rerenderStaffViews();
  // company accounts only (ops.js refreshes the operator screens). Redraw what depends on the order in place;
  // re-enter the page only when read-only mode flipped, which may have to move the user off a write page
  if (e.key === ORDERS_KEY && session && session.type === 'EMPLOYEE') {
    const wasReadOnly = readOnly;
    refreshReadOnly();
    renderTrialBar();
    const key = location.hash.slice(1);
    if (key === 'admin-billing') renderBilling();
    if (key === 'hr-employee') renderEmployees();
    if (readOnly !== wasReadOnly) showView();
  }
});

document.getElementById('demoRole').addEventListener('change', e => {
  userRoles = e.target.value.split(' ');
  applyRoles();
  renderTrialBar();
  refreshJoinBadge();
  showView();
});

applyRoles();
window.addEventListener('hashchange', showView);
if (inApp && session) enterApp();
showView();

// Narrow screens: hamburger opens the sidebar as a drawer
const btnMenu = document.getElementById('btnMenu');

function setMenu(open) {
  document.body.classList.toggle('menu-open', open);
  btnMenu.setAttribute('aria-expanded', open);
  btnMenu.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
}

btnMenu.addEventListener('click', () => setMenu(!document.body.classList.contains('menu-open')));
document.getElementById('backdrop').addEventListener('click', () => setMenu(false));
document.addEventListener('keydown', e => { if (e.key === 'Escape') setMenu(false); });
window.addEventListener('hashchange', () => setMenu(false));
// Widening the window past the breakpoint shows the sidebar normally, so drop the drawer state
matchMedia('(max-width: 900px)').addEventListener('change', e => { if (!e.matches) setMenu(false); });

