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

// ---------- 화면 만들기 도구 (여러 모듈의 화면 JS가 같이 쓴다) ----------
// h(태그, class, ...자식): 요소를 만든다. 글자는 textContent로 들어가므로 사용자가 쓴 글도 그대로 넣어도 된다
const h = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
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
  s.setAttribute('aria-label', label);
  return s;
}
// 자산 한 줄 이름: '노트북 1 (SN-123)'. 제조 일련번호(serial)가 없으면 이름만
const assetLabel = a => (a.serial ? `${a.name} (${a.serial})` : a.name);
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

// 다른 화면(내 장비, 결재함의 재기안·이어서 쓰기)에서 기안 작성을 채워서 연다. 페이지가 바뀌므로 sessionStorage로 넘긴다
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
