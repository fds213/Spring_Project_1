// 이음 운영자: 신청 현황 (views/ops/application.jsp). 페이지가 열리면 openApplicationView()를 부른다.
// 신청 = 소개 사이트의 무료 체험 신청, 업체 하나에 한 줄.
// 체험 중(TRIAL) → 결제하면 바로 결제 완료(PAID, 운영자 승인 없음: 요구분석서 PAY-02) → 체험·이용 기간이 끝나면 읽기 전용(EXPIRED).
// 메뉴의 새 신청 배지(data-badge="application")는 common.js 가 GET /badges 로 채운다.

const APP_STATUS = { TRIAL: '체험 중', PAID: '결제 완료', EXPIRED: '읽기 전용' };
const APP_PLAN = { MONTHLY: '월간', ANNUAL: '연간' };
const appDday = n => (n === 0 ? 'D-day' : `D-${n}`);

let appItems = []; // 마지막으로 받은 신청 목록

// 신청 한 건의 상세(카드 세 장). JSP 의 appDetailTpl 틀로 만든다
function buildDetail(o) {
  const box = document.getElementById('appDetailTpl').content.firstElementChild.cloneNode(true);

  // 체험 신청 양식과 같은 순서로 보여 준다 (업체 정보 → 최초 관리자 계정 → 결제 정보)
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
    // 계정은 신청할 때 바로 생기고, 이메일 인증 전에는 로그인할 수 없다 (ACC-06)
    ['이메일 인증', !o.emailVerified ? `대기 (인증 메일 ${whenFull(o.verifySentAt || o.at)} 발송, 인증 전 로그인 불가)` : o.verifiedAt ? `완료 (${whenFull(o.verifiedAt)})` : '완료'],
  ]);

  // 카드 제목이 '최초 관리자 계정'이라 항목 이름은 짧게 둔다. 비밀번호는 서버도 해시로만 가지므로 보여 주지 않는다
  fill('admin', [
    ['이름', o.adminName],
    ['사번', o.adminNo],
  ]);

  const payText = o.paid ? `결제 완료 (첫 결제 ${ymd(o.paidAt)})` : o.failedPayments ? `미결제 · 실패 ${o.failedPayments}회` : '미결제';
  fill('pay', [
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

// 상세를 펼친 신청 (한 번에 하나). 다시 그려도 그대로 둔다
let openApp = null;

// 신청 줄 바로 아래의 상세 줄. 안쪽 상자가 미끄러지듯 열리고 닫힌다(CSS .slide).
// open 이 true 면 처음부터 열린 채로 만든다 (다시 그릴 때 애니메이션이 또 돌지 않게)
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

// 펼친 상세를 닫는다: 미끄러지듯 닫은 뒤 그 줄을 지운다
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
  setTimeout(remove, 400); // 애니메이션을 끈 환경이거나 이벤트가 오지 않았을 때
}

// 줄을 누르면: 열린 것을 닫고, 방금 연 줄이 아니었으면 이 줄을 연다
function toggleDetail(row, o) {
  const id = o.no;
  const wasOpen = openApp === id;
  closeDetail();
  openApp = wasOpen ? null : id;
  if (wasOpen) return;
  const detail = detailRow(o, false);
  row.after(detail);
  setRowOpen(row, true);
  // 닫힌 크기를 먼저 잡게 한 뒤(크기를 읽으면 그렇게 된다) 열어야 높이가 애니메이션된다.
  // requestAnimationFrame 은 뒤에 있는 탭에서 돌지 않아 줄이 닫힌 채로 남을 수 있다
  const slide = detail.querySelector('.slide');
  slide.getBoundingClientRect();
  slide.classList.add('open');
}

// 이 화면을 열 때 받은 새 신청. 바로 읽음으로 바꾸지만(배지는 없어짐), 이 화면을 떠나기 전까지는 새 신청으로 보여 준다
let seenNew = new Set();
// 고른 요약 카드: 'all', 'new', 또는 상태 코드
let appFilter = 'all';

function drawStatus() {
  const orders = appItems;
  const newSignup = o => seenNew.has(o.no) || o.isNew;
  const count = (id, n) => { document.getElementById(id).textContent = `${n}건`; };
  count('cntAll', orders.length);
  count('cntNew', orders.filter(newSignup).length);
  count('cntTrial', orders.filter(o => o.status === 'TRIAL').length);
  count('cntPaid', orders.filter(o => o.status === 'PAID').length);
  count('cntExpired', orders.filter(o => o.status === 'EXPIRED').length);

  // 칸 하나: data-label 은 폰에서 값 앞에 붙는 열 이름 (표가 카드 모양으로 바뀐다)
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
  const node = (tag, text, cls) => { const e = document.createElement(tag); e.textContent = text; if (cls) e.className = cls; return e; };

  document.querySelectorAll('#appFilters .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === appFilter));
  const shown = orders.filter(o =>
    appFilter === 'all' ? true : appFilter === 'new' ? newSignup(o) : o.status === appFilter);

  document.getElementById('appList').replaceChildren(...shown.flatMap(o => {
    const isNew = newSignup(o);
    const isOpen = openApp === o.no;
    const tr = document.createElement('tr');
    tr.className = 'row-link' + (isNew ? ' is-new' : '') + (isOpen ? ' is-open' : '');
    // 줄 어디를 눌러도 바로 아래 상세가 열리고 닫힌다. 업체명은 키보드로도 누를 수 있게 버튼이다
    tr.addEventListener('click', () => toggleDetail(tr, o));

    const name = node('button', o.company, 'row-title');
    name.type = 'button';
    name.setAttribute('aria-expanded', isOpen);
    const company = [name];
    if (isNew) company.push(node('span', '새 신청', 'tag-new'));
    if (!o.emailVerified) company.push(node('span', '인증 대기', 'status'));
    company.push(node('small', o.no, 'cell-sub'));

    const end = ymd(o.end);
    const statusSub = o.status === 'EXPIRED' ? `${end} ${o.paid ? '이용' : '체험'} 만료`
      : o.paid ? `${APP_PLAN[o.plan] || '월간'} · ${end} 만료` : `${appDday(o.daysLeft)} · ${end} 만료`;
    tr.append(
      cell('신청 시각', '', node('span', when(o.at))),
      cell('업체', '', ...company),
      cell('인원', 'num', node('span', `${o.users}명`)),
      cell('월 이용료', 'num', node('span', won(o.total))),
      cell('관리자', '', node('span', o.adminName), node('small', o.adminNo, 'cell-sub')),
      cell('상태', '', node('span', APP_STATUS[o.status], `status status-${o.status.toLowerCase()}`), node('small', statusSub, 'cell-sub')),
    );
    return isOpen ? [tr, detailRow(o, true)] : [tr];
  }));
  // 보여 줄 줄이 없으면 표는 그대로 두고 안내 한 줄을 넣는다 (카드를 바꿀 때 화면이 들썩이지 않게)
  if (shown.length === 0) {
    document.getElementById('appList').append(emptyTr(6, !orders.length ? '아직 들어온 신청이 없습니다' : '해당하는 신청이 없습니다'));
  }
}

document.querySelectorAll('#appFilters .card-filter').forEach(b => b.addEventListener('click', () => {
  appFilter = b.dataset.filter;
  openApp = null;
  drawStatus();
}));

// 신청 현황을 열면 새 신청을 본 것으로 한다: 이번에는 목록에 새 신청으로 표시하고, 메뉴 배지는 지운다.
// 이 화면이 받은 새 신청만 읽음으로 한다 (그 사이에 들어온 신청은 새 신청으로 남는다)
function markAllRead() {
  const nos = [...seenNew];
  if (!nos.length) return;
  api.post('/ops/api/applications/read', { nos }).always(() => IEUM.refreshBadges());
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
