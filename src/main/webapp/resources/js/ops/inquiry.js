// 이음 운영자: 장애/문의 접수 관리 (비회원 접수 / 회원 접수. 조각 views/ops/inquiry-guest.html, inquiry-company.html).
// 업체 관리는 ops/company.js. 페이지가 열리면 openInquiryView('guest')를 부른다. 회원 접수(장애 문의, OPS-04)는 이번 범위에서 빠져 화면이 없다.
// 작은 DOM 도구(h, span, small, dlOf, cardOf, inqCell, titleBtn, dataRow, detailTr, emptyTr)는 util.js에 있다.
// 서버 API (운영자만. 시안은 demo/ops.js가 답한다):
//   GET  /ops/api/inquiry-counts                          → { guest, company } 사이드 메뉴 배지 (운영자가 할 일 수)
//   GET  /ops/api/guest-inquiries                         → 비회원 접수 전체, 최신순
//   POST /ops/api/guest-inquiries/{no}/replies              { body } → { sent, reason, inquiry } 답변 메일 발송
//   POST /ops/api/guest-inquiries/{no}/replies/{seq}/resend          → { sent, reason, inquiry } 실패한 가장 최근 답변 재발송
//        메일이 실패해도 요청은 200이다 (sent: false, reason). 스팸 접수·최근 것이 아닌 답변은 409
//   GET  /ops/api/company-inquiries                       → 회원 접수 전체, 최신순
//   POST /ops/api/company-inquiries/{no}/reply              { body, renotify } → { first, notified, inquiry }
//   POST /ops/api/company-inquiries/{no}/notice/read                 → { inquiry } (시연 전용 "읽음으로 표시". 실제로는 없다)
// 비회원 한 건: { no, type: INCIDENT|INQUIRY, at, name, email, company, phone, title, body, spam,
//                 state: WAIT|DONE|FAILED|SPAM, replies: [{ seq, body, subject, status: SENT|FAILED, at, reason }],
//                 mail: { from, subject } }   (from은 서버의 고정 발신 주소, subject는 새 답변 메일의 제목)
// 회원 한 건:   { no, type, at, company, companyCode, writer, coAdmins, title, body,
//                 reply: { body, savedAt, editedAt } | null, notice: { summary, link, createdAt, readAt } | null }
// 목록은 한 번에 받고, 탭·카드·검색·기간·"답변 없음"으로 좁히는 것은 화면에서 한다.
// 접수 내용은 바깥 사람이 쓴 글이라 textContent로만 넣는다 (innerHTML을 쓰지 않는다).

// 모든 접수는 장애 접수 아니면 문의 접수다. 화면마다 두 탭으로 나눠 본다
const TYPE_LABEL = { INCIDENT: '장애 접수', INQUIRY: '문의 접수' };
// 목록 줄의 짧은 표시 (펼치기 전에도 유형이 보이게)
const TYPE_SHORT = { INCIDENT: '장애', INQUIRY: '문의' };
const GUEST_LABEL = { WAIT: '답변 대기', DONE: '답변 완료', FAILED: '발송 실패', SPAM: '스팸' };
const COMPANY_LABEL = { WAIT: '답변 대기', DONE: '답변 완료' };
const INQ_ANSWER_MAX = 4000;
const INQ_PATH = { guest: '/ops/api/guest-inquiries', company: '/ops/api/company-inquiries' };
const inqEnc = encodeURIComponent;

// --- 작은 DOM 도구 (textContent만 쓴다) ---

// 제목 앞에 유형 표시. 색만이 아니라 글자로 적어서 캡처·흑백·화면 낭독기에서도 같게 읽힌다
function titleLine(q, isOpen) {
  return h('div', 'title-line', span(TYPE_SHORT[q.type], `type-pill type-${q.type.toLowerCase()}`), titleBtn(q.title, isOpen));
}

const setCount = (id, n) => { document.getElementById(id).textContent = `${n}건`; };
const markPressed = (boxId, filter) =>
  document.querySelectorAll(`#${boxId} .card-filter`).forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === filter));

// 답변 없이 이보다 오래된 접수는 "답변 없음" 거르기에 걸린다
const STALE_HOURS = 24;
const PERIOD_LABEL = { all: '전체 기간', 1: '오늘', 7: '최근 7일', 30: '최근 30일' };

// 화면마다: 켠 카드, 펼친 줄(한 번에 하나), 검색어, 기간, "답변 늦음" 스위치. 카드·검색·기간·스위치는 같은 목록을 함께 좁힌다.
// type은 탭(장애 접수 / 문의 접수)이다. 탭이 먼저라 카드와 그 숫자는 그 탭만 센다.
// 장애 접수가 보통 더 급해서 장애 접수 탭으로 연다.
const freshView = () => ({ type: 'INCIDENT', filter: 'all', open: null, query: '', period: 'all', stale: false });
const inq = { guest: freshView(), company: freshView() };
// 서버에서 받은 목록 (화면마다)
const inqItems = { guest: [], company: [] };
// 적던 답변 (접수번호별). 다른 탭의 변경으로 목록을 다시 그려도 적던 글이 지워지지 않게 둔다
const inqDrafts = { guest: {}, company: {} };

// "최근 7일" = 오늘과 그 앞 6일, 0시부터 (운영 데이터의 일별 그래프와 같은 7일)
function inPeriod(iso, period) {
  if (period === 'all') return true;
  const now = new Date();
  return new Date(iso) >= new Date(now.getFullYear(), now.getMonth(), now.getDate() - (Number(period) - 1));
}

// unanswered는 화면마다 정한다 (발송 실패는 답변 안 한 것으로 치고, 스팸은 치지 않는다)
const isStale = (q, unanswered) => unanswered && Date.now() - new Date(q.at) > STALE_HOURS * 3600000;

// 두 화면이 같이 쓰는 좁히기: 기간 + 답변 늦음 스위치 + 검색어
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

const renderInquiry = kind => (kind === 'guest' ? renderGuest() : renderCompany());

// 화면을 열 때 (JSP 페이지가 열리면 부른다). 필터·검색·펼친 줄을 처음으로 돌린다
function openInquiryView(kind) {
  inq[kind] = freshView();
  document.getElementById(`${kind}Search`).value = '';
  document.getElementById(`${kind}Period`).value = 'all';
  document.getElementById(`${kind}Stale`).checked = false;
  renderInquiry(kind); // 전에 받은 목록으로 먼저 그리고, 새 목록이 오면 바꾼다
  loadInquiries(kind);
}

// 목록을 다시 받아 그린다. 필터·검색·펼친 줄은 그대로 (다른 탭의 변경을 반영할 때도 이것을 부른다)
function loadInquiries(kind) {
  api.get(INQ_PATH[kind])
    .done(items => { inqItems[kind] = items; renderInquiry(kind); })
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

// 탭마다 아직 운영자가 할 일이 남은 접수 수를 붙인다. 보고 있지 않은 탭에 일이 숨지 않게.
// full은 그 화면의 모든 접수(두 유형 다), needsReply는 "아직 할 일"을 정한다
function renderTabs(kind, full, needsReply) {
  for (const t of Object.keys(TYPE_LABEL)) {
    const n = full.filter(q => q.type === t && needsReply(q)).length;
    const badge = document.getElementById(`${kind}Tab${t}`);
    badge.textContent = n;
    badge.hidden = n === 0;
    const tab = badge.parentElement;
    tab.setAttribute('aria-selected', inq[kind].type === t);
    tab.tabIndex = inq[kind].type === t ? 0 : -1; // 탭 멈춤은 하나, 화살표 키로 탭 사이를 옮긴다 (wireTabs)
  }
}

function wireTabs(kind, render) {
  const tabs = [...document.querySelectorAll(`#${kind}Tabs .type-tab`)];
  const select = t => { inq[kind] = { ...inq[kind], type: t, filter: 'all', open: null }; render(); };
  tabs.forEach((tab, i) => {
    tab.addEventListener('click', () => select(tab.dataset.type));
    tab.addEventListener('keydown', e => {
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
      const next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
      select(next.dataset.type);
      next.focus();
    });
  });
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

// =====================================================================
// 비회원 접수: 답변은 메일로 나간다
// =====================================================================

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
      renderInquiryBadges();
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
    dlOf([['접수번호', q.no], ['유형', TYPE_LABEL[q.type]], ['접수 시각', whenFull(q.at)], ...(q.company ? [['회사명', q.company]] : []), ['이름', q.name],
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
  const full = inqItems.guest; // 서버가 최신순으로 준다
  renderTabs('guest', full, guestNeedsReply);
  // 아래(카드, 숫자, 목록)는 열려 있는 탭만 다룬다
  const all = full.filter(q => q.type === inq.guest.type);
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
      inqCell('제목', titleLine(q, isOpen), small(q.no)),
      inqCell('상태', span(sendingNo === q.no ? '발송 중…' : GUEST_LABEL[q.state], `status status-${q.state.toLowerCase()}`)));
    return isOpen ? [tr, detailTr(4, guestDetail(q))] : [tr];
  });
  document.getElementById('guestList').replaceChildren(...(rows.length ? rows : [emptyTr(4, narrowed('guest') ? '조건에 맞는 접수가 없습니다' : '해당하는 접수가 없습니다')]));
}

// =====================================================================
// 회원 접수: 답변을 저장하면 알림이 생긴다 (메일 없음)
// =====================================================================

const companyState = q => (q.reply ? 'DONE' : 'WAIT');
const readState = q => (!q.notice ? 'NONE' : q.notice.readAt ? 'READ' : 'UNREAD');

function saveCompanyReply(no, answer, renotify, btn) {
  btn.disabled = true; // 두 번 눌러 알림이 두 번 가지 않게
  api.post(`${INQ_PATH.company}/${inqEnc(no)}/reply`, { body: answer, renotify })
    .done(res => {
      replaceInquiry('company', res.inquiry);
      delete inqDrafts.company[no];
      renderCompany();
      renderInquiryBadges();
      showToast(res.first ? '답변을 저장하고 알림을 보냈습니다' : res.notified ? '답변을 수정하고 알림을 다시 보냈습니다' : '답변을 수정했습니다 (알림은 다시 보내지 않음)');
    })
    .fail(xhr => { btn.disabled = false; showToast(apiMessage(xhr, '답변을 저장하지 못했습니다')); });
}

// 시연 전용: 실제로는 업체 관리자 화면에서 알림을 열 때 바뀐다 (이 버튼은 JSP에 옮기지 않는다)
function markNoticeRead(no, btn) {
  btn.disabled = true;
  api.post(`${INQ_PATH.company}/${inqEnc(no)}/notice/read`)
    .done(res => { replaceInquiry('company', res.inquiry); renderCompany(); })
    .fail(xhr => { btn.disabled = false; showToast(apiMessage(xhr)); });
}

function companyDetail(q) {
  const left = cardOf('접수 내용', dlOf([
    ['접수번호', q.no], ['유형', TYPE_LABEL[q.type]], ['접수 시각', whenFull(q.at)], ['업체', q.company], ['접수자', q.writer],
    ['열람 가능', q.coAdmins ? `${q.writer} 외 같은 업체 관리자 ${q.coAdmins}명` : q.writer],
    ['제목', q.title], ['내용', text(q.body)],
  ]));

  const ta = inqDraftArea('company', q.no, q.reply ? q.reply.body : '');
  ta.setAttribute('aria-label', '답변');
  ta.placeholder = '답변을 입력하세요';
  const renotify = h('input');
  renotify.type = 'checkbox';
  const save = h('button', 'btn btn-primary', q.reply ? '수정 저장' : '답변 저장');
  save.type = 'button';
  save.addEventListener('click', () => {
    const body = ta.value.trim();
    // 서버도 같은 검사를 다시 한다
    if (!body) { showToast('답변 내용을 입력하세요'); ta.focus(); return; }
    if (body.length > INQ_ANSWER_MAX) { showToast(`답변은 ${INQ_ANSWER_MAX}자까지 쓸 수 있습니다`); return; }
    saveCompanyReply(q.no, body, renotify.checked, save);
  });
  const meta = q.reply
    ? h('p', 'inq-hint', `저장 ${whenFull(q.reply.savedAt)}${q.reply.editedAt ? ` · 수정 ${whenFull(q.reply.editedAt)}` : ''}`)
    : h('p', 'inq-hint', '저장하면 업체 관리자에게 알림이 만들어집니다. 알림에는 요약과 링크만 담기고, 전체 내용은 접수 상세에서 봅니다.');
  const actions = h('div', 'inq-actions', save);
  // 처음 저장은 늘 알린다. 수정은 고를 때만 다시 알린다 (오타를 고쳤다고 고객에게 또 알림이 가지 않게)
  if (q.reply) actions.append(h('label', 'inq-check', renotify, '수정 내용을 알림으로 다시 보내기'));
  const write = h('div', 'inq-body', ta, meta, actions);

  const parts = [write];
  if (q.notice) {
    const read = readState(q) === 'READ';
    const info = dlOf([
      ['알림 요약', q.notice.summary],
      ['링크', `접수 상세 보기 (${q.notice.link})`],
      ['보낸 시각', whenFull(q.notice.createdAt)],
      ['업체 관리자', read ? `읽음 (${whenFull(q.notice.readAt)})` : '안 읽음'],
    ]);
    parts.push(info);
    if (!read) {
      const demo = h('button', 'btn', '읽음으로 표시 (시연)');
      demo.type = 'button';
      demo.addEventListener('click', () => markNoticeRead(q.no, demo));
      parts.push(h('div', 'inq-body', demo));
    }
  }
  return h('div', 'inq-grid', left, cardOf('답변 저장', ...parts));
}

function renderCompany() {
  const full = inqItems.company; // 서버가 최신순으로 준다
  renderTabs('company', full, q => !q.reply);
  const all = full.filter(q => q.type === inq.company.type);
  setCount('cCntAll', all.length);
  setCount('cCntWait', all.filter(q => companyState(q) === 'WAIT').length);
  setCount('cCntDone', all.filter(q => companyState(q) === 'DONE').length);
  setCount('cCntUnread', all.filter(q => readState(q) === 'UNREAD').length);
  markPressed('companyFilters', inq.company.filter);

  const f = inq.company.filter;
  const shown = all.filter(q => (f === 'all' ? true : f === 'UNREAD' ? readState(q) === 'UNREAD' : companyState(q) === f)
    && passesControls('company', q, companyState(q) === 'WAIT', q.company, q.writer, q.title, q.body, q.no));
  showSearchInfo('company', shown.length);
  const rows = shown.flatMap(q => {
    const isOpen = inq.company.open === q.no;
    const state = companyState(q);
    const rs = readState(q);
    const readCell = rs === 'NONE' ? span('-', 'cell-sub') : span(rs === 'READ' ? '읽음' : '안 읽음', `status status-${rs.toLowerCase()}`);
    const tr = dataRow(isOpen, () => { inq.company.open = isOpen ? null : q.no; renderCompany(); },
      inqCell('접수 시각', span(when(q.at))),
      inqCell('업체 · 접수자', span(q.company), small(q.writer)),
      inqCell('제목', titleLine(q, isOpen), small(q.no)),
      inqCell('답변', span(COMPANY_LABEL[state], `status status-${state.toLowerCase()}`)),
      inqCell('업체 관리자 읽음', readCell));
    return isOpen ? [tr, detailTr(5, companyDetail(q))] : [tr];
  });
  document.getElementById('companyList').replaceChildren(...(rows.length ? rows : [emptyTr(5, narrowed('company') ? '조건에 맞는 접수가 없습니다' : '해당하는 접수가 없습니다')]));
}

// 사이드 메뉴 배지: 운영자가 할 일 (답변 대기·발송 실패 비회원 접수, 답변 없는 회원 접수).
// 운영자로 들어올 때와 답변한 뒤에 부른다 (시안은 demo/ops.js가 들어올 때 부른다)
function renderInquiryBadges() {
  api.get('/ops/api/inquiry-counts').done(c => {
    for (const [id, n] of [['navGuestCount', c.guest], ['navCompanyCount', c.company]]) {
      const el = document.getElementById(id);
      if (!el) continue;
      el.textContent = n;
      el.hidden = n === 0;
    }
  }); // 실패하면 배지를 그대로 둔다 (화면을 막을 일이 아니다)
}

// 그 화면이 있는 페이지에서만 연결한다 (JSP에서는 업체 관리·운영 데이터 페이지도 위 도구를 쓰려고 이 파일을 불러온다)
if (document.getElementById('guestList')) {
  wireFilters('guestFilters', 'guest', renderGuest);
  wireSearch('guest', renderGuest);
  wireTabs('guest', renderGuest);
}
if (document.getElementById('companyList')) {
  wireFilters('companyFilters', 'company', renderCompany);
  wireSearch('company', renderCompany);
  wireTabs('company', renderCompany);
}
