// Prototype only: free-trial signup (replaces the mock purchase on the intro site). No payment here: the
// company and its first admin are created right away and a 14-day trial starts (requirements v1.4 ACC-01/02).
// E-mail verification (v1.4 ACC-06): the signup sends a verification mail; until the applicant verifies, the
// company can't sign in, but the 14 days count from the signup day either way. The prototype shows the code
// on screen instead of mailing it, and trial.html?verify=<업체 코드> reopens this step later (the mail link).
// The admin pays later inside the groupware (업체 관리 > 이용 결제), and paid service begins the day it pays.
// In the real project the server creates the company and admin in one transaction, exactly once per
// application. Uses setError, saveStore, loadStore, PRICE_PER_USER from site.js.
// adminPw is kept only so the prototype's operator screen can show what was requested. The real server
// stores a BCrypt hash and never keeps or shows the plain password.
const TRIAL_DAYS = 14;
// Accounts allowed during the trial, admin included (same as TRIAL_SEATS in hr.js). The form's headcount is
// capped at this; the paid headcount is chosen again at the first payment (이용 결제 step 1).
const TRIAL_SEATS = 10;
// The admin doesn't choose a login ID: the 사번 is <signup year>0001, given here once and stored with the
// application (the rule in ops.js). The form shows it before signup, and the result screen again.
const adminNoFor = date => `${date.getFullYear()}0001`;
document.getElementById('pAdminNo').textContent = adminNoFor(new Date());
const app = { no: null, company: '', companyCode: '', users: 0, email: '', adminName: '', adminPw: '' };

const pad2 = n => String(n).padStart(2, '0');
const ymd = d => `${d.getFullYear()}.${pad2(d.getMonth() + 1)}.${pad2(d.getDate())}`;
// trial ends at the end of the 14th day counting today as day 1
function trialEndOf(start) {
  const end = new Date(start);
  end.setDate(end.getDate() + TRIAL_DAYS - 1);
  end.setHours(23, 59, 59, 999);
  return end;
}

function goStep(n) {
  document.querySelectorAll('.step').forEach(s => { s.hidden = s.id !== `step${n}`; });
  document.querySelectorAll('#stepper li').forEach(li => {
    const k = Number(li.dataset.step);
    li.className = k === n ? 'on' : k < n ? 'done-step' : '';
  });
  window.scrollTo({ top: 0 });
}

document.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => goStep(Number(b.dataset.go))));

// 업체 코드 is not chosen by the applicant: companies are numbered in signup order from ieum0001 (requirements
// v1.7 ACC-03; ieum0000 is IEUM's own company, the operators). Shown when the page opens in a locked field like
// the 사번. If another signup takes it first (another tab), start() takes the next one and the result screen
// shows the code actually given. After ieum9999 no code can be issued and the signup stops with a notice.
// The real server takes the next number inside the transaction that creates the company (unique key in the DB).
const CODE_COUNT = 10000;
// the built-in demo companies (ops.js demoCompanies) may not be stored yet when this page opens first
const RESERVED_CODES = ['ieum0001', 'ieum0002', 'ieum0003'];
const takenCodes = () => new Set([...RESERVED_CODES, ...loadStore('ieum-demo-orders', []).map(o => o.companyCode)]);
const codeOf = n => `ieum${String(n).padStart(4, '0')}`;
function drawCode() {
  let next = 1;
  for (const c of takenCodes()) {
    const n = /^ieum\d{4}$/.test(c) ? Number(c.slice(4)) : 0;
    if (n >= next) next = n + 1;
  }
  return next < CODE_COUNT ? codeOf(next) : null;
}
const NO_CODE = '지금은 발급할 수 있는 업체 코드가 없습니다. 도입 문의로 연락해 주세요.';
let previewCode = drawCode();
document.getElementById('pCode').textContent = previewCode || '발급 불가';
if (!previewCode) document.getElementById('pCodeError').textContent = NO_CODE;

// 1. info
const step1 = document.getElementById('step1');
clearOnInput(step1);

// headcount: 1~10 only (the trial's account limit). Typing more than 10 is pulled back to 10 right away
const usersInput = document.getElementById('pUsers');
usersInput.addEventListener('input', () => {
  if (Number(usersInput.value) > TRIAL_SEATS) usersInput.value = TRIAL_SEATS;
});
step1.addEventListener('submit', e => {
  e.preventDefault();
  const f = id => document.getElementById(id);
  const rules = [
    [f('pCompany'), v => v.trim() ? '' : '업체명을 입력해 주세요'],
    [f('pUsers'), v => (Number.isInteger(Number(v)) && Number(v) >= 1 && Number(v) <= TRIAL_SEATS) ? '' : `이용 인원은 1~${TRIAL_SEATS}명으로 입력해 주세요`],
    [f('pEmail'), v => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()) ? '' : '이메일 형식을 확인해 주세요'],
    [f('pAdminName'), v => v.trim() ? '' : '관리자 이름을 입력해 주세요'],
    [f('pPw'), v => v.length >= 8 ? '' : '비밀번호를 8자 이상 입력해 주세요'],
    [f('pPw2'), v => v === f('pPw').value ? '' : '비밀번호가 서로 다릅니다'],
  ];
  let ok = !!previewCode;
  for (const [el, rule] of rules) {
    const msg = rule(el.value);
    setError(el, msg);
    if (msg && ok) { el.focus(); ok = false; }
  }
  if (!ok) return;

  Object.assign(app, {
    company: f('pCompany').value.trim(), companyCode: previewCode, users: Number(f('pUsers').value), email: f('pEmail').value.trim(),
    adminName: f('pAdminName').value.trim(), adminPw: f('pPw').value, no: null,
  });
  const today = new Date();
  document.getElementById('sCompany').textContent = app.company;
  document.getElementById('sCode').textContent = app.companyCode;
  document.getElementById('sAdmin').textContent = `${app.adminName} · 사번 ${adminNoFor(today)}`;
  document.getElementById('sEmail').textContent = app.email;
  document.getElementById('sUsers').textContent = `${app.users}명 (체험 중 최대 ${TRIAL_SEATS}명)`;
  document.getElementById('sTrial').textContent = `${TRIAL_DAYS}일 (${ymd(today)} ~ ${ymd(trialEndOf(today))})`;
  goStep(2);
});

// 2. confirm → start the trial. The submit button locks while "processing" so a double click can't
// create two companies
const step2 = document.getElementById('step2');
step2.addEventListener('submit', e => {
  e.preventDefault();
  const agree = document.getElementById('pAgree');
  agree.closest('.form-row').querySelector('.field-error').textContent = agree.checked ? '' : '이용 조건에 동의해야 체험을 시작할 수 있습니다';
  if (!agree.checked) return;
  const submit = step2.querySelector('[type="submit"]');
  if (submit.disabled) return;
  submit.disabled = true;
  setTimeout(() => { submit.disabled = false; start(); }, 500);
});

// A random number with `digits` digits, drawn again until `make` gives a value not in `taken`; after 20 clashes
// it moves to one more digit, so it always ends. Used for the application number: random rather than counting up,
// so it doesn't reveal how many customers there are.
// The real server keeps both unique in the DB and draws again on a clash.
function uniqueRandom(make, taken, digits = 4) {
  for (let tries = 0; ; tries++) {
    const d = tries < 20 ? digits : digits + 1;
    const value = make(Math.floor(10 ** (d - 1) + Math.random() * 9 * 10 ** (d - 1)));
    if (!taken.has(value)) return value;
  }
}

// 3. result
function start() {
  const orders = loadStore('ieum-demo-orders', []);
  // the same form submitted twice → the company already created (requirements: create once)
  let done = app.no && orders.find(o => o.no === app.no);
  if (!done) {
    // the code shown may have been taken since the page opened (another signup): draw a new one
    if (takenCodes().has(app.companyCode)) {
      previewCode = app.companyCode = drawCode();
      if (!previewCode) {
        goStep(1);
        document.getElementById('pCode').textContent = '발급 불가';
        document.getElementById('pCodeError').textContent = NO_CODE;
        return;
      }
    }
    const now = new Date();
    const stamp = `${now.getFullYear()}${pad2(now.getMonth() + 1)}${pad2(now.getDate())}`;
    app.no = uniqueRandom(n => `T-${stamp}-${n}`, new Set(orders.map(o => o.no)));
    done = {
      ...app, status: 'TRIAL', total: app.users * PRICE_PER_USER,
      at: now.toISOString(), trialEnd: trialEndOf(now).toISOString(), payments: [],
      adminNo: adminNoFor(now),
      // e-mail verification: the real server mails a one-time link; the prototype keeps a 6-digit code and shows it
      emailVerified: false, verifyCode: newVerifyCode(), verifySentAt: now.toISOString(),
    };
    orders.push(done);
    saveStore('ieum-demo-orders', orders);
  }
  renderResult(done);
}

const newVerifyCode = () => String(Math.floor(100000 + Math.random() * 900000));

// Step 3: the application summary, then the verification box (until verified) or the way into the app
function renderResult(done) {
  const verified = done.emailVerified !== false;
  const box = document.getElementById('step3');
  box.replaceChildren();
  const add = (tag, text, cls) => { const el = document.createElement(tag); el.textContent = text; if (cls) el.className = cls; box.append(el); return el; };
  add('h3', verified ? '이메일 인증이 끝났습니다' : '신청이 끝났습니다. 이메일을 인증해 주세요');
  const table = document.createElement('table');
  table.className = 'summary';
  const rows = [
    ['신청번호', done.no],
    ['업체명', done.company],
    ['체험 기간', `${ymd(new Date(done.at))} ~ ${ymd(new Date(done.trialEnd))} (${TRIAL_DAYS}일)`],
    ['업체 코드', done.companyCode],
    ['관리자 사번', done.adminNo],
    ['이용 인원', `${done.users}명 (체험 중 최대 ${TRIAL_SEATS}명)`],
  ];
  for (const [k, v] of rows) {
    const tr = document.createElement('tr');
    const th = document.createElement('th'); th.textContent = k;
    const td = document.createElement('td'); td.textContent = v;
    tr.append(th, td);
    table.append(tr);
  }
  box.append(table);

  if (!verified) {
    // verification box. 14 days run from the signup day whether or not the mail is opened today (v1.4 ACC-02)
    add('p', `${done.email}로 인증 메일을 보냈습니다. 메일의 인증 링크를 누르면 바로 로그인할 수 있습니다. ` +
      '인증 전에는 로그인할 수 없고, 체험 기간은 신청한 날부터 14일이라 인증이 늦어도 늘어나지 않습니다.', 'note');
    const form = document.createElement('form');
    form.className = 'form-row';
    form.noValidate = true;
    form.innerHTML = '<label for="pVerify">인증 코드 <span class="label-opt">(시연용: 메일 대신 여기에 보여줍니다)</span></label>' +
      '<div class="actions" style="margin-top:0;justify-content:flex-start"><input class="input" id="pVerify" inputmode="numeric" maxlength="6" autocomplete="one-time-code" style="max-width:200px">' +
      '<button class="btn btn-primary" type="submit">인증하기</button><button class="btn" type="button" id="btnResend">인증 메일 다시 보내기</button></div>' +
      '<span class="field-hint" id="pVerifyHint"></span><span class="field-error"></span>';
    box.append(form);
    const hint = form.querySelector('#pVerifyHint');
    const showCode = () => { hint.textContent = `시연용 인증 코드: ${done.verifyCode}`; };
    showCode();
    form.addEventListener('submit', e => {
      e.preventDefault();
      const input = form.querySelector('#pVerify');
      if (input.value.trim() !== done.verifyCode) { setError(input, '인증 코드가 맞지 않습니다'); return; }
      // the real server marks the account verified when the one-time link is opened
      const orders = loadStore('ieum-demo-orders', []);
      const o = orders.find(x => x.no === done.no);
      if (o) { o.emailVerified = true; o.verifiedAt = new Date().toISOString(); delete o.verifyCode; saveStore('ieum-demo-orders', orders); }
      renderResult(o || { ...done, emailVerified: true });
    });
    form.querySelector('#btnResend').addEventListener('click', () => {
      // a new code replaces the old one, like a re-sent link
      const orders = loadStore('ieum-demo-orders', []);
      const o = orders.find(x => x.no === done.no);
      if (o) { o.verifyCode = newVerifyCode(); o.verifySentAt = new Date().toISOString(); saveStore('ieum-demo-orders', orders); done.verifyCode = o.verifyCode; }
      showCode();
      form.querySelector('.field-error').textContent = '';
      form.querySelector('#pVerify').value = '';
    });
    const actions = add('div', '', 'actions');
    const home = document.createElement('a'); home.className = 'btn btn-lg'; home.href = 'site.html'; home.textContent = '소개 페이지로';
    actions.append(home);
    goStep(3);
    return;
  }

  // the groupware opens only in the installed app, so the way in from here is the download, not a link
  add('p', '그룹웨어는 IEUM 앱에서 사용합니다. 앱을 설치한 뒤 업체 코드, 관리자 사번, 신청할 때 정한 비밀번호로 로그인하세요.', 'note');
  add('div', `결제는 체험 중 언제든 그룹웨어의 업체 관리 > 이용 결제에서 하고, 그때 유료로 쓸 인원을 정합니다. ` +
    `결제한 날부터 유료 이용이 시작되고(남은 체험 기간은 이어지지 않습니다), 결제하면 ${TRIAL_SEATS}명 제한이 풀립니다. ` +
    `만료 7일·3일·1일 전과 마지막 날에 결제 안내를 드립니다. 결제하지 않으면 체험이 끝난 뒤 읽기 전용으로 바뀌며, 그때 결제해도 계정과 데이터는 그대로 이어집니다.`, 'done');
  const actions = add('div', '', 'actions');
  const home = document.createElement('a'); home.className = 'btn btn-lg'; home.href = 'site.html'; home.textContent = '소개 페이지로';
  const download = document.createElement('button'); download.type = 'button'; download.className = 'btn btn-primary btn-lg'; download.textContent = 'IEUM 앱 다운로드';
  download.addEventListener('click', startDownload);
  actions.append(home, download);
  goStep(3);
}

// Opened from the verification mail (trial.html?verify=업체코드): straight to step 3 of that application
const verifyCode = new URLSearchParams(location.search).get('verify');
const reopened = verifyCode && loadStore('ieum-demo-orders', []).find(o => o.companyCode === verifyCode.toLowerCase());
if (reopened) {
  Object.assign(app, reopened);
  renderResult(reopened);
} else {
  goStep(1);
}
