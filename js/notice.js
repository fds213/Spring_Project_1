// 공지사항 시안 (목록 · 상세 · 작성/수정)
// - 서버가 없으므로 공지는 브라우저(localStorage)에 저장한다. 실제 프로젝트에서는 DB에 저장하고,
//   권한 검사는 화면(JSP)과 서버(Security·Service) 양쪽에서 한다.
// - 화면에 글자를 넣을 때는 모두 textContent를 쓴다(innerHTML 금지). 남이 쓴 글에 들어 있는
//   <script> 같은 코드가 실행되지 않게 하려는 것이다.

// ---------- 시안용 로그인 사용자 ----------
// 역할(role)은 v1.6 3.12의 DB 값: ROLE_ADMIN / ROLE_HR / ROLE_IT / ROLE_EMP (한 계정에 하나)
const USERS = [
  { empNo: '20260001', name: '박유료', dept: '경영지원팀', rank: '관리자', role: 'ADMIN', label: '업체 관리자' },
  { empNo: '20260002', name: '김인사', dept: '인사팀', rank: '대리', role: 'HR', label: '인사 담당' },
  { empNo: '20260003', name: '이인사', dept: '인사팀', rank: '사원', role: 'HR', label: '인사 담당' },
  { empNo: '20260004', name: '최사원', dept: '개발팀', rank: '사원', role: 'EMP', label: '직원' },
  { empNo: '20260005', name: '정아이티', dept: '전산팀', rank: '주임', role: 'IT', label: 'IT 담당' },
  { empNo: '20260006', name: '한개발', dept: '개발팀', rank: '대리', role: 'EMP', label: '직원' },
];

const STORE_KEY = 'ieum-notice-demo-v2'; // v2: 맨 위 고정(pinned) 추가
const USER_KEY = 'ieum-notice-demo-user';
const READONLY_KEY = 'ieum-notice-demo-readonly';
const PAGE_SIZE = 10;
const TITLE_MAX = 100;
const CONTENT_MAX = 2000;
const PIN_MAX = 5; // 맨 위 고정은 5개까지 (너무 많으면 고정한 의미가 없다)

// ---------- 권한 (팀에 제안한 규칙) ----------
// 글쓰기: 업체 관리자 · 인사 담당
// 수정·삭제: 업체 관리자는 모든 공지, 인사 담당은 본인이 쓴 공지만
// 읽기 전용(만료) 업체: 위 동작을 모두 막는다
const canWrite = u => u.role === 'ADMIN' || u.role === 'HR';
const canEdit = (u, n) => u.role === 'ADMIN' || (u.role === 'HR' && n.writerEmpNo === u.empNo);

// ---------- 저장소 ----------
const daysAgo = (d, h = 10, m = 0) => {
  const t = new Date();
  t.setDate(t.getDate() - d);
  t.setHours(h, m, 0, 0);
  return t.toISOString();
};

function seed() {
  const W = { admin: USERS[0], hr1: USERS[1], hr2: USERS[2] };
  const rows = [
    ['admin', 62, '이음 그룹웨어 사용 안내', '오늘부터 사내 업무를 이음 그룹웨어로 처리합니다.\n\n- 결재: 전자결재 메뉴\n- 장비·장애 요청: IT 서비스 메뉴\n- 출퇴근: 출입구 지문 단말기\n\n사용 중 불편한 점은 경영지원팀으로 알려 주세요.'],
    ['hr1', 58, '9월 급여 지급일 안내', '9월 급여는 9월 25일(목)에 지급됩니다.\n명세서는 개인 메일로 발송됩니다.'],
    ['hr1', 55, '추석 연휴 근무 안내', '추석 연휴(10/3~10/9) 동안 사무실은 운영하지 않습니다.\n긴급 연락은 각 팀장에게 해 주세요.'],
    ['admin', 51, '사무실 출입 카드 교체', '보안 강화를 위해 출입 카드를 새 카드로 교체합니다.\n기존 카드는 10월 말까지 사용할 수 있습니다.'],
    ['hr2', 47, '건강검진 대상자 안내', '올해 건강검진 대상자는 인사팀에서 개별 안내드렸습니다.\n12월 15일까지 검진을 마쳐 주세요.'],
    ['hr1', 44, '신규 입사자 소개', '10월 1일자로 개발팀에 두 분이 입사했습니다.\n따뜻하게 맞아 주세요.'],
    ['admin', 40, '회의실 사용 규칙', '회의실은 1시간 단위로 사용하고, 사용 후 정리해 주세요.\n장시간 사용은 경영지원팀과 협의 바랍니다.'],
    ['hr2', 37, '연차 사용 촉진 안내', '남은 연차가 많은 분은 연말 전에 계획을 세워 주세요.\n연차는 다음 해로 이월되지 않습니다.'],
    ['hr1', 33, '사내 동호회 모집', '등산·풋살·독서 동호회 회원을 모집합니다.\n관심 있는 분은 인사팀으로 연락 주세요.'],
    ['admin', 30, '프린터 교체 공사', '3층 복합기를 교체합니다.\n10월 14일 오전에는 2층 복합기를 이용해 주세요.'],
    ['hr2', 27, '4분기 교육 일정', '4분기 법정 의무 교육은 온라인으로 진행합니다.\n11월 30일까지 수강을 마쳐 주세요.'],
    ['hr1', 24, '경조사 지원 기준 안내', '경조사 지원 기준이 바뀌었습니다.\n자세한 내용은 인사팀에 문의해 주세요.'],
    ['admin', 21, '사내 Wi-Fi 비밀번호 변경', '사내 Wi-Fi 비밀번호가 바뀌었습니다.\n새 비밀번호는 각 팀장에게 받아 주세요.'],
    ['hr2', 18, '주차장 이용 안내', '건물 주차장 공사로 다음 주 월요일부터 2주 동안 지하 2층만 이용할 수 있습니다.'],
    ['hr1', 15, '연말정산 서류 제출', '연말정산 서류는 1월 20일까지 인사팀으로 제출해 주세요.\n제출 방법은 개인 메일로 안내드립니다.'],
    ['admin', 12, '정수기 필터 교체', '내일 오전 정수기 필터를 교체합니다.\n오전 10시~11시에는 정수기를 쓸 수 없습니다.'],
    ['hr2', 10, '11월 회식 일정', '11월 전사 회식은 11월 21일(금) 저녁입니다.\n참석 여부를 팀장에게 알려 주세요.'],
    ['hr1', 8, '복장 규정 안내', '11월부터 금요일은 자율 복장입니다.\n외부 미팅이 있는 날은 단정한 복장으로 부탁드립니다.'],
    ['admin', 6, '사무실 대청소', '이번 주 금요일 오후 4시에 전 직원 대청소를 진행합니다.'],
    ['hr2', 5, '인사 평가 일정', '하반기 인사 평가는 11월 셋째 주에 진행합니다.\n평가 양식은 추후 안내드립니다.'],
    ['hr1', 3, '사내 Wi-Fi 비밀번호 변경 (재안내)', '지난 공지에 이어 다시 안내드립니다.\n새 비밀번호를 아직 받지 못한 분은 경영지원팀으로 와 주세요.'],
    ['admin', 2, '[필독] 11월 보안 점검 안내', '11월 3일(월) 보안 점검이 있습니다.\n\n1. 오전 9시 전까지 개인 PC를 켜 두세요.\n2. 점검 중에는 외부 USB를 쓰지 마세요.\n3. 점검이 끝나면 메일로 결과를 알려 드립니다.\n\n문의: 경영지원팀'],
    ['hr1', 0, '10월 마감 근태 확인 요청', '10월 근태 기록을 오늘 퇴근 전까지 확인해 주세요.\n누락된 기록은 인사팀으로 알려 주세요.'],
  ];
  return {
    nextId: rows.length + 1,
    list: rows.map(([who, d, title, content], i) => {
      const w = W[who];
      const n = { id: i + 1, title, content, writerEmpNo: w.empNo, writerName: w.name, writerDept: w.dept,
        createdAt: daysAgo(d, 9 + (i % 7), (i * 13) % 60), updatedAt: null,
        pinned: i === 0 || i === 21 }; // 예시: 사용 안내(오래된 글)와 보안 점검을 고정
      if (i === 12 || i === 21) n.updatedAt = daysAgo(Math.max(d - 1, 0), 15, 20); // 수정된 공지 예시
      return n;
    }),
  };
}

function load() {
  try {
    const s = JSON.parse(localStorage.getItem(STORE_KEY));
    if (s && Array.isArray(s.list)) return s;
  } catch (e) { /* 저장된 값이 깨졌으면 처음 데이터로 */ }
  const s = seed();
  save(s);
  return s;
}
function save(s) { try { localStorage.setItem(STORE_KEY, JSON.stringify(s)); } catch (e) { /* 저장 못 해도 화면은 동작 */ } }

let store = load();
const byNewest = () => [...store.list].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
const findNotice = id => store.list.find(n => n.id === Number(id));

// ---------- 공통 ----------
const $ = id => document.getElementById(id);
const pad = n => String(n).padStart(2, '0');
const fmtDate = iso => { const d = new Date(iso); return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`; };
const fmtTime = iso => { const d = new Date(iso); return `${fmtDate(iso)} ${pad(d.getHours())}:${pad(d.getMinutes())}`; };

function el(tag, attrs = {}, text) {
  const e = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
  if (text !== undefined) e.textContent = text;
  return e;
}

let toastTimer;
function toast(msg) {
  const t = $('toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 2600);
}

// 확인 창(<dialog>)을 열고, 누른 버튼이 '확인'이면 true
function confirmDialog(id) {
  const d = $(id);
  return new Promise(resolve => {
    const done = v => { d.close(); resolve(v); };
    d.querySelector('[value="ok"]').onclick = () => done(true);
    d.querySelector('[value="cancel"]').onclick = () => done(false);
    d.oncancel = () => resolve(false); // Esc
    d.showModal();
  });
}

// ---------- 로그인 사용자 · 읽기 전용 (시안 전용 상자) ----------
let me = USERS.find(u => u.empNo === localStorage.getItem(USER_KEY)) || USERS[0];
let readonly = localStorage.getItem(READONLY_KEY) === '1';

function applyUser() {
  $('userAvatar').textContent = me.name[0];
  $('userName').textContent = me.name;
  $('userSub').textContent = `${me.dept} · ${me.label}`;
  $('homeGreeting').textContent = `안녕하세요, ${me.name}님`;
  document.body.classList.toggle('is-readonly', readonly);
  $('readonlyBar').hidden = !readonly;
}

function setupDemo() {
  const sel = $('demoUser');
  USERS.forEach(u => sel.append(el('option', { value: u.empNo }, `${u.name} (${u.label})`)));
  sel.value = me.empNo;
  sel.addEventListener('change', () => {
    me = USERS.find(u => u.empNo === sel.value);
    try { localStorage.setItem(USER_KEY, me.empNo); } catch (e) { /* 무시 */ }
    applyUser();
    route();
  });
  $('demoReadonly').checked = readonly;
  $('demoReadonly').addEventListener('change', e => {
    readonly = e.target.checked;
    try { localStorage.setItem(READONLY_KEY, readonly ? '1' : '0'); } catch (err) { /* 무시 */ }
    applyUser();
    route();
  });
  $('demoReset').addEventListener('click', () => {
    store = seed();
    save(store);
    listState.page = 1; listState.q = '';
    if (typeof resetBoard === 'function') resetBoard();
    toast('공지·게시판 데이터를 처음 상태로 되돌렸습니다');
    route();
  });
}

// 읽기 전용 업체: data-write 버튼은 보이되 흐리고, 누르면 이유를 알려 준다 (가이드 3번 섹션)
const READONLY_MSG = () => (me.role === 'ADMIN'
  ? '체험이 끝나 읽기 전용입니다. 이용 결제에서 결제하면 바로 이어서 쓸 수 있습니다.'
  : '체험이 끝나 읽기 전용입니다. 업체 관리자가 결제하면 바로 이어서 쓸 수 있습니다.');
document.addEventListener('click', e => {
  if (readonly && e.target.closest('[data-write]')) {
    e.preventDefault();
    e.stopImmediatePropagation();
    toast(READONLY_MSG());
  }
}, true);

// ---------- 화면 전환 (주소 # 뒤로 구분) ----------
// #home · #notice · #notice/view/12 · #notice/write · #notice/edit/12
// #board · #board/view/3 · #board/write · #board/edit/3 (board.js) · #other/메뉴이름
function showView(id) {
  document.querySelectorAll('.view').forEach(v => v.classList.toggle('on', v.id === id));
  document.querySelector('.content').scrollTop = 0;
}
function markMenu(key) {
  document.querySelectorAll('.nav-item').forEach(a => a.classList.toggle('on', a.getAttribute('href') === `#${key}`));
}

function route() {
  const [view, action, id] = decodeURIComponent(location.hash.slice(1) || 'notice').split('/');
  closeMenu();
  if (view === 'home') { markMenu('home'); renderHome(); return showView('view-home'); }
  if (view === 'other') {
    markMenu(`other/${action}`);
    $('otherCrumb').textContent = action;
    $('otherTitle').textContent = action;
    return showView('view-other');
  }
  if (view === 'board') { markMenu('board'); return routeBoard(action, id); }
  markMenu('notice');
  if (action === 'view') return openDetail(id);
  if (action === 'write' || action === 'edit') return openForm(action === 'edit' ? id : null);
  renderList();
  showView('view-notice-list');
}

// 작성 중에 다른 곳으로 가려 하면 먼저 물어본다
document.addEventListener('click', async e => {
  const a = e.target.closest('a[href^="#"]');
  if (!a || !anyFormDirty()) return;
  e.preventDefault();
  if (await confirmDialog('leaveDialog')) { setLeaving(true); location.hash = a.getAttribute('href'); }
});
// 공지 작성 화면과 게시판 작성 화면 둘 다 확인한다
const anyFormDirty = () => formDirty() || (typeof boardFormDirty === 'function' && boardFormDirty());
function setLeaving(v) {
  formState.leaving = v;
  if (typeof boardFormState === 'object') boardFormState.leaving = v;
}
window.addEventListener('hashchange', () => { setLeaving(false); route(); });

// ---------- 홈 ----------
function renderHome() {
  const ul = $('homeNotices');
  // 홈: 고정 공지는 2개까지만 먼저, 나머지는 최신 공지 (모두 합쳐 5개). 고정이 많아도 새 공지가 가려지지 않게
  const list = byNewest();
  const pins = list.filter(n => n.pinned).slice(0, 2);
  const top5 = [...pins, ...list.filter(n => !pins.includes(n))].slice(0, 5);
  ul.replaceChildren(...top5.map(n => {
    const li = el('li');
    const a = el('a', { href: `#notice/view/${n.id}` });
    if (n.pinned) a.append(el('span', { class: 'pin-label' }, '고정 '));
    a.append(el('span', {}, n.title));
    li.append(a, el('small', {}, fmtDate(n.createdAt).slice(5)));
    return li;
  }));
}

// ---------- 1. 목록 ----------
const listState = { page: 1, q: '' };

function filtered() {
  // 제목 + 내용 검색. 띄어쓴 단어는 모두 들어 있어야 찾는다 (실제 SQL: title LIKE … OR content LIKE …)
  const words = listState.q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const all = byNewest();
  if (!words.length) return all;
  return all.filter(n => {
    const text = `${n.title}\n${n.content}`.toLowerCase();
    return words.every(w => text.includes(w));
  });
}

function renderList() {
  $('btnWrite').hidden = !canWrite(me);
  // 치고 있는 중에는 검색창을 건드리지 않는다 (상세에서 돌아왔을 때만 검색어를 다시 채움)
  if (document.activeElement !== $('searchInput')) $('searchInput').value = listState.q;

  const rows = filtered();
  // 맨 위 고정: 검색하지 않을 때만 모든 페이지 맨 위에 따로 보여 준다. 일반 목록에서는 빼서 두 번 나오지 않게 한다.
  // 검색할 때는 고정 공지도 일반 결과에 섞여 나온다.
  const pinned = listState.q ? [] : rows.filter(n => n.pinned);
  const normal = listState.q ? rows : rows.filter(n => !n.pinned);
  const pages = Math.max(1, Math.ceil(normal.length / PAGE_SIZE));
  listState.page = Math.min(Math.max(1, listState.page), pages);
  const start = (listState.page - 1) * PAGE_SIZE;
  const pageRows = normal.slice(start, start + PAGE_SIZE);

  $('resultInfo').textContent = listState.q
    ? `"${listState.q}" 검색 결과 ${rows.length}건`
    : `전체 ${rows.length}건`;

  const row = (n, noText, isPinned) => {
    const tr = el('tr', isPinned ? { class: 'is-pinned' } : {});
    const titleTd = el('td', { 'data-label': '제목' });
    const wrap = el('span');
    wrap.append(el('a', { href: `#notice/view/${n.id}`, class: 'notice-link' }, n.title));
    if (n.updatedAt) wrap.append(el('span', { class: 'edited-tag' }, '수정됨'));
    titleTd.append(wrap);
    const noTd = el('td', { 'data-label': '번호', class: 'num' });
    if (isPinned) noTd.append(el('span', { class: 'pin-label' }, noText)); else noTd.textContent = noText;
    tr.append(
      noTd,
      titleTd,
      el('td', { 'data-label': '작성자' }, n.writerName),
      el('td', { 'data-label': '작성일', class: 'num' }, fmtDate(n.createdAt)),
    );
    return tr;
  };

  const tbody = $('noticeRows');
  if (!pinned.length && !pageRows.length) {
    const tr = el('tr', { class: 'row-empty' });
    tr.append(el('td', { colspan: '4' }, listState.q ? '검색 결과가 없습니다.' : '등록된 공지가 없습니다.'));
    tbody.replaceChildren(tr);
  } else {
    tbody.replaceChildren(
      ...pinned.map(n => row(n, '고정', true)),
      // 번호: 일반 공지 개수에서 거꾸로 센다 (가장 최근 공지가 가장 큰 번호)
      ...pageRows.map((n, i) => row(n, String(normal.length - start - i), false)),
    );
  }
  renderPager('pager', listState, pages, renderList);
}

// 페이지 번호 (공지·게시판 같이 씀): state.page를 바꾸고 rerender로 다시 그린다
function renderPager(navId, state, pages, rerender) {
  const nav = $(navId);
  const btn = (label, page, opts = {}) => {
    const b = el('button', { type: 'button' }, label);
    if (opts.aria) b.setAttribute('aria-label', opts.aria);
    if (page === state.page && opts.current) b.setAttribute('aria-current', 'page');
    b.disabled = !!opts.disabled;
    b.addEventListener('click', () => { state.page = page; rerender(); document.querySelector('.content').scrollTop = 0; });
    return b;
  };
  const items = [btn('이전', state.page - 1, { disabled: state.page === 1, aria: '이전 페이지' })];
  for (let p = 1; p <= pages; p++) items.push(btn(String(p), p, { current: true, aria: `${p}페이지` }));
  items.push(btn('다음', state.page + 1, { disabled: state.page === pages, aria: '다음 페이지' }));
  nav.replaceChildren(...items);
  nav.hidden = pages <= 1;
}

// 실시간 검색: 글자를 칠 때마다 바로 걸러 보여 준다. 다 지우면 전체 목록.
// 실제 프로젝트에서는 이때 서버에 AJAX를 보내므로, 타자를 멈추고 0.25초 뒤에 한 번만 검색한다(디바운스).
const SEARCH_DELAY = 250;
let searchTimer;
function runSearch() {
  const q = $('searchInput').value.trim();
  if (q === listState.q) return; // 띄어쓰기만 늘었으면 다시 그리지 않는다
  listState.q = q;
  listState.page = 1;
  renderList();
}
$('searchInput').addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(runSearch, SEARCH_DELAY);
});
// Enter는 기다리지 않고 바로 검색
$('searchForm').addEventListener('submit', e => {
  e.preventDefault();
  clearTimeout(searchTimer);
  runSearch();
});

// 사이드 메뉴 "공지사항"은 항상 처음 목록 (상세의 "목록으로"는 검색 결과를 그대로 둔다)
document.querySelector('.nav-item[data-view="notice"]').addEventListener('click', () => {
  listState.q = '';
  listState.page = 1;
  if (location.hash === '#notice') { renderList(); closeMenu(); } // 이미 목록이면 주소가 안 바뀌어서 직접 다시 그린다
});

// ---------- 2. 상세 ----------
let currentId = null;

function openDetail(id) {
  const n = findNotice(id);
  if (!n) { toast('없는 공지이거나 삭제된 공지입니다'); location.replace('#notice'); return; }
  currentId = n.id;
  $('detailTitle').textContent = n.title;

  const meta = $('detailMeta');
  const who = el('span');
  who.append(el('strong', {}, `${n.writerDept} ${n.writerName}`));
  const parts = [who, el('span', {}, `${fmtTime(n.createdAt)} 작성`)];
  if (n.pinned) parts.unshift(el('span', { class: 'pin-tag' }, '맨 위 고정'));
  if (n.updatedAt) parts.push(el('span', {}, `${fmtTime(n.updatedAt)} 수정`));
  meta.replaceChildren(...parts);

  $('detailBody').textContent = n.content; // 일반 글자 그대로 (줄바꿈은 CSS가 살린다)

  const mine = canEdit(me, n);
  $('detailOwnerActions').hidden = !mine;
  $('btnEdit').setAttribute('href', `#notice/edit/${n.id}`);
  $('btnPin').textContent = n.pinned ? '고정 풀기' : '고정하기';
  $('btnPin').setAttribute('aria-pressed', String(!!n.pinned));
  showView('view-notice-detail');
}

// 고정하기 / 고정 풀기: 수정 화면에 가지 않고 바로 바꾼다. 내용을 고친 게 아니라서 수정일은 그대로 둔다
$('btnPin').addEventListener('click', async () => {
  const n = findNotice(currentId);
  if (!n) return;
  // 서버라면 여기서 다시 확인한다: 권한, 읽기 전용, 고정 개수 (두 사람이 동시에 눌러도 5개를 넘지 않게)
  if (!canEdit(me, n) || readonly) { toast('고정을 바꿀 권한이 없습니다'); return; }
  if (!n.pinned && pinFull(n.id)) { await showPinFull(); return; }
  n.pinned = !n.pinned;
  save(store);
  toast(n.pinned ? '목록 맨 위에 고정했습니다' : '고정을 풀었습니다');
  openDetail(n.id);
});

// 5개가 꽉 찼을 때: 지금 고정된 공지 목록을 보여 주고, 누르면 그 공지로 가서 바로 풀 수 있게 한다
function showPinFull() {
  const d = $('pinFullDialog');
  const pins = byNewest().filter(x => x.pinned);
  $('pinFullList').replaceChildren(...pins.map(x => {
    const li = el('li');
    const a = el('a', { href: `#notice/view/${x.id}` });
    a.append(el('span', {}, x.title), el('small', {}, `${x.writerName} · ${fmtDate(x.createdAt)}`));
    a.addEventListener('click', () => d.close());
    li.append(a);
    return li;
  }));
  return confirmDialog('pinFullDialog');
}

$('btnDelete').addEventListener('click', async () => {
  const n = findNotice(currentId);
  if (!n) return;
  if (!(await confirmDialog('deleteDialog'))) return;
  // 서버라면 여기서 한 번 더 확인한다: 권한, 읽기 전용
  if (!canEdit(me, n) || readonly) { toast('삭제할 권한이 없습니다'); return; }
  store.list = store.list.filter(x => x.id !== n.id);
  save(store);
  toast('공지를 삭제했습니다');
  location.hash = '#notice';
});

// ---------- 3. 작성 · 수정 ----------
const formState = { id: null, initial: { title: '', content: '', pinned: false }, saving: false, leaving: false };
const formOpen = () => $('view-notice-form').classList.contains('on');
const formDirty = () => formOpen() && !formState.leaving && !formState.saving &&
  ($('fTitle').value !== formState.initial.title || $('fContent').value !== formState.initial.content ||
   $('fPinned').checked !== formState.initial.pinned);

function openForm(id) {
  // 주소를 직접 쳐서 들어와도 막는다 (실제로는 서버가 403으로 막고, 화면은 안내만 한다)
  if (readonly) { toast(READONLY_MSG()); location.replace('#notice'); return; }
  const n = id ? findNotice(id) : null;
  if (id && !n) { toast('없는 공지이거나 삭제된 공지입니다'); location.replace('#notice'); return; }
  if (n ? !canEdit(me, n) : !canWrite(me)) { toast(n ? '이 공지를 수정할 권한이 없습니다' : '공지를 쓸 권한이 없습니다'); location.replace(n ? `#notice/view/${n.id}` : '#notice'); return; }

  formState.id = n ? n.id : null;
  formState.initial = { title: n ? n.title : '', content: n ? n.content : '', pinned: !!(n && n.pinned) };
  formState.saving = false;
  $('formTitle').textContent = n ? '공지 수정' : '공지 쓰기';
  $('formCrumb').textContent = n ? '수정' : '글쓰기';
  $('fTitle').value = formState.initial.title;
  $('fContent').value = formState.initial.content;
  $('fPinned').checked = formState.initial.pinned;
  $('btnSave').disabled = false;
  $('btnSave').textContent = '저장';
  clearErrors();
  updateCounters();
  showView('view-notice-form');
  $('fTitle').focus();
}

function updateCounters() {
  const set = (input, counter, max) => {
    const len = $(input).value.length;
    $(counter).textContent = `${len} / ${max}`;
    $(counter).classList.toggle('is-full', len >= max);
  };
  set('fTitle', 'fTitleCount', TITLE_MAX);
  set('fContent', 'fContentCount', CONTENT_MAX);
}
$('fTitle').addEventListener('input', () => { updateCounters(); setError('fTitle', ''); });
$('fContent').addEventListener('input', () => { updateCounters(); setError('fContent', ''); });

function setError(field, msg) {
  $(`${field}Error`).textContent = msg;
  $(field).classList.toggle('is-error', !!msg);
  $(field).setAttribute('aria-invalid', msg ? 'true' : 'false');
}
function clearErrors() { setError('fTitle', ''); setError('fContent', ''); $('fPinnedError').textContent = ''; }
$('fPinned').addEventListener('change', () => { $('fPinnedError').textContent = ''; });

// 이미 고정된 공지가 5개면 더 고정할 수 없다 (지금 고치는 공지는 빼고 센다)
function pinFull(exceptId) {
  return store.list.filter(n => n.pinned && n.id !== exceptId).length >= PIN_MAX;
}

// 서버가 다시 검사하는 규칙과 같게 맞춘다 (화면 명세: 400 + data.field)
function validate(title, content) {
  if (!title) return { field: 'fTitle', message: '제목을 입력해 주세요' };
  if (title.length > TITLE_MAX) return { field: 'fTitle', message: `제목은 ${TITLE_MAX}자까지 쓸 수 있습니다` };
  if (!content) return { field: 'fContent', message: '내용을 입력해 주세요' };
  if (content.length > CONTENT_MAX) return { field: 'fContent', message: `내용은 ${CONTENT_MAX}자까지 쓸 수 있습니다` };
  return null;
}

$('noticeForm').addEventListener('submit', e => {
  e.preventDefault();
  if (formState.saving) return; // 두 번 눌러도 한 번만 저장
  clearErrors();
  const title = $('fTitle').value.trim();
  const content = $('fContent').value.replace(/\s+$/, '').replace(/^\s*\n/, '');
  const err = validate(title, content);
  if (err) { setError(err.field, err.message); $(err.field).focus(); return; }
  const pinned = $('fPinned').checked;
  if (pinned && pinFull(formState.id)) {
    $('fPinnedError').textContent = `맨 위 고정은 ${PIN_MAX}개까지입니다. 다른 공지의 고정을 먼저 풀어 주세요.`;
    return;
  }

  formState.saving = true;
  $('btnSave').disabled = true;
  $('btnSave').textContent = '저장 중…';

  // 서버에 보내는 것처럼 잠깐 기다린다
  setTimeout(() => {
    const now = new Date().toISOString();
    let id = formState.id;
    if (id) {
      const n = findNotice(id);
      if (!n || !canEdit(me, n) || readonly) { formState.saving = false; toast('수정할 권한이 없습니다'); location.hash = '#notice'; return; }
      // 제목·내용을 고쳤을 때만 수정일을 바꾼다 (고정만 바꾼 경우는 그대로)
      const changed = n.title !== title || n.content !== content;
      Object.assign(n, { title, content, pinned, updatedAt: changed ? now : n.updatedAt });
    } else {
      if (!canWrite(me) || readonly) { formState.saving = false; toast('공지를 쓸 권한이 없습니다'); location.hash = '#notice'; return; }
      id = store.nextId++;
      store.list.push({ id, title, content, pinned, writerEmpNo: me.empNo, writerName: me.name, writerDept: me.dept, createdAt: now, updatedAt: null });
      listState.page = 1; listState.q = '';
    }
    save(store);
    toast(formState.id ? '공지를 수정했습니다' : '공지를 등록했습니다');
    location.hash = `#notice/view/${id}`;
  }, 500);
});

$('btnCancel').addEventListener('click', async () => {
  if (formDirty() && !(await confirmDialog('leaveDialog'))) return;
  formState.leaving = true;
  location.hash = formState.id ? `#notice/view/${formState.id}` : '#notice';
});

// ---------- 좁은 화면 메뉴 서랍 ----------
function closeMenu() {
  document.body.classList.remove('menu-open');
  $('btnMenu').setAttribute('aria-expanded', 'false');
}
$('btnMenu').addEventListener('click', () => {
  const open = document.body.classList.toggle('menu-open');
  $('btnMenu').setAttribute('aria-expanded', String(open));
});
$('backdrop').addEventListener('click', closeMenu);
$('btnLogout').addEventListener('click', () => toast('시안에서는 로그아웃하지 않습니다'));

// ---------- 시작 ----------
// board.js까지 다 읽은 뒤에 시작한다 (주소가 #board로 시작해도 열리게)
document.addEventListener('DOMContentLoaded', () => {
  setupDemo();
  applyUser();
  route();
});
