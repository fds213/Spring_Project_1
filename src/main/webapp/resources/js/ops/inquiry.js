// 이음 운영자: 비회원 접수 (views/ops/inquiry-guest.jsp). 페이지가 열리면 openInquiryView('guest')를 부른다.
// 소개 사이트 도입 문의(WEB-02)로 들어온 접수를 본다. 회원 접수(장애 문의, OPS-04)는 이번 구현 범위에서 빠졌다.
// TODO(WEB-03): 답변 발송. 메일 기능이 들어오면 guestDetail에 입력칸과 발송 결과를 붙인다(발송 화면 코드는 0c694fd의 이 파일에 있다).
// 작은 DOM 도구(h, span, small, dlOf, cardOf, inqCell, titleBtn, dataRow, detailTr, emptyTr)는 util.js에 있다.
// 목록은 한 번에 받고, 카드·검색·기간·"답변 없음"으로 좁히는 것은 화면에서 한다. 메뉴 배지(inquiryGuest)는 common.js 의 /badges 가 채운다.
// 접수 내용은 바깥 사람이 쓴 글이라 textContent로만 넣는다 (innerHTML을 쓰지 않는다).

// 상태는 공통 코드 INQUIRY_STATUS다. 지금은 답변이 없어 IQ_RECEIVED만 쌓인다
const GUEST_STATE = {
  IQ_RECEIVED: { label: '답변 대기', tone: 'wait' },
  IQ_PROCESSING: { label: '확인 중', tone: 'wait' },
  IQ_ANSWERED: { label: '답변 완료', tone: 'done' },
  IQ_CLOSED: { label: '종료', tone: 'done' },
};
const INQ_PATH = { guest: '/ops/api/guest-inquiries' };

const setCount = (id, n) => { document.getElementById(id).textContent = `${n}건`; };
const markPressed = (boxId, filter) =>
  document.querySelectorAll(`#${boxId} .card-filter`).forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === filter));

// 답변 없이 이보다 오래된 접수는 "답변 없음" 거르기에 걸린다
const STALE_HOURS = 24;
const PERIOD_LABEL = { all: '전체 기간', 1: '오늘', 7: '최근 7일', 30: '최근 30일' };

// 켠 카드, 펼친 줄(한 번에 하나), 검색어, 기간, "답변 늦음" 스위치. 카드·검색·기간·스위치는 같은 목록을 함께 좁힌다
const freshView = () => ({ filter: 'all', open: null, query: '', period: 'all', stale: false });
const inq = { guest: freshView() };
// 서버에서 받은 목록
const inqItems = { guest: [] };

// "최근 7일" = 오늘과 그 앞 6일, 0시부터
function inPeriod(iso, period) {
  if (period === 'all') return true;
  const now = new Date();
  return new Date(iso) >= new Date(now.getFullYear(), now.getMonth(), now.getDate() - (Number(period) - 1));
}

const isStale = (q, unanswered) => unanswered && Date.now() - new Date(q.at) > STALE_HOURS * 3600000;

// 기간 + 답변 늦음 스위치 + 검색어
function passesControls(kind, q, unanswered, ...searchFields) {
  const c = inq[kind];
  return inPeriod(q.at, c.period) && (!c.stale || isStale(q, unanswered)) && matchesQuery(c.query, ...searchFields);
}

// 적은 낱말이 모두 어딘가에 있어야 하고(AND), 낱말의 일부여도 된다: "홍길"로 "홍길동"을 찾는다
function matchesQuery(query, ...fields) {
  const hay = fields.join(' ').toLowerCase();
  return query.toLowerCase().split(/\s+/).filter(Boolean).every(w => hay.includes(w));
}

// 무엇이 목록을 좁히고 있는지 알려 준다. 빈 목록이 수수께끼가 되지 않게
const narrowed = kind => {
  const c = inq[kind];
  return !!c.query.trim() || c.period !== 'all' || c.stale;
};

function showSearchInfo(kind, shownCount) {
  const c = inq[kind];
  const parts = [];
  if (c.query.trim()) parts.push(`"${c.query.trim()}"`);
  if (c.period !== 'all') parts.push(PERIOD_LABEL[c.period]);
  if (c.stale) parts.push(`${STALE_HOURS}시간 넘게 답변 없음`);
  document.getElementById(`${kind}SearchInfo`).textContent = parts.length ? `${parts.join(' · ')} 결과 ${shownCount}건` : '';
}

// 화면을 열 때 (JSP 페이지가 열리면 부른다). 필터·검색·펼친 줄을 처음으로 돌린다
function openInquiryView(kind) {
  inq[kind] = freshView();
  document.getElementById(`${kind}Search`).value = '';
  document.getElementById(`${kind}Period`).value = 'all';
  document.getElementById(`${kind}Stale`).checked = false;
  renderGuest(); // 전에 받은 목록으로 먼저 그리고, 새 목록이 오면 바꾼다
  loadInquiries(kind);
}

// 목록을 다시 받아 그린다. 필터·검색·펼친 줄은 그대로
function loadInquiries(kind) {
  api.get(INQ_PATH[kind])
    .done(items => { inqItems[kind] = items; renderGuest(); })
    .fail(xhr => showToast(apiMessage(xhr, '접수 목록을 불러오지 못했습니다')));
}

function wireFilters(boxId, kind, render) {
  document.querySelectorAll(`#${boxId} .card-filter`).forEach(b => b.addEventListener('click', () => {
    // 검색어는 그대로 둔다: 카드와 검색은 함께 목록을 좁힌다
    inq[kind] = { ...inq[kind], filter: b.dataset.filter, open: null };
    render();
  }));
}

function wireSearch(kind, render) {
  const set = patch => { inq[kind] = { ...inq[kind], ...patch, open: null }; render(); };
  document.getElementById(`${kind}Search`).addEventListener('input', e => set({ query: e.target.value }));
  document.getElementById(`${kind}Period`).addEventListener('change', e => set({ period: e.target.value }));
  document.getElementById(`${kind}Stale`).addEventListener('change', e => set({ stale: e.target.checked }));
}

const guestNeedsReply = q => q.state === 'IQ_RECEIVED';

function guestDetail(q) {
  const left = cardOf('접수 내용',
    dlOf([['접수번호', q.no], ['접수 시각', whenFull(q.at)], ...(q.company ? [['회사명', q.company]] : []), ['이름', q.name],
      ['이메일', q.email], ...(q.phone ? [['휴대전화', q.phone]] : []), ['제목', q.title], ['내용', text(q.body)]]));
  const right = cardOf('답변', h('div', 'inq-body',
    h('p', 'inq-hint', '답변 메일은 메일 기능을 붙인 뒤에 보낼 수 있습니다. 그동안은 접수 내용의 이메일로 직접 연락해 주세요.')));
  return h('div', 'inq-grid', left, right);
}

function renderGuest() {
  const all = inqItems.guest; // 서버가 최신순으로 준다
  const n = s => all.filter(q => q.state === s).length;
  setCount('gCntAll', all.length);
  setCount('gCntWait', n('IQ_RECEIVED'));
  setCount('gCntDone', n('IQ_ANSWERED'));
  markPressed('guestFilters', inq.guest.filter);

  const f = inq.guest.filter;
  const shown = all.filter(q => (f === 'all' || q.state === f)
    && passesControls('guest', q, guestNeedsReply(q), q.name, q.company || '', q.email, q.title, q.body, q.no));
  showSearchInfo('guest', shown.length);
  const rows = shown.flatMap(q => {
    const isOpen = inq.guest.open === q.no;
    const tr = dataRow(isOpen, () => { inq.guest.open = isOpen ? null : q.no; renderGuest(); },
      inqCell('접수 시각', span(when(q.at))),
      inqCell('보낸 사람', span(q.company ? `${q.name} · ${q.company}` : q.name), small(q.email)),
      inqCell('제목', titleBtn(q.title, isOpen), small(q.no)),
      inqCell('상태', span(GUEST_STATE[q.state].label, `status status-${GUEST_STATE[q.state].tone}`)));
    return isOpen ? [tr, detailTr(4, guestDetail(q))] : [tr];
  });
  document.getElementById('guestList').replaceChildren(...(rows.length ? rows : [emptyTr(4, narrowed('guest') ? '조건에 맞는 접수가 없습니다' : '해당하는 접수가 없습니다')]));
}

// 비회원 접수 화면이 있는 페이지에서만 연결한다
if (document.getElementById('guestList')) {
  wireFilters('guestFilters', 'guest', renderGuest);
  wireSearch('guest', renderGuest);
}
