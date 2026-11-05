// 화면에서 같이 쓰는 작은 도구: 날짜·금액 표시, 알림(toast), 목록·상세 칸 만들기.
// common.js 다음, 각 모듈 JS 앞에 불린다. 각 JSP 의 scripts 조각이 불러오고, 알림을 쓰려면 <div class="toast" id="toast" hidden>이 있어야 한다.
const pad2 = n => String(n).padStart(2, '0');
// 2026.10.29
const ymd = iso => { const d = new Date(iso); return `${d.getFullYear()}.${pad2(d.getMonth() + 1)}.${pad2(d.getDate())}`; };
// 10.29 14:05 (올해 안의 짧은 표시)
const when = iso => { const d = new Date(iso); return `${pad2(d.getMonth() + 1)}.${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`; };
// 2026.10.29 14:05
const whenFull = iso => { const d = new Date(iso); return `${d.getFullYear()}.${pad2(d.getMonth() + 1)}.${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`; };
// 80,000원
const won = n => `${n.toLocaleString('ko-KR')}원`;

// 화면 아래 알림 한 줄 (4초 뒤 사라진다). common.js 의 토스트를 그대로 쓴다: 타이머가 둘이면 앞 알림이 뒤 알림을 일찍 지운다
function showToast(text) {
  if (window.IEUM && IEUM.toast) IEUM.toast(text);
}
// 안내를 남기고 다른 화면으로 옮긴다. 옮기면 이 화면의 알림은 바로 사라지므로, 다음 화면이 열리며 한 번 띄운다
const NEXT_TOAST_KEY = 'ieum-next-toast';
function toastAndGo(text, href) {
  try { sessionStorage.setItem(NEXT_TOAST_KEY, JSON.stringify({ text, at: Date.now() })); } catch { /* 저장소가 막혔으면 안내 없이 옮긴다 */ }
  location.href = href;
}
// util.js 는 레이아웃의 알림 칸과 common.js 다음에 불리므로 여기서 바로 띄울 수 있다.
// 알림 칸이 없는 화면(계정 생성 join.jsp)은 다음 화면에 넘기고, 로그인 화면을 거치는 등 30초가 지나 열리면 버린다
try {
  const next = JSON.parse(sessionStorage.getItem(NEXT_TOAST_KEY));
  if (next && document.getElementById('toast')) {
    sessionStorage.removeItem(NEXT_TOAST_KEY);
    if (Date.now() - next.at < 30000) showToast(next.text);
  }
} catch { /* 저장소가 막혔다 */ }

// ---------- 화면 만들기 도구 (여러 모듈의 화면 JS가 같이 쓴다) ----------
// h(태그, class, ...자식): 요소를 만든다. 글자는 textContent로 들어가므로 사용자가 쓴 글도 그대로 넣어도 된다
// 서버가 빈 칸을 null로 보내도 화면에 'null'·'undefined' 글자가 찍히지 않게 null·undefined 자식은 건너뛴다(0과 '' 는 그대로 넣는다).
// 빈 칸에 '-' 같은 안내가 필요하면 부르는 쪽에서 값 || '-' 로 넣는다
const h = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids.filter(k => k != null)); return e; };
const span = (text, cls) => h('span', cls, text);
const small = text => h('small', 'cell-sub', text);
const dlOf = rows => h('dl', 'detail', ...rows.flatMap(([k, v]) => [h('dt', '', k), h('dd', '', v)]));
const cardOf = (title, ...kids) => h('section', 'info-card', h('header', 'info-card-head', h('h2', '', title)), ...kids);
const text = s => h('span', 'reply-text', s); // 쓴 사람이 넣은 줄바꿈을 그대로 보인다
function inqCell(label, ...parts) {
  const td = h('td', '', h('div', 'cell', ...parts));
  td.dataset.label = label; // 폰에서 값 앞에 붙는 열 이름
  return td;
}
function titleBtn(label, isOpen) {
  const b = h('button', 'row-title', label);
  b.type = 'button';
  b.setAttribute('aria-expanded', isOpen);
  return b;
}
function dataRow(isOpen, onToggle, ...cells) {
  const tr = h('tr', 'row-link' + (isOpen ? ' is-open' : ''), ...cells);
  tr.addEventListener('click', onToggle);
  return tr;
}
// 이미 펼친 모양으로 만든다: 동작 뒤 다시 그릴 때 펼침 애니메이션이 또 돌지 않게
function detailTr(cols, content) {
  const td = h('td', '', h('div', 'slide open', h('div', 'slide-inner', content)));
  td.colSpan = cols;
  return h('tr', 'row-detail', td);
}
function emptyTr(cols, label) {
  const td = h('td', '', label);
  td.colSpan = cols;
  return h('tr', 'row-empty', td);
}
// 쓰는 버튼: 읽기 전용 업체에서는 흐리게 두고 누르면 안내만 한다(data-write, common.js)
function writeBtn(label, cls, onClick) {
  const b = h('button', cls, label);
  b.type = 'button';
  b.dataset.write = '';
  b.addEventListener('click', () => onClick(b));
  return b;
}
function textBox(placeholder, max, label) {
  const t = h('textarea', 'inq-text');
  t.maxLength = max;
  t.placeholder = placeholder;
  t.setAttribute('aria-label', label);
  return t;
}
// 고르기 칸: 첫 줄은 '고르기' 안내(값 없음). options 는 [글자, 값] 목록, selected 는 처음 골라 둘 값
function pickSelect(label, first, options, selected) {
  const s = h('select', 'input ta-assignee', h('option', '', options.length ? first : '고를 항목이 없습니다'),
    ...options.map(([text, value]) => {
      const o = h('option', '', text);
      o.value = value;
      o.selected = selected != null && String(value) === String(selected);
      return o;
    }));
  s.firstChild.value = '';
  s.firstChild.dataset.placeholder = ''; // 안내 문구: 펼친 목록에는 넣지 않는다(select.js)
  s.setAttribute('aria-label', label);
  return s;
}
// 자산 한 줄 이름: '노트북 1 (SN-123)'. 제조 일련번호(serial)가 없으면 이름만
const assetLabel = a => (a.serial ? `${a.name} (${a.serial})` : a.name);
// 공통 코드 이름표: 키는 공통 코드(common.code enum) 그대로. 티켓 화면(내 티켓·내 장비·티켓 처리)이 같이 쓴다
const TICKET_TYPE = { TK_INCIDENT: '장애', TK_QUESTION: '문의', TK_EQUIPMENT: '장비 지급' };
// 티켓 상태 (AGENTS.md): 접수 → 배정 → 처리 중 → 처리 완료 → 종료(요청자 확인), 그 밖에 취소·반려. TICKET_CLASS 는 상태 배지 색(status-*)
const TICKET_STATUS = { RECEIVED: '접수', ASSIGNED: '배정', IN_PROGRESS: '처리 중', RESOLVED: '처리 완료', CLOSED: '종료', CANCELED: '취소', REJECTED: '반려' };
const TICKET_CLASS = { RECEIVED: 'requested', ASSIGNED: 'requested', IN_PROGRESS: 'waiting', RESOLVED: 'paid', CLOSED: 'paid', CANCELED: 'expired', REJECTED: 'failed' };
const ASSET_TYPE = { AT_LAPTOP: '노트북', AT_DESKTOP: '데스크톱', AT_MONITOR: '모니터', AT_OTHER: '기타 장비', AT_PRINTER: '프린터', AT_NETWORK: '네트워크 장비', AT_TABLET: '태블릿', AT_PHONE: '업무용 전화' };
// 장비 종류 이름. 이름표에 없는 값은 받은 그대로 보인다
const assetTypeName = k => ASSET_TYPE[k] || k;
// 이력·댓글 목록 (bill-history). rows 는 한 줄에 넣을 요소(또는 그 목록)
const historyList = (rows, emptyText) =>
  h('ul', 'bill-history', ...(rows.length ? rows.map(r => h('li', '', ...[].concat(r))) : [h('li', '', span(emptyText, 'muted'))]));

// el(태그, { 속성 }, 글자)
function el(tag, attrs = {}, text) {
  const e = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
  if (text !== undefined) e.textContent = text;
  return e;
}
const emptyRow = emptyTr; // 옛 이름 (board.js 가 쓴다)

// 다른 화면(내 장비, 결재함의 재기안)에서 기안 작성을 채워서 연다. 페이지가 바뀌므로 sessionStorage로 넘긴다
const APPROVAL_PRE_KEY = 'ieum-approval-pre';
function startApprovalWrite(form, pre) {
  try { sessionStorage.setItem(APPROVAL_PRE_KEY, JSON.stringify({ form, ...pre })); } catch (e) { /* 저장소 막힘: 빈 양식으로 연다 */ }
  location.href = pageHref('/approval/write');
}
// 기안 작성 화면이 열릴 때 한 번 꺼낸다 (없으면 null)
function takeApprovalPre() {
  try {
    const v = sessionStorage.getItem(APPROVAL_PRE_KEY);
    sessionStorage.removeItem(APPROVAL_PRE_KEY);
    return v ? JSON.parse(v) : null;
  } catch (e) { return null; }
}
