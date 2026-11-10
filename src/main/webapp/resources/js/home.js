// 홈 화면 (/home, common/layout-sample.jsp). 이미 있는 각 모듈 API를 불러 카드·일정·공지를 채운다.
// 요청은 칸마다 따로 보낸다: 한 칸이 실패해도 다른 칸은 그대로 채워지고, 실패한 칸은 처음의 "-"가 남는다.
// util.js(h·span·pad2) 다음에 불리고, JSP가 openHome()을 부른다.

const HOME_WEEK_DAYS = 5;     // 이번 주 일정에 보일 날 수 (오늘 + 다음 평일들)
const HOME_DAY_MAX = 3;       // 하루에 보일 일정 수. 넘으면 "+2건 더"
const HOME_NOTICE_MAX = 5;    // 공지 목록 수
const HOME_NEW_DAYS = 3;      // 공지 목록에서 N(새 글)을 붙일 기준 (며칠 안에 올라온 글)
const HOME_WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];
const HOME_SCOPE_LABEL = { PERSONAL: '개인', DEPT: '부서', COMPANY: '전사' };      // calendar.js 와 같은 이름
const HOME_LEAVE_LABEL = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };
const HOME_TICKET_DONE = ['RESOLVED', 'CLOSED', 'CANCELED', 'REJECTED'];           // 끝난 티켓 (it-service.js 의 DONE 묶음)

const homeEl = id => document.getElementById(id);
const homeKey = d => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`; // 2026-11-04
const homeDays = n => `${Number.isInteger(n) ? n : Number(n).toFixed(1)}`;              // 12 / 12.5

function openHome() {
  homeGreeting(new Date());
  homeApproval();
  homeTicket();
  homeLeave();
  homeAsset();
  homeWeek();
  homeNotices();
}

// ---------- 1. 인사말과 날짜 ----------
function homeGreeting(now) {
  const hour = now.getHours();
  homeEl('homeHello').textContent =
    hour >= 5 && hour < 12 ? '좋은 아침입니다' : hour >= 12 && hour < 18 ? '오후도 힘내세요' : '오늘 하루 수고하셨습니다';
  homeEl('homeDate').textContent = `${now.getMonth() + 1}월 ${now.getDate()}일 (${HOME_WEEKDAY[now.getDay()]})`;
}

// ---------- 2. 숫자 카드 ----------
// 숫자 + 단위를 넣는다. alert 이면 0보다 클 때 카드 숫자를 파랗게(처리할 것이 있다)
function homeCount(id, n, unit, alert) {
  homeEl(id).replaceChildren(String(n), span(unit, 'unit'));
  if (alert) homeEl(id).closest('.home-card').classList.toggle('is-alert', n > 0);
}

// 결재함 한 번으로 두 카드를 채운다. WAIT: 지금 내 차례 / PROGRESS: 진행 중(내가 올렸거나 내가 이미 승인한 문서)
// 진행 중 중에서 결재선에 내가 없는 문서 = 내가 올린 문서. 목록은 최근에 올라온 순서다
function homeApproval() {
  const me = homeEl('home').dataset.emp;
  api.get('/approval/api/box').done(box => {
    const wait = box.WAIT || [];
    homeCount('homeWait', wait.length, '건', true);
    homeSub('homeWaitSub', wait[wait.length - 1], wait.length); // 가장 오래 기다린 문서부터 처리하도록
    if (!me) return;
    const mine = (box.PROGRESS || []).filter(d => !(d.steps || []).some(s => String(s.approverEmployeeId) === me));
    homeCount('homeMine', mine.length, '건', false);
    homeSub('homeMineSub', mine[0], mine.length);               // 가장 최근에 올린 문서
  });
}

// 카드 아랫줄: "휴가 신청서 외 1건". 문서가 없으면 비운다(멘트는 챙길 일이 있을 때만)
function homeSub(id, doc, total) {
  homeEl(id).textContent = doc ? `${doc.title || '제목 없음'}${total > 1 ? ` 외 ${total - 1}건` : ''}` : '';
}

// 내 티켓: 끝나지 않은 티켓 수. 티켓 목록 API(/ticket/api/tickets)가 아직 없으면 "-"가 남는다
function homeTicket() {
  api.get('/ticket/api/tickets').done(items => {
    const open = (items || []).filter(t => !HOME_TICKET_DONE.includes(t.status));
    homeCount('homeTicket', open.length, '건', false);
    const doing = open.filter(t => t.status === 'IN_PROGRESS').length;
    homeEl('homeTicketSub').textContent = open.length ? `처리 중 ${doing} · 접수 ${open.length - doing}` : '';
  });
}

// 남은 연차: 입사일이 없으면 summary 가 비어 있고 reason 으로 이유를 알려 준다.
// 아랫줄은 신청해 두고 아직 결재가 안 끝난 연차가 있을 때만 보인다
function homeLeave() {
  api.get('/attendance/api/leaves').done(r => {
    const sum = r && r.summary;
    if (!sum) {
      homeEl('homeLeaveSub').textContent = r && r.reason === 'NO_HIRE_DATE' ? '입사일 미등록' : '연차 정보 없음';
      return;
    }
    homeEl('homeLeave').replaceChildren(homeDays(sum.left), span('일', 'unit'));
    homeEl('homeLeaveSub').textContent = Number(sum.pendingDays) > 0 ? `신청 중 ${homeDays(Number(sum.pendingDays))}일` : '';
  });
}

// 내 장비: 나에게 지급되어 아직 회수하지 않은 장비 수. 목록은 최근에 받은 순서라 아랫줄에 가장 최근 장비를 보인다
function homeAsset() {
  api.get('/asset/api/assets/assigned').done(items => {
    const list = items || [];
    homeCount('homeAsset', list.length, '대', false);
    const first = list[0];
    homeEl('homeAssetSub').textContent = first
      ? `${first.assetName || first.assetNo || '장비'}${list.length > 1 ? ` 외 ${list.length - 1}대` : ''}`
      : '';
  });
}

// ---------- 3. 이번 주 일정: 오늘 + 다음 평일들. 달이 넘어가면 두 달 치를 받는다 ----------
function homeWeekDates(today) {
  const days = [new Date(today)];
  const d = new Date(today);
  while (days.length < HOME_WEEK_DAYS) {
    d.setDate(d.getDate() + 1);
    if (d.getDay() !== 0 && d.getDay() !== 6) days.push(new Date(d));
  }
  return days;
}

function homeWeek() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = homeWeekDates(today);
  const months = [...new Set(days.map(d => homeKey(d).slice(0, 7)))];
  Promise.all(months.map(m => api.get('/schedule/api/schedules', { month: m })))
    .then(list => {
      const all = { items: [], holidays: [], leaves: [] };
      list.forEach(r => ['items', 'holidays', 'leaves'].forEach(k => all[k].push(...(r[k] || []))));
      homeEl('homeWeek').replaceChildren(...days.map(d => homeDayRow(d, homeKey(d) === homeKey(today), all)));
    })
    .catch(() => homeEl('homeWeek').replaceChildren(h('li', 'home-empty', '일정을 불러오지 못했습니다')));
}

// 하루 한 줄: 휴일 → 내 연차 → 일정 순서 (달력 화면과 같은 순서·색)
function homeDayRow(d, isToday, all) {
  const key = homeKey(d);
  const evs = [
    ...all.holidays.filter(x => x.date === key).map(x => ['holiday', '휴일', x.name]),
    ...all.leaves.filter(x => x.from <= key && key <= x.to).map(x => ['leave', '연차', HOME_LEAVE_LABEL[x.kind] || '연차']),
    ...all.items.filter(x => x.date === key).map(x => [String(x.scope).toLowerCase(), HOME_SCOPE_LABEL[x.scope] || '일정', x.title]),
  ];
  const day = h('div', 'home-day' + (isToday ? ' is-today' : ''), h('b', '', `${d.getDate()} ${HOME_WEEKDAY[d.getDay()]}`));
  if (isToday) day.append(span('오늘', 'today-badge'));
  const box = h('div', 'home-evs');
  if (!evs.length) box.append(h('div', 'none', '일정 없음'));
  evs.slice(0, HOME_DAY_MAX).forEach(([cls, label, title]) =>
    box.append(h('div', 'home-ev', span(label, `sch-chip chip-${cls}`), span(title, 't'))));
  if (evs.length > HOME_DAY_MAX) box.append(h('div', 'none', `+${evs.length - HOME_DAY_MAX}건 더`));
  return h('li', '', day, box);
}

// ---------- 4. 공지: 맨 위 띠(고정 공지만) + 목록 ----------
const homeMd = iso => { const d = new Date(iso); return !iso || isNaN(d) ? '' : `${pad2(d.getMonth() + 1)}.${pad2(d.getDate())}`; }; // 11.03 (날짜가 없으면 비움)
const homeNoticeHref = n => IEUM.url(`/board/notice/view/${n.id}`);
const homeIsNew = n => !!n.createdAt && Date.now() - new Date(n.createdAt) < HOME_NEW_DAYS * 86400000; // 며칠 안에 올라온 글

function homeNotices() {
  api.get('/board/api/notices', { page: 1 }).done(r => {
    const items = (r && r.items) || [];
    // 띠: 관리자가 맨 위 고정한 공지만 올린다. 지금은 DB에 고정 칸이 없어 늘 숨는다(고정 기능이 생기면 저절로 보인다)
    const top = ((r && r.pinned) || [])[0] || items.find(n => n.pinned);
    if (top) {
      homeEl('homePinTitle').textContent = top.title;
      homeEl('homePinDate').textContent = homeMd(top.createdAt);
      homeEl('homePin').href = homeNoticeHref(top);
      homeEl('homePin').hidden = false;
    }
    // 목록: 띠에 올린 글은 빼서 같은 글이 두 번 보이지 않게 한다. 새 글은 제목 앞에 N
    const rest = items.filter(n => !top || n.id !== top.id).slice(0, HOME_NOTICE_MAX);
    homeEl('homeNotices').replaceChildren(...(rest.length ? rest.map(n => {
      const tags = [];
      if (n.pinned) tags.push(span('고정', 'home-tag'));
      if (homeIsNew(n)) tags.push(span('N', 'home-new'));
      const a = h('a', '', ...tags, span(n.title, 't'), span(homeMd(n.createdAt), 'd'));
      a.href = homeNoticeHref(n);
      if (homeIsNew(n)) a.setAttribute('aria-label', `새 글 ${n.title}`);
      return h('li', '', a);
    }) : [h('li', 'home-empty', top ? '다른 공지가 없습니다' : '아직 공지가 없습니다')]));
  }).fail(() => homeEl('homeNotices').replaceChildren(h('li', 'home-empty', '공지를 불러오지 못했습니다')));
}
