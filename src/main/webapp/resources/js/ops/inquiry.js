// 이음 운영자: 비회원 접수 (views/ops/inquiry-guest.jsp). 페이지가 열리면 openInquiryView('guest')를 부른다.
// 소개 사이트 도입 문의(WEB-02)로 들어온 접수를 보고 메일로 답변한다(WEB-03). 회원 접수(장애 문의, OPS-04)는 이번 구현 범위에서 빠졌다.
// 작은 DOM 도구(h, span, small, dlOf, cardOf, inqCell, titleBtn, dataRow, detailTr, emptyTr)는 util.js에 있다.
// 목록은 한 번에 받고, 카드·검색·기간·"답변 없음"으로 좁히는 것은 화면에서 한다. 메뉴 배지(inquiryGuest)는 common.js 의 /badges 가 채운다.
// 접수 내용은 바깥 사람이 쓴 글이라 textContent로만 넣는다 (innerHTML을 쓰지 않는다).

const GUEST_LABEL = { WAIT: '답변 대기', DONE: '답변 완료', FAILED: '발송 실패', SPAM: '스팸' };
const INQ_ANSWER_MAX = 4000;
const INQ_PATH = { guest: '/ops/api/guest-inquiries' };
const inqEnc = encodeURIComponent;

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
// 적던 답변 (접수번호별). 목록을 다시 그려도 적던 글이 지워지지 않게 둔다
const inqDrafts = { guest: {} };

// "최근 7일" = 오늘과 그 앞 6일, 0시부터
function inPeriod(iso, period) {
  if (period === 'all') return true;
  const now = new Date();
  return new Date(iso) >= new Date(now.getFullYear(), now.getMonth(), now.getDate() - (Number(period) - 1));
}

// 발송 실패는 답변 안 한 것으로 치고, 스팸은 치지 않는다
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

// 받은 한 건으로 목록의 같은 건을 바꾼다
function replaceInquiry(kind, q) {
  inqItems[kind] = inqItems[kind].map(x => (x.no === q.no ? q : x));
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

// 적던 답변을 지키는 입력칸
function inqDraftArea(kind, no, initial) {
  const ta = h('textarea', 'inq-text');
  ta.maxLength = INQ_ANSWER_MAX;
  ta.value = inqDrafts[kind][no] ?? initial;
  ta.addEventListener('input', () => { inqDrafts[kind][no] = ta.value; });
  return ta;
}

// ---------- 답변 발송: 답변은 메일로 나간다 ----------
const guestNeedsReply = q => q.state === 'WAIT' || q.state === 'FAILED';

// 한 번에 메일 하나만 보낸다. 보내는 동안 버튼을 막아 두 번 눌러도 두 번 나가지 않게
let sendingNo = null;

// retrySeq === null: 새 답변(answerText)을 보낸다. 아니면 retrySeq번 답변을 다시 보낸다
function deliverGuest(no, retrySeq, answerText) {
  if (sendingNo) return;
  sendingNo = no;
  renderGuest();
  const path = `${INQ_PATH.guest}/${inqEnc(no)}/replies`;
  const req = retrySeq === null ? api.post(path, { body: answerText }) : api.post(`${path}/${retrySeq}/resend`);
  req
    .done(res => {
      replaceInquiry('guest', res.inquiry);
      if (retrySeq === null) delete inqDrafts.guest[no]; // 보냈으면(실패여도 기록에 남는다) 적던 글을 비운다
      IEUM.refreshBadges(); // 메뉴의 할 일 수
      showToast(res.sent ? '답변 메일을 발송했습니다' : `발송 실패: ${res.reason}`);
    })
    .fail(xhr => showToast(apiMessage(xhr, '답변을 발송하지 못했습니다')))
    .always(() => { sendingNo = null; renderGuest(); });
}

function replyItem(q, r) {
  const ok = r.status === 'SENT';
  const li = h('li', '', h('div', 'reply-meta', span(ok ? '발송 성공' : '발송 실패', `status status-${ok ? 'done' : 'failed'}`), span(whenFull(r.at))), h('div', 'reply-text', r.body));
  if (!ok) {
    li.append(h('p', 'reply-fail', `실패 사유: ${r.reason}`));
    // 가장 최근 시도만 다시 보낼 수 있다. 앞선 실패는 그 뒤에 보낸 것으로 대신되었다
    if (r.seq === q.replies.length) {
      const retry = h('button', 'btn', sendingNo ? '발송 중…' : '재발송');
      retry.type = 'button';
      retry.disabled = !!sendingNo;
      retry.addEventListener('click', () => deliverGuest(q.no, r.seq, null));
      li.append(retry);
    }
  }
  return li;
}

function guestDetail(q) {
  const left = cardOf('접수 내용',
    dlOf([['접수번호', q.no], ['접수 시각', whenFull(q.at)], ...(q.company ? [['회사명', q.company]] : []), ['이름', q.name],
      ['수신 이메일', q.email], ...(q.phone ? [['휴대전화', q.phone]] : []), ['제목', q.title], ['내용', text(q.body)]]),
    h('p', 'inq-body inq-hint', '답변은 위 이메일로 발송됩니다. 주소가 틀리면 발송 실패로 기록되니 먼저 확인하세요.'));

  const log = q.replies.length
    ? h('ul', 'reply-log', ...q.replies.map(r => replyItem(q, r)))
    : h('p', 'inq-hint', '아직 보낸 답변이 없습니다.');

  let compose;
  if (q.spam) {
    compose = h('div', 'inq-body', h('p', 'inq-hint', '스팸으로 숨긴 접수라 답변을 발송할 수 없습니다.'));
  } else {
    const hasSent = q.replies.some(r => r.status === 'SENT');
    const sending = sendingNo === q.no;
    const ta = inqDraftArea('guest', q.no, '');
    ta.setAttribute('aria-label', hasSent ? '추가 답변' : '답변');
    ta.placeholder = hasSent ? '추가로 보낼 답변을 입력하세요' : '답변을 입력하세요';
    ta.disabled = !!sendingNo;
    const send = h('button', 'btn btn-primary', sending ? '발송 중…' : hasSent ? '추가 답변 발송' : '답변 발송');
    send.type = 'button';
    send.disabled = !!sendingNo;
    send.addEventListener('click', () => {
      const body = ta.value.trim();
      // 서버도 같은 검사를 다시 한다
      if (!body) { showToast('답변 내용을 입력하세요'); ta.focus(); return; }
      if (body.length > INQ_ANSWER_MAX) { showToast(`답변은 ${INQ_ANSWER_MAX}자까지 쓸 수 있습니다`); return; }
      deliverGuest(q.no, null, body);
    });
    compose = h('div', 'inq-body', ta,
      h('p', 'inq-hint', `발신: ${q.mail.from} · 제목: ${q.mail.subject}`),
      h('p', 'inq-hint', '이미 나간 메일은 고칠 수 없어서, 정정은 추가 답변으로 보냅니다.'),
      h('div', 'inq-actions', send));
  }
  return h('div', 'inq-grid', left, cardOf('답변 발송', h('div', 'inq-body', log), compose));
}

function renderGuest() {
  const all = inqItems.guest; // 서버가 최신순으로 준다
  const n = s => all.filter(q => q.state === s).length;
  setCount('gCntAll', all.filter(q => !q.spam).length);
  setCount('gCntWait', n('WAIT'));
  setCount('gCntFailed', n('FAILED'));
  setCount('gCntDone', n('DONE'));
  setCount('gCntSpam', n('SPAM'));
  markPressed('guestFilters', inq.guest.filter);

  // 스팸은 "전체"에 넣지 않고 자기 카드에서만 보인다
  const f = inq.guest.filter;
  const shown = all.filter(q => (f === 'all' ? !q.spam : q.state === f)
    && passesControls('guest', q, guestNeedsReply(q), q.name, q.company || '', q.email, q.title, q.body, q.no));
  showSearchInfo('guest', shown.length);
  const rows = shown.flatMap(q => {
    const isOpen = inq.guest.open === q.no;
    const tr = dataRow(isOpen, () => { inq.guest.open = isOpen ? null : q.no; renderGuest(); },
      inqCell('접수 시각', span(when(q.at))),
      inqCell('보낸 사람', span(q.company ? `${q.name} · ${q.company}` : q.name), small(q.email)),
      inqCell('제목', titleBtn(q.title, isOpen), small(q.no)),
      inqCell('상태', span(sendingNo === q.no ? '발송 중…' : GUEST_LABEL[q.state], `status status-${q.state.toLowerCase()}`)));
    return isOpen ? [tr, detailTr(4, guestDetail(q))] : [tr];
  });
  document.getElementById('guestList').replaceChildren(...(rows.length ? rows : [emptyTr(4, narrowed('guest') ? '조건에 맞는 접수가 없습니다' : '해당하는 접수가 없습니다')]));
}

// 비회원 접수 화면이 있는 페이지에서만 연결한다
if (document.getElementById('guestList')) {
  wireFilters('guestFilters', 'guest', renderGuest);
  wireSearch('guest', renderGuest);
}
