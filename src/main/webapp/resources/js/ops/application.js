// 이음 운영자 화면 (이음 운영자 계정으로 로그인했을 때).
// 신청 현황 페이지가 열리면 openApplicationView()를 부른다.
// 이 파일은 신청 현황 화면(조각 views/ops/application.html)과 운영자 배지다. 신청 = 소개 사이트의 무료 체험 신청, 업체 하나에 한 줄.
// 체험 중(TRIAL) → 결제하면 바로 결제 완료(PAID, 운영자 승인 없음: 요구분석서 PAY-02) → 체험·이용 기간이 끝나면 읽기 전용(EXPIRED).
// 서버 API (시안은 demo/subscription.js가 답한다. 이음 운영자만, 아니면 403):
//   GET  /ops/api/applications          신청 목록, 신청 시각 최신순. 한 건:
//        { no, at, company, companyCode, isNew, adminName, adminNo, email, adminPw(시안 전용), emailVerified, verifiedAt, verifySentAt,
//          status: TRIAL|PAID|EXPIRED, paid, plan: MONTHLY|ANNUAL|null, end(마지막 날), daysLeft, trialFrom, trialEnd, serviceFrom, paidAt,
//          users, total(월 기준 이용료), plannedUsers, seatChange: { from, users }|null, failedPayments,
//          lastRejected: { decidedAt, kind, amount, reason }|null }   (날짜는 모두 ISO 문자열)
//   POST /ops/api/applications/read     { nos: [신청번호...] } 본 새 신청을 읽음으로 → { unread }
//   GET  /ops/api/applications/badge    → { unread }  읽지 않은 새 신청 수 (헤더 종·메뉴 배지)
// 실제 프로젝트에서는 배지를 15초마다 다시 받는다. 시안은 화면을 옮길 때와 다른 탭에서 바뀔 때(demo/subscription.js) 다시 받는다.

const APP_STATUS = { TRIAL: '체험 중', PAID: '결제 완료', EXPIRED: '읽기 전용' };
const APP_PLAN = { MONTHLY: '월간', ANNUAL: '연간' };
const APP_PAY_KIND = { FIRST: '첫 결제', EXTEND: '연장', RESTART: '다시 결제', RENEW: '정기 결제', ADD: '증설 결제', REFUND: '증설 환불' };
const appDday = n => (n === 0 ? 'D-day' : `D-${n}`);

let appItems = []; // 마지막으로 받은 신청 목록

// 헤더 종과 메뉴의 새 신청 배지 (운영자 화면이 아니면 건너뛴다)
function renderBadges() {
  const bell = document.getElementById('bell');
  if (!bell || bell.hidden) return;
  api.get('/ops/api/applications/badge').done(({ unread: n }) => {
    for (const id of ['bellCount', 'navCount']) {
      const el = document.getElementById(id);
      el.textContent = n;
      el.hidden = n === 0;
    }
    bell.setAttribute('aria-label', n ? `새 신청 ${n}건` : '새 신청 알림');
  }); // 배지는 못 받아도 조용히 둔다 (다음에 다시 받는다)
}


// One application's detail (three cards), built from the template in the view fragment
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
    ['이용 인원', o.seatChange ? `${o.users}명 (${ymd(o.seatChange.from)}부터 ${o.plannedUsers}명)` : `${o.users}명`],
    ['이메일', o.email],
    // v1.4: the account exists from the signup; until the e-mail is verified the company can't sign in
    ['이메일 인증', !o.emailVerified ? `대기 (인증 메일 ${whenFull(o.verifySentAt || o.at)} 발송, 인증 전 로그인 불가)` : o.verifiedAt ? `완료 (${whenFull(o.verifiedAt)})` : '완료'],
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
    ['사번', o.adminNo],
    ['비밀번호', pw],
  ]);
  if (o.adminPw) addPasswordToggle(pw);

  const last = o.lastRejected;
  const payText = o.paid ? `결제 완료 (첫 결제 ${ymd(o.paidAt)})` : o.failedPayments ? `미결제 · 실패 ${o.failedPayments}회` : '미결제';
  fill('pay', [
    ...(last ? [['최근 반려', `${ymd(last.decidedAt)} ${APP_PAY_KIND[last.kind] || ''} · ${last.reason}`]] : []),
    ['신청번호', o.no],
    ['신청 시각', whenFull(o.at)],
    ['체험 기간', `${ymd(o.at)} ~ ${ymd(o.trialEnd)}`],
    ['월 기준 이용료', won(o.total)],
    ['결제 상태', payText],
    ...(o.paid ? [
      ['요금제', APP_PLAN[o.plan] || '월간'],
      ['이용 기간', `${ymd(o.serviceFrom)} ~ ${ymd(o.end)}${o.status === 'EXPIRED' ? ' (만료, 읽기 전용)' : ''}`],
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
  tr.dataset.app = o.no;
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
  const id = o.no;
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

// 목록을 새로 받아 그 자리에서 다시 그린다 (거른 카드와 펼친 줄은 그대로)
function renderStatus() {
  api.get('/ops/api/applications')
    .done(items => { appItems = items; drawStatus(); })
    .fail(xhr => showToast(apiMessage(xhr, '신청 목록을 불러오지 못했습니다')));
}

function drawStatus() {
  const orders = appItems;
  // a row is new for its signup, until the page is left
  const newSignup = o => seenNew.has(o.no) || o.isNew;
  const count = (id, n) => { document.getElementById(id).textContent = `${n}건`; };
  count('cntAll', orders.length);
  count('cntNew', orders.filter(newSignup).length);
  count('cntTrial', orders.filter(o => o.status === 'TRIAL').length);
  count('cntPaid', orders.filter(o => o.status === 'PAID').length);
  count('cntExpired', orders.filter(o => o.status === 'EXPIRED').length);

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
    appFilter === 'all' ? true : appFilter === 'new' ? newSignup(o) : o.status === appFilter);

  document.getElementById('appList').replaceChildren(...shown.flatMap(o => {
    const isNew = newSignup(o);
    const isOpen = openApp === o.no;
    const tr = document.createElement('tr');
    tr.className = 'row-link' + (isNew ? ' is-new' : '') + (isOpen ? ' is-open' : '');
    // the whole row opens or closes the detail right below it; the company name is a button for keyboard users
    tr.addEventListener('click', () => toggleDetail(tr, o));

    const name = el('button', o.company, 'row-title');
    name.type = 'button';
    name.setAttribute('aria-expanded', isOpen);
    const company = [name];
    if (isNew) company.push(el('span', '새 신청', 'tag-new'));
    if (!o.emailVerified) company.push(el('span', '인증 대기', 'status'));
    company.push(el('small', o.no, 'cell-sub'));

    const end = ymd(o.end);
    const statusSub = o.status === 'EXPIRED' ? `${end} ${o.paid ? '이용' : '체험'} 만료`
      : o.paid ? `${APP_PLAN[o.plan] || '월간'} · ${end} 만료` : `${appDday(o.daysLeft)} · ${end} 만료`;
    tr.append(
      cell('신청 시각', '', el('span', when(o.at))),
      cell('업체', '', ...company),
      cell('인원', 'num', el('span', `${o.users}명`)),
      cell('월 이용료', 'num', el('span', won(o.total))),
      cell('관리자', '', el('span', o.adminName), el('small', o.adminNo, 'cell-sub')),
      cell('상태', '', el('span', APP_STATUS[o.status], `status status-${o.status.toLowerCase()}`), el('small', statusSub, 'cell-sub')),
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
    td.textContent = !orders.length ? '아직 들어온 신청이 없습니다' : '해당하는 신청이 없습니다';
    tr.append(td);
    document.getElementById('appList').append(tr);
  }
}

document.querySelectorAll('#appFilters .card-filter').forEach(b => b.addEventListener('click', () => {
  appFilter = b.dataset.filter;
  openApp = null;
  drawStatus();
}));

// Opening the status page counts as having seen the new applications: the list still marks them
// this time, and the badges clear. 이 화면이 받은 새 신청만 읽음으로 한다 (그 사이에 들어온 신청은 새 신청으로 남는다)
function markAllRead() {
  const nos = [...seenNew];
  if (!nos.length) { renderBadges(); return; }
  api.post('/ops/api/applications/read', { nos }).always(renderBadges);
}

// 신청 현황 화면을 열 때 (JSP 페이지가 열리면 부른다)
function openApplicationView() {
  appFilter = 'all';
  openApp = null;
  api.get('/ops/api/applications')
    .done(items => {
      appItems = items;
      seenNew = new Set(items.filter(o => o.isNew).map(o => o.no));
      drawStatus();
      markAllRead();
    })
    .fail(xhr => showToast(apiMessage(xhr, '신청 목록을 불러오지 못했습니다')));
}
