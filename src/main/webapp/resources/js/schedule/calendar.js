// 근무 > 일정 (views/schedule/calendar.jsp): 개인·부서·전사 일정을 달력으로 본다 (요구분석서 3.1 "일정·게시판").
// 달력에는 일정 외에 휴일과 본인의 연차(승인된 것만)가 함께 나온다. 다른 직원의 연차·근태는 보이지 않는다.
// 누가 무엇을 볼 수 있나: 개인 일정은 쓴 본인만, 부서 일정은 같은 부서 직원, 전사 일정은 업체 전체.
// 누가 쓸 수 있나: 개인은 누구나, 부서는 부서장·업체 관리자(자기 부서), 전사는 인사 담당자·업체 관리자. 지우기는 쓴 본인·업체 관리자.
// 이 규칙은 서버가 정하고 검사한다. 화면은 서버가 보내 준 구분(scopes)과 canDelete 로 선택지·버튼만 고른다.
//
// 서버 API (프론트 제안):
//   GET    /schedule/api/schedules?month=YYYY-MM  그달 달력에 들어갈 것
//          → { month, dept, scopes: [쓸 수 있는 구분 PERSONAL|DEPT|COMPANY], holidays: [{ date, name }],
//              leaves: [{ from, to, kind: FULL|AM|PM }] (본인의 승인된 연차 중 그달에 걸친 것),
//              items: [{ id, scope, title, date, memo, dept, mine, canDelete }] (내가 볼 수 있는 일정, 등록 순) }
//   POST   /schedule/api/schedules  { scope, date: YYYY-MM-DD, title, memo } → 일정 한 건
//          쓸 수 없는 구분이면 403, 날짜·제목(40자)·메모(200자)가 틀리면 400, 읽기 전용 업체 403
//   POST   /schedule/api/schedules/{id}/delete → { ok: true }  볼 수 없는 일정 404, 쓴 본인·업체 관리자가 아니면 403

const SCOPE_LABEL = { PERSONAL: '개인', DEPT: '부서', COMPANY: '전사' };
const SCH_LEAVE_LABEL = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };
const SCH_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const SCH_SHOW_MAX = 3; // 달력 칸에 보이는 일정 수. 나머지는 "+n"
const SCH_TITLE_MAX = 40;
const SCH_MEMO_MAX = 200;

const schEl = id => document.getElementById(id);
const schNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const schDayKey = d => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
const schFromKey = k => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };

const schState = {
  month: null,  // { y, m } 보고 있는 달
  selected: '', // 선택한 날짜 'YYYY-MM-DD'
  show: { PERSONAL: true, DEPT: true, COMPANY: true, LEAVE: true },
  data: null,   // 서버에서 받은 그달 것
};
const schMonthParam = () => `${schState.month.y}-${pad2(schState.month.m + 1)}`;

// 화면을 열 때 (JSP 페이지가 열리면 부른다). "오늘" 버튼도 이것을 부른다
function openScheduleView() {
  const now = new Date();
  schState.month = { y: now.getFullYear(), m: now.getMonth() };
  schState.selected = schDayKey(now);
  renderSchedule();
}

// 보고 있는 달을 서버에서 다시 받아 그린다 (다른 탭에서 바뀌었을 때도 이것을 부른다)
function renderSchedule() {
  if (!schState.month) return openScheduleView();
  const month = schMonthParam();
  api.get('/schedule/api/schedules', { month })
    .done(r => {
      if (r.month !== schMonthParam()) return; // 달을 빨리 넘기면 늦게 온 앞 달 응답은 버린다
      schState.data = r;
      drawSchedule();
    })
    .fail(xhr => showToast(apiMessage(xhr, '일정을 불러오지 못했습니다')));
}

// 한 날짜의 칸에 들어갈 것: 휴일, 내 연차, 일정 (보기 선택에 맞춰 거른다)
function schItemsOn(key) {
  const r = schState.data;
  const items = [];
  r.holidays.filter(h => h.date === key).forEach(h => items.push({ type: 'HOLIDAY', title: h.name }));
  if (schState.show.LEAVE) {
    r.leaves.filter(l => l.from <= key && key <= l.to).forEach(l => items.push({ type: 'LEAVE', title: SCH_LEAVE_LABEL[l.kind] }));
  }
  r.items.filter(s => s.date === key && schState.show[s.scope]).forEach(s => items.push({ type: s.scope, title: s.title, schedule: s }));
  return items;
}
const schIsHoliday = key => schState.data.holidays.some(h => h.date === key);

// 받아 둔 값으로 달력과 날짜 칸을 그린다 (보기 선택·날짜 선택은 서버에 다시 묻지 않는다)
function drawSchedule() {
  if (!schState.data) return;
  const { y, m } = schState.month;
  schEl('schMonthLabel').textContent = `${y}년 ${m + 1}월`;

  // 달력: 일요일부터 시작하는 7열
  const first = new Date(y, m, 1);
  const days = new Date(y, m + 1, 0).getDate();
  const cells = [];
  for (let i = 0; i < first.getDay(); i++) cells.push(schNode('div', 'sch-cell is-blank'));
  const todayKey = schDayKey(new Date());
  for (let d = 1; d <= days; d++) {
    const date = new Date(y, m, d);
    const key = schDayKey(date);
    const items = schItemsOn(key);
    const cell = schNode('button', 'sch-cell' + (key === todayKey ? ' is-today' : '') + (key === schState.selected ? ' is-selected' : '')
      + (date.getDay() === 0 || schIsHoliday(key) ? ' is-red' : date.getDay() === 6 ? ' is-blue' : ''));
    cell.type = 'button';
    cell.setAttribute('aria-pressed', key === schState.selected);
    cell.setAttribute('aria-label', `${m + 1}월 ${d}일${items.length ? `, 일정 ${items.length}건` : ''}`);
    cell.append(schNode('span', 'sch-num', String(d)));
    items.slice(0, SCH_SHOW_MAX).forEach(it => cell.append(schNode('span', `sch-chip chip-${it.type.toLowerCase()}`, it.title)));
    if (items.length > SCH_SHOW_MAX) cell.append(schNode('span', 'sch-more', `+${items.length - SCH_SHOW_MAX}`));
    cell.addEventListener('click', () => { schState.selected = key; drawSchedule(); });
    cells.push(cell);
  }
  schEl('schGrid').replaceChildren(...cells);

  renderDayPanel();
}

// 선택한 날짜의 목록과 추가 폼
function renderDayPanel() {
  const r = schState.data;
  const d = schFromKey(schState.selected);
  schEl('schDayTitle').textContent = `${d.getMonth() + 1}월 ${d.getDate()}일 (${SCH_WEEKDAYS[d.getDay()]})`;
  const items = schItemsOn(schState.selected);
  schEl('schDayList').replaceChildren(...(items.length ? items.map(it => {
    const li = schNode('li', '', schNode('span', `sch-chip chip-${it.type.toLowerCase()}`, it.type === 'HOLIDAY' ? '휴일' : it.type === 'LEAVE' ? '연차' : SCOPE_LABEL[it.type]),
      schNode('span', 'sch-title', it.title));
    if (it.schedule && it.schedule.memo) li.append(schNode('small', 'cell-sub', it.schedule.memo));
    if (it.schedule && it.schedule.canDelete) {
      const del = schNode('button', 'btn', '삭제');
      del.type = 'button';
      del.addEventListener('click', () => deleteSchedule(it.schedule.id, del));
      li.append(del);
    }
    return li;
  }) : [schNode('li', 'muted', '이 날짜에는 일정이 없습니다')]));

  // 구분 선택지는 쓸 수 있는 것만 (서버가 정해 보낸다). 부서 일정은 내 부서 이름을 함께 보여 준다
  const scope = schEl('schScope');
  const keep = scope.value;
  scope.replaceChildren(...r.scopes.map(s => {
    const o = schNode('option', '', s === 'DEPT' ? `부서 (${r.dept || '-'})` : SCOPE_LABEL[s]);
    o.value = s;
    return o;
  }));
  if ([...scope.options].some(o => o.value === keep)) scope.value = keep;
  schEl('schDate').value = schState.selected;
}

function deleteSchedule(id, btn) {
  btn.disabled = true;
  api.post(`/schedule/api/schedules/${encodeURIComponent(id)}/delete`)
    .done(() => { showToast('일정을 삭제했습니다'); renderSchedule(); })
    .fail(xhr => { showToast(apiMessage(xhr)); btn.disabled = false; });
}

// 달을 옮기면 그달의 오늘(없으면 1일)을 선택한다 (받아 오는 것은 그달 것뿐이다)
const schMoveMonth = step => {
  const { y, m } = schState.month;
  const d = new Date(y, m + step, 1);
  schState.month = { y: d.getFullYear(), m: d.getMonth() };
  const now = new Date();
  schState.selected = now.getFullYear() === d.getFullYear() && now.getMonth() === d.getMonth() ? schDayKey(now) : schDayKey(d);
  renderSchedule();
};
schEl('schPrev').addEventListener('click', () => schMoveMonth(-1));
schEl('schNext').addEventListener('click', () => schMoveMonth(1));
schEl('schToday').addEventListener('click', openScheduleView);
document.querySelectorAll('#schFilters input').forEach(box => box.addEventListener('change', () => {
  schState.show[box.dataset.scope] = box.checked;
  drawSchedule();
}));

// 서버도 같은 검사를 다시 한다. 화면은 먼저 알려 줄 뿐이다. 문제가 없으면 ''
function checkScheduleForm(scope, date, title, memo) {
  if (!schState.data || !schState.data.scopes.includes(scope)) return '이 구분의 일정은 쓸 수 있는 권한이 없습니다';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return '날짜를 입력해 주세요';
  if (!title) return '제목을 입력해 주세요';
  if (title.length > SCH_TITLE_MAX) return `제목은 ${SCH_TITLE_MAX}자까지입니다`;
  if (memo.length > SCH_MEMO_MAX) return `메모는 ${SCH_MEMO_MAX}자까지입니다`;
  return '';
}

schEl('schForm').addEventListener('submit', e => {
  e.preventDefault();
  const scope = schEl('schScope').value;
  const date = schEl('schDate').value;
  const title = schEl('schTitle').value.trim();
  const memo = schEl('schMemo').value.trim();
  const err = schEl('schError');
  const error = checkScheduleForm(scope, date, title, memo);
  err.textContent = error;
  if (error) return;
  const btn = schEl('schForm').querySelector('[type="submit"]');
  if (btn) btn.disabled = true; // 두 번 눌러 두 건이 생기지 않게
  api.post('/schedule/api/schedules', { scope, date, title, memo })
    .done(s => {
      schEl('schTitle').value = '';
      schEl('schMemo').value = '';
      err.textContent = '';
      schState.selected = s.date;
      const d = schFromKey(s.date);
      schState.month = { y: d.getFullYear(), m: d.getMonth() }; // 다른 달 날짜로 추가해도 그 달로 옮겨 보여 준다
      showToast('일정을 추가했습니다');
      renderSchedule();
    })
    .fail(xhr => { err.textContent = apiMessage(xhr); })
    .always(() => { if (btn) btn.disabled = false; });
});
schEl('schTitle').addEventListener('input', () => { schEl('schError').textContent = ''; });
