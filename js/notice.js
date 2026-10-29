// 공지사항·부서 게시판·익명 게시판 (PR #10 시안을 그룹웨어 안으로 옮김, 2026-10-29)
// 10-29 추가: 공지 범위(전사·부서), 부서장 권한, 공지 에디터·첨부파일·댓글, 회사 전체 익명 게시판
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
// 부서장은 역할이 아니라 부서 설정에서 지정한 사람이다(v1.6). 그룹웨어가 로그인할 때 HEAD로 알려 준다.
let me = null;
let readonly = false;
function syncMe() {
  const staff = staffOf(session.company).find(e => e.empNo === session.id);
  const role = userRoles.includes('ADMIN') ? 'ADMIN' : userRoles.includes('HR') ? 'HR' : userRoles.includes('IT') ? 'IT' : 'EMP';
  // the staff record first: a department renamed in 부서 관리 shows here without signing in again
  me = { empNo: session.id, name: session.name, dept: (staff && staff.dept) || session.dept || '경영지원팀', role,
    head: userRoles.includes('HEAD') };
  readonly = readOnly;
}

// 공지·게시글은 업체마다 따로 둔다 (실제로는 DB의 업체 ID로 나뉜다)
const STORE_KEY = 'ieum-notice-demo-v4'; // v4: 공지 범위·서식 글·첨부·댓글 (10-29)
const storeKey = () => `${STORE_KEY}:${session.company}`;
const PAGE_SIZE = 10;
const TITLE_MAX = 100;
const CONTENT_MAX = 2000; // 서식을 뺀 글자 수 기준
const PIN_MAX = 5; // 맨 위 고정은 범위마다 5개까지 (전사 5개, 각 부서 5개)
const ALL = 'ALL'; // 전사 공지의 범위 값. 부서 공지는 부서 이름

// ---------- 권한 (10-29 팀 요청) ----------
// 업체 관리자·인사 담당: 전사·모든 부서 공지를 쓰고, 모든 공지를 수정·삭제·고정한다. 모든 부서 공지가 보인다
// 부서장: 자기 부서 공지만 쓰고, 자기가 쓴 공지만 수정·삭제·고정한다
// 직원: 전사 공지 + 자기 부서 공지만 본다
// 읽기 전용(만료) 업체: 쓰는 동작을 모두 막는다
const isManager = u => u.role === 'ADMIN' || u.role === 'HR';
const canWrite = u => isManager(u) || u.head;
const canSeeNotice = (u, n) => n.scope === ALL || isManager(u) || n.scope === u.dept;
const canEdit = (u, n) => isManager(u) || (u.head && n.scope === u.dept && n.writerEmpNo === u.empNo);
// 업체의 부서 목록 + 실제 직원이 있는 부서 (시안 데이터에서 둘이 어긋나도 빠지는 부서가 없게)
const companyDepts = () => [...new Set([...deptsOf(session.company), ...staffOf(session.company).map(e => e.dept).filter(Boolean)])];
const scopesFor = u => (isManager(u) ? [ALL, ...companyDepts()] : u.head ? [u.dept] : []);

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
  // 부서 공지 예시: 이 업체 부서 앞 3곳에 하나씩 (그 부서 부서장이 있으면 부서장이, 없으면 인사 담당이 쓴 것으로)
  const heads = staffOf(session.company).filter(e => e.head);
  // 직원이 있는 부서를 먼저 (시연 계정으로 로그인하면 자기 부서 공지가 보이게)
  const staffDepts = [...new Set(staffOf(session.company).map(e => e.dept).filter(Boolean))];
  const deptRows = [...new Set([...staffDepts, ...companyDepts()])].slice(0, 3).map((dept, k) => {
    const h = heads.find(e => e.dept === dept);
    const w = h ? { empNo: h.empNo, name: h.name, dept } : W.hr1;
    return [w, 4 + k * 3, '이번 주 부서 회의 안내',
      `${dept} 주간 회의는 ${['월', '화', '수'][k]}요일 오전 10시, 3층 회의실입니다.\n각자 지난주 진행 상황을 한 줄로 정리해 와 주세요.`, dept];
  });
  const all = [...rows.map(([who, d, title, content]) => [W[who], d, title, content, ALL]), ...deptRows];
  let cid = 1;
  const list = all.map(([w, d, title, content, scope], i) => {
      const n = { id: i + 1, scope, title, content: textToHtml(content), files: [], comments: [],
        writerEmpNo: w.empNo, writerName: w.name, writerDept: w.dept,
        createdAt: daysAgo(d, 9 + (i % 7), (i * 13) % 60), updatedAt: null,
        pinned: i === 0 || i === 21 }; // 예시: 사용 안내(오래된 글)와 보안 점검을 고정
      if (i === 12 || i === 21) n.updatedAt = daysAgo(Math.max(d - 1, 0), 15, 20); // 수정된 공지 예시
      if (i === 21) { // 보안 점검 공지: 서식과 댓글 예시
        n.content = '<p><b>11월 3일(월) 보안 점검</b>이 있습니다.</p><ol><li>오전 9시 전까지 개인 PC를 켜 두세요.</li>'
          + '<li><span style="color: #a32d2d;">점검 중에는 외부 USB를 쓰지 마세요.</span></li><li>점검이 끝나면 메일로 결과를 알려 드립니다.</li></ol>'
          + '<p>문의: 경영지원팀</p>';
        n.comments = [
          { id: cid++, writerEmpNo: W.hr1.empNo, writerName: W.hr1.name, writerDept: W.hr1.dept, text: '재택 근무자는 어떻게 하면 될까요?', createdAt: daysAgo(1, 11, 5) },
          { id: cid++, writerEmpNo: W.admin.empNo, writerName: W.admin.name, writerDept: W.admin.dept, text: '재택 근무자는 VPN 접속 상태로 PC를 켜 두시면 됩니다.', createdAt: daysAgo(1, 13, 40) },
        ];
      }
      return n;
  });
  return { nextId: all.length + 1, nextCid: cid, list };
}

// ---------- 서식 글(에디터) 다루기 ----------
// 에디터로 쓴 내용은 HTML이다. 남이 몰래 넣은 <script>나 onerror 같은 코드가 실행되지 않도록,
// 허용한 태그와 스타일만 남기고 나머지는 모두 지운다. 실제 프로젝트에서는 서버가 저장하기 전에
// 같은 일을 한다(예: jsoup Safelist). 화면에서도 보여 주기 전에 한 번 더 거른다.
const ALLOWED_TAGS = new Set(['P', 'DIV', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'SPAN', 'UL', 'OL', 'LI']);
// 글자 크기는 10~40px만 (에디터 숫자 칸과 같은 범위)
const sizeOk = v => { const m = /^(\d{2})px$/.exec(v); return !!m && +m[1] >= 10 && +m[1] <= 40; };
const COLOR_RE = /^(#[0-9a-f]{3,6}|rgb\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*\))$/i;

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}
// 일반 글자를 서식 글로: 줄마다 <p>, 빈 줄은 <p><br></p>
const textToHtml = text => text.split('\n').map(line => `<p>${line ? escapeHtml(line) : '<br>'}</p>`).join('');

function sanitizeHtml(html) {
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html');
  const clean = node => {
    [...node.childNodes].forEach(child => {
      if (child.nodeType === Node.TEXT_NODE) return;
      if (child.nodeType !== Node.ELEMENT_NODE) { child.remove(); return; }
      let tag = child.tagName;
      // 에디터가 글자 크기에 쓰는 <font size>는 <span style="font-size">로 바꿔 둔다 (applySize 참고)
      if (tag === 'FONT') {
        const span = doc.createElement('span');
        span.append(...child.childNodes);
        child.replaceWith(span);
        child = span; tag = 'SPAN';
      }
      if (!ALLOWED_TAGS.has(tag)) {
        // 허용하지 않는 태그: <script>·<style>·<iframe> 등은 내용째 지우고, 나머지는 글자만 남긴다
        if (['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'EMBED', 'TEMPLATE', 'svg', 'SVG', 'MATH'].includes(tag)) { child.remove(); return; }
        clean(child);
        child.replaceWith(...child.childNodes);
        return;
      }
      // 허용한 태그도 속성은 모두 지우고, span의 글자색·글자 크기·굵게·기울임·밑줄 스타일만 다시 붙인다
      const style = child.style;
      const keep = {};
      if (tag === 'SPAN') {
        if (COLOR_RE.test((style.color || '').trim())) keep.color = style.color.trim();
        if (sizeOk((style.fontSize || '').trim())) keep['font-size'] = style.fontSize.trim();
        if (/^(bold|700)$/.test(style.fontWeight)) keep['font-weight'] = 'bold';
        if (style.fontStyle === 'italic') keep['font-style'] = 'italic';
        if (/underline/.test(style.textDecorationLine || style.textDecoration || '')) keep['text-decoration'] = 'underline';
      }
      [...child.attributes].forEach(a => child.removeAttribute(a.name));
      const css = Object.entries(keep).map(([k, v]) => `${k}: ${v}`).join('; ');
      if (css) child.setAttribute('style', css);
      clean(child);
    });
  };
  const root = doc.body.firstChild;
  clean(root);
  return root.innerHTML;
}
// 서식을 뺀 글자만 (검색·글자 수 세기용)
function textOf(html) {
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html');
  doc.querySelectorAll('p, div, li, br').forEach(e => e.append('\n'));
  return doc.body.textContent.replace(/\n{3,}/g, '\n\n').trim();
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
  ensureAnon();
  const [view, action, id] = decodeURIComponent(hash).split('/');
  if (view === 'board-dept') return routeBoard(action, id);
  if (view === 'board-anon') return routeAnon(action, id);
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
// 공지·부서 게시판·익명 게시판 작성 화면을 모두 확인한다
const anyFormDirty = () => formDirty() || boardFormDirty() || anonFormDirty();
function setLeaving(v) {
  formState.leaving = v;
  boardFormState.leaving = v;
  anonFormState.leaving = v;
}
// 화면은 common.js가 주소가 바뀔 때 다시 그린다. 여기서는 떠나는 중 표시만 지운다
window.addEventListener('hashchange', () => setLeaving(false));

// ---------- 홈 ----------
function renderHome() {
  const ul = $('homeNotices');
  // 홈: 고정 공지는 2개까지만 먼저, 나머지는 최신 공지 (모두 합쳐 5개). 고정이 많아도 새 공지가 가려지지 않게
  // 볼 수 있는 공지만 (전사 + 내 부서. 관리자·인사 담당은 전부)
  const list = byNewest().filter(n => canSeeNotice(me, n));
  const pins = list.filter(n => n.pinned).slice(0, 2);
  const top5 = [...pins, ...list.filter(n => !pins.includes(n))].slice(0, 5);
  ul.replaceChildren(...top5.map(n => {
    const li = el('li');
    const a = el('a', { href: `#notice/view/${n.id}` });
    if (n.pinned) a.append(el('span', { class: 'pin-label' }, '고정 '));
    // 목록의 구분 칸과 같은 정보: 전사 / 부서 이름
    a.append(el('span', { class: n.scope === ALL ? 'home-scope' : 'home-scope is-dept' }, n.scope === ALL ? '전사' : n.scope));
    a.append(el('span', {}, n.title));
    li.append(a, el('small', {}, fmtDate(n.createdAt).slice(5)));
    return li;
  }));
}

// ---------- 1. 목록 ----------
const listState = { page: 1, q: '', tab: 'all' };

function filtered() {
  // 1) 볼 수 있는 공지만 (실제로는 서버 SQL이 로그인한 사람의 부서로 거른다: scope = 'ALL' OR scope = 내 부서)
  // 2) 탭: 전체 / 부서 공지 (목록에는 범위 꼬리표를 붙이지 않고, 어느 부서 공지인지는 상세에서 보여 준다)
  // 3) 제목 + 내용 검색. 띄어쓴 단어는 모두 들어 있어야 찾는다 (서식은 빼고 글자만 본다)
  const words = listState.q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return byNewest()
    .filter(n => canSeeNotice(me, n))
    .filter(n => listState.tab === 'all' || n.scope !== ALL)
    .filter(n => {
      if (!words.length) return true;
      const text = `${n.title}\n${textOf(n.content)}`.toLowerCase();
      return words.every(w => text.includes(w));
    });
}

// 탭 (전체 / 부서 공지). 직원에게는 "물류팀 공지"처럼 자기 부서 이름으로 보인다. 좌우 화살표로도 옮긴다
function renderTabs() {
  $('noticeTabDept').textContent = isManager(me) ? '부서 공지' : `${me.dept} 공지`;
  document.querySelectorAll('#noticeTabs .type-tab').forEach(t => {
    const on = t.dataset.tab === listState.tab;
    t.setAttribute('aria-selected', String(on));
    t.tabIndex = on ? 0 : -1;
  });
}
$('noticeTabs').addEventListener('click', e => {
  const t = e.target.closest('.type-tab');
  if (!t) return;
  listState.tab = t.dataset.tab;
  listState.page = 1;
  renderList();
});
$('noticeTabs').addEventListener('keydown', e => {
  if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
  const tabs = [...document.querySelectorAll('#noticeTabs .type-tab')];
  const i = tabs.findIndex(t => t.dataset.tab === listState.tab);
  const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
  listState.tab = next.dataset.tab; listState.page = 1;
  renderList();
  next.focus();
});

function renderList() {
  $('btnWrite').hidden = !canWrite(me);
  renderTabs();
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
    if (n.comments.length) wrap.append(el('span', { class: 'comment-badge', 'aria-label': `댓글 ${n.comments.length}개` }, `[${n.comments.length}]`));
    if (n.files.length) wrap.append(el('span', { class: 'file-tag', 'aria-label': `첨부파일 ${n.files.length}개` }, '첨부'));
    if (n.updatedAt) wrap.append(el('span', { class: 'edited-tag' }, '수정됨'));
    titleTd.append(wrap);
    const noTd = el('td', { 'data-label': '번호', class: 'num' });
    if (isPinned) noTd.append(el('span', { class: 'pin-label' }, noText)); else noTd.textContent = noText;
    tr.append(
      noTd,
      // 구분 칸: 전사 공지는 "전사", 부서 공지는 부서 이름 (제목은 깔끔하게 두고 칸으로 나눈다)
      el('td', { 'data-label': '구분', class: n.scope === ALL ? 'scope-cell' : 'scope-cell is-dept' }, n.scope === ALL ? '전사' : n.scope),
      titleTd,
      el('td', { 'data-label': '작성자' }, n.writerName),
      el('td', { 'data-label': '작성일', class: 'num' }, fmtDate(n.createdAt)),
    );
    return tr;
  };

  const tbody = $('noticeRows');
  if (!pinned.length && !pageRows.length) {
    const tr = el('tr', { class: 'row-empty' });
    tr.append(el('td', { colspan: '5' }, listState.q ? '검색 결과가 없습니다.' : '등록된 공지가 없습니다.'));
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
  listState.tab = 'all';
  if (location.hash === '#notice') { renderList(); closeMenu(); } // 이미 목록이면 주소가 안 바뀌어서 직접 다시 그린다
});

// ---------- 2. 상세 ----------
let currentId = null;

// 볼 수 없는 공지(다른 부서 공지)와 없는 공지를 같은 말로 안내한다 (다른 부서에 그런 공지가 있는지 드러내지 않게)
const NOTICE_NOT_FOUND = '없는 공지이거나 볼 수 없는 공지입니다';

function openDetail(id) {
  const n = findNotice(id);
  if (!n || !canSeeNotice(me, n)) { toast(NOTICE_NOT_FOUND); location.replace('#notice'); return; }
  currentId = n.id;
  $('detailTitle').textContent = n.title;

  const meta = $('detailMeta');
  const who = el('span');
  who.append(el('strong', {}, `${n.writerDept} ${n.writerName}`));
  const parts = [
    el('span', { class: `scope-tag${n.scope === ALL ? '' : ' is-dept'}` }, n.scope === ALL ? '전사 공지' : `${n.scope} 공지`),
    who, el('span', {}, `${fmtTime(n.createdAt)} 작성`),
  ];
  if (n.pinned) parts.unshift(el('span', { class: 'pin-tag' }, '맨 위 고정'));
  if (n.updatedAt) parts.push(el('span', {}, `${fmtTime(n.updatedAt)} 수정`));
  meta.replaceChildren(...parts);

  // 서식 글: 허용한 태그만 남긴 뒤 넣는다. 거르지 않은 HTML을 innerHTML로 넣으면 안 된다
  $('detailBody').innerHTML = sanitizeHtml(n.content);
  renderDetailFiles(n);
  $('nCommentText').value = '';
  setFieldError('nCommentText', 'nCommentError', '');
  updateCount('nCommentText', 'nCommentCountNow', COMMENT_MAX);
  renderNoticeComments(n);

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
  if (!n.pinned && pinFull(n.id, n.scope)) { await showPinFull(n.scope); return; }
  n.pinned = !n.pinned;
  save(store);
  toast(n.pinned ? '목록 맨 위에 고정했습니다' : '고정을 풀었습니다');
  openDetail(n.id);
});

// 5개가 꽉 찼을 때: 지금 고정된 공지 목록을 보여 주고, 누르면 그 공지로 가서 바로 풀 수 있게 한다
function showPinFull(scope) {
  const d = $('pinFullDialog');
  d.querySelector('h2').textContent = `${scope === ALL ? '전사 공지' : `${scope} 공지`}의 맨 위 고정은 ${PIN_MAX}개까지예요`;
  const pins = byNewest().filter(x => x.pinned && x.scope === scope);
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

// ---------- 첨부파일 보여 주기 ----------
// 이미지는 미리보기(누르면 크게), 나머지는 이름·크기·내려받기.
// 실제로는 내려받기 주소도 서버가 "이 사람이 이 공지를 볼 수 있나"를 확인한 뒤에만 파일을 준다
// (공지는 숨겼는데 파일 주소를 알면 받아지는 일이 없게)
const isImage = f => /^image\/(png|jpe?g|gif|webp)$/.test(f.type);
const fmtSize = b => (b >= 1048576 ? `${(b / 1048576).toFixed(1)}MB` : `${Math.max(1, Math.round(b / 1024))}KB`);

// 상세 화면의 첨부파일 상자 (공지 · 부서 게시판). ids: { box, count, images, list }
function renderFilesBox(files, ids) {
  $(ids.box).hidden = !files.length;
  $(ids.count).textContent = String(files.length);
  $(ids.images).replaceChildren(...files.filter(isImage).map(f => {
    const b = el('button', { type: 'button', class: 'attach-thumb', 'aria-label': `${f.name} 크게 보기` });
    const img = el('img', { src: f.data, alt: f.name, loading: 'lazy' });
    b.append(img, el('span', {}, f.name));
    b.addEventListener('click', () => {
      $('imageDialogImg').src = f.data;
      $('imageDialogImg').alt = f.name;
      $('imageDialogDownload').href = f.data;
      $('imageDialogDownload').setAttribute('download', f.name);
      confirmDialog('imageDialog');
    });
    return b;
  }));
  $(ids.list).replaceChildren(...files.map(f => {
    const li = el('li');
    li.append(el('span', { class: 'attach-name' }, f.name), el('small', {}, fmtSize(f.size)));
    const a = el('a', { class: 'link-btn', href: f.data, download: f.name }, '내려받기');
    li.append(a);
    return li;
  }));
}
const renderDetailFiles = n => renderFilesBox(n.files, { box: 'detailFiles', count: 'detailFileCount', images: 'detailImages', list: 'detailFileList' });

// ---------- 공지 댓글 ----------
// 그 공지를 볼 수 있는 사람이면 누구나 쓴다. 본인 댓글은 지울 수 있고, 업체 관리자·인사 담당은
// 부적절한 댓글을 정리할 수 있게 모든 댓글을 지울 수 있다
const canDeleteNoticeComment = c => c.writerEmpNo === me.empNo || isManager(me);

function renderNoticeComments(n) {
  $('nCommentCount').textContent = String(n.comments.length);
  $('nCommentEmpty').hidden = n.comments.length > 0;
  $('nComments').replaceChildren(...n.comments.map(c => {
    const li = el('li');
    const head = el('div', { class: 'comment-head' });
    head.append(el('strong', {}, `${c.writerDept} ${c.writerName}`), el('small', {}, fmtTime(c.createdAt)));
    if (canDeleteNoticeComment(c)) {
      const del = el('button', { type: 'button', class: 'link-btn', 'data-write': '' }, '삭제');
      del.addEventListener('click', () => deleteNoticeComment(c.id));
      head.append(del);
    }
    li.append(head, el('p', { class: 'comment-text' }, c.text));
    return li;
  }));
}

let nCommentSaving = false;
$('nCommentText').addEventListener('input', () => {
  updateCount('nCommentText', 'nCommentCountNow', COMMENT_MAX);
  setFieldError('nCommentText', 'nCommentError', '');
});
$('nCommentForm').addEventListener('submit', e => {
  e.preventDefault();
  if (nCommentSaving) return;
  const n = findNotice(currentId);
  if (!n || !canSeeNotice(me, n)) { toast(NOTICE_NOT_FOUND); location.hash = '#notice'; return; }
  if (readonly) { toast(READONLY_MSG()); return; }
  const text = $('nCommentText').value.trim();
  if (!text) { setFieldError('nCommentText', 'nCommentError', '댓글을 입력해 주세요'); $('nCommentText').focus(); return; }
  if (text.length > COMMENT_MAX) { setFieldError('nCommentText', 'nCommentError', `댓글은 ${COMMENT_MAX}자까지 쓸 수 있습니다`); return; }
  nCommentSaving = true;
  $('nCommentSave').disabled = true;
  setTimeout(() => {
    n.comments.push({ id: store.nextCid++, writerEmpNo: me.empNo, writerName: me.name, writerDept: me.dept, text, createdAt: new Date().toISOString() });
    save(store);
    nCommentSaving = false;
    $('nCommentSave').disabled = false;
    $('nCommentText').value = '';
    updateCount('nCommentText', 'nCommentCountNow', COMMENT_MAX);
    renderNoticeComments(n);
    toast('댓글을 등록했습니다');
  }, 300);
});

async function deleteNoticeComment(cid) {
  const n = findNotice(currentId);
  const c = n && n.comments.find(x => x.id === cid);
  if (!c) return;
  if (!(await confirmDialog('commentDeleteDialog'))) return;
  if (!canDeleteNoticeComment(c) || readonly) { toast('삭제할 권한이 없습니다'); return; }
  n.comments = n.comments.filter(x => x.id !== cid);
  save(store);
  renderNoticeComments(n);
  toast('댓글을 삭제했습니다');
}

$('btnDelete').addEventListener('click', async () => {
  const n = findNotice(currentId);
  if (!n) return;
  const d = $('deleteDialog');
  d.querySelector('h2').textContent = '공지를 삭제할까요?';
  d.querySelector('p').textContent = (n.comments.length || n.files.length)
    ? `댓글 ${n.comments.length}개와 첨부파일 ${n.files.length}개도 함께 삭제되며 되돌릴 수 없습니다.` : '삭제한 공지는 되돌릴 수 없습니다.';
  if (!(await confirmDialog('deleteDialog'))) return;
  // 서버라면 여기서 한 번 더 확인한다: 권한, 읽기 전용
  if (!canEdit(me, n) || readonly) { toast('삭제할 권한이 없습니다'); return; }
  store.list = store.list.filter(x => x.id !== n.id);
  save(store);
  toast('공지를 삭제했습니다');
  location.hash = '#notice';
});

// ---------- 공통: 에디터 (공지 · 부서 게시판 · 익명 게시판이 같이 쓴다) ----------
// 브라우저의 글 꾸미기 기능(execCommand)을 쓴 간단한 에디터. 시안용이라 라이브러리 없이 만들었다.
// 실제 JSP에서는 팀이 고른 에디터 라이브러리(Bootstrap 없이 동작하는 것)로 바꿔도 된다.
// - 툴바는 지금 커서 자리의 상태(크기·색·굵게 등)를 늘 보여 준다
// - 글자 크기: 숫자를 넣고 Enter 또는 칸 밖을 누르면 적용. 10~40, 기본 15
const SIZE_MIN = 10;
const SIZE_MAX = 40;
const SIZE_BASE = 15;
const ZWSP = '​'; // 커서만 있을 때 크기를 바꾸면 그 자리에 넣어 두는 "보이지 않는 글자". 저장할 때 지운다
const SWATCH_RGB = new Map(['#1f1f1d', '#a32d2d', '#185fa5', '#2b6a2f', '#8a4b08'].map(c => {
  const h = c.slice(1);
  return [c, `rgb(${parseInt(h.slice(0, 2), 16)}, ${parseInt(h.slice(2, 4), 16)}, ${parseInt(h.slice(4, 6), 16)})`];
}));
const editors = [];

function makeEditor(areaId) {
  const area = $(areaId);
  const bar = area.closest('.editor').querySelector('.editor-toolbar');
  bar.append($('editorToolbarTpl').content.cloneNode(true));
  const sizeBox = bar.querySelector('.ed-size');
  const ed = { area, saved: null, pendingColor: null, shownSize: String(SIZE_BASE), deferSize: null };
  const inArea = node => !!node && area.contains(node);

  // 선택(커서) 위치를 "앞에서 몇 번째 글자"로 기억한다. 브라우저가 글자 덩어리를 새로 만들어도 위치를 잃지 않게
  const offsetOf = (node, offset) => {
    const r = document.createRange();
    r.selectNodeContents(area);
    r.setEnd(node, offset);
    return r.toString().length;
  };
  function pointAt(pos) {
    const walker = document.createTreeWalker(area, NodeFilter.SHOW_TEXT);
    let n;
    let last = null;
    while ((n = walker.nextNode())) {
      if (pos <= n.length) return [n, pos];
      pos -= n.length;
      last = n;
    }
    return last ? [last, last.length] : [area, area.childNodes.length];
  }

  // 툴바를 누르면 에디터의 선택(커서)이 풀릴 수 있어서, 에디터 안의 마지막 선택을 기억해 두고 되살린다
  function restoreRange() {
    // 커서가 이미 에디터 안에 있으면 건드리지 않는다 (다시 잡으면 "다음에 쓸 색·굵게"가 풀린다)
    if (document.activeElement === area && inArea(getSelection().anchorNode)) return;
    area.focus();
    if (!ed.saved) return;
    const r = document.createRange();
    r.setStart(...pointAt(ed.saved[0]));
    r.setEnd(...pointAt(ed.saved[1]));
    const sel = getSelection();
    sel.removeAllRanges();
    sel.addRange(r);
  }
  function runCmd(cmd, value) {
    restoreRange();
    document.execCommand('styleWithCSS', false, true);
    document.execCommand(cmd, false, value);
    area.dispatchEvent(new Event('input'));
    update();
  }
  bar.querySelectorAll('.ed-btn').forEach(b => {
    b.addEventListener('mousedown', e => e.preventDefault()); // 버튼을 눌러도 고르던 글자가 풀리지 않게
    b.addEventListener('click', () => runCmd(b.dataset.cmd));
  });
  bar.querySelectorAll('.ed-color').forEach(b => {
    b.addEventListener('mousedown', e => e.preventDefault());
    b.addEventListener('click', () => {
      runCmd('foreColor', b.dataset.color);
      ed.pendingColor = getSelection().isCollapsed ? b.dataset.color : null; // 글자를 안 고르고 색만 눌렀으면 "다음에 쓸 색"
      update();
    });
  });

  // 글자 크기 적용
  // - 글자를 고른 상태: 고른 글자만 그 크기로
  // - 커서만 있는 상태: 그 자리에 크기 상자를 만들고 커서를 넣는다 → 이어서 쓰는 글자가 그 크기로
  function applySize(px, atCurrentCaret) {
    if (!atCurrentCaret) restoreRange();
    const sel = getSelection();
    if (!sel.rangeCount || !inArea(sel.anchorNode)) return;
    if (sel.isCollapsed) {
      const span = document.createElement('span');
      span.style.fontSize = `${px}px`;
      span.textContent = ZWSP;
      // 넣을 자리를 "몇 번째 글자"로 다시 찾는다 (커서를 되살리는 사이 브라우저가 글자 덩어리를 바꿔 놓을 수 있어서)
      const pos = offsetOf(sel.anchorNode, sel.anchorOffset);
      const r = document.createRange();
      r.setStart(...pointAt(pos));
      r.collapse(true);
      r.insertNode(span);
      const caret = document.createRange();
      caret.setStart(span.firstChild, 1);
      caret.collapse(true);
      sel.removeAllRanges();
      sel.addRange(caret);
    } else {
      // 브라우저는 크기를 1~7 단계만 알아서, 7로 칠한 뒤 그 부분을 우리 크기(px)로 바꾼다
      document.execCommand('styleWithCSS', false, false);
      document.execCommand('fontSize', false, '7');
      area.querySelectorAll('font[size="7"]').forEach(f => {
        const span = document.createElement('span');
        span.style.fontSize = `${px}px`;
        span.append(...f.childNodes);
        f.replaceWith(span);
      });
    }
    area.dispatchEvent(new Event('input'));
    update();
  }
  // 칸에 넣은 숫자를 읽는다. 비었거나 숫자가 아니면 null, 범위를 넘으면 10~40으로 맞추고 알려 준다
  function readSize() {
    const v = parseInt(sizeBox.value, 10);
    if (Number.isNaN(v)) { sizeBox.value = ed.shownSize; return null; }
    const px = Math.min(SIZE_MAX, Math.max(SIZE_MIN, v));
    if (px !== v) toast(`글자 크기는 ${SIZE_MIN}~${SIZE_MAX}까지예요. ${px}로 맞췄어요`);
    sizeBox.value = String(px);
    return px;
  }
  sizeBox.addEventListener('input', () => { sizeBox.value = sizeBox.value.replace(/\D/g, ''); });
  sizeBox.addEventListener('keydown', e => {
    if (e.key !== 'Enter') return;
    e.preventDefault(); // 폼이 저장되지 않게
    const px = readSize();
    if (px === null) return;
    ed.shownSize = String(px); // 아래 blur에서 또 적용하지 않게
    applySize(px, false);
  });
  // 칸 밖을 누르면 적용. 에디터 글자 위를 눌러서 나왔으면 "누른 자리"에 적용한다
  sizeBox.addEventListener('blur', e => {
    if (sizeBox.value === ed.shownSize) return;
    const px = readSize();
    if (px === null) return;
    ed.shownSize = String(px);
    if (e.relatedTarget === area) ed.deferSize = px; // 클릭이 끝나 커서가 자리 잡은 뒤 적용
    else applySize(px, false);
  });
  area.addEventListener('click', () => {
    if (ed.deferSize === null) return;
    const px = ed.deferSize;
    ed.deferSize = null;
    applySize(px, true);
  });

  // 툴바에 지금 상태 보여 주기
  function update() {
    const sel = getSelection();
    if (!sel.rangeCount || !inArea(sel.anchorNode)) return;
    if (ed.deferSize !== null) return; // 방금 넣은 크기를 곧 적용할 참이면 칸을 그대로 둔다
    const node = sel.anchorNode.nodeType === Node.ELEMENT_NODE ? sel.anchorNode : sel.anchorNode.parentElement;
    const cs = getComputedStyle(node);
    ed.shownSize = String(Math.round(parseFloat(cs.fontSize)));
    if (document.activeElement !== sizeBox) sizeBox.value = ed.shownSize;
    const color = ed.pendingColor ? SWATCH_RGB.get(ed.pendingColor) : cs.color;
    bar.querySelectorAll('.ed-color').forEach(b => b.setAttribute('aria-pressed', String(SWATCH_RGB.get(b.dataset.color) === color)));
    bar.querySelectorAll('.ed-btn[aria-pressed]').forEach(b => {
      let on = false;
      try { on = document.queryCommandState(b.dataset.cmd); } catch (err) { /* 지원 안 하면 꺼진 것으로 */ }
      b.setAttribute('aria-pressed', String(on));
    });
  }
  ed.onSelection = () => {
    const sel = getSelection();
    if (!sel.rangeCount || !inArea(sel.anchorNode)) return;
    const r = sel.getRangeAt(0);
    ed.saved = [offsetOf(r.startContainer, r.startOffset), offsetOf(r.endContainer, r.endOffset)];
    update();
  };

  // 커서를 옮기면(클릭·방향키) "다음에 쓸 색"은 잊는다. 글자를 치면 그 색이 실제로 들어가 있다
  area.addEventListener('mousedown', () => { ed.pendingColor = null; });
  area.addEventListener('keydown', e => { if (e.key.startsWith('Arrow') || e.key === 'Home' || e.key === 'End') ed.pendingColor = null; });
  area.addEventListener('input', () => { ed.pendingColor = null; });
  // 붙여넣기는 글자만 받는다 (다른 곳의 서식·코드가 딸려 들어오지 않게)
  area.addEventListener('paste', e => {
    e.preventDefault();
    document.execCommand('insertText', false, (e.clipboardData || window.clipboardData).getData('text/plain'));
  });
  // 끌어다 놓기로 HTML·파일이 들어오는 것도 막는다 (첨부는 "파일 선택"으로)
  area.addEventListener('drop', e => e.preventDefault());

  // 바깥에서 쓰는 것
  ed.html = () => sanitizeHtml(area.innerHTML.replace(/​/g, '')); // 저장할 서식 글 (걸러진 것)
  ed.text = () => textOf(ed.html());                                    // 서식을 뺀 글자 (글자 수·빈칸 검사)
  ed.set = html => {
    area.innerHTML = html ? sanitizeHtml(html) : '';
    // 툴바를 처음 상태로 (15 · 검정 · 아무것도 안 눌림)
    ed.saved = null; ed.pendingColor = null; ed.deferSize = null;
    ed.shownSize = String(SIZE_BASE);
    sizeBox.value = ed.shownSize;
    bar.querySelectorAll('[aria-pressed]').forEach(b => b.setAttribute('aria-pressed', 'false'));
    bar.querySelector('.ed-color[data-color="#1f1f1d"]').setAttribute('aria-pressed', 'true');
  };
  editors.push(ed);
  return ed;
}
document.addEventListener('selectionchange', () => editors.forEach(ed => ed.onSelection()));

// ---------- 공통: 첨부파일 (공지 · 부서 게시판) ----------
// 글 하나에 5개까지. 실제는 파일당 10MB(서버 저장). 시안은 브라우저에 저장해서 1MB까지만
const FILE_MAX = 5;
const FILE_SIZE_MAX = 1024 * 1024;        // 시안
const FILE_SIZE_REAL = 10 * 1024 * 1024;  // 실제 프로젝트 기준 (안내 문구에만 쓴다)
const FILE_EXT = /\.(jpe?g|png|gif|webp|pdf|hwpx?|docx?|xlsx?|pptx?|txt|zip)$/i;
const fileSig = files => files.map(f => f.id).join(',');

// 글쓰기 화면의 "파일 선택" 묶음. ids: { pick, input, info, list, error }
function makeFilePicker(ids) {
  const fp = { files: [] };
  fp.render = () => {
    $(ids.info).textContent = fp.files.length ? `${fp.files.length} / ${FILE_MAX}개` : '선택한 파일 없음';
    $(ids.pick).disabled = fp.files.length >= FILE_MAX;
    $(ids.list).replaceChildren(...fp.files.map(f => {
      const li = el('li');
      if (isImage(f)) li.append(el('img', { src: f.data, alt: '', class: 'attach-mini' }));
      li.append(el('span', { class: 'attach-name' }, f.name), el('small', {}, fmtSize(f.size)));
      const del = el('button', { type: 'button', class: 'link-btn' }, '빼기');
      del.setAttribute('aria-label', `${f.name} 첨부 빼기`);
      del.addEventListener('click', () => {
        fp.files = fp.files.filter(x => x.id !== f.id);
        $(ids.error).textContent = '';
        fp.render();
      });
      li.append(del);
      return li;
    }));
  };
  fp.set = files => { fp.files = (files || []).map(f => ({ ...f })); $(ids.error).textContent = ''; fp.render(); };
  fp.error = msg => { $(ids.error).textContent = msg; };

  $(ids.pick).addEventListener('click', () => $(ids.input).click());
  $(ids.input).addEventListener('change', async e => {
    const picked = [...e.target.files];
    e.target.value = ''; // 같은 파일을 다시 골라도 change가 오게
    $(ids.error).textContent = '';
    const errors = [];
    for (const file of picked) {
      if (fp.files.length >= FILE_MAX) { errors.push(`첨부파일은 ${FILE_MAX}개까지입니다`); break; }
      if (!FILE_EXT.test(file.name)) { errors.push(`${file.name}: 이미지·문서 파일만 올릴 수 있습니다`); continue; }
      if (file.size > FILE_SIZE_MAX) {
        errors.push(`${file.name}: 시안은 1MB까지만 저장할 수 있어요 (실제는 ${fmtSize(FILE_SIZE_REAL)}까지)`);
        continue;
      }
      const data = await new Promise((resolve, reject) => {
        const r = new FileReader();
        r.onload = () => resolve(r.result);
        r.onerror = reject;
        r.readAsDataURL(file);
      }).catch(() => null);
      if (!data) { errors.push(`${file.name}: 파일을 읽지 못했습니다`); continue; }
      fp.files.push({ id: `f${Date.now()}${Math.random().toString(36).slice(2, 6)}`, name: file.name, size: file.size, type: file.type, data });
    }
    if (errors.length) $(ids.error).textContent = [...new Set(errors)].join(' / ');
    fp.render();
  });
  return fp;
}

// ---------- 3. 작성 · 수정 ----------
const noticeEditor = makeEditor('fContent');
const noticeFiles = makeFilePicker({ pick: 'btnPickFiles', input: 'fFiles', info: 'fFilesInfo', list: 'fFileList', error: 'fFilesError' });
const editorHtml = () => noticeEditor.html();

const formState = { id: null, initial: { title: '', content: '', pinned: false, scope: '', files: '' }, saving: false, leaving: false };
const formOpen = () => $('view-notice-form').classList.contains('on');
const formDirty = () => formOpen() && !formState.leaving && !formState.saving &&
  ($('fTitle').value !== formState.initial.title || editorHtml() !== formState.initial.content ||
   $('fPinned').checked !== formState.initial.pinned || $('fScope').value !== formState.initial.scope ||
   fileSig(noticeFiles.files) !== formState.initial.files);

function openForm(id) {
  // 주소를 직접 쳐서 들어와도 막는다 (실제로는 서버가 403으로 막고, 화면은 안내만 한다)
  if (readonly) { toast(READONLY_MSG()); location.replace('#notice'); return; }
  const n = id ? findNotice(id) : null;
  if (id && (!n || !canSeeNotice(me, n))) { toast(NOTICE_NOT_FOUND); location.replace('#notice'); return; }
  if (n ? !canEdit(me, n) : !canWrite(me)) { toast(n ? '이 공지를 수정할 권한이 없습니다' : '공지를 쓸 권한이 없습니다'); location.replace(n ? `#notice/view/${n.id}` : '#notice'); return; }

  // 공지 범위: 관리자·인사 담당은 전사 + 모든 부서, 부서장은 자기 부서 하나로 잠근다
  const scopes = scopesFor(me);
  if (n && !scopes.includes(n.scope)) scopes.push(n.scope); // 지금은 없는 부서의 옛 공지를 고칠 때
  $('fScope').replaceChildren(...scopes.map(s => el('option', { value: s }, s === ALL ? '전사 공지 (전 직원)' : `${s} 공지 (${s}만)`)));
  $('fScope').disabled = !isManager(me);
  $('fScopeHint').textContent = isManager(me)
    ? '전사 공지는 전 직원이, 부서 공지는 그 부서 사람만 봅니다.'
    : `부서장은 ${me.dept} 공지만 쓸 수 있어요. ${me.dept} 사람만 봅니다.`;

  formState.id = n ? n.id : null;
  formState.saving = false;
  $('formTitle').textContent = n ? '공지 수정' : '공지 쓰기';
  $('formCrumb').textContent = n ? '수정' : '글쓰기';
  $('fScope').value = n ? n.scope : scopes[0];
  $('fTitle').value = n ? n.title : '';
  noticeEditor.set(n ? n.content : '');
  noticeFiles.set(n ? n.files : []);
  $('fPinned').checked = !!(n && n.pinned);
  formState.initial = { title: $('fTitle').value, content: editorHtml(), pinned: $('fPinned').checked,
    scope: $('fScope').value, files: fileSig(noticeFiles.files) };
  $('btnSave').disabled = false;
  $('btnSave').textContent = '저장';
  clearErrors();
  updateCounters();
  showSub('view-notice-form');
  $('fTitle').focus();
}

function updateCounters() {
  const set = (counter, len, max) => {
    $(counter).textContent = `${len} / ${max}`;
    $(counter).classList.toggle('is-full', len >= max);
  };
  set('fTitleCount', $('fTitle').value.length, TITLE_MAX);
  set('fContentCount', noticeEditor.text().length, CONTENT_MAX); // 서식을 뺀 글자 수
}
$('fTitle').addEventListener('input', () => { updateCounters(); setError('fTitle', ''); });
$('fContent').addEventListener('input', () => { updateCounters(); setError('fContent', ''); });

function setError(field, msg) {
  $(`${field}Error`).textContent = msg;
  $(field).classList.toggle('is-error', !!msg);
  $(field).setAttribute('aria-invalid', msg ? 'true' : 'false');
}
function clearErrors() {
  setError('fTitle', ''); setError('fContent', '');
  $('fPinnedError').textContent = ''; noticeFiles.error('');
}
$('fPinned').addEventListener('change', () => { $('fPinnedError').textContent = ''; });
$('fScope').addEventListener('change', () => { $('fPinnedError').textContent = ''; });

// 같은 범위 안에서 이미 고정된 공지가 5개면 더 고정할 수 없다 (지금 고치는 공지는 빼고 센다)
function pinFull(exceptId, scope) {
  return store.list.filter(n => n.pinned && n.scope === scope && n.id !== exceptId).length >= PIN_MAX;
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
  const content = editorHtml();
  // 서식을 뺀 글자로 비었는지·글자 수를 본다
  const err = validate(title, textOf(content));
  if (err) { setError(err.field, err.message); $(err.field).focus(); return; }
  const scope = $('fScope').value;
  // 서버라면 여기서 다시 확인한다: 고른 범위가 이 사람이 쓸 수 있는 범위인지 (부서장이 다른 부서를 고르지 못하게)
  if (!scopesFor(me).includes(scope) && !(formState.id && findNotice(formState.id).scope === scope)) {
    toast('이 범위에는 공지를 쓸 수 없습니다'); return;
  }
  const pinned = $('fPinned').checked;
  if (pinned && pinFull(formState.id, scope)) {
    $('fPinnedError').textContent = `${scope === ALL ? '전사 공지' : `${scope} 공지`}의 맨 위 고정은 ${PIN_MAX}개까지입니다. 다른 공지의 고정을 먼저 풀어 주세요.`;
    return;
  }
  const files = noticeFiles.files.map(f => ({ ...f }));

  formState.saving = true;
  $('btnSave').disabled = true;
  $('btnSave').textContent = '저장 중…';

  // 서버에 보내는 것처럼 잠깐 기다린다
  setTimeout(() => {
    const now = new Date().toISOString();
    let id = formState.id;
    const before = JSON.stringify(store);
    if (id) {
      const n = findNotice(id);
      if (!n || !canEdit(me, n) || readonly) { formState.saving = false; toast('수정할 권한이 없습니다'); location.hash = '#notice'; return; }
      // 제목·내용·범위·첨부를 고쳤을 때만 수정일을 바꾼다 (고정만 바꾼 경우는 그대로)
      const changed = n.title !== title || n.content !== content || n.scope !== scope || fileSig(n.files) !== fileSig(files);
      Object.assign(n, { scope, title, content, files, pinned, updatedAt: changed ? now : n.updatedAt });
    } else {
      if (!canWrite(me) || readonly) { formState.saving = false; toast('공지를 쓸 권한이 없습니다'); location.hash = '#notice'; return; }
      id = store.nextId++;
      store.list.push({ id, scope, title, content, files, comments: [], pinned, writerEmpNo: me.empNo, writerName: me.name, writerDept: me.dept, createdAt: now, updatedAt: null });
      listState.page = 1; listState.q = ''; listState.tab = 'all';
    }
    // 시안 한계: 브라우저 저장 공간(약 5MB)이 차면 저장하지 못한다. 그때는 되돌리고 알려 준다
    try {
      localStorage.setItem(storeKey(), JSON.stringify(store));
    } catch (err) {
      store = JSON.parse(before);
      formState.saving = false;
      $('btnSave').disabled = false;
      $('btnSave').textContent = '저장';
      noticeFiles.error('브라우저 저장 공간이 부족해 저장하지 못했어요 (시안 한계). 첨부파일을 줄여 주세요.');
      return;
    }
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

const BOARD_KEY = 'ieum-board-demo-v3'; // v3: 에디터(서식 글)·첨부파일 (10-29)
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
        id: i + 1, dept: w.dept, title, content: textToHtml(content), files: [],
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
// 저장하면 true. 브라우저 저장 공간이 차면 false (시안 한계: 첨부파일이 크면 생긴다)
function saveBoard(s) { try { localStorage.setItem(boardKey(), JSON.stringify(s)); return true; } catch (e) { return false; } }

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
    .filter(p => !words.length || words.every(w => `${p.title}\n${textOf(p.content)}`.toLowerCase().includes(w)))
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
      if ((p.files || []).length) wrap.append(el('span', { class: 'file-tag' }, '첨부'));
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
  $('bDetailBody').innerHTML = sanitizeHtml(p.content); // 서식 글. 허용한 태그만 남기고 넣는다
  renderFilesBox(p.files || [], { box: 'bDetailFiles', count: 'bDetailFileCount', images: 'bDetailImages', list: 'bDetailFileList' });
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
  const extra = [p.comments.length && `댓글 ${p.comments.length}개`, (p.files || []).length && `첨부파일 ${p.files.length}개`].filter(Boolean);
  d.querySelector('p').textContent = extra.length
    ? `${extra.join('와 ')}도 함께 삭제되며 되돌릴 수 없습니다.` : '삭제한 글은 되돌릴 수 없습니다.';
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
// 내용은 공지와 같은 에디터, 첨부파일도 공지와 같은 규칙
const boardEditor = makeEditor('bContent');
const boardFiles = makeFilePicker({ pick: 'bBtnPickFiles', input: 'bFiles', info: 'bFilesInfo', list: 'bFileList', error: 'bFilesError' });
const boardFormState = { id: null, initial: { title: '', content: '', files: '' }, saving: false, leaving: false };
const boardFormOpen = () => $('view-board-form').classList.contains('on');
const boardFormDirty = () => boardFormOpen() && !boardFormState.leaving && !boardFormState.saving &&
  ($('bTitle').value !== boardFormState.initial.title || boardEditor.html() !== boardFormState.initial.content ||
   fileSig(boardFiles.files) !== boardFormState.initial.files);

function updateCount(input, counter, max) {
  // 에디터(서식 글)는 서식을 뺀 글자 수를 센다
  const len = $(input).isContentEditable ? textOf($(input).innerHTML.replace(/\u200B/g, '')).length : $(input).value.length;
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
  boardFormState.saving = false;
  $('bFormTitle').textContent = p ? '글 수정' : '글쓰기';
  $('bFormCrumb').textContent = p ? '수정' : '글쓰기';
  $('bFormSub').textContent = `${me.dept} 게시판에 올라갑니다. ${me.dept} 사람만 볼 수 있어요.`;
  $('bTitle').value = p ? p.title : '';
  boardEditor.set(p ? p.content : '');
  boardFiles.set(p ? p.files : []);
  boardFormState.initial = { title: $('bTitle').value, content: boardEditor.html(), files: fileSig(boardFiles.files) };
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
  boardFiles.error('');
  const title = $('bTitle').value.trim();
  const content = boardEditor.html();
  const files = boardFiles.files.map(f => ({ ...f }));
  const err = validate(title, textOf(content)); // 공지와 같은 규칙 (서식을 뺀 글자로 검사)
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
      Object.assign(p, { title, content, files, updatedAt: now });
    } else {
      if (readonly) { boardFormState.saving = false; toast(READONLY_MSG()); location.hash = '#board-dept'; return; }
      id = board.nextId++;
      // 부서는 화면에서 받지 않고 로그인한 사람의 부서로 정한다 (실제로도 서버가 정함)
      board.posts.push({ id, dept: me.dept, title, content, files, writerEmpNo: me.empNo, writerName: me.name, createdAt: now, updatedAt: null, comments: [] });
      boardState.page = 1; boardState.q = '';
    }
    if (!saveBoard(board)) {
      board = loadBoard(); // 저장 못 한 변경은 버리고 저장된 것으로 되돌린다
      boardFormState.saving = false;
      $('bBtnSave').disabled = false;
      $('bBtnSave').textContent = '저장';
      boardFiles.error('브라우저 저장 공간이 부족해 저장하지 못했어요 (시안 한계). 첨부파일을 줄여 주세요.');
      return;
    }
    toast(boardFormState.id ? '글을 수정했습니다' : '글을 등록했습니다');
    location.hash = `#board-dept/view/${id}`;
  }, 500);
});

$('bBtnCancel').addEventListener('click', async () => {
  if (boardFormDirty() && !(await confirmDialog('leaveDialog'))) return;
  boardFormState.leaving = true;
  location.hash = boardFormState.id ? `#board-dept/view/${boardFormState.id}` : '#board-dept';
});

// ======================================================================
// 익명 게시판 (목록 · 상세+익명 댓글 · 작성/수정) - 10-29 팀 요청
// - 회사 전체 하나. 직원 누구나 보고 쓴다 (읽기 전용 업체는 쓰기 모두 막음)
// - 화면에서만 익명: 글쓴이 사번은 저장하지만(본인 수정·삭제, 신고 1번 확인용) 누구에게도 보여 주지 않는다.
//   업체 관리자·인사 담당도 작성자를 볼 수 없다. 서버도 응답에 글쓴이 정보를 넣지 않고 "내 글인지"만 보낸다
// - 댓글은 글마다 "익명1, 익명2"로 구분하고, 글쓴이가 단 댓글은 "글쓴이"
// - 신고: 본인 글이 아니면 한 사람이 한 번. 업체 관리자·인사 담당이 숨기기·숨김 풀기
// - 에디터는 있고 첨부파일은 없음 (사진 위치·문서 작성자·파일 이름 같은 파일 속 정보로 작성자가 드러날 수 있어서)

const ANON_KEY = 'ieum-anon-demo-v2'; // v2: 에디터(서식 글)
const anonKey = () => `${ANON_KEY}:${session.company}`;

function anonSeed() {
  const admin = (findCompanyOrder(session.company) || {}).adminNo || '20260001';
  const E = { a: admin, kim: '20260002', lee: '20260003', choi: '20260004', jung: '20260005', han: '20260006', yoon: '20260007' };
  // [글쓴이, 며칠 전, 제목, 내용, [[댓글쓴이, 내용], ...], 신고한 사람들, 숨김]
  const rows = [
    ['choi', 12, '사내 카페 메뉴 바뀌었나요?', '요즘 아메리카노 맛이 달라진 것 같아요. 원두 바뀐 걸까요?', [['han', '저도 그렇게 느꼈어요.'], ['choi', '역시 저만 그런 게 아니었네요.'], ['kim', '공급 업체가 바뀌었다고 들었어요.']], [], false],
    ['han', 9, '야근 줄이는 팁 공유해요', '저는 오전에 제일 어려운 일을 먼저 합니다.\n여러분은 어떻게 하세요?', [['yoon', '회의를 오후로 몰아요.'], ['lee', '알림을 꺼 두는 시간을 정해 둡니다.']], [], false],
    ['kim', 7, '주차장 자리가 너무 부족해요', '아침 8시 반만 넘어도 자리가 없어요. 건의해 볼까요?', [['jung', '건의하면 좋겠어요.']], [], false],
    ['jung', 5, '휴게실 안마의자 고장', '2층 휴게실 안마의자가 며칠째 안 돼요.', [], [], false],
    ['yoon', 3, '이 글은 신고 예시예요', '다른 사람을 비방하는 내용이라고 가정한 예시 글입니다.', [['choi', '이런 글은 좀…']], [E.choi, E.han], true],
    ['lee', 1, '연말 행사 아이디어 있나요?', '올해 연말 행사로 해 보고 싶은 것 있으면 남겨 주세요.', [['han', '보드게임 대회요!'], ['lee', '좋네요 ㅎㅎ'], ['choi', '장기자랑은 빼 주세요…']], [], false],
  ];
  let cid = 1;
  return {
    nextId: rows.length + 1,
    posts: rows.map(([who, d, title, content, comments, reports, hidden], i) => ({
      id: i + 1, title, content: textToHtml(content), writerEmpNo: E[who],
      createdAt: daysAgo(d, 10 + (i % 6), (i * 7) % 60), updatedAt: null,
      reports, hidden,
      comments: comments.map(([cw, text], k) => ({
        id: cid++, writerEmpNo: E[cw], text, createdAt: daysAgo(d, 17 + k, (k * 13) % 60), // 글 쓴 날 저녁
      })),
    })),
    nextCid: cid,
  };
}
function loadAnon() {
  try {
    const s = JSON.parse(localStorage.getItem(anonKey()));
    if (s && Array.isArray(s.posts)) return s;
  } catch (e) { /* 깨졌으면 처음 데이터로 */ }
  const s = anonSeed();
  saveAnon(s);
  return s;
}
function saveAnon(s) { try { localStorage.setItem(anonKey(), JSON.stringify(s)); } catch (e) { /* 무시 */ } }

let anon = null;
let anonOf = '';
function ensureAnon() {
  if (anonOf === session.company) return;
  anon = loadAnon();
  anonOf = session.company;
  anonState.page = 1; anonState.q = '';
}

// ---------- 권한 ----------
const findAnon = id => anon.posts.find(p => p.id === Number(id));
const isAnonOwner = p => p.writerEmpNo === me.empNo;
// 숨긴 글은 업체 관리자·인사 담당만 본다. 다른 사람에게는 없는 글과 같은 말로 안내한다
const canSeeAnon = p => !p.hidden || isManager(me);
const ANON_NOT_FOUND = '없는 글이거나 볼 수 없는 글입니다';

function routeAnon(action, id) {
  if (action === 'view') return openAnonDetail(id);
  if (action === 'write' || action === 'edit') return openAnonForm(action === 'edit' ? id : null);
  renderAnonList();
  showSub('view-anon-list');
}

// ---------- 1. 목록 ----------
const anonState = { page: 1, q: '' };

function anonRows() {
  const words = anonState.q.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return anon.posts
    .filter(canSeeAnon)
    .filter(p => !words.length || words.every(w => `${p.title}\n${textOf(p.content)}`.toLowerCase().includes(w)))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
}

function renderAnonList() {
  if (document.activeElement !== $('aSearchInput')) $('aSearchInput').value = anonState.q;
  const rows = anonRows();
  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  anonState.page = Math.min(Math.max(1, anonState.page), pages);
  const start = (anonState.page - 1) * PAGE_SIZE;
  const pageRows = rows.slice(start, start + PAGE_SIZE);
  $('aResultInfo').textContent = anonState.q ? `"${anonState.q}" 검색 결과 ${rows.length}건` : `전체 ${rows.length}건`;

  if (!pageRows.length) {
    const tr = el('tr', { class: 'row-empty' });
    tr.append(el('td', { colspan: '4' }, anonState.q ? '검색 결과가 없습니다.' : '아직 글이 없습니다. 첫 글을 써 보세요.'));
    $('aRows').replaceChildren(tr);
  } else {
    $('aRows').replaceChildren(...pageRows.map((p, i) => {
      const tr = el('tr');
      if (p.hidden) tr.classList.add('is-hidden-row');
      const titleTd = el('td', { 'data-label': '제목' });
      const wrap = el('span');
      if (p.hidden) wrap.append(el('span', { class: 'anon-tag is-hidden' }, '숨김'));
      wrap.append(el('a', { href: `#board-anon/view/${p.id}`, class: 'notice-link' }, p.title));
      if (p.comments.length) wrap.append(el('span', { class: 'comment-badge', 'aria-label': `댓글 ${p.comments.length}개` }, `[${p.comments.length}]`));
      if (p.updatedAt) wrap.append(el('span', { class: 'edited-tag' }, '수정됨'));
      // 신고 수는 업체 관리자·인사 담당에게만 보인다
      if (isManager(me) && p.reports.length) wrap.append(el('span', { class: 'anon-tag is-report' }, `신고 ${p.reports.length}`));
      titleTd.append(wrap);
      tr.append(
        el('td', { 'data-label': '번호', class: 'num' }, String(rows.length - start - i)),
        titleTd,
        el('td', { 'data-label': '작성자' }, isAnonOwner(p) ? '익명 (내 글)' : '익명'),
        el('td', { 'data-label': '작성일', class: 'num' }, fmtDate(p.createdAt)),
      );
      return tr;
    }));
  }
  renderPager('aPager', anonState, pages, renderAnonList);
}

let aSearchTimer;
function runAnonSearch() {
  const q = $('aSearchInput').value.trim();
  if (q === anonState.q) return;
  anonState.q = q;
  anonState.page = 1;
  renderAnonList();
}
$('aSearchInput').addEventListener('input', () => { clearTimeout(aSearchTimer); aSearchTimer = setTimeout(runAnonSearch, SEARCH_DELAY); });
$('aSearchForm').addEventListener('submit', e => { e.preventDefault(); clearTimeout(aSearchTimer); runAnonSearch(); });
document.querySelector('.nav-item[data-view="board-anon"]').addEventListener('click', () => {
  anonState.q = ''; anonState.page = 1;
  if (location.hash === '#board-anon') { renderAnonList(); closeMenu(); }
});

// ---------- 2. 상세 + 익명 댓글 ----------
let aCurrentId = null;

function openAnonDetail(id) {
  const p = findAnon(id);
  if (!p || !canSeeAnon(p)) { toast(ANON_NOT_FOUND); location.replace('#board-anon'); return; }
  aCurrentId = p.id;
  $('aHiddenNote').hidden = !p.hidden;
  $('aDetailTitle').textContent = p.title;
  const parts = [el('strong', {}, isAnonOwner(p) ? '익명 (내 글)' : '익명'), el('span', {}, `${fmtTime(p.createdAt)} 작성`)];
  if (p.updatedAt) parts.push(el('span', {}, `${fmtTime(p.updatedAt)} 수정`));
  if (isManager(me) && p.reports.length) parts.push(el('span', { class: 'anon-tag is-report' }, `신고 ${p.reports.length}`));
  $('aDetailMeta').replaceChildren(...parts);
  $('aDetailBody').innerHTML = sanitizeHtml(p.content);
  renderAnonActions(p);
  $('aBtnEdit').setAttribute('href', `#board-anon/edit/${p.id}`);
  $('aCommentText').value = '';
  setFieldError('aCommentText', 'aCommentError', '');
  updateCount('aCommentText', 'aCommentCountNow', COMMENT_MAX);
  renderAnonComments(p);
  showSub('view-anon-detail');
}

function renderAnonActions(p) {
  const own = isAnonOwner(p);
  $('aOwnerActions').hidden = !own;
  // 신고: 내 글이 아니면 보인다. 이미 신고했으면 "신고함"으로 잠근다
  const reported = p.reports.includes(me.empNo);
  $('aBtnReport').hidden = own;
  $('aBtnReport').disabled = reported;
  $('aBtnReport').textContent = reported ? '신고함' : '신고';
  $('aBtnHide').hidden = !isManager(me);
  $('aBtnHide').textContent = p.hidden ? '숨김 풀기' : '숨기기';
}

// 댓글 이름: 글쓴이는 "글쓴이", 나머지는 처음 댓글을 단 순서대로 익명1, 익명2 …
function anonNames(p) {
  const names = new Map([[p.writerEmpNo, '글쓴이']]);
  let n = 0;
  p.comments.forEach(c => { if (!names.has(c.writerEmpNo)) names.set(c.writerEmpNo, `익명${++n}`); });
  return names;
}

function renderAnonComments(p) {
  const names = anonNames(p);
  $('aCommentCount').textContent = String(p.comments.length);
  $('aCommentEmpty').hidden = p.comments.length > 0;
  $('aComments').replaceChildren(...p.comments.map(c => {
    const li = el('li');
    const head = el('div', { class: 'comment-head' });
    const name = names.get(c.writerEmpNo);
    const who = el('strong', { class: name === '글쓴이' ? 'anon-writer' : '' }, name);
    head.append(who);
    if (c.writerEmpNo === me.empNo) head.append(el('span', { class: 'anon-tag' }, '나'));
    head.append(el('small', {}, fmtTime(c.createdAt)));
    // 삭제: 본인 댓글, 또는 업체 관리자·인사 담당
    if (c.writerEmpNo === me.empNo || isManager(me)) {
      const del = el('button', { type: 'button', class: 'link-btn', 'data-write': '' }, '삭제');
      del.addEventListener('click', () => deleteAnonComment(c.id));
      head.append(del);
    }
    li.append(head, el('p', { class: 'comment-text' }, c.text));
    return li;
  }));
}

let anonCommentSaving = false;
$('aCommentText').addEventListener('input', () => {
  updateCount('aCommentText', 'aCommentCountNow', COMMENT_MAX);
  setFieldError('aCommentText', 'aCommentError', '');
});
$('aCommentForm').addEventListener('submit', e => {
  e.preventDefault();
  if (anonCommentSaving) return;
  const p = findAnon(aCurrentId);
  if (!p || !canSeeAnon(p)) { toast(ANON_NOT_FOUND); location.hash = '#board-anon'; return; }
  if (readonly) { toast(READONLY_MSG()); return; }
  const text = $('aCommentText').value.trim();
  if (!text) { setFieldError('aCommentText', 'aCommentError', '댓글을 입력해 주세요'); $('aCommentText').focus(); return; }
  if (text.length > COMMENT_MAX) { setFieldError('aCommentText', 'aCommentError', `댓글은 ${COMMENT_MAX}자까지 쓸 수 있습니다`); return; }
  anonCommentSaving = true;
  $('aCommentSave').disabled = true;
  setTimeout(() => {
    p.comments.push({ id: anon.nextCid++, writerEmpNo: me.empNo, text, createdAt: new Date().toISOString() });
    saveAnon(anon);
    anonCommentSaving = false;
    $('aCommentSave').disabled = false;
    $('aCommentText').value = '';
    updateCount('aCommentText', 'aCommentCountNow', COMMENT_MAX);
    renderAnonComments(p);
    toast('익명 댓글을 등록했습니다');
  }, 300);
});

async function deleteAnonComment(cid) {
  const p = findAnon(aCurrentId);
  const c = p && p.comments.find(x => x.id === cid);
  if (!c) return;
  if (!(await confirmDialog('commentDeleteDialog'))) return;
  if ((c.writerEmpNo !== me.empNo && !isManager(me)) || readonly) { toast('삭제할 권한이 없습니다'); return; }
  // 지워도 다른 댓글의 익명 번호가 바뀌지 않게, 실제 서버에서는 번호를 댓글에 저장해 두는 것을 권한다
  p.comments = p.comments.filter(x => x.id !== cid);
  saveAnon(anon);
  renderAnonComments(p);
  toast('댓글을 삭제했습니다');
}

$('aBtnReport').addEventListener('click', async () => {
  const p = findAnon(aCurrentId);
  if (!p || isAnonOwner(p) || p.reports.includes(me.empNo)) return;
  if (!(await confirmDialog('reportDialog'))) return;
  if (readonly) { toast(READONLY_MSG()); return; }
  p.reports.push(me.empNo); // 서버에서는 (글 번호, 사번)을 한 번만 저장할 수 있게 막는다
  saveAnon(anon);
  renderAnonActions(p);
  toast('신고했습니다. 관리자가 확인할 거예요');
});

$('aBtnHide').addEventListener('click', () => {
  const p = findAnon(aCurrentId);
  if (!p || !isManager(me) || readonly) { toast('권한이 없습니다'); return; }
  p.hidden = !p.hidden;
  saveAnon(anon);
  toast(p.hidden ? '글을 숨겼습니다. 직원들에게는 보이지 않아요' : '숨김을 풀었습니다');
  openAnonDetail(p.id);
});

$('aBtnDelete').addEventListener('click', async () => {
  const p = findAnon(aCurrentId);
  if (!p) return;
  const d = $('deleteDialog');
  d.querySelector('h2').textContent = '익명 글을 삭제할까요?';
  d.querySelector('p').textContent = p.comments.length
    ? `댓글 ${p.comments.length}개도 함께 삭제되며 되돌릴 수 없습니다.` : '삭제한 글은 되돌릴 수 없습니다.';
  const ok = await confirmDialog('deleteDialog');
  if (!ok) return;
  if (!isAnonOwner(p) || readonly) { toast('삭제할 권한이 없습니다'); return; }
  anon.posts = anon.posts.filter(x => x.id !== p.id);
  saveAnon(anon);
  toast('글을 삭제했습니다');
  location.hash = '#board-anon';
});

// ---------- 3. 작성 · 수정 ----------
const anonEditor = makeEditor('aContent'); // 에디터는 있고 첨부파일은 없다
const anonFormState = { id: null, initial: { title: '', content: '' }, saving: false, leaving: false };
const anonFormDirty = () => $('view-anon-form').classList.contains('on') && !anonFormState.leaving && !anonFormState.saving &&
  ($('aTitle').value !== anonFormState.initial.title || anonEditor.html() !== anonFormState.initial.content);

function openAnonForm(id) {
  if (readonly) { toast(READONLY_MSG()); location.replace('#board-anon'); return; }
  const p = id ? findAnon(id) : null;
  if (id && (!p || !canSeeAnon(p))) { toast(ANON_NOT_FOUND); location.replace('#board-anon'); return; }
  if (p && !isAnonOwner(p)) { toast('이 글을 수정할 권한이 없습니다'); location.replace(`#board-anon/view/${p.id}`); return; }
  anonFormState.id = p ? p.id : null;
  anonFormState.saving = false;
  $('aFormTitle').textContent = p ? '익명 글 수정' : '익명 글쓰기';
  $('aFormCrumb').textContent = p ? '수정' : '글쓰기';
  $('aTitle').value = p ? p.title : '';
  anonEditor.set(p ? p.content : '');
  anonFormState.initial = { title: $('aTitle').value, content: anonEditor.html() };
  $('aBtnSave').disabled = false;
  $('aBtnSave').textContent = '익명으로 저장';
  setFieldError('aTitle', 'aTitleError', '');
  setFieldError('aContent', 'aContentError', '');
  updateCount('aTitle', 'aTitleCount', TITLE_MAX);
  updateCount('aContent', 'aContentCount', CONTENT_MAX);
  showSub('view-anon-form');
  $('aTitle').focus();
}
$('aTitle').addEventListener('input', () => { updateCount('aTitle', 'aTitleCount', TITLE_MAX); setFieldError('aTitle', 'aTitleError', ''); });
$('aContent').addEventListener('input', () => { updateCount('aContent', 'aContentCount', CONTENT_MAX); setFieldError('aContent', 'aContentError', ''); });

$('aForm').addEventListener('submit', e => {
  e.preventDefault();
  if (anonFormState.saving) return;
  setFieldError('aTitle', 'aTitleError', '');
  setFieldError('aContent', 'aContentError', '');
  const title = $('aTitle').value.trim();
  const content = anonEditor.html();
  const err = validate(title, textOf(content));
  if (err) {
    const map = { fTitle: ['aTitle', 'aTitleError'], fContent: ['aContent', 'aContentError'] }[err.field];
    setFieldError(map[0], map[1], err.message);
    $(map[0]).focus();
    return;
  }
  anonFormState.saving = true;
  $('aBtnSave').disabled = true;
  $('aBtnSave').textContent = '저장 중…';
  setTimeout(() => {
    const now = new Date().toISOString();
    let id = anonFormState.id;
    if (id) {
      const p = findAnon(id);
      if (!p || !isAnonOwner(p) || readonly) { anonFormState.saving = false; toast('수정할 권한이 없습니다'); location.hash = '#board-anon'; return; }
      Object.assign(p, { title, content, updatedAt: now });
    } else {
      if (readonly) { anonFormState.saving = false; toast(READONLY_MSG()); location.hash = '#board-anon'; return; }
      id = anon.nextId++;
      // 글쓴이 사번은 저장하지만(내 글 확인용) 화면 어디에도 보여 주지 않는다. 이름·부서는 아예 저장하지 않는다
      anon.posts.push({ id, title, content, writerEmpNo: me.empNo, createdAt: now, updatedAt: null, reports: [], hidden: false, comments: [] });
      anonState.page = 1; anonState.q = '';
    }
    saveAnon(anon);
    toast(anonFormState.id ? '글을 수정했습니다' : '익명 글을 등록했습니다');
    location.hash = `#board-anon/view/${id}`;
  }, 500);
});

$('aBtnCancel').addEventListener('click', async () => {
  if (anonFormDirty() && !(await confirmDialog('leaveDialog'))) return;
  anonFormState.leaving = true;
  location.hash = anonFormState.id ? `#board-anon/view/${anonFormState.id}` : '#board-anon';
});

// ---------- 그룹웨어에 내놓는 것 ----------
// The stores are cached in memory per company. Drop the cache when the saved copy changed under us: a department
// renamed in 부서 관리 (org-admin.js rewrites posts and notices and calls this), or another tab writing the same
// company's notices or board. Without it the next save here would write the old data back over the change.
function resetNoticeCaches() { storeOf = ''; boardOf = ''; anonOf = ''; }
window.resetNoticeCaches = resetNoticeCaches;
window.addEventListener('storage', e => {
  if (!session || !e.key || (e.key !== storeKey() && e.key !== boardKey() && e.key !== anonKey())) return;
  resetNoticeCaches();
  const v = location.hash.slice(1);
  if (/^(notice|board-dept|board-anon)(\/|$)/.test(v)) route(v);
  else if (v === 'home' && typeof renderHomeNotices === 'function') renderHomeNotices();
});
// 부서 이름 변경 (org-admin.js가 부른다): 저장된 공지·게시글 속 부서 이름을 새 이름으로 바꾼다.
// 공지는 부서 공지의 범위(scope)와 쓴 사람의 부서, 부서 게시판은 글의 부서. 익명 게시판은 부서를 저장하지 않는다.
// 실제 서버는 부서를 번호로 저장하므로 이 일이 필요 없다
window.renameNoticeDept = (code, from, to) => {
  const rewrite = (key, fix) => {
    try {
      const data = JSON.parse(localStorage.getItem(key));
      if (!data) return;
      fix(data);
      localStorage.setItem(key, JSON.stringify(data));
    } catch (err) { /* 저장 공간 문제 등은 무시 (시안) */ }
  };
  rewrite(`${STORE_KEY}:${code}`, d => (d.list || []).forEach(n => {
    if (n.scope === from) n.scope = to;
    if (n.writerDept === from) n.writerDept = to;
    (n.comments || []).forEach(c => { if (c.writerDept === from) c.writerDept = to; });
  }));
  rewrite(`${BOARD_KEY}:${code}`, d => (d.posts || []).forEach(p => { if (p.dept === from) p.dept = to; }));
  resetNoticeCaches(); // 메모리에 든 옛 사본을 버린다 (안 그러면 다음 저장이 옛 이름으로 덮어씀)
};
window.routeNoticeBoard = route;
window.renderHomeNotices = () => { syncMe(); ensureStore(); renderHome(); };
})();
