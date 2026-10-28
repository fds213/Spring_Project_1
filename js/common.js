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

// Trial strip under the header. Shown to a company still in its trial (or past it without paying);
// the pay button only for roles that can reach 업체 관리 > 이용 결제.
function renderTrialBar() {
  const bar = document.getElementById('trialBar');
  const order = session && session.type === 'EMPLOYEE' ? findCompanyOrder(session.company) : null;
  const st = order ? trialState(order) : null;
  if (!st || st.status === 'PAID') { bar.hidden = true; return; }
  const text = document.getElementById('trialText');
  text.replaceChildren();
  const strong = document.createElement('strong');
  const more = document.createElement('span'); // the longer tail; phones show only the bold part
  more.className = 'trial-more';
  if (st.status === 'TRIAL') {
    strong.textContent = st.daysLeft === 0 ? `오늘 무료 체험이 끝납니다 (${st.end})` : `무료 체험 중 · ${st.dday} (${st.end} 만료)`;
    more.textContent = st.urgent ? ' · 결제하지 않으면 체험이 끝난 뒤 읽기 전용으로 바뀝니다'
      : ' · 결제하면 그날부터 유료로 이어서 씁니다';
  } else {
    strong.textContent = `체험이 끝나 읽기 전용입니다 (${st.end} 만료)`;
    more.textContent = userRoles.includes('ADMIN') ? ' · 결제하면 바로 이어서 쓸 수 있습니다' : ' · 업체 관리자가 결제하면 바로 이어서 쓸 수 있습니다';
  }
  text.append(strong, more);
  bar.classList.toggle('is-urgent', st.urgent);
  bar.classList.toggle('is-expired', st.status === 'EXPIRED');
  document.getElementById('trialPay').hidden = !userRoles.includes('ADMIN');
  bar.hidden = false;
}

// 업체 관리 > 이용 결제: the company's trial and the mock payment. Before the first payment it runs in three
// steps: 1. 정보 변경 (headcount and billing e-mail) → 2. 결제 → 3. 신청 완료. Paid service starts the day
// after the trial ends, whenever during the trial the admin pays. After that the company pays monthly on the
// next payment date and the admin may change the headcount for the next payment (billingState in ops.js).
// In the real project the server recalculates the amount from the headcount and records each payment once
// (PAY-01·02).
let billStep = 1; // which step of the first payment is showing; back to 1 whenever the page is opened

const fillDetail = (id, rows) => document.getElementById(id).replaceChildren(...rows.flatMap(([k, v]) => {
  const dt = document.createElement('dt'); dt.textContent = k;
  const dd = document.createElement('dd'); dd.textContent = v;
  return [dt, dd];
}));

function renderBilling() {
  const order = findCompanyOrder(session.company);
  const fill = fillDetail;
  const card = (id, v) => { document.getElementById(id).textContent = v; };
  const paidCards = !!order && trialState(order).status === 'PAID';
  card('billEndLabel', paidCards ? '이용 인원' : '체험 만료일');
  card('billFromLabel', paidCards ? '다음 결제일' : '유료 이용 시작일');
  if (!order) {
    ['billStatus', 'billEnd', 'billTotal', 'billFrom'].forEach(id => card(id, '-'));
    fill('billInfo', [['안내', '이 업체의 신청 정보가 없습니다']]);
    document.getElementById('billPay').hidden = true;
    document.getElementById('billDone').hidden = true;
    return;
  }
  const st = trialState(order);
  const paid = st.status === 'PAID';
  const bill = paid ? billingState(order) : null;
  card('billStatus', paid ? '이용 중' : st.label);
  card('billEnd', paid ? `${order.users}명` : st.end);
  card('billTotal', won(order.total));
  card('billFrom', paid ? bill.nextPay : '결제한 날');
  fill('billInfo', [
    ['업체명', order.company],
    ['업체 코드', order.companyCode],
    ['이용 인원', `${order.users}명`],
    ['1인 월 이용료', won(4000)],
    ['월 이용료', won(order.total)],
    ['결제 안내 이메일', order.email || '-'],
    ['체험 기간', `${ymd(order.at)} ~ ${st.end} (14일)`],
    ...(paid ? [['유료 이용 시작일', st.serviceFrom], ['이번 이용 기간', bill.period]] : []),
    ['계정 한도', paid ? '제한 없음' : `관리자 포함 ${TRIAL_SEATS}명 (결제하면 해제)`],
  ]);
  // the completion step stays on screen after paying until the admin presses 확인
  if (!paid && billStep === 3) billStep = 1;
  const steps = !paid || billStep === 3;
  document.getElementById('billPay').hidden = !steps;
  document.getElementById('billDone').hidden = steps;
  document.getElementById('billHint').textContent = paid ? '매달 다음 결제일에 결제됩니다. 해지·환불은 제공하지 않습니다' : st.status === 'EXPIRED'
    ? '체험이 끝나 읽기 전용입니다. 결제하면 오늘부터 바로 이어서 쓸 수 있습니다' : '체험 기간 중 언제든 결제할 수 있고, 결제한 날부터 유료 이용이 시작됩니다. 남은 체험 기간은 이어지지 않습니다';
  if (!steps) {
    fill('billReceipt', [
      ['다음 결제일', bill.nextPay],
      ['다음 결제 금액', `${won(bill.nextAmount)} (${bill.nextUsers}명 × ${won(4000)})`],
      ['지금 이용 인원', bill.changing ? `${order.users}명 → 다음 결제부터 ${bill.nextUsers}명` : `${order.users}명`],
    ]);
    document.getElementById('billDoneText').textContent = `${bill.nextPay}에 ${won(bill.nextAmount)}이 결제되고, 그 뒤로도 매달 같은 날 결제됩니다. ` +
      '실제로는 저장한 결제 수단으로 자동 결제되며, 결제 수단은 확정 전입니다.';
    const users = document.getElementById('billUsers');
    if (document.activeElement !== users) users.value = bill.nextUsers;
    document.getElementById('billUsersHint').textContent =
      `다음 결제일(${bill.nextPay})부터 적용 · 최소 ${activeStaff(session.company).length}명(지금 사용 중인 계정 수)`;
    renderPayHistory(order);
    return;
  }
  renderBillSteps(order, st);
}

function renderBillSteps(order, st) {
  document.querySelectorAll('#billSteps li').forEach(li => {
    const k = Number(li.dataset.step);
    li.className = k === billStep ? 'on' : k < billStep ? 'done-step' : '';
  });
  [1, 2, 3].forEach(n => { document.getElementById(`billStep${n}`).hidden = n !== billStep; });
  if (billStep === 1) {
    // filled once per visit, so a re-render (e.g. a change from another tab) doesn't wipe what is being typed
    const users = document.getElementById('payUsers');
    if (users.dataset.for !== order.no) {
      users.value = order.users;
      document.getElementById('payEmail').value = order.email || '';
      users.dataset.for = order.no;
    }
    document.getElementById('payUsersHint').textContent =
      `최소 ${activeStaff(session.company).length}명(지금 사용 중인 계정 수) · 결제하면 체험 중 10명 제한이 풀립니다`;
    renderPayPreview(st);
  } else if (billStep === 2) {
    const failed = (order.payments || []).filter(p => p.result === 'FAILED').length;
    fillDetail('billOrder', [
      ['신청번호', order.no],
      ['이용 인원', `${order.users}명`],
      ['결제 금액', `${won(order.total)} (첫 달)`],
      ['유료 이용 시작일', `${st.serviceFrom} (결제한 날)`],
      ['결제 이력', failed ? `실패 ${failed}회` : '없음'],
    ]);
    document.getElementById('billError').textContent = '';
    document.getElementById('billBusy').textContent = '';
  } else {
    const bill = billingState(order);
    document.getElementById('payDoneText').textContent = `유료 이용 신청이 끝났습니다. ${st.serviceFrom}부터 월 ${won(order.total)}으로 ` +
      `이어서 쓰고, 체험 중 ${TRIAL_SEATS}명이던 계정 한도도 풀렸습니다. 계정과 데이터는 그대로입니다.`;
    fillDetail('payDone', [
      ['신청번호', order.no],
      ['결제일', ymd(order.paidAt)],
      ['결제 금액', `${won(order.total)} (${order.users}명)`],
      ['유료 이용 시작일', st.serviceFrom],
      ['다음 결제일', bill.nextPay],
    ]);
  }
}

// step 1: what the first payment will be, updated while the headcount is typed
function renderPayPreview(st) {
  const n = Number(document.getElementById('payUsers').value);
  const ok = Number.isInteger(n) && n >= 1 && n <= 500;
  fillDetail('payPreview', [
    ['월 이용료', ok ? `${won(n * 4000)} (${n}명 × ${won(4000)})` : '-'],
    ['유료 이용 시작일', `결제한 날 (오늘 결제하면 ${st.serviceFrom})`],
  ]);
}
document.getElementById('payUsers').addEventListener('input', () => {
  document.getElementById('payUsersError').textContent = '';
  const order = findCompanyOrder(session.company);
  if (order) renderPayPreview(trialState(order));
});
document.getElementById('payEmail').addEventListener('input', () => { document.getElementById('payEmailError').textContent = ''; });

// step 1 → 2: save the headcount and e-mail on the application, so the payment uses them
document.getElementById('btnPayNext').addEventListener('click', () => {
  const usersEl = document.getElementById('payUsers');
  const emailEl = document.getElementById('payEmail');
  const n = Number(usersEl.value);
  const email = emailEl.value.trim();
  const accounts = activeStaff(session.company).length;
  const usersError = !Number.isInteger(n) || n < 1 || n > 500 ? '이용 인원은 1~500명으로 입력해 주세요'
    : n < accounts ? `지금 계정이 ${accounts}명이라 ${accounts}명보다 적게 정할 수 없습니다` : '';
  const emailError = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? '' : '이메일 형식을 확인해 주세요';
  document.getElementById('payUsersError').textContent = usersError;
  document.getElementById('payEmailError').textContent = emailError;
  if (usersError) return usersEl.focus();
  if (emailError) return emailEl.focus();
  const orders = loadStore(ORDERS_KEY, []);
  const order = orders.find(o => o.companyCode === session.company);
  if (order && order.status !== 'PAID') {
    order.users = n;
    order.total = n * 4000;
    order.email = email;
    saveStore(ORDERS_KEY, orders);
    billStep = 2;
  }
  renderBilling();
});
document.getElementById('btnPayBack').addEventListener('click', () => { billStep = 1; renderBilling(); });
document.getElementById('btnPayFinish').addEventListener('click', () => {
  billStep = 1;
  renderBilling();
  document.querySelector('.content').scrollTop = 0;
});
// opening the page (or another page) starts the first payment again from step 1 with fresh values
window.addEventListener('hashchange', () => {
  billStep = 1;
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
    if (!order) return;
    order.payments = order.payments || [];
    const now = new Date().toISOString();
    if (order.status === 'PAID') { // already paid in another tab: record nothing, just show the receipt
      renderBilling();
      renderTrialBar();
      return;
    }
    if (success) {
      // during the trial: from the day after it ends; already read-only: from today
      order.serviceFrom = trialState(order).serviceFromIso;
      order.status = 'PAID';
      order.paidAt = now;
      order.nextPayAt = addMonths(order.serviceFrom, 1).toISOString();
      billStep = 3; // show the completion step
    }
    order.payments.push({ at: now, kind: 'FIRST', result: success ? 'OK' : 'FAILED', amount: order.total, users: order.users });
    saveStore(ORDERS_KEY, orders);
    refreshReadOnly(); // paying ends read-only mode: everything can be used again
    renderBilling();
    renderTrialBar();
    if (!success) error.textContent = '모의 결제에 실패했습니다. 다시 시도해 주세요.';
  }, 800);
}
document.getElementById('btnBillOk').addEventListener('click', () => payBill(true));
document.getElementById('btnBillFail').addEventListener('click', () => payBill(false));

// Payment history, newest first: the first payment, then one monthly payment per month
const PAY_KIND = { FIRST: '첫 결제', RENEW: '정기 결제' };
function renderPayHistory(order) {
  const list = document.getElementById('billHistory');
  list.replaceChildren(...(order.payments || []).slice().reverse().map(p => {
    const li = document.createElement('li');
    const what = document.createElement('span');
    what.textContent = `${ymd(p.at)} · ${PAY_KIND[p.kind] || '첫 결제'} · ${won(p.amount)}${p.users ? ` (${p.users}명)` : ''}`;
    const result = document.createElement('span');
    result.className = `status status-${p.result === 'OK' ? 'paid' : 'failed'}`;
    result.textContent = p.result === 'OK' ? '성공' : '실패';
    li.append(what, result);
    return li;
  }));
}

// The order of the signed-in company, read fresh so a change from another tab isn't overwritten
function withOrder(change) {
  const orders = loadStore(ORDERS_KEY, []);
  const order = orders.find(o => o.companyCode === session.company);
  if (!order || order.status !== 'PAID') return null;
  change(order);
  saveStore(ORDERS_KEY, orders);
  return order;
}

// Headcount change: takes effect from the next payment, never below the accounts the company already has
document.getElementById('btnBillUsers').addEventListener('click', () => {
  const v = Number(document.getElementById('billUsers').value);
  const accounts = activeStaff(session.company).length;
  const error = document.getElementById('billUsersError');
  error.textContent = !Number.isInteger(v) || v < 1 || v > 500 ? '이용 인원은 1~500명으로 입력해 주세요'
    : v < accounts ? `지금 계정이 ${accounts}명이라 ${accounts}명보다 적게 줄일 수 없습니다` : '';
  if (error.textContent) return;
  const order = withOrder(o => { if (v === o.users) delete o.nextUsers; else o.nextUsers = v; });
  if (!order) return;
  renderBilling();
  showToast(v === order.users ? '이용 인원 변경을 취소했습니다' : `다음 결제일부터 ${v}명, 월 ${won(v * 4000)}으로 바뀝니다`);
});

// Monthly renewal (prototype: pretend the next payment date has come). Success moves the next payment
// date one month on and applies a pending headcount change; failure is only recorded, since what happens
// after a failed renewal is still to be decided. Buttons lock while "processing" like the first payment.
function renewBill(success) {
  const ok = document.getElementById('btnRenewOk');
  const fail = document.getElementById('btnRenewFail');
  if (ok.disabled) return;
  const error = document.getElementById('billRenewError');
  const busy = document.getElementById('billRenewBusy');
  error.textContent = '';
  ok.disabled = fail.disabled = true;
  busy.textContent = '정기 결제를 처리하고 있습니다…';
  setTimeout(() => {
    ok.disabled = fail.disabled = false;
    busy.textContent = '';
    withOrder(o => {
      const bill = billingState(o);
      o.payments = o.payments || [];
      // recorded on the payment date it stands for, so the history reads like the real one
      o.payments.push({ at: bill.nextPayIso, kind: 'RENEW', result: success ? 'OK' : 'FAILED', amount: bill.nextAmount, users: bill.nextUsers });
      if (!success) return;
      o.users = bill.nextUsers;
      o.total = o.users * 4000;
      delete o.nextUsers;
      o.nextPayAt = addMonths(bill.nextPayIso, 1).toISOString();
    });
    renderBilling();
    if (!success) error.textContent = '정기 결제에 실패했습니다. 실패한 뒤 어떻게 할지(재시도, 읽기 전용 전환 등)는 확정 전입니다.';
  }, 800);
}
document.getElementById('btnRenewOk').addEventListener('click', () => renewBill(true));
document.getElementById('btnRenewFail').addEventListener('click', () => renewBill(false));

// data-fill="업체코드 사번 비밀번호"
document.querySelectorAll('[data-fill]').forEach(b => b.addEventListener('click', () => {
  const [company, id, pw] = b.dataset.fill.split(' ');
  document.getElementById('loginCompany').value = company;
  document.getElementById('loginId').value = id;
  document.getElementById('loginPw').value = pw;
  document.getElementById('loginError').textContent = '';
}));

// A company whose trial ended unpaid is read-only: everyone still signs in and sees everything there is,
// but nothing can be added or changed until the admin pays; paying lets them carry on with the same
// accounts and data. Screens mark the actions that write with data-write. In the real project the server
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
  ? '체험이 끝나 읽기 전용입니다. 이용 결제에서 결제하면 바로 이어서 쓸 수 있습니다.'
  : '체험이 끝나 읽기 전용입니다. 업체 관리자가 결제하면 바로 이어서 쓸 수 있습니다.';

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

// Payment reminder: an admin of a company still in its trial gets a pop-up on D-7, D-3, D-1 and the last
// day, once per day. Once the trial ended unpaid (read-only) everyone gets one a day (decided 2026-10-28):
// the admin with a way to pay, employees with "ask your admin" and only a 확인 button. Once per person per
// day. The real server also sends the trial ones as notifications (bell) and e-mail on the same days.
function showPaymentReminder() {
  if (!session || session.type !== 'EMPLOYEE') return;
  const admin = userRoles.includes('ADMIN');
  const order = findCompanyOrder(session.company);
  const st = order && trialState(order);
  if (!st || st.status === 'PAID' || (st.status === 'TRIAL' && (!admin || !REMIND_DAYS.includes(st.daysLeft)))) return;
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
  if (st.status === 'EXPIRED') {
    dialog.querySelector('h2').textContent = '체험이 끝나 읽기 전용입니다';
    dialog.querySelector('p').textContent = `무료 체험이 ${st.end}에 끝나, 지금은 지난 내용을 보기만 할 수 있습니다. ` + (admin
      ? '결제하면 오늘부터 바로 이어서 쓸 수 있고, 계정과 데이터는 그대로입니다.'
      : '업체 관리자에게 결제를 요청해 주세요. 결제하면 계정과 데이터 그대로 바로 이어서 쓸 수 있습니다.');
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

