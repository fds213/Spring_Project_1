// Operator: 장애/문의 접수 관리 (비회원 접수 / 회원 접수). 운영 데이터는 admin-stats.js, 업체 관리는 admin-company.js.
// Loaded after ops.js, whose helpers (loadStore, when, whenFull, showToast, ...) it uses. common.js routes to
// these screens through opsViewShown in ops.js.
// Prototype only: the real project keeps inquiries in the DB and sends mail with the server's mail sender.

const GUEST_KEY = 'ieum-demo-inquiry-guest-v2';
const COMPANY_KEY = 'ieum-demo-inquiry-company-v3'; // v3: sample companies removed, starts empty

// Sender is a fixed setting, never typed by the operator (otherwise anyone could send as any address)
const MAIL_SENDER = 'noreply@ieum.example.com';
// A notification carries only a short summary + a link; the full text is read on the inquiry detail
const NOTICE_SUMMARY_LEN = 40;
const MAIL_FAIL = '수신 주소의 도메인을 확인할 수 없음 (오타 가능)';

// Every intake is either a fault report (장애) or a question (문의); each screen shows them in two tabs.
// Records saved before this split have no type: they are treated as questions, never dropped.
const TYPE_LABEL = { INCIDENT: '장애 접수', INQUIRY: '문의 접수' };
const typeOf = q => (q.type === 'INCIDENT' ? 'INCIDENT' : 'INQUIRY');
// Short form for the label in each list row, so the type is visible before the row is opened
const TYPE_SHORT = { INCIDENT: '장애', INQUIRY: '문의' };

const hoursAgo = n => new Date(Date.now() - n * 3600000).toISOString();

const GUEST_SAMPLES = [
  { no: 'G-1027-005', type: 'INCIDENT', name: '정우진', email: 'woojin@example.com', title: '체험 신청 안내 메일이 오지 않아요', body: '무료 체험을 신청했는데 업체 코드가 적힌 안내 메일이 10분이 지나도 안 옵니다. 스팸함도 확인했습니다.', at: hoursAgo(5), spam: false, replies: [] },
  { no: 'G-1027-006', type: 'INCIDENT', name: '한수빈', email: 'subin@example.net', title: '체험 신청 버튼이 눌리지 않아요', body: '2주 무료 체험 신청에서 "다음: 신청 확인"을 눌러도 아무 반응이 없습니다. 세 번 시도했습니다.', at: hoursAgo(40), spam: false, replies: [] },
  { no: 'G-1027-007', type: 'INQUIRY', name: '이하준', email: 'hajun@example.com', title: '무료 체험이 가능한가요?', body: '결제 전에 일주일 정도 써 보고 싶습니다. 체험 기간이 있나요?', at: hoursAgo(1), spam: false, replies: [] },
  { no: 'G-1027-008', type: 'INQUIRY', name: '송다인', email: 'dain@example.kr', title: '연 단위로 결제하면 할인이 있나요?', body: '직원 30명 기준으로 1년 계약을 생각하고 있습니다.', at: hoursAgo(8), spam: false,
    replies: [{ body: '연 단위 결제는 월 이용료의 10%를 할인해 드립니다. 자세한 견적은 별도로 보내드리겠습니다.', subject: '[G-1027-008] 연 단위로 결제하면 할인이 있나요? - 답변', status: 'SENT', at: hoursAgo(6), reason: '' }] },
  { no: 'G-1027-009', type: 'INQUIRY', name: '오세훈', email: 'sehun@example.co.kr', title: '세금계산서는 어떻게 발행되나요?', body: '법인 사업자입니다. 매달 세금계산서를 받을 수 있나요?', at: hoursAgo(55), spam: false, replies: [] },
  { no: 'G-1027-001', name: '박민수', email: 'minsu@example.com', title: '요금제가 어떻게 되나요?', body: '직원이 15명인데 월 비용이 얼마나 나오는지 궁금합니다.', at: hoursAgo(2), spam: false, replies: [] },
  { no: 'G-1027-002', name: '이서연', email: 'seoyeon@example.co.kr', title: '도입 상담 요청', body: '다음 달부터 쓰고 싶습니다. 상담 가능한 시간을 알려주세요.', at: hoursAgo(20), spam: false,
    replies: [{ body: '안녕하세요, IEUM입니다. 평일 10~17시에 상담 가능합니다. 편한 시간을 회신해 주세요.', subject: '[G-1027-002] 도입 상담 요청 - 답변', status: 'SENT', at: hoursAgo(18), reason: '' }] },
  { no: 'G-1027-003', name: '최도현', email: 'dohyun@example.con', title: '견적서 요청', body: '견적서를 메일로 보내주세요.', at: hoursAgo(26), spam: false,
    replies: [{ body: '견적서를 첨부해 보내드립니다.', subject: '[G-1027-003] 견적서 요청 - 답변', status: 'FAILED', at: hoursAgo(25), reason: MAIL_FAIL }] },
  { no: 'G-1027-004', name: '(광고)', email: 'promo@spam.example.com', title: '최저가 대출 안내', body: '지금 바로 연락 주세요!!!', at: hoursAgo(30), spam: true, replies: [] },
];

// 회원 접수 samples came from the sample companies, removed 2026-10-28; real ones are filed by signed-up companies
const COMPANY_SAMPLES = [
];

// 도입 문의 sent from the intro site (inquiry.html, js/inquiry.js) arrive here as 비회원 접수 · 문의 접수.
// Each is taken in once, by its 접수번호; the answer is then kept on this list. In the real project both are one table.
const SITE_INQ_KEY = 'ieum-demo-inquiries-v2';
function guestList() {
  const list = loadList(GUEST_KEY, GUEST_SAMPLES);
  const have = new Set(list.map(q => q.no));
  const fresh = loadStore(SITE_INQ_KEY, []).filter(s => !have.has(s.inquiryNo)).map(s => ({
    no: s.inquiryNo, type: 'INQUIRY', name: s.name, email: s.email, company: s.companyName, phone: s.phone || '',
    title: s.title, body: s.content, at: s.createdAt, spam: false, replies: [],
  }));
  if (fresh.length) {
    list.push(...fresh);
    saveStore(GUEST_KEY, list);
  }
  return list;
}

// First visit stores the samples; after that the stored list is the truth. Samples added later (by number)
// are appended once, so a browser that already holds answers typed earlier keeps them and still gets the new ones.
function loadList(key, samples) {
  const stored = loadStore(key, null);
  if (!stored) {
    saveStore(key, samples);
    return samples;
  }
  const have = new Set(stored.map(q => q.no));
  const added = samples.filter(s => !have.has(s.no));
  if (added.length) {
    stored.push(...added);
    saveStore(key, stored);
  }
  return stored;
}

// --- tiny DOM helpers (textContent only, never innerHTML: inquiry text is typed by outsiders) ---
const h = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const span = (text, cls) => h('span', cls, text);
const small = text => h('small', 'cell-sub', text);
const dlOf = rows => h('dl', 'detail', ...rows.flatMap(([k, v]) => [h('dt', '', k), h('dd', '', v)]));
const cardOf = (title, ...kids) => h('section', 'info-card', h('header', 'info-card-head', h('h2', '', title)), ...kids);
const text = s => h('span', 'reply-text', s); // keeps the line breaks the writer typed

function inqCell(label, ...parts) {
  const td = h('td', '', h('div', 'cell', ...parts));
  td.dataset.label = label; // column name shown in front of the value on phones
  return td;
}

// The title with its type label in front. The label is text, not color alone, so it reads the same in a
// screenshot, in black and white and with a screen reader.
function titleLine(q, isOpen) {
  const t = typeOf(q);
  return h('div', 'title-line', span(TYPE_SHORT[t], `type-pill type-${t.toLowerCase()}`), titleBtn(q.title, isOpen));
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

// Built already open: re-renders after an action must not replay the slide animation
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

const setCount = (id, n) => { document.getElementById(id).textContent = `${n}건`; };
const markPressed = (boxId, filter) =>
  document.querySelectorAll(`#${boxId} .card-filter`).forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === filter));

// An unanswered inquiry older than this is flagged by the "답변 없음" filter
const STALE_HOURS = 24;
const PERIOD_LABEL = { all: '전체 기간', 1: '오늘', 7: '최근 7일', 30: '최근 30일' };

// which filter card is on, which row is open (one at a time), the search text, the period and the
// "answer is overdue" switch, per view. The cards, search, period and switch all narrow the same list.
// `type` is the tab (장애 접수 / 문의 접수): it comes first, so the cards and their counts describe only that tab.
// A view opens on 장애 접수 because a fault report is usually the more urgent one.
const freshView = () => ({ type: 'INCIDENT', filter: 'all', open: null, query: '', period: 'all', stale: false });
const inq = { guest: freshView(), company: freshView() };

// "최근 7일" = today and the 6 days before it, from 00:00 (the same 7 days the daily chart shows)
function inPeriod(iso, period) {
  if (period === 'all') return true;
  const now = new Date();
  return new Date(iso) >= new Date(now.getFullYear(), now.getMonth(), now.getDate() - (Number(period) - 1));
}

// `unanswered` is decided by each view (a failed mail still counts as unanswered; a spam inquiry does not)
const isStale = (q, unanswered) => unanswered && Date.now() - new Date(q.at) > STALE_HOURS * 3600000;

// one narrowing rule for both views: period + overdue switch + search text
function passesControls(kind, q, unanswered, ...searchFields) {
  const c = inq[kind];
  return inPeriod(q.at, c.period) && (!c.stale || isStale(q, unanswered)) && matchesQuery(c.query, ...searchFields);
}

// Every word typed must appear somewhere in the fields (AND), and a part of a word is enough: "홍길" finds "홍길동".
// For names and titles that is what a searcher wants; the same looseness would be wrong for an ID lookup.
function matchesQuery(query, ...fields) {
  const hay = fields.join(' ').toLowerCase();
  return query.toLowerCase().split(/\s+/).filter(Boolean).every(w => hay.includes(w));
}

// Says which controls are narrowing the list, so an empty list is never a mystery
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

function openInquiryView(kind) {
  inq[kind] = freshView();
  document.getElementById(`${kind}Search`).value = '';
  document.getElementById(`${kind}Period`).value = 'all';
  document.getElementById(`${kind}Stale`).checked = false;
  if (kind === 'guest') renderGuest(); else renderCompany();
}

function wireFilters(boxId, kind, render) {
  document.querySelectorAll(`#${boxId} .card-filter`).forEach(b => b.addEventListener('click', () => {
    // the search text stays: cards and search narrow the list together
    inq[kind] = { ...inq[kind], filter: b.dataset.filter, open: null };
    render();
  }));
}

// Each tab carries the number of intakes in it that still need the operator, so the tab you are NOT on
// can't hide work. `full` is every record of the view (both types); `needsReply` decides what "still needs" means.
function renderTabs(kind, full, needsReply) {
  for (const t of Object.keys(TYPE_LABEL)) {
    const n = full.filter(q => typeOf(q) === t && needsReply(q)).length;
    const badge = document.getElementById(`${kind}Tab${t}`);
    badge.textContent = n;
    badge.hidden = n === 0;
    const tab = badge.parentElement;
    tab.setAttribute('aria-selected', inq[kind].type === t);
    tab.tabIndex = inq[kind].type === t ? 0 : -1; // one tab stop; arrow keys move between the tabs (see wireTabs)
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

// =====================================================================
// 비회원 접수: the answer goes out by mail
// =====================================================================

// "답변 완료" only after a mail really went out. A failed LAST try shows as 발송 실패 so it can't hide in the list.
function guestState(q) {
  if (q.spam) return 'SPAM';
  const last = q.replies[q.replies.length - 1];
  if (last && last.status === 'FAILED') return 'FAILED';
  return q.replies.some(r => r.status === 'SENT') ? 'DONE' : 'WAIT';
}
const GUEST_LABEL = { WAIT: '답변 대기', DONE: '답변 완료', FAILED: '발송 실패', SPAM: '스팸' };

// One mail format: the subject carries the inquiry number, the body holds the answer and then the original question
const mailSubject = q => `[${q.no}] ${q.title} - 답변`;
const mailBody = (q, answer) => `${answer}\n\n----- 접수 원문 -----\n${q.body}`;

// Mock sender. The real one calls the server's mail sender with { from, to, subject, text } and can fail
// for reasons like a wrong address or a mail-server setting; here only the address is checked.
function sendMail(message) {
  return new Promise(resolve => setTimeout(() => resolve(
    /@[^@\s]+\.(?!con$)[a-z]{2,}$/i.test(message.to) ? { ok: true } : { ok: false, reason: MAIL_FAIL }), 700));
}

// Only one mail is in flight at a time; while it is, buttons are disabled so a double click can't send twice
let sendingNo = null;

// retryAt === null: send a new answer (answerText). Otherwise: send reply number retryAt again.
async function deliverGuest(no, retryAt, answerText) {
  const list = guestList();
  const q = list.find(x => x.no === no);
  if (!q || q.spam || sendingNo) return;
  const reply = retryAt === null ? { body: answerText, subject: mailSubject(q) } : q.replies[retryAt];
  sendingNo = no;
  renderGuest();
  const res = await sendMail({ from: MAIL_SENDER, to: q.email, subject: reply.subject, text: mailBody(q, reply.body) });
  Object.assign(reply, { status: res.ok ? 'SENT' : 'FAILED', at: new Date().toISOString(), reason: res.ok ? '' : res.reason });
  if (retryAt === null) q.replies.push(reply);
  saveStore(GUEST_KEY, list);
  sendingNo = null;
  renderGuest();
  renderInquiryBadges();
  showToast(res.ok ? '답변 메일을 발송했습니다' : `발송 실패: ${res.reason}`);
}

function replyItem(q, r, i) {
  const ok = r.status === 'SENT';
  const li = h('li', '', h('div', 'reply-meta', span(ok ? '발송 성공' : '발송 실패', `status status-${ok ? 'done' : 'failed'}`), span(whenFull(r.at))), h('div', 'reply-text', r.body));
  if (!ok) {
    li.append(h('p', 'reply-fail', `실패 사유: ${r.reason}`));
    // only the newest try can be resent; an older failure was superseded by what came after it
    if (i === q.replies.length - 1) {
      const retry = h('button', 'btn', sendingNo ? '발송 중…' : '재발송');
      retry.type = 'button';
      retry.disabled = !!sendingNo;
      retry.addEventListener('click', () => deliverGuest(q.no, i, null));
      li.append(retry);
    }
  }
  return li;
}

function guestDetail(q) {
  const left = cardOf('접수 내용',
    dlOf([['접수번호', q.no], ['유형', TYPE_LABEL[typeOf(q)]], ['접수 시각', whenFull(q.at)], ...(q.company ? [['회사명', q.company]] : []), ['이름', q.name],
      ['수신 이메일', q.email], ...(q.phone ? [['휴대전화', q.phone]] : []), ['제목', q.title], ['내용', text(q.body)]]),
    h('p', 'inq-body inq-hint', '답변은 위 이메일로 발송됩니다. 주소가 틀리면 발송 실패로 기록되니 먼저 확인하세요.'));

  const log = q.replies.length
    ? h('ul', 'reply-log', ...q.replies.map((r, i) => replyItem(q, r, i)))
    : h('p', 'inq-hint', '아직 보낸 답변이 없습니다.');

  let compose;
  if (q.spam) {
    compose = h('div', 'inq-body', h('p', 'inq-hint', '스팸으로 숨긴 접수라 답변을 발송할 수 없습니다.'));
  } else {
    const hasSent = q.replies.some(r => r.status === 'SENT');
    const sending = sendingNo === q.no;
    const ta = h('textarea', 'inq-text');
    ta.setAttribute('aria-label', hasSent ? '추가 답변' : '답변');
    ta.placeholder = hasSent ? '추가로 보낼 답변을 입력하세요' : '답변을 입력하세요';
    ta.disabled = !!sendingNo;
    const send = h('button', 'btn btn-primary', sending ? '발송 중…' : hasSent ? '추가 답변 발송' : '답변 발송');
    send.type = 'button';
    send.disabled = !!sendingNo;
    send.addEventListener('click', () => {
      const body = ta.value.trim();
      if (!body) { showToast('답변 내용을 입력하세요'); ta.focus(); return; }
      deliverGuest(q.no, null, body);
    });
    compose = h('div', 'inq-body', ta,
      h('p', 'inq-hint', `발신: ${MAIL_SENDER} · 제목: ${mailSubject(q)}`),
      h('p', 'inq-hint', '이미 나간 메일은 고칠 수 없어서, 정정은 추가 답변으로 보냅니다.'),
      h('div', 'inq-actions', send));
  }
  return h('div', 'inq-grid', left, cardOf('답변 발송', h('div', 'inq-body', log), compose));
}

function renderGuest() {
  const full = guestList().slice().sort((a, b) => new Date(b.at) - new Date(a.at));
  renderTabs('guest', full, q => ['WAIT', 'FAILED'].includes(guestState(q)));
  // everything below (cards, counts, list) describes only the tab that is open
  const all = full.filter(q => typeOf(q) === inq.guest.type);
  const n = s => all.filter(q => guestState(q) === s).length;
  setCount('gCntAll', all.filter(q => !q.spam).length);
  setCount('gCntWait', n('WAIT'));
  setCount('gCntFailed', n('FAILED'));
  setCount('gCntDone', n('DONE'));
  setCount('gCntSpam', n('SPAM'));
  markPressed('guestFilters', inq.guest.filter);

  // spam stays out of "전체" and shows only under its own card
  const f = inq.guest.filter;
  const shown = all.filter(q => (f === 'all' ? !q.spam : guestState(q) === f)
    && passesControls('guest', q, ['WAIT', 'FAILED'].includes(guestState(q)), q.name, q.company || '', q.email, q.title, q.body, q.no));
  showSearchInfo('guest', shown.length);
  const rows = shown.flatMap(q => {
    const isOpen = inq.guest.open === q.no;
    const state = guestState(q);
    const tr = dataRow(isOpen, () => { inq.guest.open = isOpen ? null : q.no; renderGuest(); },
      inqCell('접수 시각', span(when(q.at))),
      inqCell('보낸 사람', span(q.company ? `${q.name} · ${q.company}` : q.name), small(q.email)),
      inqCell('제목', titleLine(q, isOpen), small(q.no)),
      inqCell('상태', span(sendingNo === q.no ? '발송 중…' : GUEST_LABEL[state], `status status-${state.toLowerCase()}`)));
    return isOpen ? [tr, detailTr(4, guestDetail(q))] : [tr];
  });
  document.getElementById('guestList').replaceChildren(...(rows.length ? rows : [emptyTr(4, narrowed('guest') ? '조건에 맞는 접수가 없습니다' : '해당하는 접수가 없습니다')]));
}

// =====================================================================
// 회원 접수: saving the answer creates a notification, no mail
// =====================================================================

const companyState = q => (q.reply ? 'DONE' : 'WAIT');
const COMPANY_LABEL = { WAIT: '답변 대기', DONE: '답변 완료' };
const readState = q => (!q.notice ? 'NONE' : q.notice.readAt ? 'READ' : 'UNREAD');

function summarize(answer) {
  const flat = answer.replace(/\s+/g, ' ').trim();
  return flat.length > NOTICE_SUMMARY_LEN ? flat.slice(0, NOTICE_SUMMARY_LEN) + '…' : flat;
}

function saveCompanyReply(no, answer, renotify) {
  const list = loadList(COMPANY_KEY, COMPANY_SAMPLES);
  const q = list.find(x => x.no === no);
  if (!q) return;
  const now = new Date().toISOString();
  const first = !q.reply;
  q.reply = { body: answer, savedAt: first ? now : q.reply.savedAt, editedAt: first ? null : now };
  // The first save always notifies. An edit notifies again only when the operator ticks the box,
  // otherwise a typo fix would ping the customer a second time.
  if (first || renotify) q.notice = { summary: summarize(answer), link: `#inquiry/${q.no}`, createdAt: now, readAt: null };
  saveStore(COMPANY_KEY, list);
  renderCompany();
  renderInquiryBadges();
  showToast(first ? '답변을 저장하고 알림을 보냈습니다' : renotify ? '답변을 수정하고 알림을 다시 보냈습니다' : '답변을 수정했습니다 (알림은 다시 보내지 않음)');
}

// Demo only: in the real project the company admin's screen sets this when they open the notification
function markNoticeRead(no) {
  const list = loadList(COMPANY_KEY, COMPANY_SAMPLES);
  const q = list.find(x => x.no === no);
  if (!q || !q.notice) return;
  q.notice.readAt = new Date().toISOString();
  saveStore(COMPANY_KEY, list);
  renderCompany();
}

function companyDetail(q) {
  const left = cardOf('접수 내용', dlOf([
    ['접수번호', q.no], ['유형', TYPE_LABEL[typeOf(q)]], ['접수 시각', whenFull(q.at)], ['업체', q.company], ['접수자', q.writer],
    ['열람 가능', q.coAdmins ? `${q.writer} 외 같은 업체 관리자 ${q.coAdmins}명` : q.writer],
    ['제목', q.title], ['내용', text(q.body)],
  ]));

  const ta = h('textarea', 'inq-text');
  ta.setAttribute('aria-label', '답변');
  ta.placeholder = '답변을 입력하세요';
  ta.value = q.reply ? q.reply.body : '';
  const renotify = h('input');
  renotify.type = 'checkbox';
  const save = h('button', 'btn btn-primary', q.reply ? '수정 저장' : '답변 저장');
  save.type = 'button';
  save.addEventListener('click', () => {
    const body = ta.value.trim();
    if (!body) { showToast('답변 내용을 입력하세요'); ta.focus(); return; }
    saveCompanyReply(q.no, body, renotify.checked);
  });
  const meta = q.reply
    ? h('p', 'inq-hint', `저장 ${whenFull(q.reply.savedAt)}${q.reply.editedAt ? ` · 수정 ${whenFull(q.reply.editedAt)}` : ''}`)
    : h('p', 'inq-hint', '저장하면 업체 관리자에게 알림이 만들어집니다. 알림에는 요약과 링크만 담기고, 전체 내용은 접수 상세에서 봅니다.');
  const actions = h('div', 'inq-actions', save);
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
      demo.addEventListener('click', () => markNoticeRead(q.no));
      parts.push(h('div', 'inq-body', demo));
    }
  }
  return h('div', 'inq-grid', left, cardOf('답변 저장', ...parts));
}

function renderCompany() {
  const full = loadList(COMPANY_KEY, COMPANY_SAMPLES).slice().sort((a, b) => new Date(b.at) - new Date(a.at));
  renderTabs('company', full, q => !q.reply);
  const all = full.filter(q => typeOf(q) === inq.company.type);
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

// Sidebar badges count what still needs the operator: unanswered or failed-to-send guest inquiries,
// and company inquiries without an answer
function renderInquiryBadges() {
  const g = guestList().filter(q => ['WAIT', 'FAILED'].includes(guestState(q))).length;
  const c = loadList(COMPANY_KEY, COMPANY_SAMPLES).filter(q => !q.reply).length;
  for (const [id, n] of [['navGuestCount', g], ['navCompanyCount', c]]) {
    const el = document.getElementById(id);
    el.textContent = n;
    el.hidden = n === 0;
  }
}

wireFilters('guestFilters', 'guest', renderGuest);
wireFilters('companyFilters', 'company', renderCompany);
wireSearch('guest', renderGuest);
wireSearch('company', renderCompany);
wireTabs('guest', renderGuest);
wireTabs('company', renderCompany);
renderInquiryBadges();

// A 도입 문의 sent from the intro site in another tab: count it, tell the operator, refresh the open list
window.addEventListener('storage', e => {
  if (e.key !== SITE_INQ_KEY || !session || session.type !== 'OPS') return;
  const before = new Set(JSON.parse(e.oldValue || '[]').map(s => s.inquiryNo));
  const added = JSON.parse(e.newValue || '[]').filter(s => !before.has(s.inquiryNo));
  if (added.length) showToast(`새 비회원 접수: ${added[added.length - 1].title}`);
  renderInquiryBadges();
  if (location.hash.slice(1) === 'ops-inquiry-guest') renderGuest();
});
