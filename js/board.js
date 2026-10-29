// 부서 게시판 시안 (목록 · 상세+댓글 · 작성/수정)
// notice.js 다음에 읽는다. 공통 도구($, el, toast, confirmDialog, fmtDate, fmtTime, renderPager,
// showView, me, readonly, READONLY_MSG, daysAgo)는 notice.js 것을 그대로 쓴다.
//
// 공지와 다른 점
// - 보기: 같은 부서 글만. 다른 부서 글은 주소를 직접 쳐도 "없는 글이거나 볼 수 없는 글"로 막는다
//   (실제로는 서버 Service가 로그인한 사람의 부서와 글의 부서를 비교한다)
// - 쓰기: 부서원 누구나. 수정·삭제는 글쓴이 본인만
// - 댓글: 부서원 누구나 등록, 본인 댓글만 삭제 (댓글 수정은 없음), 500자까지

const BOARD_KEY = 'ieum-board-demo-v1';
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
    park: { empNo: '20260001', name: '박유료', dept: '경영지원팀' },
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
    const s = JSON.parse(localStorage.getItem(BOARD_KEY));
    if (s && Array.isArray(s.posts)) return s;
  } catch (e) { /* 깨졌으면 처음 데이터로 */ }
  const s = boardSeed();
  saveBoard(s);
  return s;
}
function saveBoard(s) { try { localStorage.setItem(BOARD_KEY, JSON.stringify(s)); } catch (e) { /* 무시 */ } }

let board = loadBoard();
function resetBoard() { // notice.js의 "데이터 처음으로"가 부른다
  board = boardSeed();
  saveBoard(board);
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
  showView('view-board-list');
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
      wrap.append(el('a', { href: `#board/view/${p.id}`, class: 'notice-link' }, p.title));
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
document.querySelector('.nav-item[data-view="board"]').addEventListener('click', () => {
  boardState.q = ''; boardState.page = 1;
  if (location.hash === '#board') { renderBoardList(); closeMenu(); }
});

// ---------- 2. 상세 + 댓글 ----------
let bCurrentId = null;

function openBoardDetail(id) {
  const p = findPost(id);
  if (!p || !canSeePost(p)) { toast(NOT_FOUND); location.replace('#board'); return; }
  bCurrentId = p.id;
  $('bDetailTitle').textContent = p.title;
  const who = el('span');
  who.append(el('strong', {}, p.writerName));
  const parts = [who, el('span', {}, `${fmtTime(p.createdAt)} 작성`)];
  if (p.updatedAt) parts.push(el('span', {}, `${fmtTime(p.updatedAt)} 수정`));
  $('bDetailMeta').replaceChildren(...parts);
  $('bDetailBody').textContent = p.content;
  $('bOwnerActions').hidden = !isPostOwner(p);
  $('bBtnEdit').setAttribute('href', `#board/edit/${p.id}`);
  $('bCommentText').value = '';
  setFieldError('bCommentText', 'bCommentError', '');
  updateCount('bCommentText', 'bCommentCountNow', COMMENT_MAX);
  renderComments(p);
  showView('view-board-detail');
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
  if (!p || !canSeePost(p)) { toast(NOT_FOUND); location.hash = '#board'; return; }
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
  location.hash = '#board';
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
  if (readonly) { toast(READONLY_MSG()); location.replace('#board'); return; }
  const p = id ? findPost(id) : null;
  if (id && (!p || !canSeePost(p))) { toast(NOT_FOUND); location.replace('#board'); return; }
  if (p && !isPostOwner(p)) { toast('이 글을 수정할 권한이 없습니다'); location.replace(`#board/view/${p.id}`); return; }

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
  showView('view-board-form');
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
      if (!p || !isPostOwner(p) || readonly) { boardFormState.saving = false; toast('수정할 권한이 없습니다'); location.hash = '#board'; return; }
      Object.assign(p, { title, content, updatedAt: now });
    } else {
      if (readonly) { boardFormState.saving = false; toast(READONLY_MSG()); location.hash = '#board'; return; }
      id = board.nextId++;
      // 부서는 화면에서 받지 않고 로그인한 사람의 부서로 정한다 (실제로도 서버가 정함)
      board.posts.push({ id, dept: me.dept, title, content, writerEmpNo: me.empNo, writerName: me.name, createdAt: now, updatedAt: null, comments: [] });
      boardState.page = 1; boardState.q = '';
    }
    saveBoard(board);
    toast(boardFormState.id ? '글을 수정했습니다' : '글을 등록했습니다');
    location.hash = `#board/view/${id}`;
  }, 500);
});

$('bBtnCancel').addEventListener('click', async () => {
  if (boardFormDirty() && !(await confirmDialog('leaveDialog'))) return;
  boardFormState.leaving = true;
  location.hash = boardFormState.id ? `#board/view/${boardFormState.id}` : '#board';
});
