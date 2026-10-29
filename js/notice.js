// 공지사항·부서 게시판 (PR #10 시안을 그룹웨어 안으로 옮김, 2026-10-29)
// 원래는 notice.html이 자기 머리글·사이드 메뉴·시연용 사용자 상자를 따로 가진 페이지였다. 이제 index.html 안의
// 화면이고, 로그인한 사람·역할·부서·읽기 전용·토스트·메뉴는 그룹웨어 것을 쓴다. 다른 스크립트와 이름이
// 겹치지 않게 함수 하나로 감싸고, 그룹웨어에는 아래 두 가지만 내놓는다.
//   routeNoticeBoard(hash): #notice… / #board-dept… 화면을 연다 (common.js showView가 부른다)
//   renderHomeNotices(): 홈 화면의 공지 5개를 그린다
// 실제 프로젝트에서는 /notice/*, /board/* JSP와 서버 권한 검사로 나뉜다 (화면 명세).
(() => {
// 공지사항 시안 (목록 · 상세 · 작성/수정)
// - 서버가 없으므로 공지는 브라우저(localStorage)에 저장한다. 실제 프로젝트에서는 DB에 저장하고,
//   권한 검사는 화면(JSP)과 서버(Security·Service) 양쪽에서 한다.
// - 화면에 글자를 넣을 때는 모두 textContent를 쓴다(innerHTML 금지). 남이 쓴 글에 들어 있는
//   <script> 같은 코드가 실행되지 않게 하려는 것이다.

// ---------- 로그인한 사람 (그룹웨어 로그인을 그대로 쓴다) ----------
// 역할은 v1.7처럼 한 사람에 하나로 읽는다: 업체 관리자 > 인사 담당 > IT 담당 > 직원.
// 역할은 계정에 붙은 것을 그대로 따른다 (common.js userRoles).
// 읽기 전용(만료)도 그룹웨어 값(common.js readOnly)을 쓴다.
let me = null;
let readonly = false;
function syncMe() {
  const staff = staffOf(session.company).find(e => e.empNo === session.id);
  const role = userRoles.includes('ADMIN') ? 'ADMIN' : userRoles.includes('HR') ? 'HR' : userRoles.includes('IT') ? 'IT' : 'EMP';
  // the staff record first: a department renamed in 부서 관리 shows here without signing in again
  me = { empNo: session.id, name: session.name, dept: (staff && staff.dept) || session.dept || '경영지원팀', role };
  readonly = readOnly;
}

// 공지·게시글은 업체마다 따로 둔다 (실제로는 DB의 업체 ID로 나뉜다)
const STORE_KEY = 'ieum-notice-demo-v3'; // v3: 그룹웨어 안으로 옮기며 업체마다 따로
const storeKey = () => `${STORE_KEY}:${session.company}`;
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
  // 예시 글쓴이: 관리자 글은 이 업체의 최초 관리자, 인사 글은 예시 인사 담당
  const order = findCompanyOrder(session.company) || {};
  const W = {
    admin: { empNo: order.adminNo || '20260001', name: order.adminName || '업체 관리자', dept: '경영지원팀' },
    hr1: { empNo: '20260002', name: '김인사', dept: '인사팀' },
    hr2: { empNo: '20260003', name: '이인사', dept: '인사팀' },
  };
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
    const s = JSON.parse(localStorage.getItem(storeKey()));
    if (s && Array.isArray(s.list)) return s;
  } catch (e) { /* 저장된 값이 깨졌으면 처음 데이터로 */ }
  const s = seed();
  save(s);
  return s;
}
function save(s) { try { localStorage.setItem(storeKey(), JSON.stringify(s)); } catch (e) { /* 저장 못 해도 화면은 동작 */ } }

// 업체가 바뀌면(다른 업체로 다시 로그인) 그 업체의 공지를 읽는다
let store = null;
let storeOf = '';
function ensureStore() {
  if (storeOf === session.company) return;
  store = load();
  storeOf = session.company;
  listState.page = 1; listState.q = '';
}
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

// 안내는 그룹웨어 토스트(ops.js showToast)로
const toast = msg => showToast(msg);

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

// 읽기 전용 업체: data-write 버튼은 보이되 흐리고, 누르면 이유를 알려 준다. 막는 일은 common.js가 하고,
// 여기서는 주소로 바로 들어온 경우만 같은 안내로 돌려보낸다
const READONLY_MSG = () => readOnlyMessage();

// ---------- 화면 전환 (주소 # 뒤로 구분) ----------
// #notice · #notice/view/12 · #notice/write · #notice/edit/12
// #board-dept · #board-dept/view/3 · #board-dept/write · #board-dept/edit/3
// 그룹웨어 화면 전환(common.js showView)이 메뉴 표시·권한·제목을 맡고, 이 두 메뉴의 화면만 여기로 넘긴다
function showSub(id) {
  document.querySelectorAll('.view').forEach(v => v.classList.toggle('on', v.id === id));
  document.querySelector('.content').scrollTop = 0;
}

function route(hash) {
  syncMe();
  ensureStore();
  ensureBoard();
  const [view, action, id] = decodeURIComponent(hash).split('/');
  if (view === 'board-dept') return routeBoard(action, id);
  if (action === 'view') return openDetail(id);
  if (action === 'write' || action === 'edit') return openForm(action === 'edit' ? id : null);
  renderList();
  showSub('view-notice-list');
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
// 화면은 common.js가 주소가 바뀔 때 다시 그린다. 여기서는 떠나는 중 표시만 지운다
window.addEventListener('hashchange', () => setLeaving(false));

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
  showSub('view-notice-detail');
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
  showSub('view-notice-form');
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

// ---------- 좁은 화면 메뉴 서랍: 그룹웨어 것(common.js setMenu)을 쓴다 ----------
function closeMenu() { setMenu(false); }

// ======================================================================
// 부서 게시판 (목록 · 상세+댓글 · 작성/수정) - 원래 board.js
// 공통 도구($, el, toast, confirmDialog, fmtDate, fmtTime, renderPager, showSub, me, readonly,
// READONLY_MSG, daysAgo)는 위 공지 코드 것을 그대로 쓴다.
//
// 공지와 다른 점
// - 보기: 같은 부서 글만. 다른 부서 글은 주소를 직접 쳐도 "없는 글이거나 볼 수 없는 글"로 막는다
//   (실제로는 서버 Service가 로그인한 사람의 부서와 글의 부서를 비교한다)
// - 쓰기: 부서원 누구나. 수정·삭제는 글쓴이 본인만
// - 댓글: 부서원 누구나 등록, 본인 댓글만 삭제 (댓글 수정은 없음), 500자까지

const BOARD_KEY = 'ieum-board-demo-v2'; // v2: 그룹웨어 안으로 옮기며 업체마다 따로
const boardKey = () => `${BOARD_KEY}:${session.company}`;
const COMMENT_MAX = 500;

// ---------- 저장소 ----------
function boardSeed() {
  // 글쓴이 정보는 시안 사람 목록(USERS)에 없는 사람도 있다 (부서원이 더 있다고 가정)
  const P = {
    choi: { empNo: '20260004', name: '최사원', dept: '개발팀' },
    han: { empNo: '20260006', name: '한개발', dept: '개발팀' },
    yoon: { empNo: '20260007', name: '윤팀장', dept: '개발팀' },
    kim: { empNo: '20260002', name: '김인사', dept: '인사팀' },
    lee: { empNo: '20260003', name: '이인사', dept: '인사팀' },
    // 경영지원팀 글은 이 업체의 최초 관리자가 쓴 것으로 (관리자로 로그인하면 자기 부서 글이 보이게)
    park: { empNo: (findCompanyOrder(session.company) || {}).adminNo || '20260001', name: (findCompanyOrder(session.company) || {}).adminName || '업체 관리자', dept: '경영지원팀' },
    jung: { empNo: '20260005', name: '정아이티', dept: '전산팀' },
  };
  // [글쓴이, 며칠 전, 제목, 내용, [[댓글쓴이, 내용], ...]]
  const rows = [
    ['yoon', 30, '10월 개발팀 업무 분담', '10월 업무 분담입니다.\n\n- 결재 모듈: 최사원\n- 게시판·일정: 한개발\n- 코드 리뷰: 윤팀장\n\n변경이 필요하면 댓글로 남겨 주세요.', [['choi', '확인했습니다.'], ['han', '네 확인했습니다!']]],
    ['han', 27, '개발 서버 접속 주소 정리', '개발 서버 주소를 정리했습니다.\n접속이 안 되면 전산팀에 문의해 주세요.', []],
    ['choi', 25, '점심 메뉴 추천 받습니다', '회사 근처 새로 생긴 식당 가 보신 분 계신가요?', [['han', '2층 국밥집 괜찮아요.'], ['yoon', '돈가스집도 추천합니다.'], ['choi', '감사합니다 내일 가 볼게요!']]],
    ['yoon', 22, '코드 리뷰 규칙', '1. PR은 하루 안에 리뷰합니다.\n2. 리뷰어 1명 이상 승인 후 병합합니다.\n3. 큰 변경은 미리 공유해 주세요.', [['han', '좋습니다.']]],
    ['han', 20, '스터디 모집 (Spring)', '매주 수요일 저녁 Spring 스터디를 하려고 합니다.\n관심 있는 분 댓글 주세요.', [['choi', '참여하고 싶습니다!']]],
    ['choi', 18, '모니터 추가 요청 방법', 'IT 서비스 > 장애·문의 접수에서 지급 요청으로 신청하면 됩니다.', []],
    ['yoon', 15, '배포 일정 안내', '이번 주 금요일 오후 4시에 배포합니다.\n그 전에 develop 브랜치에 병합해 주세요.', [['choi', '네 알겠습니다.'], ['han', '목요일까지 올리겠습니다.']]],
    ['han', 12, '테스트 계정 공유', '테스트 계정은 팀 문서에 정리해 두었습니다.', []],
    ['choi', 10, '회의실 예약 겹침 주의', '수요일 오후 회의실이 자주 겹칩니다.\n미리 확인 부탁드려요.', [['yoon', '확인하겠습니다.']]],
    ['yoon', 8, '11월 회고 일정', '11월 첫째 주 금요일에 회고를 진행합니다.', []],
    ['han', 5, '버그 공유: 로그인 후 새로고침', '로그인 직후 새로고침하면 메뉴가 잠깐 안 보이는 현상이 있습니다.\n재현 방법은 아래와 같습니다.\n\n1. 로그인\n2. F5\n3. 사이드 메뉴 확인', [['yoon', '이슈 등록해 주세요.'], ['han', '등록했습니다.']]],
    ['choi', 3, '개발팀 회식 장소 투표', '다음 주 회식 장소를 정하려고 합니다.\n원하는 곳을 댓글로 남겨 주세요.', [['han', '고기집!'], ['yoon', '저도 고기집 좋습니다.']]],
    ['yoon', 1, '이번 주 금요일 재택', '이번 주 금요일은 팀 전체 재택입니다.', []],
    ['kim', 20, '인사팀 업무 인수인계', '연말정산 업무 인수인계 자료를 공유합니다.', [['lee', '확인했습니다.']]],
    ['lee', 9, '평가 양식 초안 검토 요청', '평가 양식 초안을 검토해 주세요.', []],
    ['kim', 2, '인사팀 주간 회의', '주간 회의는 월요일 10시입니다.', []],
    ['park', 14, '경영지원팀 비품 주문', '필요한 비품을 금요일까지 알려 주세요.', []],
    ['jung', 6, '전산팀 점검 일정', '서버 점검은 일요일 새벽에 진행합니다.', []],
  ];
  let cid = 1;
  return {
    nextId: rows.length + 1,
    posts: rows.map(([who, d, title, content, comments], i) => {
      const w = P[who];
      return {
        id: i + 1, dept: w.dept, title, content,
        writerEmpNo: w.empNo, writerName: w.name,
        createdAt: daysAgo(d, 9 + (i % 8), (i * 11) % 60), updatedAt: null,
        comments: comments.map(([cw, text], k) => ({
          id: cid++, writerEmpNo: P[cw].empNo, writerName: P[cw].name, text,
          createdAt: daysAgo(Math.max(d - k, 0), 13 + k, (k * 17) % 60),
        })),
      };
    }),
    nextCid: cid,
  };
}

function loadBoard() {
  try {
    const s = JSON.parse(localStorage.getItem(boardKey()));
    if (s && Array.isArray(s.posts)) return s;
  } catch (e) { /* 깨졌으면 처음 데이터로 */ }
  const s = boardSeed();
  saveBoard(s);
  return s;
}
function saveBoard(s) { try { localStorage.setItem(boardKey(), JSON.stringify(s)); } catch (e) { /* 무시 */ } }

let board = null;
let boardOf = '';
function ensureBoard() {
  if (boardOf === session.company) return;
  board = loadBoard();
  boardOf = session.company;
  boardState.page = 1; boardState.q = '';
}

// ---------- 권한 ----------
// 실제로는 서버가 로그인한 사람의 부서로만 글을 찾는다 (주소의 부서 값을 믿지 않음, v1.6 4장)
const canSeePost = p => p.dept === me.dept;
const isPostOwner = p => p.writerEmpNo === me.empNo;
const isCommentOwner = c => c.writerEmpNo === me.empNo;
const findPost = id => board.posts.find(p => p.id === Number(id));
// 볼 수 없는 글과 없는 글을 같은 말로 안내한다 (다른 부서 글이 있는지 드러내지 않게)
const NOT_FOUND = '없는 글이거나 볼 수 없는 글입니다';

// ---------- 화면 전환 ----------
function routeBoard(action, id) {
  if (action === 'view') return openBoardDetail(id);
  if (action === 'write' || action === 'edit') return openBoardForm(action === 'edit' ? id : null);
  renderBoardList();
  showSub('view-board-list');
}

// ---------- 1. 목록 ----------
const boardState = { page: 1, q: '' };

function myDeptPosts() {
  const words = boardState.q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return board.posts
    .filter(canSeePost)
    .filter(p => !words.length || words.every(w => `${p.title}\n${p.content}`.toLowerCase().includes(w)))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
}

function renderBoardList() {
  $('bListTitle').textContent = `${me.dept} 게시판`;
  $('bListSub').textContent = `${me.dept} 사람만 보고 쓸 수 있어요.`;
  if (document.activeElement !== $('bSearchInput')) $('bSearchInput').value = boardState.q;

  const rows = myDeptPosts();
  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  boardState.page = Math.min(Math.max(1, boardState.page), pages);
  const start = (boardState.page - 1) * PAGE_SIZE;
  const pageRows = rows.slice(start, start + PAGE_SIZE);

  $('bResultInfo').textContent = boardState.q ? `"${boardState.q}" 검색 결과 ${rows.length}건` : `전체 ${rows.length}건`;

  const tbody = $('bRows');
  if (!pageRows.length) {
    const tr = el('tr', { class: 'row-empty' });
    tr.append(el('td', { colspan: '4' }, boardState.q ? '검색 결과가 없습니다.' : '아직 글이 없습니다. 첫 글을 써 보세요.'));
    tbody.replaceChildren(tr);
  } else {
    tbody.replaceChildren(...pageRows.map((p, i) => {
      const tr = el('tr');
      const titleTd = el('td', { 'data-label': '제목' });
      const wrap = el('span');
      wrap.append(el('a', { href: `#board-dept/view/${p.id}`, class: 'notice-link' }, p.title));
      // 댓글 수: 제목 옆에 [3]
      if (p.comments.length) wrap.append(el('span', { class: 'comment-badge', 'aria-label': `댓글 ${p.comments.length}개` }, `[${p.comments.length}]`));
      if (p.updatedAt) wrap.append(el('span', { class: 'edited-tag' }, '수정됨'));
      titleTd.append(wrap);
      tr.append(
        el('td', { 'data-label': '번호', class: 'num' }, String(rows.length - start - i)),
        titleTd,
        el('td', { 'data-label': '작성자' }, p.writerName),
        el('td', { 'data-label': '작성일', class: 'num' }, fmtDate(p.createdAt)),
      );
      return tr;
    }));
  }
  renderPager('bPager', boardState, pages, renderBoardList);
}

// 실시간 검색 (공지와 같음: 멈추고 0.25초 뒤 한 번)
let bSearchTimer;
function runBoardSearch() {
  const q = $('bSearchInput').value.trim();
  if (q === boardState.q) return;
  boardState.q = q;
  boardState.page = 1;
  renderBoardList();
}
$('bSearchInput').addEventListener('input', () => { clearTimeout(bSearchTimer); bSearchTimer = setTimeout(runBoardSearch, SEARCH_DELAY); });
$('bSearchForm').addEventListener('submit', e => { e.preventDefault(); clearTimeout(bSearchTimer); runBoardSearch(); });
// 사이드 메뉴 "부서 게시판"은 항상 처음 목록
document.querySelector('.nav-item[data-view="board-dept"]').addEventListener('click', () => {
  boardState.q = ''; boardState.page = 1;
  if (location.hash === '#board-dept') { renderBoardList(); closeMenu(); }
});

// ---------- 2. 상세 + 댓글 ----------
let bCurrentId = null;

function openBoardDetail(id) {
  const p = findPost(id);
  if (!p || !canSeePost(p)) { toast(NOT_FOUND); location.replace('#board-dept'); return; }
  bCurrentId = p.id;
  $('bDetailTitle').textContent = p.title;
  const who = el('span');
  who.append(el('strong', {}, p.writerName));
  const parts = [who, el('span', {}, `${fmtTime(p.createdAt)} 작성`)];
  if (p.updatedAt) parts.push(el('span', {}, `${fmtTime(p.updatedAt)} 수정`));
  $('bDetailMeta').replaceChildren(...parts);
  $('bDetailBody').textContent = p.content;
  $('bOwnerActions').hidden = !isPostOwner(p);
  $('bBtnEdit').setAttribute('href', `#board-dept/edit/${p.id}`);
  $('bCommentText').value = '';
  setFieldError('bCommentText', 'bCommentError', '');
  updateCount('bCommentText', 'bCommentCountNow', COMMENT_MAX);
  renderComments(p);
  showSub('view-board-detail');
}

function renderComments(p) {
  $('bCommentCount').textContent = String(p.comments.length);
  $('bCommentEmpty').hidden = p.comments.length > 0;
  $('bComments').replaceChildren(...p.comments.map(c => {
    const li = el('li');
    const head = el('div', { class: 'comment-head' });
    head.append(el('strong', {}, c.writerName), el('small', {}, fmtTime(c.createdAt)));
    if (isCommentOwner(c)) {
      const del = el('button', { type: 'button', class: 'link-btn', 'data-write': '' }, '삭제');
      del.addEventListener('click', () => deleteComment(c.id));
      head.append(del);
    }
    li.append(head, el('p', { class: 'comment-text' }, c.text));
    return li;
  }));
}

let commentSaving = false;
$('bCommentText').addEventListener('input', () => {
  updateCount('bCommentText', 'bCommentCountNow', COMMENT_MAX);
  setFieldError('bCommentText', 'bCommentError', '');
});
$('bCommentForm').addEventListener('submit', e => {
  e.preventDefault();
  if (commentSaving) return;
  const p = findPost(bCurrentId);
  if (!p || !canSeePost(p)) { toast(NOT_FOUND); location.hash = '#board-dept'; return; }
  if (readonly) { toast(READONLY_MSG()); return; }
  const text = $('bCommentText').value.trim();
  if (!text) { setFieldError('bCommentText', 'bCommentError', '댓글을 입력해 주세요'); $('bCommentText').focus(); return; }
  if (text.length > COMMENT_MAX) { setFieldError('bCommentText', 'bCommentError', `댓글은 ${COMMENT_MAX}자까지 쓸 수 있습니다`); return; }
  commentSaving = true;
  $('bCommentSave').disabled = true;
  setTimeout(() => {
    p.comments.push({ id: board.nextCid++, writerEmpNo: me.empNo, writerName: me.name, text, createdAt: new Date().toISOString() });
    saveBoard(board);
    commentSaving = false;
    $('bCommentSave').disabled = false;
    $('bCommentText').value = '';
    updateCount('bCommentText', 'bCommentCountNow', COMMENT_MAX);
    renderComments(p);
    toast('댓글을 등록했습니다');
  }, 300);
});

async function deleteComment(cid) {
  const p = findPost(bCurrentId);
  const c = p && p.comments.find(x => x.id === cid);
  if (!c) return;
  if (!(await confirmDialog('commentDeleteDialog'))) return;
  if (!isCommentOwner(c) || readonly) { toast('삭제할 권한이 없습니다'); return; }
  p.comments = p.comments.filter(x => x.id !== cid);
  saveBoard(board);
  renderComments(p);
  toast('댓글을 삭제했습니다');
}

$('bBtnDelete').addEventListener('click', async () => {
  const p = findPost(bCurrentId);
  if (!p) return;
  const d = $('deleteDialog');
  // 공지 삭제 창을 같이 쓰되, 글자만 게시판에 맞게 바꾼다
  d.querySelector('h2').textContent = '게시글을 삭제할까요?';
  d.querySelector('p').textContent = p.comments.length
    ? `댓글 ${p.comments.length}개도 함께 삭제되며 되돌릴 수 없습니다.` : '삭제한 글은 되돌릴 수 없습니다.';
  const ok = await confirmDialog('deleteDialog');
  d.querySelector('h2').textContent = '공지를 삭제할까요?';
  d.querySelector('p').textContent = '삭제한 공지는 되돌릴 수 없습니다.';
  if (!ok) return;
  if (!isPostOwner(p) || readonly) { toast('삭제할 권한이 없습니다'); return; }
  board.posts = board.posts.filter(x => x.id !== p.id);
  saveBoard(board);
  toast('게시글을 삭제했습니다');
  location.hash = '#board-dept';
});

// ---------- 3. 작성 · 수정 ----------
const boardFormState = { id: null, initial: { title: '', content: '' }, saving: false, leaving: false };
const boardFormOpen = () => $('view-board-form').classList.contains('on');
const boardFormDirty = () => boardFormOpen() && !boardFormState.leaving && !boardFormState.saving &&
  ($('bTitle').value !== boardFormState.initial.title || $('bContent').value !== boardFormState.initial.content);

function updateCount(input, counter, max) {
  const len = $(input).value.length;
  $(counter).textContent = `${len} / ${max}`;
  $(counter).classList.toggle('is-full', len >= max);
}
function setFieldError(input, errId, msg) {
  $(errId).textContent = msg;
  $(input).classList.toggle('is-error', !!msg);
  $(input).setAttribute('aria-invalid', msg ? 'true' : 'false');
}

function openBoardForm(id) {
  if (readonly) { toast(READONLY_MSG()); location.replace('#board-dept'); return; }
  const p = id ? findPost(id) : null;
  if (id && (!p || !canSeePost(p))) { toast(NOT_FOUND); location.replace('#board-dept'); return; }
  if (p && !isPostOwner(p)) { toast('이 글을 수정할 권한이 없습니다'); location.replace(`#board-dept/view/${p.id}`); return; }

  boardFormState.id = p ? p.id : null;
  boardFormState.initial = { title: p ? p.title : '', content: p ? p.content : '' };
  boardFormState.saving = false;
  $('bFormTitle').textContent = p ? '글 수정' : '글쓰기';
  $('bFormCrumb').textContent = p ? '수정' : '글쓰기';
  $('bFormSub').textContent = `${me.dept} 게시판에 올라갑니다. ${me.dept} 사람만 볼 수 있어요.`;
  $('bTitle').value = boardFormState.initial.title;
  $('bContent').value = boardFormState.initial.content;
  $('bBtnSave').disabled = false;
  $('bBtnSave').textContent = '저장';
  setFieldError('bTitle', 'bTitleError', '');
  setFieldError('bContent', 'bContentError', '');
  updateCount('bTitle', 'bTitleCount', TITLE_MAX);
  updateCount('bContent', 'bContentCount', CONTENT_MAX);
  showSub('view-board-form');
  $('bTitle').focus();
}

$('bTitle').addEventListener('input', () => { updateCount('bTitle', 'bTitleCount', TITLE_MAX); setFieldError('bTitle', 'bTitleError', ''); });
$('bContent').addEventListener('input', () => { updateCount('bContent', 'bContentCount', CONTENT_MAX); setFieldError('bContent', 'bContentError', ''); });

$('bForm').addEventListener('submit', e => {
  e.preventDefault();
  if (boardFormState.saving) return;
  setFieldError('bTitle', 'bTitleError', '');
  setFieldError('bContent', 'bContentError', '');
  const title = $('bTitle').value.trim();
  const content = $('bContent').value.replace(/\s+$/, '').replace(/^\s*\n/, '');
  const err = validate(title, content); // 공지와 같은 규칙 (notice.js)
  if (err) {
    const map = { fTitle: ['bTitle', 'bTitleError'], fContent: ['bContent', 'bContentError'] }[err.field];
    setFieldError(map[0], map[1], err.message.replace('공지', '글'));
    $(map[0]).focus();
    return;
  }
  boardFormState.saving = true;
  $('bBtnSave').disabled = true;
  $('bBtnSave').textContent = '저장 중…';
  setTimeout(() => {
    const now = new Date().toISOString();
    let id = boardFormState.id;
    if (id) {
      const p = findPost(id);
      if (!p || !isPostOwner(p) || readonly) { boardFormState.saving = false; toast('수정할 권한이 없습니다'); location.hash = '#board-dept'; return; }
      Object.assign(p, { title, content, updatedAt: now });
    } else {
      if (readonly) { boardFormState.saving = false; toast(READONLY_MSG()); location.hash = '#board-dept'; return; }
      id = board.nextId++;
      // 부서는 화면에서 받지 않고 로그인한 사람의 부서로 정한다 (실제로도 서버가 정함)
      board.posts.push({ id, dept: me.dept, title, content, writerEmpNo: me.empNo, writerName: me.name, createdAt: now, updatedAt: null, comments: [] });
      boardState.page = 1; boardState.q = '';
    }
    saveBoard(board);
    toast(boardFormState.id ? '글을 수정했습니다' : '글을 등록했습니다');
    location.hash = `#board-dept/view/${id}`;
  }, 500);
});

$('bBtnCancel').addEventListener('click', async () => {
  if (boardFormDirty() && !(await confirmDialog('leaveDialog'))) return;
  boardFormState.leaving = true;
  location.hash = boardFormState.id ? `#board-dept/view/${boardFormState.id}` : '#board-dept';
});

// ---------- 그룹웨어에 내놓는 것 ----------
window.routeNoticeBoard = route;
window.renderHomeNotices = () => { syncMe(); ensureStore(); renderHome(); };
})();
