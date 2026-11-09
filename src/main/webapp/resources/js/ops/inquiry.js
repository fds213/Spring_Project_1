// 이음 운영자: 비회원 접수 (views/ops/inquiry-guest.jsp)와 회원 접수 (views/ops/inquiry-member.jsp). 페이지가 열리면 openInquiryView('guest' 또는 'member')를 부른다.
// 비회원 접수는 소개 사이트 도입 문의(WEB-02)를 보고 메일로 답변한다(WEB-03). 회원 접수는 업체 직원의 이음 장애 문의(OPS-04 최소판)에 답변하고, 답변은 직원의 '내가 보낸 문의'에 보인다.
// 작은 DOM 도구(h, span, small, dlOf, cardOf, inqCell, titleBtn, dataRow, detailTr, emptyTr)는 util.js에 있다.
// 목록은 한 번에 받고, 카드·검색·기간·"답변 없음"으로 좁히는 것은 화면에서 한다. 메뉴 배지(inquiryGuest)는 common.js 의 /badges 가 채운다.
// 접수 내용은 바깥 사람이 쓴 글이라 textContent로만 넣는다 (innerHTML을 쓰지 않는다).

// 상태는 공통 코드 INQUIRY_STATUS다. 답변 메일이 나가면 IQ_ANSWERED가 된다
const GUEST_STATE = {
  IQ_RECEIVED: { label: '답변 대기', tone: 'wait' },
  IQ_PROCESSING: { label: '확인 중', tone: 'wait' },
  IQ_ANSWERED: { label: '답변 완료', tone: 'done' },
  IQ_CLOSED: { label: '종료', tone: 'done' },
};
// 답변 한 건의 발송 상태는 공통 코드 MAIL_STATUS다. stale은 결과를 남기지 못한 채 멈춘 발송이다(메일이 나갔는지 알 수 없다)
const REPLY_STATE = {
  MAIL_PENDING: { label: '발송 중', tone: 'wait' },
  MAIL_SENT: { label: '발송 성공', tone: 'done' },
  MAIL_FAILED: { label: '발송 실패', tone: 'failed' },
};
const REPLY_STALE = { label: '결과 확인 불가', tone: 'failed' };
const INQ_ANSWER_MAX = 4000; // 서버(OpsReplyService)와 같은 값
const INQ_PATH = { guest: '/ops/api/guest-inquiries', member: '/ops/api/member-inquiries' };

const setCount = (id, n) => { document.getElementById(id).textContent = `${n}건`; };
const markPressed = (boxId, filter) =>
  document.querySelectorAll(`#${boxId} .card-filter`).forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === filter));

// 답변 없이 이보다 오래된 접수는 "답변 없음" 거르기에 걸린다
const STALE_HOURS = 24;
const PERIOD_LABEL = { all: '전체 기간', 1: '오늘', 7: '최근 7일', 30: '최근 30일' };

// 켠 카드, 펼친 줄(한 번에 하나), 검색어, 기간, "답변 늦음" 스위치. 카드·검색·기간·스위치는 같은 목록을 함께 좁힌다
const freshView = () => ({ filter: 'all', open: null, query: '', period: 'all', stale: false });
const inq = { guest: freshView(), member: freshView() };
// 서버에서 받은 목록
const inqItems = { guest: [], member: [] };
// 적던 답변 (접수번호별). 목록을 다시 그려도 적던 글이 지워지지 않게 둔다
const inqDrafts = { guest: {}, member: {} };
// 종류별로 목록을 그리는 함수
const inqRender = { guest: renderGuest, member: renderMember };

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
  inqRender[kind](); // 전에 받은 목록으로 먼저 그리고, 새 목록이 오면 바꾼다
  loadInquiries(kind);
}

// 목록을 다시 받아 그린다. 필터·검색·펼친 줄은 그대로
function loadInquiries(kind) {
  api.get(INQ_PATH[kind])
    .done(items => { inqItems[kind] = items; inqRender[kind](); })
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

const guestNeedsReply = q => q.state === 'IQ_RECEIVED';

// ---------- 답변 발송: 답변은 메일로 나간다 ----------
const lastReply = q => q.replies[q.replies.length - 1];
const replyState = r => (r.stale ? REPLY_STALE : REPLY_STATE[r.status]);
// 지금 보내는 중인 답변이 있나. 멈춘 발송은 치지 않는다
const replyInFlight = r => !!r && r.status === 'MAIL_PENDING' && !r.stale;

// 한 번에 메일 하나만 보낸다. 보내는 동안 버튼을 막는다. 그래도 겹친 요청은 서버가 409로 거절해 메일은 한 통이다
let sendingNo = null;

// retryId === null: 새 답변(answerText)을 보낸다. 아니면 그 답변을 같은 발송 기록으로 다시 보낸다
function deliverGuest(q, retryId, answerText) {
  if (sendingNo) return;
  sendingNo = q.no;
  renderGuest();
  const path = `${INQ_PATH.guest}/${encodeURIComponent(q.no)}/replies`;
  const last = lastReply(q);
  const req = retryId === null
    ? api.post(path, { body: answerText, lastReplyId: last ? last.id : null }) // 내가 본 마지막 답변. 그사이 다른 답변이 있으면 서버가 거절한다
    : api.post(`${path}/${encodeURIComponent(retryId)}/resend`);
  req
    .done(res => {
      replaceInquiry('guest', res.inquiry);
      if (retryId === null) delete inqDrafts.guest[q.no]; // 답변은 저장됐다(실패여도 기록에 남아 다시 보낼 수 있다). 적던 글을 비운다
      if (window.IEUM && IEUM.refreshBadges) IEUM.refreshBadges(); // 메뉴의 할 일 수
      showToast(res.sent ? '답변 메일을 보냈습니다' : `발송 실패: ${res.reason}`);
    })
    .fail(xhr => {
      showToast(apiMessage(xhr, '답변을 보내지 못했습니다'));
      if (xhr.status === 404 || xhr.status === 409) loadInquiries('guest'); // 다른 운영자가 먼저 답했으면 그 답변을 보여 준다. 적던 글은 그대로 둔다
    })
    .always(() => { sendingNo = null; renderGuest(); });
}

function replyItem(q, r) {
  const state = replyState(r);
  const li = h('li', '', h('div', 'reply-meta', span(state.label, `status status-${state.tone}`), span(whenFull(r.at))), h('div', 'reply-text', r.body));
  if (r.status === 'MAIL_SENT') return li;
  // 목록을 받을 때 보내는 중이었던 답변이다(이 화면이 보내는 동안에는 응답이 와서 바뀐다). 결과는 새로 받아야 보인다
  if (replyInFlight(r)) { li.append(h('p', 'inq-hint', '보내는 중입니다. 잠시 뒤 화면을 새로 고치면 결과가 보입니다.')); return li; }
  li.append(h('p', 'reply-fail', r.stale ? '서버가 발송 결과를 남기지 못했습니다. 이미 받았을 수 있으니 확인한 뒤 다시 보내 주세요.' : `실패 사유: ${r.reason}`));
  // 가장 최근 답변만 다시 보낼 수 있다. 앞선 실패는 그 뒤에 보낸 답변으로 대신되었다
  if (r === lastReply(q)) {
    const retry = h('button', 'btn', sendingNo === q.no ? '보내는 중…' : '다시 보내기');
    retry.type = 'button';
    retry.disabled = !!sendingNo;
    retry.addEventListener('click', () => deliverGuest(q, r.id, null));
    li.append(retry);
  }
  return li;
}

function guestDetail(q) {
  const left = cardOf('접수 내용',
    dlOf([['접수번호', q.no], ['접수 시각', whenFull(q.at)], ...(q.company ? [['회사명', q.company]] : []), ['이름', q.name || '-'],
      ['이메일', q.email], ...(q.phone ? [['휴대전화', q.phone]] : []), ['제목', q.title || '-'], ['내용', text(q.body)]]),
    h('p', 'inq-body inq-hint', '답변은 위 이메일로 보냅니다. 발송 성공은 메일 서버가 받았다는 뜻이라, 없는 주소면 나중에 반송될 수 있습니다.'));

  const log = q.replies.length
    ? h('ul', 'reply-log', ...q.replies.map(r => replyItem(q, r)))
    : h('p', 'inq-hint', '아직 보낸 답변이 없습니다.');

  const hasSent = q.replies.some(r => r.status === 'MAIL_SENT');
  const busy = !!sendingNo || replyInFlight(lastReply(q));
  const ta = h('textarea', 'inq-text');
  ta.maxLength = INQ_ANSWER_MAX;
  ta.value = inqDrafts.guest[q.no] ?? '';
  ta.setAttribute('aria-label', hasSent ? '추가 답변' : '답변');
  ta.placeholder = hasSent ? '추가로 보낼 답변을 입력하세요' : '답변을 입력하세요';
  ta.disabled = busy;
  ta.addEventListener('input', () => { inqDrafts.guest[q.no] = ta.value; });
  const send = h('button', 'btn btn-primary', sendingNo === q.no ? '보내는 중…' : hasSent ? '추가 답변 보내기' : '답변 보내기');
  send.type = 'button';
  send.disabled = busy;
  send.addEventListener('click', () => {
    const body = ta.value.trim();
    // 서버도 같은 검사를 다시 한다
    if (!body) { showToast('답변 내용을 입력하세요'); ta.focus(); return; }
    deliverGuest(q, null, body);
  });
  const compose = h('div', 'inq-body', ta,
    h('p', 'inq-hint', '이미 나간 메일은 고칠 수 없어서, 정정은 추가 답변으로 보냅니다.'),
    h('div', 'inq-actions', send));
  return h('div', 'inq-grid', left, cardOf('답변', h('div', 'inq-body', log), compose));
}

// 목록의 상태 칸: 마지막 답변이 나가지 못했으면 그것을 먼저 보인다(답변 대기로만 보이면 실패를 놓친다)
function guestRowState(q) {
  if (sendingNo === q.no) return REPLY_STATE.MAIL_PENDING;
  const last = lastReply(q);
  return last && last.status !== 'MAIL_SENT' ? replyState(last) : GUEST_STATE[q.state];
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
    const state = guestRowState(q);
    const tr = dataRow(isOpen, () => { inq.guest.open = isOpen ? null : q.no; renderGuest(); },
      inqCell('접수 시각', span(when(q.at))),
      // 이름 칸은 11/2 스키마 패치 전에 받은 문의에는 비어 있다(REQUESTER_NAME 은 나중에 더한 칸)
      inqCell('보낸 사람', span([q.name, q.company].filter(Boolean).join(' · ') || '-'), small(q.email)),
      inqCell('제목', titleBtn(q.title || '(제목 없음)', isOpen), small(q.no)),
      inqCell('상태', span(state.label, `status status-${state.tone}`)));
    return isOpen ? [tr, detailTr(4, guestDetail(q))] : [tr];
  });
  document.getElementById('guestList').replaceChildren(...(rows.length ? rows : [emptyTr(4, narrowed('guest') ? '조건에 맞는 접수가 없습니다' : '해당하는 접수가 없습니다')]));
}

// ---------- 회원 접수: 업체 직원이 보낸 이음 장애 문의(OPS-04 최소판). 메일을 보내지 않는다 ----------
// 상태는 공통 코드 SUPPORT_STATUS다. 답변을 달면 SUP_PROCESSING이 된다
const MEMBER_STATE = {
  SUP_RECEIVED: { label: '답변 대기', tone: 'wait' },
  SUP_PROCESSING: { label: '답변 완료', tone: 'done' },
};
// 이름표에 없는 상태가 와도 목록 전체가 멈추지 않게 받은 값을 그대로 보인다
const memberState = q => MEMBER_STATE[q.state] || { label: q.state, tone: 'wait' };
const memberNeedsReply = q => q.state === 'SUP_RECEIVED';

// 저장하는 동안 버튼을 막아 두 번 눌러도 답변이 하나만 들어가게 한다
let memberSendingNo = null;

function sendMemberReply(q, body) {
  if (memberSendingNo) return;
  memberSendingNo = q.no;
  renderMember();
  api.post(`${INQ_PATH.member}/${encodeURIComponent(q.no)}/replies`, { body })
    .done(updated => {
      replaceInquiry('member', updated);
      delete inqDrafts.member[q.no];
      showToast('답변을 등록했습니다');
    })
    .fail(xhr => {
      showToast(apiMessage(xhr, '답변을 등록하지 못했습니다'));
      if (xhr.status === 404) loadInquiries('member'); // 적던 글은 그대로 둔다
    })
    .always(() => { memberSendingNo = null; renderMember(); });
}

// 고치는 중인 답변 번호(한 번에 하나)와 적던 글. 목록을 다시 그려도 적던 글이 지워지지 않게 둔다
let memberEditingId = null;
let memberEditDraft = '';

function saveMemberEdit(q, r, body) {
  if (memberSendingNo) return;
  memberSendingNo = q.no;
  renderMember();
  api.post(`${INQ_PATH.member}/${encodeURIComponent(q.no)}/replies/${encodeURIComponent(r.id)}`, { body })
    .done(updated => {
      replaceInquiry('member', updated);
      memberEditingId = null;
      showToast('답변을 고쳤습니다');
    })
    .fail(xhr => {
      showToast(apiMessage(xhr, '답변을 고치지 못했습니다'));
      if (xhr.status === 404) loadInquiries('member'); // 적던 글은 그대로 둔다
    })
    .always(() => { memberSendingNo = null; renderMember(); });
}

function memberBtn(label, cls, onClick) {
  const b = h('button', cls, label);
  b.type = 'button';
  b.disabled = !!memberSendingNo;
  b.addEventListener('click', onClick);
  return b;
}

// 답변 한 건: 평소에는 내용과 수정 버튼, 고치는 중이면 입력 칸과 저장·취소 버튼
function memberReplyItem(q, r) {
  const meta = h('div', 'reply-meta', span(whenFull(r.at)), r.editedAt ? span('수정됨') : null);
  if (memberEditingId !== r.id) {
    return h('li', '', meta, h('div', 'reply-text', r.body),
      h('div', 'inq-actions', memberBtn('수정', 'btn', () => { memberEditingId = r.id; memberEditDraft = r.body; renderMember(); })));
  }
  const ta = h('textarea', 'inq-text');
  ta.maxLength = INQ_ANSWER_MAX;
  ta.value = memberEditDraft;
  ta.disabled = !!memberSendingNo;
  ta.setAttribute('aria-label', '답변 고치기');
  ta.addEventListener('input', () => { memberEditDraft = ta.value; });
  return h('li', '', meta, ta, h('div', 'inq-actions',
    memberBtn(memberSendingNo === q.no ? '저장하는 중…' : '저장', 'btn btn-primary', () => {
      const body = ta.value.trim();
      // 서버도 같은 검사를 다시 한다
      if (!body) { showToast('답변 내용을 입력하세요'); ta.focus(); return; }
      saveMemberEdit(q, r, body);
    }),
    memberBtn('취소', 'btn', () => { memberEditingId = null; renderMember(); })));
}

function memberDetail(q) {
  const left = cardOf('문의 내용',
    dlOf([['접수번호', q.no], ['접수 시각', whenFull(q.at)], ['업체', `${q.company} (${q.companyCode})`], ['보낸 사람', q.name],
      ['제목', q.title], ['내용', text(q.body)]]));

  const log = q.replies.length
    ? h('ul', 'reply-log', ...q.replies.map(r => memberReplyItem(q, r)))
    : h('p', 'inq-hint', '아직 등록한 답변이 없습니다.');

  const busy = !!memberSendingNo;
  const ta = h('textarea', 'inq-text');
  ta.maxLength = INQ_ANSWER_MAX;
  ta.value = inqDrafts.member[q.no] ?? '';
  ta.setAttribute('aria-label', q.replies.length ? '추가 답변' : '답변');
  ta.placeholder = q.replies.length ? '추가로 남길 답변을 입력하세요' : '답변을 입력하세요';
  ta.disabled = busy;
  ta.addEventListener('input', () => { inqDrafts.member[q.no] = ta.value; });
  const send = h('button', 'btn btn-primary', memberSendingNo === q.no ? '등록하는 중…' : q.replies.length ? '추가 답변 등록' : '답변 등록');
  send.type = 'button';
  send.disabled = busy;
  send.addEventListener('click', () => {
    const body = ta.value.trim();
    // 서버도 같은 검사를 다시 한다
    if (!body) { showToast('답변 내용을 입력하세요'); ta.focus(); return; }
    sendMemberReply(q, body);
  });
  const compose = h('div', 'inq-body', ta,
    h('p', 'inq-hint', '답변은 문의한 직원의 내가 보낸 문의 화면에 바로 보입니다. 고친 답변에는 수정됨이 함께 보입니다.'),
    h('div', 'inq-actions', send));
  return h('div', 'inq-grid', left, cardOf('답변', h('div', 'inq-body', log), compose));
}

function renderMember() {
  const all = inqItems.member; // 서버가 최신순으로 준다
  const n = s => all.filter(q => q.state === s).length;
  setCount('mCntAll', all.length);
  setCount('mCntWait', n('SUP_RECEIVED'));
  setCount('mCntDone', n('SUP_PROCESSING'));
  markPressed('memberFilters', inq.member.filter);

  const f = inq.member.filter;
  const shown = all.filter(q => (f === 'all' || q.state === f)
    && passesControls('member', q, memberNeedsReply(q), q.name, q.company, q.companyCode, q.title, q.body, q.no));
  showSearchInfo('member', shown.length);
  const rows = shown.flatMap(q => {
    const isOpen = inq.member.open === q.no;
    const state = memberState(q);
    const tr = dataRow(isOpen, () => { inq.member.open = isOpen ? null : q.no; memberEditingId = null; renderMember(); },
      inqCell('접수 시각', span(when(q.at))),
      inqCell('보낸 사람', span(q.name), small(q.company)),
      inqCell('제목', titleBtn(q.title, isOpen), small(q.no)),
      inqCell('상태', span(state.label, `status status-${state.tone}`)));
    return isOpen ? [tr, detailTr(4, memberDetail(q))] : [tr];
  });
  document.getElementById('memberList').replaceChildren(...(rows.length ? rows : [emptyTr(4, narrowed('member') ? '조건에 맞는 접수가 없습니다' : '해당하는 접수가 없습니다')]));
}

// 접수 화면이 있는 페이지에서만 연결한다
if (document.getElementById('guestList')) {
  wireFilters('guestFilters', 'guest', renderGuest);
  wireSearch('guest', renderGuest);
}
if (document.getElementById('memberList')) {
  wireFilters('memberFilters', 'member', renderMember);
  wireSearch('member', renderMember);
}
