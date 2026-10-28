// IEUM operator screens (shown when an 이음 운영자 account signs in). common.js does the routing and
// the menu drawer for both kinds of account; this file holds the operator views and their data.
// The first menu item is the landing page.
const OPS_VIEWS = {
  'ops-company': { crumb: '업체', title: '업체 관리' },
  'ops-account-company': { crumb: '업체', title: '업체 계정' },
  'ops-application-status': { crumb: '신청', title: '신청 현황' },
  'ops-inquiry-guest': { crumb: '장애/문의', title: '비회원 접수' },
  'ops-inquiry-company': { crumb: '장애/문의', title: '회원 접수' },
  'ops-stats': { crumb: '운영', title: '운영 데이터' },
};

// Applications = free-trial signups from the intro site (trial.js saves them in localStorage). One row per
// company: status TRIAL while the 14-day trial runs, PAID once the admin pays inside the groupware (any time
// during the trial). A trial past its end date without payment is EXPIRED and the company turns read-only:
// employees can't sign in, the admin can sign in only to pay (team decision 2026-10-27).
// In the real project the server keeps them and the badge comes from GET /badges every 15 seconds.
const ORDERS_KEY = 'ieum-demo-orders';
const READ_KEY = 'ieum-demo-orders-read';
const SEEDED_KEY = 'ieum-demo-orders-seeded-v7'; // v7: sample companies removed, all stored companies cleared
const DAY_MS = 86400000;

function loadStore(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
}
function saveStore(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage blocked: the demo still runs for this page */ }
}

// The old sample companies were removed 2026-10-28 at the user's request. Companies now come from a trial signup,
// plus the two built-in demo companies below (demoCompanies). Opening this version once clears the companies an earlier version
// stored in this browser (samples and test signups alike), so everyone starts from the same state.
// Only a browser that ran an older version (an older seeded-v* mark) has stale companies to clear. A fresh
// browser may already hold a company signed up on trial.html, which doesn't load this file: clearing then
// would delete the signup the person is about to sign in with, so it is kept.
if (!loadStore(SEEDED_KEY, false)) {
  let stale = false;
  try { stale = Object.keys(localStorage).some(k => k.startsWith('ieum-demo-orders-seeded-') && k !== SEEDED_KEY); } catch { /* storage blocked */ }
  if (stale) {
    saveStore(ORDERS_KEY, []);
    saveStore(READ_KEY, []);
  }
  saveStore(SEEDED_KEY, true);
}

// Built-in demo companies (added 2026-10-28 at the user's request): one admin of a company on its trial and one
// of a paid company, so anyone opening the prototype can sign in without signing up first. Dates are counted
// from today, so the trial never runs out. A company missing from this browser (first visit, or deleted) is put
// back on every load; one already stored is left as it is, so payments or staff changes made in the demo stay.
// Their codes are kept out of the codes a signup can draw (trial.js RESERVED_CODES).
const DEMO_PW = 'ieum1234';
function demoCompanies() {
  const today = dayStart(Date.now());
  const daysAgo = n => new Date(today.getTime() - n * DAY_MS);
  const trialEnd = start => { const d = new Date(start); d.setDate(d.getDate() + 13); d.setHours(23, 59, 59, 999); return d.toISOString(); };
  const trialAt = daysAgo(3);
  const paidAt = daysAgo(30);
  const paidFrom = daysAgo(20); // paid a monthly plan on day 11 of its trial; it expires in about 10 days
  const paidTo = addMonths(paidFrom, 1);
  const base = { emailVerified: true, payments: [], adminPw: DEMO_PW };
  return [
    { ...base, no: 'T-DEMO-TRIAL', company: '체험상사', companyCode: 'ieum0001', users: 5, total: 5 * 4000,
      email: 'trial-admin@example.com', adminName: '김체험', adminNo: empNo(trialAt.getFullYear(), 1),
      status: 'TRIAL', at: trialAt.toISOString(), verifiedAt: trialAt.toISOString(), trialEnd: trialEnd(trialAt) },
    { ...base, no: 'T-DEMO-PAID', company: '유료물산', companyCode: 'ieum0002', users: 20, total: 20 * 4000,
      email: 'paid-admin@example.com', adminName: '박유료', adminNo: empNo(paidAt.getFullYear(), 1),
      status: 'PAID', at: paidAt.toISOString(), verifiedAt: paidAt.toISOString(), trialEnd: trialEnd(paidAt),
      paidAt: paidFrom.toISOString(), serviceFrom: paidFrom.toISOString(),
      plan: 'MONTHLY', subAnchorAt: paidFrom.toISOString(), subMonths: 1,
      payments: [{ at: paidFrom.toISOString(), kind: 'FIRST', plan: 'MONTHLY', months: 1, pct: 0, result: 'OK',
        amount: 20 * 4000, users: 20, from: paidFrom.toISOString(), to: paidTo.toISOString() }] },
  ];
}
function ensureDemoCompanies() {
  const orders = loadStore(ORDERS_KEY, []);
  const missing = demoCompanies().filter(d => !orders.some(o => o.companyCode === d.companyCode));
  if (!missing.length) return;
  saveStore(ORDERS_KEY, [...orders, ...missing]);
  // not "new applications" for the operator's badge
  saveStore(READ_KEY, [...new Set([...loadStore(READ_KEY, []), ...missing.map(appId)])]);
}

// Paid companies stored by the older monthly auto-renewal version (nextPayAt) become a monthly subscription
// that started on its first paid day and covers one month per successful payment.
function upgradeStoredOrders() {
  const orders = loadStore(ORDERS_KEY, []);
  const old = orders.filter(o => o.status === 'PAID' && !o.subAnchorAt);
  if (!old.length) return;
  old.forEach(o => {
    o.plan = 'MONTHLY';
    o.subAnchorAt = dayStart(o.serviceFrom || o.paidAt || o.at).toISOString();
    o.subMonths = Math.max(1, (o.payments || []).filter(p => p.result === 'OK').length);
    o.paidAt = o.paidAt || (o.payments || []).find(p => p.result === 'OK')?.at || o.serviceFrom;
    delete o.nextPayAt;
    delete o.nextUsers;
  });
  saveStore(ORDERS_KEY, orders);
}

// 사번 = 등록 연도 + 4자리 번호 (20260001), given in the order accounts are created within the company;
// the number starts again at 0001 each year. It is the login ID for everyone, operators included (IEUM is
// company ieum). Unique only within a company, so login always checks 업체 코드 + 사번 together.
// The first admin is <signup year>0001, given once at signup and stored (trial.js), like every 사번;
// applications saved before that field existed fall back to the signup year.
const empNo = (year, n) => `${year}${String(n).padStart(4, '0')}`;
const adminNo = o => o.adminNo || empNo(new Date(o.at).getFullYear(), 1);

// One row per application, so the application number identifies it
const appId = o => o.no;
const STATUS_LABEL = { TRIAL: '체험 중', PAID: '결제 완료', EXPIRED: '읽기 전용' };
const won = n => `${n.toLocaleString('ko-KR')}원`;
const pad2 = n => String(n).padStart(2, '0');
const ymd = iso => { const d = new Date(iso); return `${d.getFullYear()}.${pad2(d.getMonth() + 1)}.${pad2(d.getDate())}`; };
const dayStart = t => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d; };

// Days before a payment reminder: the admin gets one on these days before the trial or the paid period ends
// (D-day = the last day)
const REMIND_DAYS = [7, 3, 1, 0];
const endOfDay = t => { const d = new Date(t); d.setHours(23, 59, 59, 999); return d; };

// State of a company, computed from today: its trial, or its paid subscription once it has paid.
// status TRIAL / PAID / EXPIRED (trial or paid period over → read-only for everyone, requirements v1.5
// DEM-03·SUB-04); paid tells which period `end` and daysLeft are about. daysLeft counts calendar days to
// the last day (0 = the last day itself, "D-day"). Paid service starts the day the company pays, during the
// trial or after it ended (PAY-02): the days left in the trial are not added on. serviceFrom is the first day
// of the current paid run, or today before paying (= the start date if it paid now).
// Shared with the groupware's trial strip, reminder, billing page and the operator screens.
function trialState(o) {
  const sub = subscriptionOf(o);
  const last = sub ? sub.expire : new Date(o.trialEnd);
  const daysLeft = Math.max(0, Math.round((dayStart(last) - dayStart(Date.now())) / DAY_MS));
  const over = endOfDay(last) < new Date();
  const status = over ? 'EXPIRED' : sub ? 'PAID' : 'TRIAL';
  const serviceFrom = sub ? new Date(sub.from) : dayStart(Date.now());
  return {
    status, paid: !!sub, label: STATUS_LABEL[status], daysLeft, end: ymd(last), trialEnd: ymd(o.trialEnd),
    serviceFrom: ymd(serviceFrom), serviceFromIso: serviceFrom.toISOString(),
    dday: daysLeft === 0 ? 'D-day' : `D-${daysLeft}`, urgent: status !== 'EXPIRED' && daysLeft <= 3,
  };
}

// Paid plans (requirements v1.5 3.5·3.6, SUB-03·05). No automatic renewal or charge: the admin extends the
// subscription directly, as many times as it likes, choosing monthly or annual each time.
// - The first purchase, and a purchase after the paid period ended, start on the payment day.
// - An extension before the end adds to the current expiry date (monthly → annual starts where the month ends).
// - Months are calendar months; a day the month doesn't have becomes its last day, and the run keeps its first
//   day as the base, so 1.31 → 2.28 → 3.31 (anchor + total months, not chained from the adjusted date).
// - The period runs through the expiry date itself; the day after it the company is read-only.
// - Annual is 10% off the first time and 20% off for an annual extension or a new annual purchase until
//   3 months after the last annual period ended; later than that it is 10% again. Monthly has no discount.
// The real server works this out again from the order and checks the approved Toss amount against it.
const PRICE_PER_USER = 4000;
const PLANS = { MONTHLY: { label: '월간', months: 1 }, ANNUAL: { label: '연간', months: 12 } };

function subscriptionOf(o) {
  if (o.status !== 'PAID' || !o.subAnchorAt) return null;
  const expire = addMonths(o.subAnchorAt, o.subMonths);
  return { plan: o.plan, planLabel: PLANS[o.plan]?.label || '월간', from: o.subAnchorAt, expire, expireIso: expire.toISOString() };
}

// The latest day an annual period paid so far ends (null if the company never bought annual)
function lastAnnualEnd(o) {
  const ends = (o.payments || []).filter(p => p.result === 'OK' && p.plan === 'ANNUAL' && p.to).map(p => new Date(p.to).getTime());
  return ends.length ? new Date(Math.max(...ends)) : null;
}

// What paying for `plan` today would buy: FIRST (trial, or read-only after it), EXTEND (paid period running)
// or RESTART (paid period ended), the discount, the amount and the period it covers
function quoteOf(o, plan, users) {
  const sub = subscriptionOf(o);
  const st = trialState(o);
  const mode = !sub ? 'FIRST' : st.status === 'EXPIRED' ? 'RESTART' : 'EXTEND';
  const months = PLANS[plan].months;
  let pct = 0;
  let why = '할인 없음';
  if (plan === 'ANNUAL') {
    const last = lastAnnualEnd(o);
    if (last && new Date() <= endOfDay(addMonths(last, 3))) {
      pct = 20;
      why = mode === 'EXTEND' && endOfDay(last) >= new Date() ? '연간 연장 할인' : '연간 만료 후 3개월 안 재구매 할인';
    } else {
      pct = 10;
      why = last ? '연간 만료 3개월이 지나 기본 연간 할인' : '첫 연간 할인';
    }
  }
  const n = mode === 'EXTEND' ? o.users : users;
  const amount = Math.floor(n * PRICE_PER_USER * months * (100 - pct) / 100);
  const today = dayStart(Date.now());
  const from = mode === 'EXTEND' ? sub.expire : today;
  const to = mode === 'EXTEND' ? addMonths(o.subAnchorAt, o.subMonths + months) : addMonths(today, months);
  return { mode, plan, months, users: n, pct, why, amount, from: from.toISOString(), to: to.toISOString(),
    formula: `${n}명 × ${won(PRICE_PER_USER)} × ${months}개월${pct ? ` × ${100 - pct}%` : ''}` };
}

function addMonths(t, n) {
  const d = new Date(t);
  const day = d.getDate();
  d.setDate(1);
  d.setMonth(d.getMonth() + n);
  // 1.31 + one month = 2.28 (or 2.29), not 3.3
  d.setDate(Math.min(day, new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()));
  return d;
}
const findCompanyOrder = code => loadStore(ORDERS_KEY, []).find(o => o.companyCode === code);
// here, once empNo, appId, dayStart and addMonths are defined
upgradeStoredOrders();
ensureDemoCompanies();
const when = iso => {
  const d = new Date(iso);
  const p = n => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

function newApps() {
  const read = loadStore(READ_KEY, []);
  return loadStore(ORDERS_KEY, []).filter(o => !read.includes(appId(o)));
}

function renderBadges() {
  const n = newApps().length;
  for (const id of ['bellCount', 'navCount']) {
    const el = document.getElementById(id);
    el.textContent = n;
    el.hidden = n === 0;
  }
  document.getElementById('bell').setAttribute('aria-label', n ? `새 신청 ${n}건` : '새 신청 알림');
}

const whenFull = iso => {
  const d = new Date(iso);
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

// One application's detail (three cards), built from the template in index.html
function buildDetail(o) {
  const box = document.getElementById('appDetailTpl').content.firstElementChild.cloneNode(true);

  // same sections and order as the purchase form, so the admin reads it the way the customer filled it in
  const fill = (part, rows) => box.querySelector(`[data-part="${part}"]`).replaceChildren(...rows.flatMap(([k, v]) => {
    const dt = document.createElement('dt'); dt.textContent = k;
    const dd = document.createElement('dd');
    dd.append(v);
    return [dt, dd];
  }));

  fill('company', [
    ['업체명', o.company],
    ['이용 인원', `${o.users}명`],
    ['이메일', o.email],
    // v1.4: the account exists from the signup; until the e-mail is verified the company can't sign in
    ['이메일 인증', o.emailVerified === false ? `대기 (인증 메일 ${whenFull(o.verifySentAt || o.at)} 발송, 인증 전 로그인 불가)` : o.verifiedAt ? `완료 (${whenFull(o.verifiedAt)})` : '완료'],
  ]);

  // requested password: hidden by default, the eye button shows it (same component as every password field)
  let pw = '(저장된 값 없음)';
  if (o.adminPw) {
    pw = document.createElement('input');
    pw.type = 'password';
    pw.readOnly = true;
    pw.value = o.adminPw;
    pw.className = 'pw-readonly';
    pw.setAttribute('aria-label', '신청한 비밀번호');
  }
  fill('admin', [
    // the card title already says 관리자 계정, so the labels stay short enough for one line
    ['이름', o.adminName],
    ['사번', adminNo(o)],
    ['비밀번호', pw],
  ]);
  if (o.adminPw) addPasswordToggle(pw);

  const st = trialState(o);
  const failed = (o.payments || []).filter(p => p.result === 'FAILED').length;
  const sub = subscriptionOf(o);
  const payText = sub ? `결제 완료 (첫 결제 ${ymd(o.paidAt)})` : failed ? `미결제 · 실패 ${failed}회` : '미결제';
  fill('pay', [
    ['신청번호', o.no],
    ['신청 시각', whenFull(o.at)],
    ['체험 기간', `${ymd(o.at)} ~ ${st.trialEnd}`],
    ['월 기준 이용료', won(o.total)],
    ['결제 상태', payText],
    ...(sub ? [
      ['요금제', sub.planLabel],
      ['이용 기간', `${st.serviceFrom} ~ ${st.end}${st.status === 'EXPIRED' ? ' (만료, 읽기 전용)' : ''}`],
    ] : [['유료 이용 시작', '결제한 날부터']]),
    ['업체 코드', o.companyCode],
  ]);
  return box;
}

// The application whose detail is open under its row (one at a time); kept across re-renders
let openApp = null;

// Detail row under an application row. Its inner box slides open and shut (CSS animates .slide);
// `open` true builds it already open, for re-renders that shouldn't animate.
function detailRow(o, open) {
  const tr = document.createElement('tr');
  tr.className = 'row-detail';
  tr.dataset.app = appId(o);
  const td = document.createElement('td');
  td.colSpan = 6;
  const slide = document.createElement('div');
  slide.className = open ? 'slide open' : 'slide';
  const inner = document.createElement('div');
  inner.className = 'slide-inner';
  inner.append(buildDetail(o));
  slide.append(inner);
  td.append(slide);
  tr.append(td);
  return tr;
}

function setRowOpen(row, open) {
  row.classList.toggle('is-open', open);
  row.querySelector('.row-title').setAttribute('aria-expanded', open);
}

// Close the open detail: slide it shut, then remove its row
function closeDetail() {
  const detail = document.querySelector('#appList tr.row-detail:not(.closing)');
  if (!detail) return;
  setRowOpen(detail.previousElementSibling, false);
  detail.classList.add('closing');
  const slide = detail.querySelector('.slide');
  slide.classList.remove('open');
  let done = false;
  const remove = () => { if (!done) { done = true; detail.remove(); } };
  slide.addEventListener('transitionend', remove, { once: true });
  setTimeout(remove, 400); // no transition (reduced motion) or the event got lost
}

// Row click: close what's open; open this one unless it was the one open
function toggleDetail(row, o) {
  const id = appId(o);
  const wasOpen = openApp === id;
  closeDetail();
  openApp = wasOpen ? null : id;
  if (wasOpen) return;
  const detail = detailRow(o, false);
  row.after(detail);
  setRowOpen(row, true);
  // let the browser lay it out closed first (reading its size forces that), then open so the height animates.
  // requestAnimationFrame would also work but never fires in a background tab, leaving the row shut
  const slide = detail.querySelector('.slide');
  slide.getBoundingClientRect();
  slide.classList.add('open');
}

// New applications seen when the status page was opened. They're marked read right away (badges clear),
// but this page keeps showing them as new until you leave it.
let seenNew = new Set();
// Which summary card is selected: 'all', 'new', or a status code
let appFilter = 'all';

function renderStatus() {
  const orders = loadStore(ORDERS_KEY, []).slice().sort((a, b) => new Date(b.at) - new Date(a.at));
  const fresh = new Set([...seenNew, ...newApps().map(appId)]);
  const states = new Map(orders.map(o => [o.no, trialState(o)]));
  const count = (id, n) => { document.getElementById(id).textContent = `${n}건`; };
  count('cntAll', orders.length);
  count('cntNew', fresh.size);
  count('cntTrial', orders.filter(o => states.get(o.no).status === 'TRIAL').length);
  count('cntPaid', orders.filter(o => states.get(o.no).status === 'PAID').length);
  count('cntExpired', orders.filter(o => states.get(o.no).status === 'EXPIRED').length);

  // one cell: data-label is the column name shown in front of the value on phones (table turns into cards)
  const cell = (label, cls, ...parts) => {
    const td = document.createElement('td');
    td.dataset.label = label;
    if (cls) td.className = cls;
    const box = document.createElement('div');
    box.className = 'cell';
    box.append(...parts);
    td.append(box);
    return td;
  };
  const el = (tag, text, cls) => { const e = document.createElement(tag); e.textContent = text; if (cls) e.className = cls; return e; };

  document.querySelectorAll('#appFilters .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === appFilter));
  const shown = orders.filter(o =>
    appFilter === 'all' ? true : appFilter === 'new' ? fresh.has(appId(o)) : states.get(o.no).status === appFilter);

  document.getElementById('appList').replaceChildren(...shown.flatMap(o => {
    const id = appId(o);
    const isNew = fresh.has(id);
    const isOpen = openApp === id;
    const tr = document.createElement('tr');
    tr.className = 'row-link' + (isNew ? ' is-new' : '') + (isOpen ? ' is-open' : '');
    // the whole row opens or closes the detail right below it; the company name is a button for keyboard users
    tr.addEventListener('click', () => toggleDetail(tr, o));

    const name = el('button', o.company, 'row-title');
    name.type = 'button';
    name.setAttribute('aria-expanded', isOpen);
    const company = [name];
    if (isNew) company.push(el('span', '새 신청', 'tag-new'));
    if (o.emailVerified === false) company.push(el('span', '인증 대기', 'status'));
    company.push(el('small', o.no, 'cell-sub'));

    const st = states.get(o.no);
    const statusSub = st.status === 'EXPIRED' ? `${st.end} ${st.paid ? '이용' : '체험'} 만료`
      : st.paid ? `${subscriptionOf(o).planLabel} · ${st.end} 만료` : `${st.dday} · ${st.end} 만료`;
    tr.append(
      cell('신청 시각', '', el('span', when(o.at))),
      cell('업체', '', ...company),
      cell('인원', 'num', el('span', `${o.users}명`)),
      cell('월 이용료', 'num', el('span', won(o.total))),
      cell('관리자', '', el('span', o.adminName), el('small', adminNo(o), 'cell-sub')),
      cell('상태', '', el('span', st.label, `status status-${st.status.toLowerCase()}`), el('small', statusSub, 'cell-sub')),
    );
    return isOpen ? [tr, detailRow(o, true)] : [tr];
  }));
  // Nothing to show: keep the table and put one row the height of a normal row with a centered notice,
  // so the layout doesn't jump when switching cards
  if (shown.length === 0) {
    const tr = document.createElement('tr');
    tr.className = 'row-empty';
    const td = document.createElement('td');
    td.colSpan = 6;
    td.textContent = orders.length ? '해당하는 신청이 없습니다' : '아직 들어온 신청이 없습니다';
    tr.append(td);
    document.getElementById('appList').append(tr);
  }
}

document.querySelectorAll('#appFilters .card-filter').forEach(b => b.addEventListener('click', () => {
  appFilter = b.dataset.filter;
  openApp = null;
  renderStatus();
}));

// Opening the status page counts as having seen the new applications: the list still marks them
// this time, and the badges clear
function markAllRead() {
  saveStore(READ_KEY, loadStore(ORDERS_KEY, []).map(appId));
  renderBadges();
}

// common.js calls this each time an operator view is shown
function opsViewShown(key) {
  // 접수·운영 데이터·업체 관리 are drawn by admin-inquiry.js, admin-stats.js and admin-company.js; each opens
  // fresh (filters, search and the open row reset) every time it is entered
  if (key === 'ops-inquiry-guest') openInquiryView('guest');
  if (key === 'ops-inquiry-company') openInquiryView('company');
  if (key === 'ops-stats') renderStats();
  if (key === 'ops-company') openFirmView();
  if (key !== 'ops-application-status') return;
  seenNew = new Set(newApps().map(appId));
  appFilter = 'all';
  openApp = null;
  renderStatus();
  markAllRead();
}

// A purchase finished in another tab: update the badges, tell the admin, refresh the open list
let toastTimer = null;
function showToast(text) {
  const toast = document.getElementById('toast');
  toast.textContent = text;
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 4000);
}

window.addEventListener('storage', e => {
  if (e.key !== ORDERS_KEY) return;
  renderBadges();
  if (document.body.dataset.mode !== 'OPS') return; // only an operator gets the notice
  const before = new Map(JSON.parse(e.oldValue || '[]').map(o => [appId(o), o.status]));
  const now = JSON.parse(e.newValue || '[]');
  const added = now.filter(o => !before.has(appId(o)));
  const paid = now.filter(o => o.status === 'PAID' && before.get(appId(o)) === 'TRIAL');
  if (added.length) showToast(`새 체험 신청: ${added[added.length - 1].company}`);
  else if (paid.length) showToast(`결제 완료: ${paid[paid.length - 1].company}`);
  // redraw the open screen in place, keeping its filters and open row
  const key = location.hash.slice(1);
  if (key === 'ops-application-status') renderStatus();
  if (key === 'ops-company') renderFirms();
  if (key === 'ops-stats') renderStats();
});

renderBadges();
