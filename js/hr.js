// 인사 관리 > 사원 관리 and 가입 승인, plus the 계정 생성 form on the login screen.
//
// How an employee gets an account (user decided 2026-10-29; replaces the 가입 키 of requirements v1.8):
//   0. 가입 활성화: on 가입 승인 a 부서장 opens joining for their own department, the company admin for the whole
//      company, for a time picked next to the button (10분 ~ 하루). It turns itself off when the time is up.
//      While nothing is open, the company code does nothing on the join form, so a leaked code is of little use.
//   1. 승인 요청: on the login screen the employee opens 계정 생성 and confirms the 업체 코드. Only while joining
//      is open does 확인 load the open departments and the ranks; otherwise nothing happens (the same as a code that
//      doesn't exist), and 5 wrong tries in 10 minutes lock 확인 for 10 minutes (v1.8). The employee picks 부서 and
//      직급, types 이름, verifies the 이메일 with a mailed code and sets the 비밀번호. The 승인 요청 button is grey
//      until all of that is done. Sending it makes a join request (REQUESTED, no 사번 yet).
//   2. 승인·반려: the company admin (every request) or the head of that team (that team's requests) approves or
//      rejects it on 가입 승인 within 1 hour of the request; after that it has expired and must be made again.
//      Approving gives the next 사번 in order (<year> + the next number after the highest one used, never reused)
//      and, from the company admin, roles, and makes the account right away with the password from the request
//      (ACTIVE, or WAITING when it was approved past the headcount, see below). The employee's screen shows the
//      사번 and a way to the login. The same device picks the request up again if the screen was closed.
// Seats (decided 2026-10-28), counted when a request is approved:
//   - Trial: up to 10 accounts in use including the admin. The 11th approval is refused.
//   - Paid: accounts in use up to the purchased headcount. Approvals may go 20% further (구매 인원 × 1.2, rounded
//     up) so that one mistaken approval can't keep a real employee out: an approval past the headcount becomes
//     "자리 대기" (can't sign in). The admin picks which waiting account to activate once a seat is free
//     (usually by deactivating the mistaken one). Seats used never pass the purchased headcount.
//   An approved account that hasn't been created yet already holds its seat (or its 자리 대기 place).
// Deactivation: an employee who leaves is deactivated (status RETIRED as in the team repo's DB, can't sign in,
// records stay) and frees the seat. The company admin can't be deactivated, nor yourself.
// 부서장 is a department setting, not a rank or a role (v1.7 3.7): the company admin marks one employee per
// department in 사원 관리 (staff record head: true); the real server keeps it on the department.
// Prototype only: requests and accounts live in localStorage per company code, the password is kept in plain
// text so the account can sign in here, and the e-mail code is shown on screen instead of being mailed. The real
// server stores a BCrypt hash, mails the code and checks every rule above again (the screen only explains why).
// Uses loadStore, saveStore, findCompanyOrder, trialState, ymd, empNo, adminNo, showToast from ops.js, and
// session, userRoles, readOnly, signOut from common.js (read when called, after common.js has loaded).
const EMP_KEY = 'ieum-demo-employees-v5'; // v5: sample companies removed (2026-10-28), stored staff start empty
const TRIAL_SEATS = 10;
const JOIN_LIMIT_MS = 60 * 60 * 1000; // a join request must be approved within 1 hour
const ROLE_LABEL = { IT: 'IT 담당', HR: '인사 담당', ADMIN: '업체 관리자' };
// EXPIRED is not stored: it is a REQUESTED request older than 1 hour (statusOf)
const EMP_STATUS = {
  REQUESTED: '승인 대기', EXPIRED: '요청 만료', REJECTED: '반려', APPROVED: '계정 생성 전',
  ACTIVE: '사용 중', WAITING: '자리 대기', RETIRED: '비활성',
  APPROVAL_EXPIRED: '승인 만료', CANCELED: '취소',
};
const APPROVAL_LIMIT_MS = 60 * 60 * 1000; // an approved account must be created within 1 hour (v1.7 ACC-05)
// Departments are per company: the company's admin keeps them (부서 관리, not built in the prototype yet), and a
// company without its own list uses DEPTS. The two built-in demo companies (ops.js demoCompanies) have different
// lists so the join form visibly loads the company's own. The real server answers GET /join/company/{code} with
// the company name and its departments (permitAll, like /join).
const DEPTS = ['경영지원팀', '개발팀', '영업팀', '물류팀'];
const DEPT_KEY = 'ieum-demo-depts-v1';
const DEMO_DEPTS = {
  ieum0001: ['경영지원팀', '개발팀', '영업팀'],
  ieum0002: ['경영지원팀', '영업팀', '물류팀', '품질관리팀', '구매팀'],
};
const deptsOf = code => loadStore(DEPT_KEY, {})[code] || DEMO_DEPTS[code] || DEPTS;
// Ranks (직급): the master list is the one requirements v1.7 3.9 maps to years of service (연차 계산 uses it),
// and each company uses a subset of it (user decided 2026-10-28), kept by the company's admin like departments
// and sent with them by GET /join/company/{code}. 부서장 isn't a rank (see isHeadOf).
const RANKS = ['사원', '주임', '대리', '과장', '차장', '부장'];
const RANK_KEY = 'ieum-demo-ranks-v1';
const DEMO_RANKS = {
  ieum0001: ['사원', '대리', '과장', '부장'],
  ieum0002: ['사원', '주임', '대리', '과장', '차장', '부장'],
};
const ranksOf = code => loadStore(RANK_KEY, {})[code] || DEMO_RANKS[code] || RANKS;

// 가입 활성화 per company: [{ scope, until, by, byName }], scope = a department or JOIN_OPEN_ALL (the company admin).
// Entries whose time is up are dropped when read. The real server keeps them on the company (or department) with
// the end time and answers the join form only for an open department.
const JOIN_OPEN_KEY = 'ieum-demo-join-open-v1';
const JOIN_OPEN_ALL = '*';
function joinOpenings(code) {
  const all = loadStore(JOIN_OPEN_KEY, {});
  const now = Date.now();
  const list = (all[code] || []).filter(x => new Date(x.until).getTime() > now);
  if ((all[code] || []).length !== list.length) { all[code] = list; saveStore(JOIN_OPEN_KEY, all); }
  return list;
}
// minutes = 0 turns that scope off
function setJoinOpening(code, scope, minutes) {
  const all = loadStore(JOIN_OPEN_KEY, {});
  const list = (all[code] || []).filter(x => x.scope !== scope);
  if (minutes) list.push({ scope, until: new Date(Date.now() + minutes * 60000).toISOString(), by: session.id, byName: session.name });
  all[code] = list;
  saveStore(JOIN_OPEN_KEY, all);
}
// the departments an employee can join now: all of them while the company admin has it open, else the open ones
function openDeptsOf(code) {
  const open = joinOpenings(code);
  if (open.some(x => x.scope === JOIN_OPEN_ALL)) return deptsOf(code);
  return deptsOf(code).filter(d => open.some(x => x.scope === d));
}
const scopeLabel = s => (s === JOIN_OPEN_ALL ? '업체 전체' : s);
const hhmm = iso => { const d = new Date(iso); return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`; };
const minsUntil = iso => Math.max(1, Math.ceil((new Date(iso).getTime() - Date.now()) / 60000));
// this device's own join request (계정 생성), so closing the screen doesn't lose it
const MY_JOIN_KEY = 'ieum-demo-join-mine';

// A company's accounts and join requests; the first look creates its first admin from the trial signup.
function staffOf(code) {
  const all = loadStore(EMP_KEY, {});
  if (!all[code]) {
    const order = findCompanyOrder(code);
    // nothing stored for a company that isn't there (yet): an empty list kept now would lose its admin later
    if (!order) return [];
    all[code] = [{ name: order.adminName, empNo: adminNo(order), dept: '경영지원팀', rank: '관리자', roles: ['ADMIN'], status: 'ACTIVE', at: order.at, email: order.email }];
    saveStore(EMP_KEY, all);
  }
  return all[code];
}
function saveStaff(code, list) {
  const all = loadStore(EMP_KEY, {});
  all[code] = list;
  saveStore(EMP_KEY, all);
}
// accounts that have a 사번 (the employees shown in 사원 관리); requests don't have one yet
const employeesOf = code => staffOf(code).filter(e => e.empNo);

// a request with no readable request time counts as expired (no "NaN분 남음")
const requestExpired = e => e.status === 'REQUESTED' && !(Date.now() - new Date(e.requestAt).getTime() <= JOIN_LIMIT_MS);
// an approval not turned into an account within 1 hour is over: the seat is free, the 사번 stays used (ACC-05)
const approvalExpired = e => e.status === 'APPROVED' && !(Date.now() - new Date(e.approvedAt).getTime() <= APPROVAL_LIMIT_MS);
const approvalMinutesLeft = e => Math.max(0, Math.ceil((new Date(e.approvedAt).getTime() + APPROVAL_LIMIT_MS - Date.now()) / 60000));
const statusOf = e => (requestExpired(e) ? 'EXPIRED' : approvalExpired(e) ? 'APPROVAL_EXPIRED' : e.status);
const minutesLeft = e => Math.max(0, Math.ceil((new Date(e.requestAt).getTime() + JOIN_LIMIT_MS - Date.now()) / 60000));

// Next 사번: this year + one past the highest number used this year, so a number is never handed out twice
// (the real server takes it from a per-company, per-year sequence in the same transaction as the approval)
function nextEmpNo(code) {
  const year = String(new Date().getFullYear());
  const used = employeesOf(code).filter(e => e.empNo.startsWith(year)).map(e => Number(e.empNo.slice(4)));
  return empNo(year, Math.max(0, ...used) + 1);
}

// Seats: trial 10 / 10; paid = purchased headcount, approvals up to 120% of it (rounded up)
function seatCaps(code) {
  const order = findCompanyOrder(code);
  if (!order) return null;
  const paid = order.status === 'PAID'; // also after the paid period ended (read-only)
  // a smaller headcount bought at an extension limits approvals from now on, so the accounts in use never pass it
  const n = Math.min(order.users, plannedUsers(order));
  return paid ? { paid, active: n, approve: Math.ceil(n * 1.2) } : { paid, active: TRIAL_SEATS, approve: TRIAL_SEATS };
}
// SEAT = uses a seat (signed in, or approved and about to be created); WAIT = holds a 자리 대기 place
const seatOf = e => (e.status === 'ACTIVE' || (e.status === 'APPROVED' && !approvalExpired(e) && !e.wait) ? 'SEAT'
  : e.status === 'WAITING' || (e.status === 'APPROVED' && !approvalExpired(e) && e.wait) ? 'WAIT' : '');
const activeStaff = code => staffOf(code).filter(e => seatOf(e) === 'SEAT');
const waitingStaff = code => staffOf(code).filter(e => seatOf(e) === 'WAIT');
const isFull = code => { const c = seatCaps(code); return !!c && activeStaff(code).length >= c.active; };
// the employee's own team, for a 부서장: from the session or their staff record
const myDept = () => session.dept || (employeesOf(session.company).find(e => e.empNo === session.id) || {}).dept;
const isHeadOf = e => !!e.head;
const validEmail = v => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);

const cellOf = (label, ...parts) => {
  const td = document.createElement('td');
  td.dataset.label = label;
  td.append(...parts);
  return td;
};
const whoCell = e => {
  const who = document.createElement('div'); who.className = 'cell';
  const name = document.createElement('strong'); name.textContent = e.name;
  const id = document.createElement('span'); id.className = 'cell-sub'; id.textContent = e.empNo || e.email || '';
  who.append(name, id);
  return who;
};
const statusChip = e => {
  const code = statusOf(e);
  const s = document.createElement('span');
  s.className = `status status-${code.toLowerCase()}`;
  s.textContent = code === 'APPROVED' && e.wait ? '계정 생성 전 (자리 대기)' : EMP_STATUS[code] || code;
  return s;
};
const writeButton = (label, aria, onClick, primary) => {
  const b = document.createElement('button');
  b.type = 'button';
  b.className = primary ? 'btn btn-primary' : 'btn';
  b.setAttribute('data-write', ''); // greyed out and refused while the company is read-only
  if (readOnly) b.setAttribute('aria-disabled', 'true');
  b.textContent = label;
  b.setAttribute('aria-label', aria);
  b.addEventListener('click', onClick);
  return b;
};
// a button that still works while the company is read-only: 비활성화 and 계정 생성 전 취소 only shrink the accounts
// in use, so requirements v1.7 3.11 allows them (and a smaller re-purchase may need them)
const allowedButton = (label, aria, onClick) => {
  const b = writeButton(label, aria, onClick);
  b.removeAttribute('data-write');
  b.removeAttribute('aria-disabled');
  return b;
};
const dash = () => { const s = document.createElement('span'); s.className = 'muted'; s.textContent = '-'; return s; };
const whenAt = iso => { const d = new Date(iso); if (Number.isNaN(d.getTime())) return '-'; return `${ymd(iso)} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`; };

// ---------- 사원 관리 ----------
function renderEmployees() {
  const code = session.company;
  const staff = employeesOf(code);
  const caps = seatCaps(code);
  const n = activeStaff(code).length;
  const w = waitingStaff(code).length;
  const retired = staff.filter(e => e.status === 'RETIRED').length;
  const full = !!caps && n >= caps.active;
  const set = (id, v) => { document.getElementById(id).textContent = v; };

  set('empTotal', retired ? `${staff.length}명 (비활성 ${retired})` : `${staff.length}명`);
  set('empActive', `${staff.filter(e => seatOf(e) === 'SEAT').length}명`); // like 가입 승인: approved, not yet created counts
  set('empPending', `${staff.filter(e => e.status === 'APPROVED').length}명`);
  set('empLimit', !caps ? '-' : readOnly ? '읽기 전용' : caps.paid ? `${caps.active}명 (구매 인원)` : `${caps.active}명 (체험 중)`);

  // seats in use. Orange with 2 or fewer left, red when full
  const bar = document.getElementById('seatBar');
  bar.hidden = !caps || readOnly; // read-only: nothing can be approved anyway
  if (!bar.hidden) {
    set('seatTitle', caps.paid ? '사용 중인 계정' : '체험 중 계정');
    set('seatCount', `${n} / ${caps.active}명${w ? ` · 자리 대기 ${w}명` : ''}`);
    const meter = document.getElementById('seatMeter');
    meter.setAttribute('aria-valuenow', n);
    meter.setAttribute('aria-valuemax', caps.active);
    meter.setAttribute('aria-valuetext', `${caps.active}명 중 ${n}명 사용`);
    document.getElementById('seatFill').style.width = `${Math.min(100, (n / caps.active) * 100)}%`;
    bar.classList.toggle('is-near', !full && caps.active - n <= 2);
    bar.classList.toggle('is-full', full);
    set('seatText', caps.paid
      ? (full
        ? `구매 인원 ${caps.active}명을 모두 쓰고 있습니다. 잘못 승인한 경우를 대비해 ${caps.approve}명까지 승인할 수 있고, 구매 인원을 넘는 계정은 자리가 날 때까지 자리 대기로 남습니다.`
        : `구매 인원 ${caps.active}명 중 ${caps.active - n}명 더 쓸 수 있습니다. 비활성 계정은 세지 않습니다.`)
      : (full
        ? '체험 중 쓸 수 있는 자리를 모두 썼습니다. 쓰지 않는 직원을 비활성화하면 그만큼 다른 직원을 승인할 수 있고, 결제하면 구매한 인원만큼 쓸 수 있습니다.'
        : `체험 중에는 관리자를 포함해 ${caps.active}명까지 쓸 수 있습니다. ${caps.active - n}명 더 승인할 수 있습니다.`));
    document.getElementById('seatPay').hidden = caps.paid || !userRoles.includes('ADMIN');
  }

  const rows = staff.map(e => {
    const tr = document.createElement('tr');
    tr.classList.toggle('is-inactive', e.status === 'RETIRED');
    tr.append(
      cellOf('이름', whoCell(e)),
      cellOf('부서·직급', `${e.dept}${e.head ? ' (부서장)' : ''} · ${e.rank}`),
      cellOf('역할', (e.roles || []).length ? e.roles.map(r => ROLE_LABEL[r]).join(', ') : '직원'),
      cellOf('상태', statusChip(e)),
      cellOf('등록일', ymd(e.at)),
      cellOf('관리', stateButtons(e)),
    );
    return tr;
  });
  document.getElementById('empList').replaceChildren(...rows);
}

// Buttons of a row in 사원 관리. None for the company admin (fixed seat) or for yourself.
// 비활성화 and the cancel of a not-yet-created account work while read-only; the rest are write actions.
function stateButtons(e) {
  if ((e.roles || []).includes('ADMIN') || e.empNo === session.id) return dash();
  const box = document.createElement('div');
  box.className = 'row-actions';
  const admin = userRoles.includes('ADMIN');
  if (e.status === 'APPROVED' && !approvalExpired(e)) {
    // approved, not created yet: the admin may take the approval back (the seat frees, the 사번 stays used)
    if (seatOf(e) === 'WAIT' && admin) box.append(writeButton('활성화', `${e.name} 활성화`, () => activateWaiting(e.empNo, renderEmployees), true));
    if (admin) box.append(allowedButton('승인 취소', `${e.name} 승인 취소`, () => cancelApproved(e.empNo)));
    else return dash();
  } else if (seatOf(e) === 'WAIT') {
    if (admin) box.append(writeButton('활성화', `${e.name} 활성화`, () => activateWaiting(e.empNo, renderEmployees), true));
    box.append(allowedButton('비활성화', `${e.name} 비활성화`, () => confirmState(e.empNo)));
  } else if (e.status === 'ACTIVE') {
    box.append(allowedButton('비활성화', `${e.name} 비활성화`, () => confirmState(e.empNo)));
    if (admin) box.append(writeButton(e.head ? '부서장 해제' : '부서장 지정', `${e.name} ${e.head ? '부서장 해제' : '부서장 지정'}`, () => setHead(e.empNo, !e.head)));
  } else if (e.status === 'RETIRED') {
    box.append(writeButton('다시 사용', `${e.name} 다시 사용`, () => confirmState(e.empNo)));
  } else {
    return dash();
  }
  return box;
}

// 부서장 지정: one per department (v1.7 3.7). The head approves that department's join requests (HEAD at sign-in).
function setHead(no, on) {
  const code = session.company;
  const list = staffOf(code);
  const e = list.find(x => x.empNo === no);
  if (!e || e.status !== 'ACTIVE') return;
  list.forEach(x => { if (x.dept === e.dept) delete x.head; });
  if (on) e.head = true;
  saveStaff(code, list);
  showToast(on ? `${e.name}님을 ${e.dept} 부서장으로 지정했습니다. 그 부서의 가입 요청을 승인할 수 있습니다` : `${e.name}님의 부서장 지정을 해제했습니다`);
  rerenderStaffViews();
}

// 승인 취소 (CFG-02): an approved account that was never created goes back to a free seat; its 사번 is not reused
function cancelApproved(no) {
  const e = employeesOf(session.company).find(x => x.empNo === no);
  if (!e || e.status !== 'APPROVED') return;
  askConfirm(`${e.name}님의 승인을 취소할까요?`,
    `사번 ${e.empNo}. 취소하면 이 자리는 다시 비고, 직원은 계정을 만들 수 없어 다시 승인 요청을 해야 합니다. 배정했던 사번은 다시 쓰지 않습니다.`, '승인 취소', () => {
      const code = session.company;
      const list = staffOf(code);
      const x = list.find(y => y.empNo === no);
      if (!x || x.status !== 'APPROVED') return rerenderStaffViews();
      x.status = 'CANCELED';
      x.canceledAt = new Date().toISOString();
      delete x.wait;
      saveStaff(code, list);
      showToast(`${x.name}님의 승인을 취소했습니다`);
      rerenderStaffViews();
    });
}

// A deactivated account comes back where it was: 계정 생성 전 if it was never created, otherwise 사용 중.
// Both take a seat.
const backTo = e => (e.before === 'APPROVED' ? 'APPROVED' : 'ACTIVE');

let stateTarget = null;
function confirmState(no) {
  const e = employeesOf(session.company).find(x => x.empNo === no);
  if (!e) return;
  const off = e.status !== 'RETIRED';
  // coming back takes a seat: refused first if none is free
  if (!off && isFull(session.company)) return showSeatDialog();
  stateTarget = no;
  const dialog = document.getElementById('empStateDialog');
  dialog.querySelector('h2').textContent = off ? `${e.name} 계정을 비활성화할까요?` : `${e.name} 계정을 다시 사용할까요?`;
  dialog.querySelector('p').textContent = off
    ? `사번 ${e.empNo}. 비활성화하면 이 직원은 로그인할 수 없고, 결재·게시글 같은 기록은 그대로 남습니다. 쓰던 자리가 비어 다른 직원을 승인할 수 있고, 나중에 다시 사용으로 되돌릴 수 있습니다.`
    : `사번 ${e.empNo}. 다시 사용하면 이 직원이 같은 사번으로 다시 로그인할 수 있고, 계정 한 자리를 씁니다.`;
  dialog.querySelector('[value="ok"]').textContent = off ? '비활성화' : '다시 사용';
  if (!dialog.open) dialog.showModal();
}
document.querySelectorAll('#empStateDialog button').forEach(b => b.addEventListener('click', () => {
  document.getElementById('empStateDialog').close();
  const no = stateTarget;
  stateTarget = null;
  if (b.value !== 'ok' || !no) return;
  const code = session.company;
  const list = staffOf(code);
  const e = list.find(x => x.empNo === no);
  if (!e) return;
  if (e.status === 'RETIRED') {
    // checked again: another tab may have used the last seat while the dialog was open
    if (isFull(code)) return showSeatDialog();
    e.status = backTo(e);
    e.wait = false;
    delete e.before;
    showToast(`${e.name} 계정을 다시 사용합니다`);
  } else {
    const freedSeat = seatOf(e) === 'SEAT';
    e.before = e.status;
    e.status = 'RETIRED';
    const waiting = list.filter(x => seatOf(x) === 'WAIT').length;
    showToast(freedSeat && waiting
      ? `${e.name} 계정을 비활성화했습니다. 자리 대기 ${waiting}명이 있으니 가입 승인에서 골라 활성화하세요`
      : `${e.name} 계정을 비활성화했습니다`);
  }
  saveStaff(code, list);
  rerenderStaffViews();
}));

// No seat left: why, and for a trial admin a way to the payment page
function showSeatDialog() {
  const dialog = document.getElementById('seatDialog');
  const admin = userRoles.includes('ADMIN');
  const caps = seatCaps(session.company);
  const paid = caps && caps.paid;
  dialog.querySelector('h2').textContent = paid ? '승인할 수 있는 자리가 없습니다' : '계정을 더 쓸 수 없습니다';
  dialog.querySelector('p').textContent = paid
    ? `구매 인원 ${caps.active}명에 자리 대기까지 ${caps.approve}명이 모두 찼습니다. 잘못 승인된 계정이나 쓰지 않는 직원을 비활성화한 뒤 다시 해 주세요.`
    : `체험 중에는 관리자를 포함해 ${TRIAL_SEATS}명까지 쓸 수 있습니다. 쓰지 않는 직원을 비활성화하면 그만큼 다시 승인할 수 있고, 결제하면 구매한 인원만큼 쓸 수 있습니다.`
      + (admin ? '' : ' 결제는 업체 관리자에게 요청하세요.');
  dialog.querySelector('[value="pay"]').hidden = paid || !admin;
  if (!dialog.open) dialog.showModal();
}
document.querySelectorAll('#seatDialog button').forEach(b => b.addEventListener('click', () => {
  document.getElementById('seatDialog').close();
  if (b.value === 'pay') location.hash = 'admin-billing';
}));

// Signing out or switching the demo role closes any open approval dialog
function resetEmployeePage() {
  const d = document.getElementById('approveDialog');
  if (d && d.open) d.close();
}

// ---------- 가입 승인 ----------
// The company admin sees every request and the 자리 대기 list; a 부서장 sees their own team's requests only.
function visibleRequests() {
  const all = staffOf(session.company).filter(e => e.status === 'REQUESTED');
  if (userRoles.includes('ADMIN')) return all;
  const dept = myDept();
  return all.filter(e => e.dept === dept);
}

// 가입 활성화 box: this person's own scope (부서장 = their department, 업체 관리자 = the whole company), its end
// time, and the other open scopes (the admin sees and can turn off all of them; a 부서장 sees the company-wide one)
const myJoinScope = () => (userRoles.includes('ADMIN') ? JOIN_OPEN_ALL : myDept());
function renderJoinOpen() {
  const code = session.company;
  const admin = userRoles.includes('ADMIN');
  const scope = myJoinScope();
  const list = joinOpenings(code);
  const mine = list.find(x => x.scope === scope);
  const what = admin ? '업체의 모든 부서' : `${scope || '내'} 부서`;
  $j('joinOpenBox').classList.toggle('is-on', !!mine);
  $j('joinOpenNote').textContent = mine
    ? `켜져 있습니다 · ${hhmm(mine.until)}까지 (${minsUntil(mine.until)}분 남음). 이 동안 직원이 계정 생성에서 업체 코드를 확인하면 ${what}로 가입을 요청할 수 있습니다.`
    : `꺼져 있습니다. 켜 둔 동안만 직원이 계정 생성에서 업체 코드를 확인할 수 있고, 꺼져 있으면 업체 코드를 넣어도 아무 반응이 없습니다. 켜면 ${what}가 열리고, 고른 시간이 지나면 저절로 꺼집니다.`;
  const btn = $j('btnJoinOpen');
  btn.textContent = mine ? '끄기' : '가입 활성화';
  btn.classList.toggle('btn-primary', !mine);
  btn.setAttribute('aria-label', mine ? `${scopeLabel(scope)} 가입 활성화 끄기` : `${scopeLabel(scope)} 가입 활성화`);
  if (readOnly) btn.setAttribute('aria-disabled', 'true'); else btn.removeAttribute('aria-disabled');
  $j('joinOpenTime').disabled = !!mine;
  $j('joinOpenTime').dispatchEvent(new Event('change')); // redraws the custom dropdown (select.js)
  const others = list.filter(x => x.scope !== scope && (admin || x.scope === JOIN_OPEN_ALL));
  const ul = $j('joinOpenList');
  ul.hidden = !others.length;
  ul.replaceChildren(...others.map(x => {
    const li = document.createElement('li');
    const text = document.createElement('span');
    text.textContent = `${scopeLabel(x.scope)} · ${x.byName || x.by} 님이 켬 · ${hhmm(x.until)}까지 (${minsUntil(x.until)}분 남음)`;
    li.append(text);
    if (admin) li.append(writeButton('끄기', `${scopeLabel(x.scope)} 가입 활성화 끄기`, () => turnJoinOff(x.scope)));
    return li;
  }));
}
function turnJoinOff(scope) {
  setJoinOpening(session.company, scope, 0);
  showToast(`${scopeLabel(scope)} 가입 활성화를 껐습니다`);
  renderJoin();
}
document.getElementById('btnJoinOpen').addEventListener('click', () => {
  const scope = myJoinScope();
  if (!scope) return showToast('부서가 정해지지 않아 가입을 열 수 없습니다');
  const on = joinOpenings(session.company).some(x => x.scope === scope);
  if (on) return turnJoinOff(scope);
  const minutes = Number($j('joinOpenTime').value) || 60;
  setJoinOpening(session.company, scope, minutes);
  const until = joinOpenings(session.company).find(x => x.scope === scope).until;
  showToast(`${scopeLabel(scope)} 가입을 ${hhmm(until)}까지 열었습니다. 직원에게 업체 코드를 알려 주세요`);
  renderJoin();
});

function renderJoin() {
  const code = session.company;
  renderJoinOpen();
  const caps = seatCaps(code);
  const admin = userRoles.includes('ADMIN');
  const n = activeStaff(code).length;
  const w = waitingStaff(code).length;
  const reqs = visibleRequests().sort((a, b) => new Date(b.requestAt) - new Date(a.requestAt));
  const open = reqs.filter(e => !requestExpired(e));
  const set = (id, v) => { document.getElementById(id).textContent = v; };

  set('joinOpen', `${open.length}건`);
  set('joinActive', caps ? `${n} / ${caps.active}명` : `${n}명`);
  set('joinRoom', caps ? `${Math.max(0, caps.approve - n - w)}명` : '-');
  set('joinSeatWait', caps && caps.paid ? `${w}명` : '-');
  set('joinScope', admin ? '업체의 모든 가입 요청을 처리합니다.' : `${myDept() || '내'} 부서의 가입 요청만 처리합니다.`);
  set('joinNote', !caps ? '' : (caps.paid
    ? `구매 인원 ${caps.active}명까지 사용 중으로 바뀌고, 잘못 승인한 경우를 대비해 ${caps.approve}명까지 승인할 수 있습니다. 구매 인원을 넘는 승인은 자리 대기가 되며, 업체 관리자가 자리가 날 때 직접 골라 활성화합니다.`
    : `체험 중에는 관리자를 포함해 ${caps.active}명까지 승인할 수 있습니다.`)
    + ` 승인하면 다음 사번(지금은 ${nextEmpNo(code)})이 순서대로 붙고 바로 계정이 만들어집니다. 직원은 요청할 때 정한 비밀번호로 로그인합니다(이메일은 요청할 때 인증함). 요청 뒤 1시간이 지나면 만료되어 다시 요청해야 합니다.`);

  const rows = reqs.map(e => {
    const tr = document.createElement('tr');
    const expired = requestExpired(e);
    tr.classList.toggle('is-inactive', expired);
    const left = document.createElement('span');
    left.className = expired ? 'muted' : minutesLeft(e) <= 10 ? 'join-left is-late' : 'join-left';
    left.textContent = expired ? '만료 (다시 요청 필요)' : `${minutesLeft(e)}분 남음`;
    let actions;
    if (expired) {
      actions = dash();
    } else {
      actions = document.createElement('div');
      actions.className = 'row-actions';
      actions.append(
        writeButton('승인', `${e.name} 가입 승인`, () => openApprove(e.reqId), true),
        writeButton('반려', `${e.name} 가입 반려`, () => rejectJoin(e.reqId)),
      );
    }
    tr.append(
      cellOf('이름', e.name),
      cellOf('부서·직급', `${e.dept} · ${e.rank}`),
      cellOf('이메일', e.email),
      cellOf('요청 시각', whenAt(e.requestAt)),
      cellOf('남은 시간', left),
      cellOf('처리', actions),
    );
    return tr;
  });
  document.getElementById('joinList').replaceChildren(...rows);
  document.getElementById('joinEmpty').hidden = rows.length > 0;

  // 자리 대기: paid companies, company admin only
  const box = document.getElementById('seatWaitBox');
  box.hidden = !(caps && caps.paid && admin);
  if (!box.hidden) {
    const free = caps.active - n;
    set('seatWaitNote', free > 0
      ? `지금 ${free}자리가 비어 있습니다. 쓸 직원을 골라 활성화하세요.`
      : '빈 자리가 없습니다. 사원 관리에서 잘못 승인된 계정이나 쓰지 않는 직원을 비활성화하면 자리가 생깁니다.');
    const waits = waitingStaff(code).map(e => {
      const tr = document.createElement('tr');
      const actions = document.createElement('div');
      actions.className = 'row-actions';
      actions.append(
        writeButton('활성화', `${e.name} 활성화`, () => activateWaiting(e.empNo, renderJoin), true),
        e.status === 'APPROVED'
          ? allowedButton('승인 취소', `${e.name} 승인 취소`, () => cancelApproved(e.empNo))
          : allowedButton('비활성화', `${e.name} 비활성화`, () => confirmState(e.empNo)),
      );
      tr.append(
        cellOf('이름', whoCell(e)),
        cellOf('부서·직급', `${e.dept} · ${e.rank}`),
        cellOf('승인 시각', e.approvedAt ? whenAt(e.approvedAt) : '-'),
        cellOf('처리', actions),
      );
      return tr;
    });
    document.getElementById('seatWaitList').replaceChildren(...waits);
    document.getElementById('seatWaitEmpty').hidden = waits.length > 0;
  }
  refreshJoinBadge();
}

// Where an approval would land right now: SEAT, WAIT (paid, within 120%) or null (no room)
function approvalSlot(code) {
  const caps = seatCaps(code);
  const n = activeStaff(code).length;
  const w = waitingStaff(code).length;
  if (caps && n < caps.active) return 'SEAT';
  if (caps && caps.paid && n + w < caps.approve) return 'WAIT';
  return null;
}

// Approve: a dialog shows the 사번 that will be given and, for the company admin, the roles to give
let approveTarget = null;
function openApprove(reqId) {
  const code = session.company;
  const e = staffOf(code).find(x => x.reqId === reqId);
  if (!e || e.status !== 'REQUESTED') return rerenderStaffViews();
  if (requestExpired(e)) {
    showToast('요청 뒤 1시간이 지나 만료된 요청입니다. 직원이 다시 요청해야 합니다');
    return rerenderStaffViews();
  }
  const slot = approvalSlot(code);
  if (!slot) return showSeatDialog();
  approveTarget = reqId;
  let dialog = document.getElementById('approveDialog');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'approveDialog';
    dialog.className = 'modal';
    dialog.innerHTML = '<h2></h2><p></p><fieldset class="approve-roles"><legend>역할 <span class="muted">(없으면 일반 직원)</span></legend>'
      + '<label><input type="checkbox" value="IT"> IT 담당</label><label><input type="checkbox" value="HR"> 인사 담당</label></fieldset>'
      + '<div class="modal-actions"><button type="button" class="btn" value="cancel">취소</button><button type="button" class="btn btn-primary" value="ok">승인</button></div>';
    dialog.querySelectorAll('.modal-actions button').forEach(b => b.addEventListener('click', () => {
      dialog.close();
      const id = approveTarget;
      approveTarget = null;
      if (b.value === 'ok' && id) approveJoin(id, [...dialog.querySelectorAll('.approve-roles input:checked')].map(c => c.value));
    }));
    document.body.append(dialog);
  }
  dialog.querySelector('h2').textContent = `${e.name}님의 가입을 승인할까요?`;
  dialog.querySelector('p').textContent = `${e.dept} · ${e.rank} · ${e.email}. 승인하면 사번 ${nextEmpNo(code)}이 붙고 바로 계정이 만들어집니다. 직원은 요청할 때 정한 비밀번호로 로그인합니다(이메일은 요청할 때 인증함).`
    + (slot === 'WAIT' ? ' 구매 인원이 모두 차서 자리 대기로 승인됩니다.' : '');
  // roles: the company admin only (who may give roles is still open, guide section 3); never 업체 관리자
  const roles = dialog.querySelector('.approve-roles');
  roles.hidden = !userRoles.includes('ADMIN');
  roles.querySelectorAll('input').forEach(c => { c.checked = false; });
  if (!dialog.open) dialog.showModal();
}

function approveJoin(reqId, roles) {
  const code = session.company;
  const list = staffOf(code);
  const e = list.find(x => x.reqId === reqId);
  if (!e || e.status !== 'REQUESTED' || requestExpired(e)) {
    showToast('이미 처리되었거나 만료된 요청입니다');
    return rerenderStaffViews();
  }
  if (!userRoles.includes('ADMIN') && e.dept !== myDept()) {
    showToast('내 부서의 가입 요청만 처리할 수 있습니다');
    return rerenderStaffViews();
  }
  // checked again: another tab may have used the last seat while the dialog was open
  const slot = approvalSlot(code);
  if (!slot) return showSeatDialog();
  // the account is made now, with the password the employee set at the request
  const now = new Date().toISOString();
  Object.assign(e, {
    empNo: nextEmpNo(code), status: slot === 'SEAT' ? 'ACTIVE' : 'WAITING',
    roles: userRoles.includes('ADMIN') ? roles.filter(r => r !== 'ADMIN') : [],
    approvedAt: now, approvedBy: session.id, createdAt: now, at: now,
  });
  delete e.wait;
  saveStaff(code, list);
  showToast(slot === 'SEAT'
    ? `${e.name}님을 승인했습니다. 사번 ${e.empNo}로 계정이 만들어졌고 바로 로그인할 수 있습니다`
    : `${e.name}님을 승인했습니다(사번 ${e.empNo}). 구매 인원이 모두 차서 자리 대기 계정으로 만들었습니다`);
  rerenderStaffViews();
}

// Reject: the request ends; the employee's screen says so and they can ask again
function rejectJoin(reqId) {
  const e = staffOf(session.company).find(x => x.reqId === reqId);
  if (!e) return;
  askConfirm(`${e.name}님의 가입 요청을 반려할까요?`,
    `${e.dept} · ${e.email}. 반려하면 이 요청은 끝나고, 직원은 업체에 확인한 뒤 계정 생성에서 다시 요청할 수 있습니다.`, '반려', () => {
      const code = session.company;
      const list = staffOf(code);
      const x = list.find(y => y.reqId === reqId);
      if (!x || x.status !== 'REQUESTED') return rerenderStaffViews();
      if (!userRoles.includes('ADMIN') && x.dept !== myDept()) { showToast('내 부서의 가입 요청만 처리할 수 있습니다'); return rerenderStaffViews(); }
      x.status = 'REJECTED';
      x.rejectedAt = new Date().toISOString();
      saveStaff(code, list);
      showToast(`${x.name}님의 가입 요청을 반려했습니다`);
      rerenderStaffViews();
    });
}

// 자리 대기 → 사용 중, picked by the company admin, only when a seat is free. An approved account that isn't
// created yet just gets its seat (it becomes 사용 중 when the employee creates it).
function activateWaiting(no, after) {
  const code = session.company;
  const list = staffOf(code);
  const e = list.find(x => x.empNo === no);
  if (!e || seatOf(e) !== 'WAIT') return after();
  if (isFull(code)) {
    showToast('빈 자리가 없습니다. 잘못 승인된 계정이나 쓰지 않는 직원을 먼저 비활성화해 주세요');
    return;
  }
  if (e.status === 'WAITING') e.status = 'ACTIVE';
  e.wait = false;
  saveStaff(code, list);
  showToast(e.status === 'ACTIVE'
    ? `${e.name} 계정을 활성화했습니다. 이제 로그인할 수 있습니다`
    : `${e.name}님에게 자리를 주었습니다. 계정을 만들면 바로 로그인할 수 있습니다`);
  rerenderStaffViews();
}

// A small yes/no dialog made on first use (the reject confirmation)
function askConfirm(title, body, okLabel, onOk) {
  let dialog = document.getElementById('askDialog');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'askDialog';
    dialog.className = 'modal';
    dialog.innerHTML = '<h2></h2><p></p><div class="modal-actions"><button type="button" class="btn" value="cancel">취소</button><button type="button" class="btn btn-primary" value="ok"></button></div>';
    dialog.querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
      dialog.close();
      const fn = dialog.onOk;
      dialog.onOk = null;
      if (b.value === 'ok' && fn) fn();
    }));
    document.body.append(dialog);
  }
  dialog.querySelector('h2').textContent = title;
  dialog.querySelector('p').textContent = body;
  dialog.querySelector('[value="ok"]').textContent = okLabel;
  dialog.onOk = onOk;
  if (!dialog.open) dialog.showModal();
}

// Badge on the 가입 승인 menu: open requests this person can act on
function refreshJoinBadge() {
  const badge = document.getElementById('joinCount');
  if (!badge) return;
  const canApprove = session && session.type === 'EMPLOYEE' && (userRoles.includes('ADMIN') || userRoles.includes('HEAD'));
  const count = canApprove ? visibleRequests().filter(e => !requestExpired(e)).length : 0;
  badge.textContent = count;
  badge.hidden = count === 0;
}

// The signed-in employee's own account stopped being usable (deactivated or moved to 자리 대기 in another tab or
// by the admin): the reason to show on the login screen, or '' when it's still fine. Accounts with no staff
// record (the IEUM operator) are left alone. The real server ends such sessions itself.
function staffBlockedMessage() {
  if (!session || session.type !== 'EMPLOYEE') return '';
  const me = employeesOf(session.company).find(e => e.empNo === session.id);
  if (!me || me.status === 'ACTIVE') return '';
  return me.status === 'RETIRED' ? '사용이 중지된 계정입니다. 업체 관리자에게 문의해 주세요'
    : '이 계정은 지금 사용할 수 없습니다. 업체 관리자에게 문의해 주세요';
}

function rerenderStaffViews() {
  const blocked = staffBlockedMessage();
  if (blocked) return signOut(blocked);
  const view = location.hash.slice(1);
  if (view === 'hr-employee') renderEmployees();
  if (view === 'hr-join') renderJoin();
  refreshJoinBadge();
}
// The remaining minutes count down every 30 s, only while the tab is visible: 가입 승인 is redrawn when it is
// on screen, otherwise just the menu badge (an expired request drops out of it). Coming back to the tab
// refreshes at once.
function tickJoin() {
  if (document.hidden || !session || session.type !== 'EMPLOYEE') return;
  if (location.hash.slice(1) === 'hr-join') renderJoin();
  refreshJoinBadge();
}
setInterval(tickJoin, 30000);
document.addEventListener('visibilitychange', tickJoin);

// Sign-in of an employee who made an account through 계정 생성 (called by common.js after the operator and
// trial-admin accounts). null = no such account with this password; { error } = it exists but can't sign in.
function staffSignIn(company, id, pw) {
  const order = company && company !== 'ieum0000' ? findCompanyOrder(company) : null;
  const e = order ? employeesOf(company).find(x => x.empNo === id && x.pw && x.pw === pw) : null;
  if (!e) return null;
  if (e.status === 'WAITING') return { error: '계정은 만들어졌지만 사용할 자리가 없어 아직 쓸 수 없습니다. 업체 관리자에게 문의해 주세요.' };
  if (e.status === 'RETIRED') return { error: '사용이 중지된 계정입니다. 업체 관리자에게 문의해 주세요' };
  if (e.status !== 'ACTIVE') return null;
  return {
    account: {
      type: 'EMPLOYEE', company, id, companyName: order.company, name: e.name, sub: `${e.dept} · ${e.rank}`, dept: e.dept,
      roles: ['EMPLOYEE', ...(e.roles || []), ...(isHeadOf(e) ? ['HEAD'] : [])].join(' '),
    },
  };
}

// ---------- 로그인 화면: 계정 생성 ----------
// One form, three states: request (confirm the company while joining is open, fill in, verify the e-mail, set
// the password, press 승인 요청) → waiting ("승인 요청을 하였습니다") → approved (the account exists: the 사번
// and a way to the login). The e-mail is verified before the request (user decided 2026-10-28), so an approver
// only ever sees requests with a working address. This device remembers its request.
const loginForm = document.getElementById('loginForm');
const joinForm = document.getElementById('joinForm');
const $j = id => document.getElementById(id);
const JOIN_FIELDS = ['joinCompany', 'joinDept', 'joinRank', 'joinName', 'joinEmail', 'joinCode', 'joinPw', 'joinPw2'];
let checkedCompany = ''; // the company code confirmed with 확인 ('' = not confirmed)
let joinNotice = ''; // why the last request ended (반려, 만료, 없음); cleared when the form is changed

// The department and rank lists show the confirmed company's own; before that they hold only the hint and are off
function fillSelect(id, list, pick, code) {
  const sel = $j(id);
  const keep = sel.value;
  sel.replaceChildren(new Option(code ? pick : '업체 코드를 먼저 확인하세요', ''), ...list.map(d => new Option(d, d)));
  sel.value = list.includes(keep) ? keep : '';
  sel.dispatchEvent(new Event('change')); // redraws the custom dropdown (select.js)
}
// the open departments only (all of them when a stored request is shown again, so its department stays readable)
function setDeptOptions(code, all) {
  fillSelect('joinDept', code ? (all ? deptsOf(code) : openDeptsOf(code)) : [], '부서를 고르세요', code);
  fillSelect('joinRank', code ? ranksOf(code) : [], '직급을 고르세요', code);
}
const companyLoaded = code => `가입이 열린 부서 ${openDeptsOf(code).length}개, 직급 ${ranksOf(code).length}개`;
function setCompanyNote(text, ok) {
  const note = $j('joinCompanyNote');
  note.textContent = text;
  note.hidden = !text;
  note.classList.toggle('is-ok', !!text && ok);
  note.classList.toggle('is-bad', !!text && !ok);
}
// why a company can't take a join request now ('' = it can); checked at 확인 and again at 승인 요청
function joinBlock(order) {
  if (!order) return '업체 코드를 확인해 주세요';
  if (order.emailVerified === false) return '업체의 이메일 인증이 아직 끝나지 않아 가입할 수 없습니다. 업체 관리자에게 문의해 주세요.';
  if (trialState(order).status === 'EXPIRED') return '이용 기간이 끝나 지금은 가입할 수 없습니다. 업체 관리자에게 문의해 주세요.';
  return '';
}
const companyOrder = code => (code && code !== 'ieum0000' ? findCompanyOrder(code) : null);

// 확인 tries: a code that can't be joined now (unknown, joining off, company not usable) counts as wrong. 5 wrong
// tries within 10 minutes lock 확인 for 10 minutes and say how long (requirements v1.8 2; a right code isn't
// counted). The real server counts per IP; the prototype per browser.
const CHECK_FAIL_KEY = 'ieum-demo-join-check-v1';
const CHECK_WINDOW_MS = 10 * 60 * 1000;
const CHECK_FAILS = 5;
const CHECK_LOCK_MS = 10 * 60 * 1000;
function checkLockedUntil() {
  const s = loadStore(CHECK_FAIL_KEY, {});
  return s.until && new Date(s.until).getTime() > Date.now() ? s.until : '';
}
function noteCheckFail() {
  const s = loadStore(CHECK_FAIL_KEY, {});
  const now = Date.now();
  const fails = (s.fails || []).filter(t => now - t < CHECK_WINDOW_MS).concat(now);
  saveStore(CHECK_FAIL_KEY, fails.length >= CHECK_FAILS ? { fails: [], until: new Date(now + CHECK_LOCK_MS).toISOString() } : { fails });
}
const lockNote = until => `업체 코드 확인을 잠시 막았습니다. ${minsUntil(until)}분 뒤(${hhmm(until)})에 다시 해 주세요.`;

// 확인: while joining is open, load the open departments and the ranks. Otherwise nothing happens on screen
// (a closed company looks exactly like a code that doesn't exist), until the tries are used up.
function checkCompany() {
  const locked = checkLockedUntil();
  if (locked) return setCompanyNote(lockNote(locked), false);
  const code = $j('joinCompany').value.trim().toLowerCase();
  if (!code) return;
  const order = companyOrder(code);
  if (joinBlock(order) || !openDeptsOf(code).length) {
    noteCheckFail();
    const nowLocked = checkLockedUntil();
    if (nowLocked) setCompanyNote(lockNote(nowLocked), false);
    return;
  }
  checkedCompany = code;
  setCompanyNote(`${order.company} · ${companyLoaded(code)}를 불러왔습니다`, true);
  setDeptOptions(checkedCompany);
  $j('joinDept').disabled = $j('joinRank').disabled = false;
  syncRequestButton();
  // after select.js has turned the dropdown on (it redraws on the next tick)
  setTimeout(() => $j('joinDeptBtn')?.focus());
}
$j('btnJoinCheck').addEventListener('click', checkCompany);
$j('joinCompany').addEventListener('keydown', e => {
  if (e.key === 'Enter') { e.preventDefault(); checkCompany(); }
});
// a changed code needs 확인 again: the departments of the old one are cleared
function onCompanyInput() {
  if (checkedCompany && $j('joinCompany').value.trim().toLowerCase() === checkedCompany) return;
  checkedCompany = '';
  setCompanyNote('', false);
  setDeptOptions('');
  $j('joinDept').disabled = $j('joinRank').disabled = true;
  syncRequestButton();
}
$j('joinCompany').addEventListener('input', onCompanyInput);
setDeptOptions('');
$j('joinDept').disabled = $j('joinRank').disabled = true;

const loadMyJoin = () => loadStore(MY_JOIN_KEY, null);
const saveMyJoin = v => { try { if (v) localStorage.setItem(MY_JOIN_KEY, JSON.stringify(v)); else localStorage.removeItem(MY_JOIN_KEY); } catch { /* storage blocked */ } };
// the request this device is following, as stored by the company (null when it is gone)
function myRequest() {
  const mine = loadMyJoin();
  return mine ? staffOf(mine.company).find(e => e.reqId === mine.reqId) || null : null;
}

// 승인 요청 button: grey and off until the company code is confirmed, the e-mail typed now is verified and the
// password is set (8+ characters, typed twice the same), then blue. The line above it says what is still missing.
const pwReady = () => $j('joinPw').value.length >= 8 && $j('joinPw').value === $j('joinPw2').value;
function syncRequestButton() {
  const b = $j('btnJoinRequest');
  const company = !!checkedCompany && $j('joinCompany').value.trim().toLowerCase() === checkedCompany;
  const email = emailVerifiedNow();
  const pw = pwReady();
  const on = !b.dataset.locked && company && email && pw;
  b.disabled = !on;
  b.classList.toggle('btn-primary', on);
  const hint = $j('joinRequestHint');
  hint.hidden = !!b.dataset.locked || on;
  const missing = [!company && '업체 코드 확인', !email && '이메일 인증', !pw && '비밀번호 정하기(8자 이상, 확인과 같게)'].filter(Boolean);
  hint.textContent = `승인 요청 전에 남은 것: ${missing.join(' · ')}`;
}

function setJoinStatus(text, kind) {
  const s = $j('joinStatus');
  s.textContent = text;
  s.className = `join-status${kind ? ` is-${kind}` : ''}`;
  s.hidden = !text;
}

// Draw the form for the state of this device's request
function renderJoinForm() {
  const e = myRequest();
  const mine = loadMyJoin();
  const waiting = !!e && e.status === 'REQUESTED' && !requestExpired(e);
  // approved = the account was made by the approval (사용 중, or 자리 대기 past the headcount)
  const approved = !!e && ['ACTIVE', 'WAITING'].includes(e.status);
  // the request fields are locked once sent (until it ends)
  JOIN_FIELDS.forEach(id => { $j(id).disabled = waiting || approved; });
  $j('btnJoinCheck').disabled = waiting || approved;
  $j('btnJoinRequest').dataset.locked = waiting || approved ? '1' : '';
  $j('btnJoinRequest').hidden = approved;
  $j('btnJoinCancel').hidden = !waiting;
  $j('joinAccount').hidden = !approved;
  $j('btnJoinCreate').hidden = !approved;
  $j('joinError').textContent = '';
  if (e) {
    // fill from the stored request when the screen was opened again
    $j('joinCompany').value = mine.company;
    $j('joinName').value = e.name;
    // the request was made for this company, so it counts as confirmed; its department stays shown even if
    // joining has been closed since
    checkedCompany = mine.company;
    setCompanyNote((companyOrder(mine.company) || {}).company || mine.company, true);
    setDeptOptions(mine.company, true);
    $j('joinDept').value = e.dept;
    $j('joinDept').dispatchEvent(new Event('change')); // redraws the custom dropdown (select.js)
    $j('joinRank').value = e.rank;
    $j('joinRank').dispatchEvent(new Event('change'));
    $j('joinEmail').value = e.email;
    joinCode = null;
    verifiedEmail = e.emailVerified ? e.email.toLowerCase() : '';
    $j('joinCode').hidden = true;
    setCodeNote(e.emailVerified ? '이메일 인증을 마쳤습니다.' : '', true);
    $j('joinPw').value = $j('joinPw2').value = waiting || approved ? '********' : '';
  }
  if (waiting) {
    setJoinStatus(`승인 요청을 하였습니다. 업체 관리자나 부서장이 승인하면 바로 계정이 만들어집니다. (${minutesLeft(e)}분 안에 승인되지 않으면 다시 요청해야 합니다)`, 'wait');
  } else if (approved) {
    setJoinStatus(e.status === 'WAITING'
      ? '승인되어 계정이 만들어졌습니다. 구매 인원이 모두 차서 업체 관리자가 자리를 주면 로그인할 수 있습니다.'
      : '승인되어 계정이 만들어졌습니다. 업체 코드와 아래 사번, 요청할 때 정한 비밀번호로 로그인하세요.', 'ok');
    $j('joinId').textContent = e.empNo;
  } else if (e && ['APPROVED', 'CANCELED'].includes(e.status)) {
    // made by the earlier flow (approval first, account later): asked again in the new flow
    joinNotice = '이전 방식으로 처리된 요청입니다. 다시 승인 요청을 해 주세요.';
    saveMyJoin(null);
  } else if (e && e.status === 'RETIRED') {
    joinNotice = '사용이 중지된 계정입니다. 업체 관리자에게 문의해 주세요.';
    saveMyJoin(null);
  } else if (e && e.status === 'REJECTED') {
    joinNotice = '가입 요청이 반려되었습니다. 업체에 확인한 뒤 내용을 고쳐 다시 승인 요청을 해 주세요.';
    saveMyJoin(null);
  } else if (e && requestExpired(e)) {
    joinNotice = '요청 뒤 1시간 안에 승인되지 않아 만료되었습니다. 다시 승인 요청을 해 주세요.';
    saveMyJoin(null);
  } else if (mine && !e) {
    joinNotice = '요청을 찾을 수 없습니다. 다시 승인 요청을 해 주세요.';
    saveMyJoin(null);
  }
  // an ended request's reason stays on screen until the form is changed (a later refresh has no request to show)
  if (!waiting && !approved) setJoinStatus(joinNotice, joinNotice ? 'bad' : '');
  // the department list is on only for a confirmed company while the form can still be changed
  $j('joinDept').disabled = $j('joinRank').disabled = waiting || approved || !checkedCompany;
  $j('btnJoinSend').disabled = waiting || approved || emailVerifiedNow();
  syncRequestButton();
}

// 이메일 인증 (before 승인 요청): 인증 메일 보내기 sends a 6-digit code to the typed address; typing the code
// verifies that address. Changing the address afterwards needs a new code. The real server mails the code
// (POST /join/email-code) and checks it (POST /join/email-verify); the prototype shows it on screen instead.
// Codes (v1.7 ACC-04, also used by 비밀번호 찾기): 6 digits, good for 10 minutes, dead after 5 wrong tries,
// and a new one no more than once a minute. The real server enforces these (and 5 resends a day).
const CODE_TTL_MS = 10 * 60 * 1000;
const CODE_TRIES = 5;
const RESEND_WAIT_S = 60;
const newCode = () => ({ value: String(Math.floor(100000 + Math.random() * 900000)), at: Date.now(), tries: 0 });
// null = right; otherwise why not (and whether a new code is needed now)
function checkCode(code, typed) {
  if (!code) return { why: '인증 메일을 먼저 받아 주세요.', dead: false };
  if (Date.now() - code.at > CODE_TTL_MS) return { why: '인증 코드는 10분 동안만 쓸 수 있습니다. 인증 메일을 다시 받아 주세요.', dead: true };
  if (typed === code.value) return null;
  code.tries += 1;
  if (code.tries >= CODE_TRIES) return { why: `인증 코드를 ${CODE_TRIES}번 틀려 이 코드는 쓸 수 없습니다. 인증 메일을 다시 받아 주세요.`, dead: true };
  return { why: `인증 코드가 맞지 않습니다. (${CODE_TRIES - code.tries}번 더 틀리면 다시 받아야 합니다)`, dead: false };
}
// the send button rests for a minute after each mail, counting down on the button
const resendTimers = new WeakMap();
function restSendButton(btn, label) {
  clearInterval(resendTimers.get(btn));
  let left = RESEND_WAIT_S;
  const tick = () => {
    if (left <= 0) { clearInterval(resendTimers.get(btn)); resendTimers.delete(btn); btn.disabled = false; btn.textContent = label; return; }
    btn.disabled = true;
    btn.textContent = `${label} (${left}초)`;
    left -= 1;
  };
  tick();
  resendTimers.set(btn, setInterval(tick, 1000));
}
function freeSendButton(btn, label) {
  clearInterval(resendTimers.get(btn));
  resendTimers.delete(btn);
  btn.disabled = false;
  btn.textContent = label;
}

let joinCode = null; // the code sent last ({ value, at, tries }), for joinCodeFor
let joinCodeFor = ''; // the address it was sent to
let verifiedEmail = ''; // the address verified ('' = none)
const typedEmail = () => $j('joinEmail').value.trim().toLowerCase();
const emailVerifiedNow = () => !!verifiedEmail && typedEmail() === verifiedEmail;
function setCodeNote(text, ok) {
  const note = $j('joinCodeNote');
  note.textContent = text;
  note.classList.toggle('is-ok', !!ok);
}
function resetEmailCheck() {
  joinCode = null;
  joinCodeFor = '';
  verifiedEmail = '';
  $j('joinCode').value = '';
  $j('joinCode').hidden = true;
  freeSendButton($j('btnJoinSend'), '인증 메일 보내기');
  setCodeNote('', false);
}
// why this e-mail can't get a code in this company ('' = it can): an open request or an account already uses it
// (v1.7 ACC-04: refused before the code is sent)
function emailTaken(company, email) {
  if (!company) return '';
  const same = staffOf(company).find(e => (e.email || '').toLowerCase() === email.toLowerCase()
    && (['APPROVED', 'ACTIVE', 'WAITING'].includes(e.status) && !approvalExpired(e) || (e.status === 'REQUESTED' && !requestExpired(e))));
  if (!same) return '';
  return same.status === 'REQUESTED' ? '이 이메일로 승인을 기다리는 요청이 이미 있습니다.'
    : '이 이메일로 이미 계정이 있습니다. 로그인하거나 업체 관리자에게 문의해 주세요.';
}
$j('btnJoinSend').addEventListener('click', () => {
  const email = $j('joinEmail').value.trim();
  if (!validEmail(email)) { setCodeNote('이메일 형식을 확인해 주세요.', false); return; }
  const taken = emailTaken(checkedCompany, email);
  if (taken) { setCodeNote(taken, false); return; }
  joinCode = newCode();
  joinCodeFor = email.toLowerCase();
  verifiedEmail = '';
  $j('joinCode').value = '';
  $j('joinCode').hidden = false;
  $j('joinCode').focus();
  setCodeNote(`${email}로 인증 코드를 보냈습니다. 받은 6자리를 10분 안에 넣어 주세요. (시연용 코드: ${joinCode.value})`, false);
  restSendButton($j('btnJoinSend'), '다시 보내기');
  syncRequestButton();
});
$j('joinCode').addEventListener('input', () => {
  const v = $j('joinCode').value.trim();
  if (v.length !== 6) return;
  const bad = typedEmail() === joinCodeFor ? checkCode(joinCode, v) : { why: '이메일이 바뀌었습니다. 인증 메일을 다시 받아 주세요.', dead: true };
  if (!bad) {
    verifiedEmail = joinCodeFor;
    joinCode = null;
    $j('joinCode').hidden = true;
    freeSendButton($j('btnJoinSend'), '다시 보내기');
    $j('btnJoinSend').disabled = true;
    setCodeNote('이메일 인증을 마쳤습니다.', true);
  } else {
    setCodeNote(bad.why, false);
    if (bad.dead) { joinCode = null; $j('joinCode').value = ''; freeSendButton($j('btnJoinSend'), '다시 보내기'); }
  }
  syncRequestButton();
});
// a different address needs its own code
$j('joinEmail').addEventListener('input', () => {
  if (verifiedEmail || joinCodeFor) {
    if (typedEmail() !== (verifiedEmail || joinCodeFor)) resetEmailCheck();
  }
  syncRequestButton();
});

['joinPw', 'joinPw2'].forEach(id => $j(id).addEventListener('input', syncRequestButton));

function showJoin(open) {
  loginForm.hidden = open;
  joinForm.hidden = !open;
  $j('pwForm').hidden = true;
  if (open) {
    if (!loadMyJoin()) { $j('joinCompany').value = $j('loginCompany').value; onCompanyInput(); }
    renderJoinForm();
    $j('joinCompany').focus();
  }
}
$j('btnJoin').addEventListener('click', () => showJoin(true));
$j('btnJoinBack').addEventListener('click', () => showJoin(false));

// 승인 요청: every field checked; the request is refused with a reason when the company can't take it
$j('btnJoinRequest').addEventListener('click', () => {
  const company = $j('joinCompany').value.trim().toLowerCase();
  const name = $j('joinName').value.trim();
  const dept = $j('joinDept').value;
  const rank = $j('joinRank').value;
  const email = $j('joinEmail').value.trim();
  const error = $j('joinError');
  const pw = $j('joinPw').value;
  error.textContent = !dept ? '부서를 골라 주세요' : !rank ? '직급을 골라 주세요' : !name ? '이름을 입력해 주세요' : !validEmail(email) ? '이메일 형식을 확인해 주세요'
    : !emailVerifiedNow() ? '이메일을 인증해 주세요' : pw.length < 8 ? '비밀번호는 8자 이상으로 정해 주세요'
      : pw !== $j('joinPw2').value ? '비밀번호 확인이 맞지 않습니다' : '';
  if (error.textContent) return;
  const order = companyOrder(company);
  // checked again: the company may have changed since 확인, and the department must be one of its own
  error.textContent = company !== checkedCompany ? '업체 코드를 다시 확인해 주세요'
    : joinBlock(order) || (!openDeptsOf(company).includes(dept) ? '이 부서의 가입 시간이 끝났습니다. 업체 관리자나 부서장에게 다시 열어 달라고 해 주세요.'
      : ranksOf(company).includes(rank) ? '' : '직급을 다시 골라 주세요');
  if (error.textContent) return;
  const list = staffOf(company);
  const taken = emailTaken(company, email); // checked again: another request may have come in since the code
  if (taken) { error.textContent = taken; return; }
  const req = {
    reqId: `R${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
    name, dept, rank, email, emailVerified: true, pw, roles: [], status: 'REQUESTED', requestAt: new Date().toISOString(), at: new Date().toISOString(),
  };
  saveStaff(company, [...list, req]);
  saveMyJoin({ company, reqId: req.reqId });
  renderJoinForm();
});

// 요청 취소: the request is withdrawn so the form can be filled in again
$j('btnJoinCancel').addEventListener('click', () => {
  const mine = loadMyJoin();
  if (mine) saveStaff(mine.company, staffOf(mine.company).filter(e => !(e.reqId === mine.reqId && e.status === 'REQUESTED')));
  saveMyJoin(null);
  renderJoinForm();
});

// 로그인하러 가기 (after the approval made the account): back to the login with the company code and 사번 filled in
joinForm.addEventListener('submit', ev => {
  ev.preventDefault();
  const mine = loadMyJoin();
  const x = myRequest();
  if (!mine || !x || !['ACTIVE', 'WAITING'].includes(x.status)) return renderJoinForm();
  saveMyJoin(null);
  ['joinPw', 'joinPw2', 'joinName', 'joinEmail'].forEach(id => { $j(id).value = ''; });
  resetEmailCheck();
  checkedCompany = '';
  setCompanyNote('', false);
  setDeptOptions('');
  // back to the login form with the company code and the new 사번 filled in
  showJoin(false);
  $j('loginCompany').value = mine.company;
  $j('loginId').value = x.empNo;
  $j('loginError').textContent = x.status === 'ACTIVE'
    ? `계정이 만들어졌습니다. 사번 ${x.empNo}와 요청할 때 정한 비밀번호로 로그인하세요.`
    : `계정이 만들어졌습니다(사번 ${x.empNo}). 구매 인원이 모두 차서 업체 관리자가 자리를 주면 로그인할 수 있습니다.`;
  $j('loginError').classList.add('is-ok');
  $j('loginPw').focus();
});
$j('loginForm').addEventListener('input', () => $j('loginError').classList.remove('is-ok'));

// The applicant's screen follows the request while it is open: another tab's approval arrives as a storage
// event; the real screen asks the server every few seconds (GET /join/status)
window.addEventListener('storage', ev => { if (ev.key === EMP_KEY && !joinForm.hidden) renderJoinForm(); });
joinForm.addEventListener('input', () => { if (joinNotice && !loadMyJoin()) { joinNotice = ''; setJoinStatus('', ''); } });
setInterval(() => { if (!joinForm.hidden && !document.hidden && loadMyJoin()) renderJoinForm(); }, 5000);

// ---------- 로그인 화면: 비밀번호 찾기 ----------
// 업체 코드 + 사번 → a 6-digit code to the e-mail the account was made with → with the right code, a new
// password typed twice (8+ characters) replaces the old one. The current password is never shown: the real
// server keeps only a BCrypt hash (user agreed 2026-10-28). Operators (ieum0000) aren't covered here.
// The screen answers the same way whether or not the account exists (v1.7 ACC-07), and a code goes only to an
// account in use (ACTIVE employee, or the admin of a verified company): with anything else nothing is sent and
// no code can be right. Codes follow the join form's rules (10 minutes, 5 tries, a resend a minute).
// Real server: POST /password/code { companyCode, empNo } → same reply either way, POST /password/verify →
// short-lived reset token, POST /password/reset { resetToken, password } stores the new hash and ends that
// account's other sessions.
const pwForm = $j('pwForm');
let pwTarget = null; // { company, id } the code was sent for
let pwCode = null;
let pwVerified = false;
const maskEmail = v => { const [a, d] = String(v).split('@'); return `${a.slice(0, 1)}${'*'.repeat(Math.max(2, a.length - 1))}@${d}`; };

// The account a code may go to: the company's first admin (password on the trial signup, company verified) or
// an employee in use whose address was verified. null = nothing is sent (the screen doesn't say why).
function resetTarget(company, id) {
  const order = company && company !== 'ieum0000' ? findCompanyOrder(company) : null;
  if (!order || order.emailVerified === false) return null;
  if (adminNo(order) === id) return { kind: 'ADMIN', email: order.email };
  const e = employeesOf(company).find(x => x.empNo === id && x.pw);
  if (!e || e.status !== 'ACTIVE' || !e.emailVerified) return null;
  return { kind: 'STAFF', email: e.email };
}

function resetPwForm() {
  pwTarget = null;
  pwCode = null;
  pwVerified = false;
  ['pwCompany', 'pwId'].forEach(id => { $j(id).disabled = false; });
  $j('pwCode').value = '';
  $j('pwCode').hidden = true;
  $j('pwCodeNote').textContent = '';
  $j('pwCodeNote').classList.remove('is-ok');
  freeSendButton($j('btnPwSend'), '인증 메일 보내기');
  $j('pwNewBox').hidden = true;
  $j('btnPwReset').hidden = true;
  ['pwNew', 'pwNew2'].forEach(id => { $j(id).value = ''; });
  $j('pwError').textContent = '';
  syncPwReset();
}
function syncPwReset() {
  const pw = $j('pwNew').value;
  $j('btnPwReset').disabled = !(pwVerified && pw.length >= 8 && pw === $j('pwNew2').value);
}

function showPwFind(open) {
  loginForm.hidden = open;
  joinForm.hidden = true;
  pwForm.hidden = !open;
  if (open) {
    resetPwForm();
    $j('pwCompany').value = $j('loginCompany').value;
    $j('pwId').value = $j('loginId').value;
    ($j('pwCompany').value ? $j('pwId') : $j('pwCompany')).focus();
  }
}
$j('btnPwFind').addEventListener('click', () => showPwFind(true));
$j('btnPwBack').addEventListener('click', () => showPwFind(false));

$j('btnPwSend').addEventListener('click', () => {
  const company = $j('pwCompany').value.trim().toLowerCase();
  const id = $j('pwId').value.trim();
  const error = $j('pwError');
  error.textContent = !company || !id ? '업체 코드와 사번을 입력해 주세요' : '';
  if (error.textContent) return;
  // the same words whether or not the account exists; a code only when it does (the prototype shows it here)
  const t = resetTarget(company, id);
  pwTarget = { company, id };
  pwCode = t ? newCode() : null;
  pwVerified = false;
  $j('pwCode').value = '';
  $j('pwCode').hidden = false;
  $j('pwCode').focus();
  $j('pwCodeNote').classList.remove('is-ok');
  $j('pwCodeNote').textContent = '등록된 이메일로 인증 코드를 보냈습니다. 받은 6자리를 10분 안에 넣어 주세요.'
    + (t ? ` (${maskEmail(t.email)} · 시연용 코드: ${pwCode.value})` : ' (시연용: 사용 중인 계정이 아니라 실제로는 보내지 않습니다)');
  restSendButton($j('btnPwSend'), '다시 보내기');
});
$j('pwCode').addEventListener('input', () => {
  const v = $j('pwCode').value.trim();
  if (v.length !== 6) return;
  const bad = checkCode(pwCode, v);
  if (!bad) {
    pwVerified = true;
    pwCode = null;
    $j('pwCode').hidden = true;
    freeSendButton($j('btnPwSend'), '다시 보내기');
    $j('btnPwSend').disabled = true;
    ['pwCompany', 'pwId'].forEach(id => { $j(id).disabled = true; });
    $j('pwCodeNote').textContent = '이메일 인증을 마쳤습니다.';
    $j('pwCodeNote').classList.add('is-ok');
    $j('pwNewBox').hidden = false;
    $j('btnPwReset').hidden = false;
    $j('pwNew').focus();
  } else {
    $j('pwCodeNote').textContent = bad.why;
    if (bad.dead) { pwCode = null; $j('pwCode').value = ''; freeSendButton($j('btnPwSend'), '다시 보내기'); }
  }
  syncPwReset();
});
// another company or 사번 needs its own code
['pwCompany', 'pwId'].forEach(id => $j(id).addEventListener('input', () => { if (pwTarget) resetPwForm(); }));
['pwNew', 'pwNew2'].forEach(id => $j(id).addEventListener('input', () => {
  $j('pwError').textContent = '';
  syncPwReset();
}));

pwForm.addEventListener('submit', ev => {
  ev.preventDefault();
  const pw = $j('pwNew').value;
  const error = $j('pwError');
  error.textContent = pw.length < 8 ? '비밀번호는 8자 이상으로 정해 주세요' : pw !== $j('pwNew2').value ? '비밀번호 확인이 맞지 않습니다' : '';
  if (error.textContent || !pwVerified || !pwTarget) return;
  const { company, id } = pwTarget;
  const t = resetTarget(company, id);
  if (!t) { error.textContent = '지금은 비밀번호를 바꿀 수 없는 계정입니다. 업체 관리자에게 문의해 주세요.'; return; }
  if (t.kind === 'ADMIN') {
    const orders = loadStore(ORDERS_KEY, []);
    const o = orders.find(x => x.companyCode === company);
    o.adminPw = pw;
    saveStore(ORDERS_KEY, orders);
  } else {
    const list = staffOf(company);
    list.find(x => x.empNo === id && x.pw).pw = pw;
    saveStaff(company, list);
  }
  ['pwCompany', 'pwId'].forEach(x => { $j(x).disabled = false; });
  showPwFind(false);
  $j('loginCompany').value = company;
  $j('loginId').value = id;
  $j('loginPw').value = '';
  $j('loginError').textContent = '비밀번호를 바꿨습니다. 새 비밀번호로 로그인하세요.';
  $j('loginError').classList.add('is-ok');
  $j('loginPw').focus();
});

// ---------- 헤더: 비밀번호 변경 (로그인한 직원 본인) ----------
// The current password must match; the new one is 8+ characters typed twice. Allowed while read-only (account
// security, v1.7 3.11). Operators don't get the button (demo accounts). Real server: POST /password/change.
const pwChangeDialog = $j('pwChangeDialog');
function myPassword() {
  const order = findCompanyOrder(session.company);
  if (order && adminNo(order) === session.id) return { get: () => order.adminPw, set: v => { const os = loadStore(ORDERS_KEY, []); os.find(o => o.companyCode === session.company).adminPw = v; saveStore(ORDERS_KEY, os); } };
  const e = employeesOf(session.company).find(x => x.empNo === session.id);
  return e ? { get: () => e.pw, set: v => { const list = staffOf(session.company); list.find(x => x.empNo === session.id).pw = v; saveStaff(session.company, list); } } : null;
}
function syncPwChange() {
  const n = $j('pwChgNew').value;
  $j('btnPwChangeOk').disabled = !($j('pwCur').value && n.length >= 8 && n === $j('pwChgNew2').value);
}
$j('btnPwChange').addEventListener('click', () => {
  ['pwCur', 'pwChgNew', 'pwChgNew2'].forEach(id => { $j(id).value = ''; });
  $j('pwChgError').textContent = '';
  syncPwChange();
  if (!pwChangeDialog.open) pwChangeDialog.showModal();
  $j('pwCur').focus();
});
['pwCur', 'pwChgNew', 'pwChgNew2'].forEach(id => $j(id).addEventListener('input', () => { $j('pwChgError').textContent = ''; syncPwChange(); }));
pwChangeDialog.querySelector('[value="cancel"]').addEventListener('click', () => pwChangeDialog.close());
$j('pwChangeForm').addEventListener('submit', ev => {
  ev.preventDefault();
  const me = session && session.type === 'EMPLOYEE' ? myPassword() : null;
  const error = $j('pwChgError');
  const n = $j('pwChgNew').value;
  error.textContent = !me ? '지금은 비밀번호를 바꿀 수 없습니다.' : $j('pwCur').value !== me.get() ? '현재 비밀번호가 맞지 않습니다.'
    : n.length < 8 ? '새 비밀번호는 8자 이상으로 정해 주세요.' : n !== $j('pwChgNew2').value ? '새 비밀번호 확인이 맞지 않습니다.' : n === me.get() ? '지금 쓰는 비밀번호와 다르게 정해 주세요.' : '';
  if (error.textContent) return;
  me.set(n);
  pwChangeDialog.close();
  showToast('비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 쓰세요');
});
