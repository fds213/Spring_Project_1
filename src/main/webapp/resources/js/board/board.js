// 공지사항·부서 게시판 (views/board/notice.jsp, dept.jsp). 공지 범위(전사·부서), 부서장 권한, 에디터·첨부파일·댓글.
// 데이터는 api.*(jQuery AJAX)로만 받고 바꾼다. 누가 무엇을 할 수 있는지(글쓰기·수정·삭제·고정)는 서버가 응답에 담아 보내고, 서버가 다시 검사한다.
// 한 JSP에 목록·글 보기·글쓰기가 같이 있고, 주소(/board/notice, /board/notice/view/3, /write, /edit/3)를 보고 하나를 보인다.
// 다른 스크립트와 이름이 겹치지 않게 함수 하나로 감싸고, 그룹웨어에는 아래 하나만 내놓는다.
//   routeNoticeBoard(full): 화면을 연다 (JSP 페이지가 열리면 부른다. full = 'notice', 'notice/view/3', 'board-dept/write' …)
(() => {
// - 화면에 글자를 넣을 때는 모두 textContent를 쓴다(innerHTML 금지). 남이 쓴 글에 들어 있는
//   <script> 같은 코드가 실행되지 않게 하려는 것이다. 서식 글만 허용한 태그를 남기고 걸러서(sanitizeHtml) 넣는다.
const TITLE_MAX = 100;
const CONTENT_MAX = 2000; // 서식을 뺀 글자 수 기준
const COMMENT_MAX = 500;
const PIN_MAX = 5; // 맨 위 고정은 범위마다 5개까지 (전사 5개, 각 부서 5개). 안내 문구에만 쓴다 (서버가 센다)
const ALL = 'ALL'; // 전사 공지의 범위 값. 부서 공지는 부서 이름
const scopeShort = s => (s === ALL ? '전사' : s);
const noticeScopeLabel = s => (s === ALL ? '전사 공지' : `${s} 공지`);

// ---------- 서식 글(에디터) 다루기 ----------
// 에디터로 쓴 내용은 HTML이다. 남이 몰래 넣은 <script>나 onerror 같은 코드가 실행되지 않도록,
// 허용한 태그와 스타일만 남기고 나머지는 모두 지운다. 서버가 저장하기 전에 같은 일을 하고
// (예: jsoup Safelist), 화면에서도 보여 주기 전에 한 번 더 거른다.
const ALLOWED_TAGS = new Set(['P', 'DIV', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'SPAN', 'UL', 'OL', 'LI']);
// 글자 크기는 10~40px만 (에디터 숫자 칸과 같은 범위)
const sizeOk = v => { const m = /^(\d{2})px$/.exec(v); return !!m && +m[1] >= 10 && +m[1] <= 40; };
const COLOR_RE = /^(#[0-9a-f]{3,6}|rgb\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*\))$/i;

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
// 서식을 뺀 글자만 (글자 수 세기·빈칸 검사용)
function textOf(html) {
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html');
  doc.querySelectorAll('p, div, li, br').forEach(e => e.append('\n'));
  return doc.body.textContent.replace(/\n{3,}/g, '\n\n').trim();
}

// ---------- 공통 ----------
// jQuery의 $ 와 겹치지 않게 byId 로 쓴다
const byId = id => document.getElementById(id);
const fmtDate = ymd;      // 2026.10.29 (util.js)
const fmtTime = whenFull; // 2026.10.29 14:05 (util.js)
const enc = encodeURIComponent;


// 안내는 그룹웨어 토스트(util.js showToast)로
const toast = msg => showToast(msg);

// 확인 창(<dialog>)을 열고, 누른 버튼이 '확인'이면 true
function confirmDialog(id) {
  const d = byId(id);
  return new Promise(resolve => {
    const done = v => { d.close(); resolve(v); };
    d.querySelector('[value="ok"]').onclick = () => done(true);
    d.querySelector('[value="cancel"]').onclick = () => done(false);
    d.oncancel = () => resolve(false); // Esc
    d.showModal();
  });
}

// 늦게 온 옛 응답이 새로 연 화면을 덮지 않게: 화면을 열거나 목록을 다시 받을 때마다 번호를 늘리고,
// 응답이 왔을 때 그사이 다른 화면을 열지 않았는지 확인한다 (live()가 false면 버린다)
let loadSeq = 0;
const ticket = () => { const n = ++loadSeq; return () => n === loadSeq; };

// 읽기 전용 업체: data-write 버튼은 보이되 흐리고, 누르면 이유를 알려 준다(그룹웨어 공통 동작).
// 주소로 바로 들어온 작성 화면은 서버가 403으로 막고, 화면은 그 안내를 보여 주고 목록으로 돌려보낸다

// ---------- 화면 전환 ----------
// notice · notice/view/12 · notice/write · notice/edit/12 (board-dept도 같은 모양). 메뉴 표시·권한은 공통 틀과 서버가 맡는다
function showSub(id) {
  document.querySelectorAll('.view').forEach(v => v.classList.toggle('on', v.id === id));
  document.querySelector('.content').scrollTop = 0;
}

function route(hash) {
  const [view, action, id] = decodeURIComponent(hash).split('/');
  if (view === 'board-dept') return routeBoard(action, id);
  if (action === 'view') return openDetail(id);
  if (action === 'write' || action === 'edit') return openForm(action === 'edit' ? id : null);
  showSub('view-notice-list');
  loadList();
}

// 작성 중에 다른 페이지로 가려 하면 먼저 물어본다 (메뉴·목록 같은 페이지 링크. 새 창으로 여는 링크는 빼고)
document.addEventListener('click', async e => {
  const a = e.target.closest('a[href]');
  if (!a || a.target || a.getAttribute('href').startsWith('#') || !anyFormDirty()) return;
  e.preventDefault();
  if (await confirmDialog('leaveDialog')) { setLeaving(true); location.href = a.href; }
});
// 공지·부서 게시판 작성 화면을 모두 확인한다
const anyFormDirty = () => formDirty() || boardFormDirty();
function setLeaving(v) {
  formState.leaving = v;
  boardFormState.leaving = v;
}

// 페이지 번호 (공지·게시판 같이 씀): state.page를 바꾸고 reload로 그 페이지를 다시 받는다
function renderPager(navId, state, pages, reload) {
  const nav = byId(navId);
  const btn = (label, page, opts = {}) => {
    const b = el('button', { type: 'button' }, label);
    if (opts.aria) b.setAttribute('aria-label', opts.aria);
    if (page === state.page && opts.current) b.setAttribute('aria-current', 'page');
    b.disabled = !!opts.disabled;
    b.addEventListener('click', () => { state.page = page; reload(); document.querySelector('.content').scrollTop = 0; });
    return b;
  };
  const items = [btn('이전', state.page - 1, { disabled: state.page === 1, aria: '이전 페이지' })];
  for (let p = 1; p <= pages; p++) items.push(btn(String(p), p, { current: true, aria: `${p}페이지` }));
  items.push(btn('다음', state.page + 1, { disabled: state.page === pages, aria: '다음 페이지' }));
  nav.replaceChildren(...items);
  nav.hidden = pages <= 1;
}

// 목록 한 줄의 제목 칸: 제목 링크 + 댓글 수 [3] + 첨부 + 수정됨
function titleCell(href, row, before) {
  const td = el('td', { 'data-label': '제목' });
  const wrap = el('span');
  if (before) wrap.append(before);
  wrap.append(el('a', { href, class: 'notice-link' }, row.title));
  if (row.commentCount) wrap.append(el('span', { class: 'comment-badge', 'aria-label': `댓글 ${row.commentCount}개` }, `[${row.commentCount}]`));
  if (row.fileCount) wrap.append(el('span', { class: 'file-tag', 'aria-label': `첨부파일 ${row.fileCount}개` }, '첨부'));
  if (row.updatedAt) wrap.append(el('span', { class: 'edited-tag' }, '수정됨'));
  td.append(wrap);
  return td;
}

// 실시간 검색: 글자를 칠 때마다 서버에 다시 묻되, 타자를 멈추고 0.25초 뒤에 한 번만 보낸다(디바운스). 다 지우면 전체 목록.
const SEARCH_DELAY = 250;
function liveSearch(inputId, formId, state, reload) {
  if (!byId(inputId)) return; // 이 페이지에 없는 게시판
  let timer;
  const run = () => {
    const q = byId(inputId).value.trim();
    if (q === state.q) return; // 띄어쓰기만 늘었으면 다시 받지 않는다
    state.q = q;
    state.page = 1;
    reload();
  };
  byId(inputId).addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(run, SEARCH_DELAY); });
  // Enter는 기다리지 않고 바로 검색
  byId(formId).addEventListener('submit', e => { e.preventDefault(); clearTimeout(timer); run(); });
}

// 실패 안내: 볼 수 없거나 없는 글(404)이면 목록으로 돌려보낸다
function failTo(listHash, fallback) {
  return xhr => {
    toast(apiMessage(xhr, fallback));
    if (xhr.status === 404) location.href = listHash;
  };
}


// ---------- 1. 목록 ----------
// 탭: 전체 / 부서 공지. 검색: 제목 + 내용 (서버가 띄어쓴 단어가 모두 들어 있는 것만 찾는다)
const listState = { page: 1, q: '', tab: 'all', deptTab: '부서 공지' };

function loadList() {
  const live = ticket();
  renderNoticeTabs();
  api.get('/board/api/notices', { tab: listState.tab, q: listState.q, page: listState.page })
    .done(res => { if (live()) renderList(res); })
    .fail(xhr => { if (live()) toast(apiMessage(xhr, '공지 목록을 불러오지 못했습니다')); });
}

// 탭 (전체 / 부서 공지). 직원에게는 "물류팀 공지"처럼 자기 부서 이름으로 보인다. 좌우 화살표로도 옮긴다
function renderNoticeTabs() {
  byId('noticeTabDept').textContent = listState.deptTab;
  document.querySelectorAll('#noticeTabs .type-tab').forEach(t => {
    const on = t.dataset.tab === listState.tab;
    t.setAttribute('aria-selected', String(on));
    t.tabIndex = on ? 0 : -1;
  });
}
byId('noticeTabs')?.addEventListener('click', e => {
  const t = e.target.closest('.type-tab');
  if (!t) return;
  listState.tab = t.dataset.tab;
  listState.page = 1;
  loadList();
});
byId('noticeTabs')?.addEventListener('keydown', e => {
  if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
  const tabs = [...document.querySelectorAll('#noticeTabs .type-tab')];
  const i = tabs.findIndex(t => t.dataset.tab === listState.tab);
  const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
  listState.tab = next.dataset.tab; listState.page = 1;
  loadList();
  next.focus();
});

function renderList(res) {
  listState.page = res.page;
  listState.deptTab = res.perms.allDepts ? '부서 공지' : `${res.myDept} 공지`;
  byId('btnWrite').hidden = !res.perms.canWrite;
  renderNoticeTabs();
  // 치고 있는 중에는 검색창을 건드리지 않는다 (상세에서 돌아왔을 때만 검색어를 다시 채움)
  if (document.activeElement !== byId('searchInput')) byId('searchInput').value = listState.q;

  byId('resultInfo').textContent = listState.q
    ? `"${listState.q}" 검색 결과 ${res.total}건`
    : `전체 ${res.total}건`;

  // 맨 위 고정: 검색하지 않을 때만 모든 페이지 맨 위에 따로 온다(pinned). 일반 목록(items)에는 빠져 있다
  const row = (n, isPinned) => {
    const tr = el('tr', isPinned ? { class: 'is-pinned' } : {});
    const noTd = el('td', { 'data-label': '번호', class: 'num' });
    if (isPinned) noTd.append(el('span', { class: 'pin-label' }, '고정')); else noTd.textContent = String(n.no);
    tr.append(
      noTd,
      // 구분 칸: 전사 공지는 "전사", 부서 공지는 부서 이름 (제목은 깔끔하게 두고 칸으로 나눈다)
      el('td', { 'data-label': '구분', class: n.scope === ALL ? 'scope-cell' : 'scope-cell is-dept' }, scopeShort(n.scope)),
      titleCell(pageHref(`/board/notice/view/${n.id}`), n),
      el('td', { 'data-label': '작성자' }, n.writerName),
      el('td', { 'data-label': '작성일', class: 'num' }, fmtDate(n.createdAt)),
    );
    return tr;
  };

  const tbody = byId('noticeRows');
  if (!res.pinned.length && !res.items.length) {
    tbody.replaceChildren(emptyRow(5, listState.q ? '검색 결과가 없습니다.' : '등록된 공지가 없습니다.'));
  } else {
    tbody.replaceChildren(...res.pinned.map(n => row(n, true)), ...res.items.map(n => row(n, false)));
  }
  renderPager('pager', listState, res.pages, loadList);
}

liveSearch('searchInput', 'searchForm', listState, loadList);

// 사이드 메뉴 "공지사항"은 항상 처음 목록 (상세의 "목록으로"는 검색 결과를 그대로 둔다)

// ---------- 2. 상세 ----------
let current = null; // 열려 있는 공지 (상세 응답)

// 볼 수 없는 공지(다른 부서 공지)와 없는 공지는 서버가 같은 말로 답한다 (다른 부서에 그런 공지가 있는지 드러내지 않게)
const NOTICE_NOT_FOUND = '없는 공지이거나 볼 수 없는 공지입니다';

function openDetail(id) {
  const live = ticket();
  api.get(`/board/api/notices/${enc(id)}`)
    .done(n => { if (live()) { renderDetail(n); showSub('view-notice-detail'); } })
    .fail(xhr => { if (live()) { toast(apiMessage(xhr, NOTICE_NOT_FOUND)); location.replace(pageHref('/board/notice')); } });
}

function renderDetail(n) {
  current = n;
  byId('detailTitle').textContent = n.title;

  const who = el('span');
  who.append(el('strong', {}, `${n.writerDept} ${n.writerName}`));
  const parts = [
    el('span', { class: `scope-tag${n.scope === ALL ? '' : ' is-dept'}` }, noticeScopeLabel(n.scope)),
    who, el('span', {}, `${fmtTime(n.createdAt)} 작성`),
  ];
  if (n.pinned) parts.unshift(el('span', { class: 'pin-tag' }, '맨 위 고정'));
  if (n.updatedAt) parts.push(el('span', {}, `${fmtTime(n.updatedAt)} 수정`));
  byId('detailMeta').replaceChildren(...parts);

  // 서식 글: 허용한 태그만 남긴 뒤 넣는다. 거르지 않은 HTML을 innerHTML로 넣으면 안 된다
  byId('detailBody').innerHTML = sanitizeHtml(n.content);
  renderFilesBox(n.files, { box: 'detailFiles', count: 'detailFileCount', images: 'detailImages', list: 'detailFileList' });
  byId('nCommentText').value = '';
  setFieldError('nCommentText', 'nCommentError', '');
  updateCount('nCommentText', 'nCommentCountNow', COMMENT_MAX);
  renderNoticeComments(n);

  byId('detailOwnerActions').hidden = !n.canEdit;
  byId('btnEdit').setAttribute('href', pageHref(`/board/notice/edit/${n.id}`));
  byId('btnPin').textContent = n.pinned ? '고정 풀기' : '고정하기';
  byId('btnPin').setAttribute('aria-pressed', String(!!n.pinned));
}

// 고정하기 / 고정 풀기: 수정 화면에 가지 않고 바로 바꾼다. 내용을 고친 게 아니라서 수정일은 그대로 둔다
// 권한·읽기 전용·고정 개수는 서버가 확인한다 (두 사람이 동시에 눌러도 5개를 넘지 않게)
byId('btnPin')?.addEventListener('click', () => {
  const n = current;
  const btn = byId('btnPin');
  if (!n || btn.disabled) return;
  btn.disabled = true;
  api.post(`/board/api/notices/${n.id}/pin`, { pinned: !n.pinned })
    .done(r => { toast(r.pinned ? '목록 맨 위에 고정했습니다' : '고정을 풀었습니다'); openDetail(n.id); })
    .fail(xhr => { if (xhr.status === 409) showPinFull(n.scope); else failTo(pageHref('/board/notice'))(xhr); })
    .always(() => { btn.disabled = false; });
});

// 5개가 꽉 찼을 때: 지금 고정된 공지 목록을 보여 주고, 누르면 그 공지로 가서 바로 풀 수 있게 한다
function showPinFull(scope) {
  api.get('/board/api/notices/pinned', { scope })
    .done(pins => {
      const d = byId('pinFullDialog');
      d.querySelector('h2').textContent = `${noticeScopeLabel(scope)}의 맨 위 고정은 ${PIN_MAX}개까지예요`;
      byId('pinFullList').replaceChildren(...pins.map(x => {
        const li = el('li');
        const a = el('a', { href: pageHref(`/board/notice/view/${x.id}`) });
        a.append(el('span', {}, x.title), el('small', {}, `${x.writerName} · ${fmtDate(x.createdAt)}`));
        a.addEventListener('click', () => d.close());
        li.append(a);
        return li;
      }));
      confirmDialog('pinFullDialog');
    })
    .fail(xhr => toast(apiMessage(xhr)));
}

// ---------- 첨부파일 보여 주기 ----------
// 이미지는 미리보기(누르면 크게), 나머지는 이름·크기·내려받기.
// 내려받기 주소(url)는 서버가 "이 사람이 이 글을 볼 수 있나"를 확인한 뒤에만 파일을 준다
// (글은 숨겼는데 파일 주소를 알면 받아지는 일이 없게)
const isImage = f => /^image\/(png|jpe?g|gif|webp)$/.test(f.type);
const fmtSize = b => (b >= 1048576 ? `${(b / 1048576).toFixed(1)}MB` : `${Math.max(1, Math.round(b / 1024))}KB`);

// 상세 화면의 첨부파일 상자 (공지 · 부서 게시판). ids: { box, count, images, list }
function renderFilesBox(files, ids) {
  byId(ids.box).hidden = !files.length;
  byId(ids.count).textContent = String(files.length);
  byId(ids.images).replaceChildren(...files.filter(isImage).map(f => {
    const b = el('button', { type: 'button', class: 'attach-thumb', 'aria-label': `${f.name} 크게 보기` });
    const img = el('img', { src: f.url, alt: f.name, loading: 'lazy' });
    b.append(img, el('span', {}, f.name));
    b.addEventListener('click', () => {
      byId('imageDialogImg').src = f.url;
      byId('imageDialogImg').alt = f.name;
      byId('imageDialogDownload').href = f.url;
      byId('imageDialogDownload').setAttribute('download', f.name);
      confirmDialog('imageDialog');
    });
    return b;
  }));
  byId(ids.list).replaceChildren(...files.map(f => {
    const li = el('li');
    li.append(el('span', { class: 'attach-name' }, f.name), el('small', {}, fmtSize(f.size)));
    const a = el('a', { class: 'link-btn', href: f.url, download: f.name }, '내려받기');
    li.append(a);
    return li;
  }));
}

// ---------- 댓글 (공지 · 부서 게시판이 같이 쓴다) ----------
// 삭제 버튼은 서버가 준 canDelete로 보인다. name(c)은 댓글 쓴 사람 표시, extra(c)는 이름 옆 꼬리표
function renderCommentList(ids, comments, opts) {
  byId(ids.count).textContent = String(comments.length);
  byId(ids.empty).hidden = comments.length > 0;
  byId(ids.list).replaceChildren(...comments.map(c => {
    const li = el('li');
    const head = el('div', { class: 'comment-head' });
    head.append(opts.name(c));
    if (opts.extra) { const x = opts.extra(c); if (x) head.append(x); }
    head.append(el('small', {}, fmtTime(c.createdAt)));
    if (c.canDelete) {
      const del = el('button', { type: 'button', class: 'link-btn', 'data-write': '' }, '삭제');
      del.addEventListener('click', () => opts.onDelete(c.id));
      head.append(del);
    }
    li.append(head, el('p', { class: 'comment-text' }, c.text));
    return li;
  }));
}

// 댓글 입력 묶음: 글자 수, 검사, 등록(path()로 보낼 주소). 등록하면 서버가 그 글의 댓글 전체를 돌려준다
function bindCommentForm(ids, path, listHash, onSaved, okText) {
  if (!byId(ids.form)) return; // 이 페이지에 없는 게시판
  let saving = false;
  byId(ids.text).addEventListener('input', () => {
    updateCount(ids.text, ids.counter, COMMENT_MAX);
    setFieldError(ids.text, ids.error, '');
  });
  byId(ids.form).addEventListener('submit', e => {
    e.preventDefault();
    if (saving) return;
    const url = path();
    if (!url) return;
    const text = byId(ids.text).value.trim();
    if (!text) { setFieldError(ids.text, ids.error, '댓글을 입력해 주세요'); byId(ids.text).focus(); return; }
    if (text.length > COMMENT_MAX) { setFieldError(ids.text, ids.error, `댓글은 ${COMMENT_MAX}자까지 쓸 수 있습니다`); return; }
    saving = true;
    byId(ids.save).disabled = true;
    api.post(url, { text })
      .done(comments => {
        byId(ids.text).value = '';
        updateCount(ids.text, ids.counter, COMMENT_MAX);
        onSaved(comments);
        toast(okText);
      })
      .fail(xhr => {
        if (xhr.status === 400) setFieldError(ids.text, ids.error, apiMessage(xhr));
        else failTo(listHash)(xhr);
      })
      .always(() => { saving = false; byId(ids.save).disabled = false; });
  });
}

// 댓글 삭제: 확인 창 → POST …/delete → 남은 댓글 전체를 다시 그린다
async function deleteCommentAt(url, listHash, onDeleted) {
  if (!(await confirmDialog('commentDeleteDialog'))) return;
  api.post(`${url}/delete`)
    .done(comments => { onDeleted(comments); toast('댓글을 삭제했습니다'); })
    .fail(failTo(listHash));
}

// ---------- 공지 댓글 ----------
// 그 공지를 볼 수 있는 사람이면 누구나 쓴다. 본인 댓글은 지울 수 있고, 업체 관리자·인사 담당은
// 부적절한 댓글을 정리할 수 있게 모든 댓글을 지울 수 있다 (서버가 canDelete로 알려 준다)
function renderNoticeComments(n) {
  renderCommentList({ count: 'nCommentCount', empty: 'nCommentEmpty', list: 'nComments' }, n.comments, {
    name: c => el('strong', {}, `${c.writerDept} ${c.writerName}`),
    onDelete: cid => deleteCommentAt(`/board/api/notices/${n.id}/comments/${cid}`, pageHref('/board/notice'),
      comments => { n.comments = comments; renderNoticeComments(n); }),
  });
}
bindCommentForm({ form: 'nCommentForm', text: 'nCommentText', counter: 'nCommentCountNow', error: 'nCommentError', save: 'nCommentSave' },
  () => current && `/board/api/notices/${current.id}/comments`, pageHref('/board/notice'),
  comments => { if (current) { current.comments = comments; renderNoticeComments(current); } }, '댓글을 등록했습니다');

byId('btnDelete')?.addEventListener('click', async () => {
  const n = current;
  if (!n) return;
  const d = byId('deleteDialog');
  d.querySelector('h2').textContent = '공지를 삭제할까요?';
  d.querySelector('p').textContent = (n.comments.length || n.files.length)
    ? `댓글 ${n.comments.length}개와 첨부파일 ${n.files.length}개도 함께 삭제되며 되돌릴 수 없습니다.` : '삭제한 공지는 되돌릴 수 없습니다.';
  if (!(await confirmDialog('deleteDialog'))) return;
  const btn = byId('btnDelete');
  btn.disabled = true;
  // 권한·읽기 전용은 서버가 다시 확인한다
  api.post(`/board/api/notices/${n.id}/delete`)
    .done(() => { toast('공지를 삭제했습니다'); location.href = pageHref('/board/notice'); })
    .fail(failTo(pageHref('/board/notice')))
    .always(() => { btn.disabled = false; });
});

// ---------- 공통: 에디터 (공지 · 부서 게시판이 같이 쓴다) ----------
// 브라우저의 글 꾸미기 기능(execCommand)을 쓴 간단한 에디터. 라이브러리 없이 만들었다.
// 팀이 에디터 라이브러리(Bootstrap 없이 동작하는 것)를 고르면 그것으로 바꿔도 된다.
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
  const area = byId(areaId);
  if (!area) return null; // 이 페이지에 없는 게시판
  const bar = area.closest('.editor').querySelector('.editor-toolbar');
  bar.append(byId('editorToolbarTpl').content.cloneNode(true));
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
// 글 하나에 5개까지, 파일당 10MB (AGENTS.md 파일 규칙. 서버가 다시 검사한다)
// 고른 파일은 data URL로 읽어 두었다가 저장할 때 JSON으로 보낸다. 서버가 파일 받는 방식(FormData·multipart 등)을 정하면 그에 맞춘다
const FILE_MAX = 5;
const FILE_SIZE_MAX = 10 * 1024 * 1024;
const FILE_EXT = /\.(jpe?g|png|gif|webp|pdf|hwpx?|docx?|xlsx?|pptx?|txt|zip)$/i;
const fileSig = files => files.map(f => f.id).join(',');

// 글쓰기 화면의 "파일 선택" 묶음. ids: { pick, input, info, list, error }
function makeFilePicker(ids) {
  if (!byId(ids.pick)) return null; // 이 페이지에 없는 게시판
  const fp = { files: [] };
  fp.render = () => {
    byId(ids.info).textContent = fp.files.length ? `${fp.files.length} / ${FILE_MAX}개` : '선택한 파일 없음';
    byId(ids.pick).disabled = fp.files.length >= FILE_MAX;
    byId(ids.list).replaceChildren(...fp.files.map(f => {
      const li = el('li');
      if (isImage(f)) li.append(el('img', { src: f.url || f.data, alt: '', class: 'attach-mini' }));
      li.append(el('span', { class: 'attach-name' }, f.name), el('small', {}, fmtSize(f.size)));
      const del = el('button', { type: 'button', class: 'link-btn' }, '빼기');
      del.setAttribute('aria-label', `${f.name} 첨부 빼기`);
      del.addEventListener('click', () => {
        fp.files = fp.files.filter(x => x.id !== f.id);
        byId(ids.error).textContent = '';
        fp.render();
      });
      li.append(del);
      return li;
    }));
  };
  fp.set = files => { fp.files = (files || []).map(f => ({ ...f })); byId(ids.error).textContent = ''; fp.render(); };
  fp.error = msg => { byId(ids.error).textContent = msg; };

  byId(ids.pick).addEventListener('click', () => byId(ids.input).click());
  byId(ids.input).addEventListener('change', async e => {
    const picked = [...e.target.files];
    e.target.value = ''; // 같은 파일을 다시 골라도 change가 오게
    byId(ids.error).textContent = '';
    const errors = [];
    for (const file of picked) {
      if (fp.files.length >= FILE_MAX) { errors.push(`첨부파일은 ${FILE_MAX}개까지입니다`); break; }
      if (!FILE_EXT.test(file.name)) { errors.push(`${file.name}: 이미지·문서 파일만 올릴 수 있습니다`); continue; }
      if (file.size > FILE_SIZE_MAX) {
        errors.push(`${file.name}: 파일당 ${fmtSize(FILE_SIZE_MAX)}까지 올릴 수 있어요`);
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
    if (errors.length) byId(ids.error).textContent = [...new Set(errors)].join(' / ');
    fp.render();
  });
  return fp;
}

// 저장할 첨부파일: 이미 올라가 있던 파일은 { id }만, 새로 고른 파일은 내용(data URL)까지 보낸다
// (서버가 multipart로 받기로 하면 새 파일을 FormData에 담아 보낸다)
const filesToSend = files => files.map(f => (f.data ? { name: f.name, size: f.size, type: f.type, data: f.data } : { id: f.id }));

// 작성 화면 공통 도구
function updateCount(input, counter, max) {
  // 에디터(서식 글)는 서식을 뺀 글자 수를 센다
  const len = byId(input).isContentEditable ? textOf(byId(input).innerHTML.replace(/​/g, '')).length : byId(input).value.length;
  byId(counter).textContent = `${len} / ${max}`;
  byId(counter).classList.toggle('is-full', len >= max);
}
function setFieldError(input, errId, msg) {
  byId(errId).textContent = msg;
  byId(input).classList.toggle('is-error', !!msg);
  byId(input).setAttribute('aria-invalid', msg ? 'true' : 'false');
}
// 서버가 다시 검사하는 규칙과 같게 맞춘다 (서식을 뺀 글자로 검사)
function validate(title, content) {
  if (!title) return { field: 'title', message: '제목을 입력해 주세요' };
  if (title.length > TITLE_MAX) return { field: 'title', message: `제목은 ${TITLE_MAX}자까지 쓸 수 있습니다` };
  if (!content) return { field: 'content', message: '내용을 입력해 주세요' };
  if (content.length > CONTENT_MAX) return { field: 'content', message: `내용은 ${CONTENT_MAX}자까지 쓸 수 있습니다` };
  return null;
}
// 작성 화면을 열지 못했을 때: 안내하고, 수정 권한이 없으면 그 글로, 나머지(읽기 전용·권한 없음·없는 글)는 목록으로
// 게시판 키 → 화면 주소 (formOpenFailed가 쓴다)
const BOARD_PATH = { notice: '/board/notice', 'board-dept': '/board/dept' };
function formOpenFailed(xhr, id, key) {
  toast(apiMessage(xhr));
  location.replace(id && xhr.status === 403 ? pageHref(`${BOARD_PATH[key]}/view/${id}`) : pageHref(BOARD_PATH[key]));
}
// 저장 버튼: 보내는 동안 막고 "저장 중…"
function setSaving(btnId, on, label) {
  byId(btnId).disabled = on;
  byId(btnId).textContent = on ? '저장 중…' : label;
}

// ---------- 3. 공지 작성 · 수정 ----------
const noticeEditor = makeEditor('fContent');
const noticeFiles = makeFilePicker({ pick: 'btnPickFiles', input: 'fFiles', info: 'fFilesInfo', list: 'fFileList', error: 'fFilesError' });
const editorHtml = () => noticeEditor.html();

const formState = { id: null, initial: { title: '', content: '', pinned: false, scope: '', files: '' }, saving: false, leaving: false };
const formOpen = () => byId('view-notice-form').classList.contains('on');
const formDirty = () => formOpen() && !formState.leaving && !formState.saving &&
  (byId('fTitle').value !== formState.initial.title || editorHtml() !== formState.initial.content ||
   byId('fPinned').checked !== formState.initial.pinned || byId('fScope').value !== formState.initial.scope ||
   fileSig(noticeFiles.files) !== formState.initial.files);

// 주소를 직접 쳐서 들어와도 서버가 막는다 (읽기 전용·쓸 권한 없음·수정 권한 없음은 403, 볼 수 없는 공지는 404)
function openForm(id) {
  const live = ticket();
  api.get('/board/api/notices/form', id ? { id } : undefined)
    .done(res => { if (live()) fillForm(res); })
    .fail(xhr => { if (live()) formOpenFailed(xhr, id, 'notice'); });
}

function fillForm(res) {
  const n = res.notice;
  // 공지 범위: 관리자·인사 담당은 전사 + 모든 부서, 부서장은 자기 부서 하나로 잠근다 (서버가 고를 수 있는 범위를 준다)
  byId('fScope').replaceChildren(...res.scopes.map(s => el('option', { value: s }, s === ALL ? '전사 공지 (전 직원)' : `${s} 공지 (${s}만)`)));
  byId('fScope').disabled = res.scopeLocked;
  byId('fScopeHint').textContent = res.scopeLocked
    ? `부서장은 ${res.myDept} 공지만 쓸 수 있어요. ${res.myDept} 사람만 봅니다.`
    : '전사 공지는 전 직원이, 부서 공지는 그 부서 사람만 봅니다.';

  formState.id = n ? n.id : null;
  formState.saving = false;
  byId('formTitle').textContent = n ? '공지 수정' : '공지 쓰기';
  byId('formCrumb').textContent = n ? '수정' : '글쓰기';
  byId('fScope').value = n ? n.scope : res.scopes[0];
  byId('fTitle').value = n ? n.title : '';
  noticeEditor.set(n ? n.content : '');
  noticeFiles.set(n ? n.files : []);
  byId('fPinned').checked = !!(n && n.pinned);
  formState.initial = { title: byId('fTitle').value, content: editorHtml(), pinned: byId('fPinned').checked,
    scope: byId('fScope').value, files: fileSig(noticeFiles.files) };
  setSaving('btnSave', false, '저장');
  clearErrors();
  updateCounters();
  showSub('view-notice-form');
  byId('fTitle').focus();
}

function updateCounters() {
  const set = (counter, len, max) => {
    byId(counter).textContent = `${len} / ${max}`;
    byId(counter).classList.toggle('is-full', len >= max);
  };
  set('fTitleCount', byId('fTitle').value.length, TITLE_MAX);
  set('fContentCount', noticeEditor.text().length, CONTENT_MAX); // 서식을 뺀 글자 수
}
byId('fTitle')?.addEventListener('input', () => { updateCounters(); setError('fTitle', ''); });
byId('fContent')?.addEventListener('input', () => { updateCounters(); setError('fContent', ''); });

function setError(field, msg) {
  byId(`${field}Error`).textContent = msg;
  byId(field).classList.toggle('is-error', !!msg);
  byId(field).setAttribute('aria-invalid', msg ? 'true' : 'false');
}
function clearErrors() {
  setError('fTitle', ''); setError('fContent', '');
  byId('fPinnedError').textContent = ''; noticeFiles.error('');
}
byId('fPinned')?.addEventListener('change', () => { byId('fPinnedError').textContent = ''; });
byId('fScope')?.addEventListener('change', () => { byId('fPinnedError').textContent = ''; });

byId('noticeForm')?.addEventListener('submit', e => {
  e.preventDefault();
  if (formState.saving) return; // 두 번 눌러도 한 번만 저장
  clearErrors();
  const title = byId('fTitle').value.trim();
  const content = editorHtml();
  // 서식을 뺀 글자로 비었는지·글자 수를 본다
  const err = validate(title, textOf(content));
  if (err) { const f = err.field === 'title' ? 'fTitle' : 'fContent'; setError(f, err.message); byId(f).focus(); return; }
  // 고른 범위가 이 사람이 쓸 수 있는 범위인지, 고정 5개를 넘는지는 서버가 확인한다
  const body = { scope: byId('fScope').value, title, content, pinned: byId('fPinned').checked, files: filesToSend(noticeFiles.files) };
  const editing = formState.id;

  formState.saving = true;
  setSaving('btnSave', true, '저장');
  (editing ? api.post(`/board/api/notices/${editing}`, body) : api.post('/board/api/notices', body))
    .done(r => {
      toast(editing ? '공지를 수정했습니다' : '공지를 등록했습니다');
      if (!editing) { listState.page = 1; listState.q = ''; listState.tab = 'all'; }
      location.href = pageHref(`/board/notice/view/${r.id}`);
    })
    .fail(xhr => {
      formState.saving = false;
      setSaving('btnSave', false, '저장');
      const msg = apiMessage(xhr);
      if (xhr.status === 409) byId('fPinnedError').textContent = msg; // 고정 5개가 꽉 참
      else if (xhr.status === 413) noticeFiles.error(msg);             // 파일이 너무 큼
      else if (xhr.status === 404) { toast(msg); formState.leaving = true; location.href = pageHref('/board/notice'); }
      else toast(msg);
    });
});

byId('btnCancel')?.addEventListener('click', async () => {
  if (formDirty() && !(await confirmDialog('leaveDialog'))) return;
  formState.leaving = true;
  location.href = formState.id ? pageHref(`/board/notice/view/${formState.id}`) : pageHref('/board/notice');
});


// ======================================================================
// 부서 게시판 (목록 · 상세+댓글 · 작성/수정)
// 공지와 다른 점
// - 보기: 같은 부서 글만. 다른 부서 글은 주소를 직접 쳐도 서버가 "없는 글이거나 볼 수 없는 글"(404)로 막는다
//   (서버가 로그인한 사람의 부서와 글의 부서를 비교한다)
// - 쓰기: 부서원 누구나. 수정·삭제는 글쓴이 본인만 (서버가 mine으로 알려 준다)
// - 댓글: 부서원 누구나 등록, 본인 댓글만 삭제 (댓글 수정은 없음), 500자까지
const NOT_FOUND = '없는 글이거나 볼 수 없는 글입니다';

function routeBoard(action, id) {
  if (action === 'view') return openBoardDetail(id);
  if (action === 'write' || action === 'edit') return openBoardForm(action === 'edit' ? id : null);
  showSub('view-board-list');
  loadBoardList();
}

// ---------- 1. 목록 ----------
const boardState = { page: 1, q: '' };

function loadBoardList() {
  const live = ticket();
  api.get('/board/api/dept/posts', { q: boardState.q, page: boardState.page })
    .done(res => { if (live()) renderBoardList(res); })
    .fail(xhr => { if (live()) toast(apiMessage(xhr, '게시글을 불러오지 못했습니다')); });
}

function renderBoardList(res) {
  boardState.page = res.page;
  byId('bListTitle').textContent = `${res.dept} 게시판`;
  byId('bListSub').textContent = `${res.dept} 사람만 보고 쓸 수 있어요.`;
  if (document.activeElement !== byId('bSearchInput')) byId('bSearchInput').value = boardState.q;
  byId('bResultInfo').textContent = boardState.q ? `"${boardState.q}" 검색 결과 ${res.total}건` : `전체 ${res.total}건`;

  const tbody = byId('bRows');
  if (!res.items.length) {
    tbody.replaceChildren(emptyRow(4, boardState.q ? '검색 결과가 없습니다.' : '아직 글이 없습니다. 첫 글을 써 보세요.'));
  } else {
    tbody.replaceChildren(...res.items.map(p => {
      const tr = el('tr');
      tr.append(
        el('td', { 'data-label': '번호', class: 'num' }, String(p.no)),
        titleCell(pageHref(`/board/dept/view/${p.id}`), p),
        el('td', { 'data-label': '작성자' }, p.writerName),
        el('td', { 'data-label': '작성일', class: 'num' }, fmtDate(p.createdAt)),
      );
      return tr;
    }));
  }
  renderPager('bPager', boardState, res.pages, loadBoardList);
}

liveSearch('bSearchInput', 'bSearchForm', boardState, loadBoardList);

// ---------- 2. 상세 + 댓글 ----------
let bCurrent = null;

function openBoardDetail(id) {
  const live = ticket();
  api.get(`/board/api/dept/posts/${enc(id)}`)
    .done(p => { if (live()) { renderBoardDetail(p); showSub('view-board-detail'); } })
    .fail(xhr => { if (live()) { toast(apiMessage(xhr, NOT_FOUND)); location.replace(pageHref('/board/dept')); } });
}

function renderBoardDetail(p) {
  bCurrent = p;
  byId('bDetailTitle').textContent = p.title;
  const who = el('span');
  who.append(el('strong', {}, p.writerName));
  const parts = [who, el('span', {}, `${fmtTime(p.createdAt)} 작성`)];
  if (p.updatedAt) parts.push(el('span', {}, `${fmtTime(p.updatedAt)} 수정`));
  byId('bDetailMeta').replaceChildren(...parts);
  byId('bDetailBody').innerHTML = sanitizeHtml(p.content); // 서식 글. 허용한 태그만 남기고 넣는다
  renderFilesBox(p.files, { box: 'bDetailFiles', count: 'bDetailFileCount', images: 'bDetailImages', list: 'bDetailFileList' });
  byId('bOwnerActions').hidden = !p.mine;
  byId('bBtnEdit').setAttribute('href', pageHref(`/board/dept/edit/${p.id}`));
  byId('bCommentText').value = '';
  setFieldError('bCommentText', 'bCommentError', '');
  updateCount('bCommentText', 'bCommentCountNow', COMMENT_MAX);
  renderComments(p);
}

function renderComments(p) {
  renderCommentList({ count: 'bCommentCount', empty: 'bCommentEmpty', list: 'bComments' }, p.comments, {
    name: c => el('strong', {}, c.writerName),
    onDelete: cid => deleteCommentAt(`/board/api/dept/posts/${p.id}/comments/${cid}`, pageHref('/board/dept'),
      comments => { p.comments = comments; renderComments(p); }),
  });
}
bindCommentForm({ form: 'bCommentForm', text: 'bCommentText', counter: 'bCommentCountNow', error: 'bCommentError', save: 'bCommentSave' },
  () => bCurrent && `/board/api/dept/posts/${bCurrent.id}/comments`, pageHref('/board/dept'),
  comments => { if (bCurrent) { bCurrent.comments = comments; renderComments(bCurrent); } }, '댓글을 등록했습니다');

byId('bBtnDelete')?.addEventListener('click', async () => {
  const p = bCurrent;
  if (!p) return;
  const d = byId('deleteDialog');
  // 공지 삭제 창을 같이 쓰되, 글자만 게시판에 맞게 바꾼다
  d.querySelector('h2').textContent = '게시글을 삭제할까요?';
  const extra = [p.comments.length && `댓글 ${p.comments.length}개`, p.files.length && `첨부파일 ${p.files.length}개`].filter(Boolean);
  d.querySelector('p').textContent = extra.length
    ? `${extra.join('와 ')}도 함께 삭제되며 되돌릴 수 없습니다.` : '삭제한 글은 되돌릴 수 없습니다.';
  const ok = await confirmDialog('deleteDialog');
  d.querySelector('h2').textContent = '공지를 삭제할까요?';
  d.querySelector('p').textContent = '삭제한 공지는 되돌릴 수 없습니다.';
  if (!ok) return;
  const btn = byId('bBtnDelete');
  btn.disabled = true;
  api.post(`/board/api/dept/posts/${p.id}/delete`)
    .done(() => { toast('게시글을 삭제했습니다'); location.href = pageHref('/board/dept'); })
    .fail(failTo(pageHref('/board/dept')))
    .always(() => { btn.disabled = false; });
});

// ---------- 3. 작성 · 수정 ----------
// 내용은 공지와 같은 에디터, 첨부파일도 공지와 같은 규칙
const boardEditor = makeEditor('bContent');
const boardFiles = makeFilePicker({ pick: 'bBtnPickFiles', input: 'bFiles', info: 'bFilesInfo', list: 'bFileList', error: 'bFilesError' });
const boardFormState = { id: null, initial: { title: '', content: '', files: '' }, saving: false, leaving: false };
const boardFormOpen = () => byId('view-board-form').classList.contains('on');
const boardFormDirty = () => boardFormOpen() && !boardFormState.leaving && !boardFormState.saving &&
  (byId('bTitle').value !== boardFormState.initial.title || boardEditor.html() !== boardFormState.initial.content ||
   fileSig(boardFiles.files) !== boardFormState.initial.files);

function openBoardForm(id) {
  const live = ticket();
  api.get('/board/api/dept/posts/form', id ? { id } : undefined)
    .done(res => { if (live()) fillBoardForm(res); })
    .fail(xhr => { if (live()) formOpenFailed(xhr, id, 'board-dept'); });
}

function fillBoardForm(res) {
  const p = res.post;
  boardFormState.id = p ? p.id : null;
  boardFormState.saving = false;
  byId('bFormTitle').textContent = p ? '글 수정' : '글쓰기';
  byId('bFormCrumb').textContent = p ? '수정' : '글쓰기';
  byId('bFormSub').textContent = `${res.dept} 게시판에 올라갑니다. ${res.dept} 사람만 볼 수 있어요.`;
  byId('bTitle').value = p ? p.title : '';
  boardEditor.set(p ? p.content : '');
  boardFiles.set(p ? p.files : []);
  boardFormState.initial = { title: byId('bTitle').value, content: boardEditor.html(), files: fileSig(boardFiles.files) };
  setSaving('bBtnSave', false, '저장');
  setFieldError('bTitle', 'bTitleError', '');
  setFieldError('bContent', 'bContentError', '');
  updateCount('bTitle', 'bTitleCount', TITLE_MAX);
  updateCount('bContent', 'bContentCount', CONTENT_MAX);
  showSub('view-board-form');
  byId('bTitle').focus();
}

byId('bTitle')?.addEventListener('input', () => { updateCount('bTitle', 'bTitleCount', TITLE_MAX); setFieldError('bTitle', 'bTitleError', ''); });
byId('bContent')?.addEventListener('input', () => { updateCount('bContent', 'bContentCount', CONTENT_MAX); setFieldError('bContent', 'bContentError', ''); });

byId('bForm')?.addEventListener('submit', e => {
  e.preventDefault();
  if (boardFormState.saving) return;
  setFieldError('bTitle', 'bTitleError', '');
  setFieldError('bContent', 'bContentError', '');
  boardFiles.error('');
  const title = byId('bTitle').value.trim();
  const content = boardEditor.html();
  const err = validate(title, textOf(content)); // 공지와 같은 규칙 (서식을 뺀 글자로 검사)
  if (err) {
    const [input, errId] = err.field === 'title' ? ['bTitle', 'bTitleError'] : ['bContent', 'bContentError'];
    setFieldError(input, errId, err.message);
    byId(input).focus();
    return;
  }
  // 부서는 화면에서 보내지 않는다. 서버가 로그인한 사람의 부서로 정한다
  const body = { title, content, files: filesToSend(boardFiles.files) };
  const editing = boardFormState.id;
  boardFormState.saving = true;
  setSaving('bBtnSave', true, '저장');
  (editing ? api.post(`/board/api/dept/posts/${editing}`, body) : api.post('/board/api/dept/posts', body))
    .done(r => {
      toast(editing ? '글을 수정했습니다' : '글을 등록했습니다');
      if (!editing) { boardState.page = 1; boardState.q = ''; }
      location.href = pageHref(`/board/dept/view/${r.id}`);
    })
    .fail(xhr => {
      boardFormState.saving = false;
      setSaving('bBtnSave', false, '저장');
      const msg = apiMessage(xhr);
      if (xhr.status === 413) boardFiles.error(msg); // 파일이 너무 큼
      else if (xhr.status === 404) { toast(msg); boardFormState.leaving = true; location.href = pageHref('/board/dept'); }
      else toast(msg);
    });
});

byId('bBtnCancel')?.addEventListener('click', async () => {
  if (boardFormDirty() && !(await confirmDialog('leaveDialog'))) return;
  boardFormState.leaving = true;
  location.href = boardFormState.id ? pageHref(`/board/dept/view/${boardFormState.id}`) : pageHref('/board/dept');
});


// ---------- 그룹웨어에 내놓는 것 ----------
window.routeNoticeBoard = route;
})();
