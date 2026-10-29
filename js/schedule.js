// 근무 > 일정: 개인·부서·전사 일정을 달력으로 본다 (요구분석서 v1.5 3.1 "일정·게시판").
// 달력에는 일정 외에 휴일과 본인의 연차(승인된 것만)가 함께 나온다. 다른 직원의 연차·근태는 보이지 않는다.
// 누가 무엇을 볼 수 있나: 개인 일정은 쓴 본인만, 부서 일정은 같은 부서 직원, 전사 일정은 업체 전체.
// 누가 쓸 수 있나: 개인은 누구나, 부서는 부서장·업체 관리자(자기 부서), 전사는 인사 담당자·업체 관리자.
// 시안이라 localStorage 에 업체별로 저장하고, 실제 서버는 같은 규칙을 다시 검사한다 (화면은 안내만 한다).
// attendance.js 의 날짜 도구(dayKeyOf, dayFromKey, holidayName, myLeaves, myStaffRecord, LEAVE_LABEL)를 쓴다.

const SCHEDULE_KEY = 'ieum-demo-schedules-v1'; // { 업체코드: [{ id, scope, ownerEmpNo, dept, title, date, memo, at }] }
const SCOPE_LABEL = { PERSONAL: '개인', DEPT: '부서', COMPANY: '전사' };
const SHOW_MAX = 3; // 달력 칸에 보이는 일정 수. 나머지는 "+n"
const TITLE_MAX = 40;
const MEMO_MAX = 200;

const schEl = id => document.getElementById(id);
const schNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };

let schMonth = null;  // { y, m }
let schSelected = ''; // 선택한 날짜 'YYYY-MM-DD'
let schShow = { PERSONAL: true, DEPT: true, COMPANY: true, LEAVE: true };

// 이 사람이 쓸 수 있는 구분 (화면이 고를 수 있는 선택지이기도 하다)
function allowedScopes() {
  const out = ['PERSONAL'];
  if (userRoles.includes('HEAD') || userRoles.includes('ADMIN')) out.push('DEPT');
  if (userRoles.includes('HR') || userRoles.includes('ADMIN')) out.push('COMPANY');
  return out;
}

const myDeptName = () => (myStaffRecord() || {}).dept || '';
const schedulesOf = () => loadStore(SCHEDULE_KEY, {})[session.company] || [];

// 내가 볼 수 있는 일정
function visibleSchedules() {
  const dept = myDeptName();
  return schedulesOf().filter(s => s.scope === 'COMPANY' || (s.scope === 'DEPT' && s.dept === dept) || (s.scope === 'PERSONAL' && s.ownerEmpNo === session.id));
}

// 한 날짜의 칸에 들어갈 것: 휴일, 내 연차, 일정
function itemsOn(key) {
  const items = [];
  const hol = holidayName(key);
  if (hol) items.push({ type: 'HOLIDAY', title: hol });
  if (schShow.LEAVE) {
    myLeaves().filter(l => isApprovedLeave(l) && l.from <= key && key <= l.to).forEach(l => items.push({ type: 'LEAVE', title: LEAVE_LABEL[l.kind] }));
  }
  visibleSchedules().filter(s => s.date === key && schShow[s.scope]).forEach(s => items.push({ type: s.scope, title: s.title, schedule: s }));
  return items;
}

function openScheduleView() {
  const now = new Date();
  schMonth = { y: now.getFullYear(), m: now.getMonth() };
  schSelected = dayKeyOf(now);
  renderSchedule();
}

function renderSchedule() {
  if (!schMonth) return openScheduleView();
  schEl('schMonthLabel').textContent = `${schMonth.y}년 ${schMonth.m + 1}월`;

  // 달력: 일요일부터 시작하는 7열
  const first = new Date(schMonth.y, schMonth.m, 1);
  const days = new Date(schMonth.y, schMonth.m + 1, 0).getDate();
  const cells = [];
  for (let i = 0; i < first.getDay(); i++) cells.push(schNode('div', 'sch-cell is-blank'));
  const todayKey = dayKeyOf(new Date());
  for (let d = 1; d <= days; d++) {
    const date = new Date(schMonth.y, schMonth.m, d);
    const key = dayKeyOf(date);
    const items = itemsOn(key);
    const cell = schNode('button', 'sch-cell' + (key === todayKey ? ' is-today' : '') + (key === schSelected ? ' is-selected' : '')
      + (date.getDay() === 0 || holidayName(key) ? ' is-red' : date.getDay() === 6 ? ' is-blue' : ''));
    cell.type = 'button';
    cell.setAttribute('aria-pressed', key === schSelected);
    cell.setAttribute('aria-label', `${schMonth.m + 1}월 ${d}일${items.length ? `, 일정 ${items.length}건` : ''}`);
    cell.append(schNode('span', 'sch-num', String(d)));
    items.slice(0, SHOW_MAX).forEach(it => cell.append(schNode('span', `sch-chip chip-${it.type.toLowerCase()}`, it.title)));
    if (items.length > SHOW_MAX) cell.append(schNode('span', 'sch-more', `+${items.length - SHOW_MAX}`));
    cell.addEventListener('click', () => { schSelected = key; renderSchedule(); });
    cells.push(cell);
  }
  schEl('schGrid').replaceChildren(...cells);

  renderDayPanel();
}

// 선택한 날짜의 목록과 추가 폼
function renderDayPanel() {
  const d = dayFromKey(schSelected);
  schEl('schDayTitle').textContent = `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]})`;
  const items = itemsOn(schSelected);
  const mine = s => s.ownerEmpNo === session.id || userRoles.includes('ADMIN');
  schEl('schDayList').replaceChildren(...(items.length ? items.map(it => {
    const li = schNode('li', '', schNode('span', `sch-chip chip-${it.type.toLowerCase()}`, it.type === 'HOLIDAY' ? '휴일' : it.type === 'LEAVE' ? '연차' : SCOPE_LABEL[it.type]),
      schNode('span', 'sch-title', it.title));
    if (it.schedule && it.schedule.memo) li.append(schNode('small', 'cell-sub', it.schedule.memo));
    if (it.schedule && mine(it.schedule)) {
      const del = schNode('button', 'btn', '삭제');
      del.type = 'button';
      del.addEventListener('click', () => deleteSchedule(it.schedule.id));
      li.append(del);
    }
    return li;
  }) : [schNode('li', 'muted', '이 날짜에는 일정이 없습니다')]));

  // 구분 선택지는 쓸 수 있는 것만. 부서 일정은 내 부서 이름을 함께 보여 준다
  const scope = schEl('schScope');
  const keep = scope.value;
  scope.replaceChildren(...allowedScopes().map(s => {
    const o = schNode('option', '', s === 'DEPT' ? `부서 (${myDeptName() || '-'})` : SCOPE_LABEL[s]);
    o.value = s;
    return o;
  }));
  if ([...scope.options].some(o => o.value === keep)) scope.value = keep;
  schEl('schDate').value = schSelected;
}

function addSchedule(scope, date, title, memo) {
  if (readOnly) return readOnlyMessage();
  if (!allowedScopes().includes(scope)) return '이 구분의 일정은 쓸 수 있는 권한이 없습니다';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(dayFromKey(date).getTime())) return '날짜를 입력해 주세요';
  if (!title) return '제목을 입력해 주세요';
  if (title.length > TITLE_MAX) return `제목은 ${TITLE_MAX}자까지입니다`;
  if (memo.length > MEMO_MAX) return `메모는 ${MEMO_MAX}자까지입니다`;
  const all = loadStore(SCHEDULE_KEY, {});
  (all[session.company] = all[session.company] || []).push({
    id: `S-${Date.now()}-${Math.floor(Math.random() * 1000)}`, scope, ownerEmpNo: session.id,
    dept: scope === 'DEPT' ? myDeptName() : '', title, date, memo, at: new Date().toISOString(),
  });
  saveStore(SCHEDULE_KEY, all);
  return '';
}

function deleteSchedule(id) {
  if (readOnly) return showToast(readOnlyMessage());
  const all = loadStore(SCHEDULE_KEY, {});
  const s = (all[session.company] || []).find(x => x.id === id);
  // 본인이 쓴 것이거나 업체 관리자만 지운다
  if (!s || !(s.ownerEmpNo === session.id || userRoles.includes('ADMIN'))) return;
  all[session.company] = all[session.company].filter(x => x.id !== id);
  saveStore(SCHEDULE_KEY, all);
  renderSchedule();
}

schEl('schPrev').addEventListener('click', () => {
  schMonth = schMonth.m === 0 ? { y: schMonth.y - 1, m: 11 } : { y: schMonth.y, m: schMonth.m - 1 };
  renderSchedule();
});
schEl('schNext').addEventListener('click', () => {
  schMonth = schMonth.m === 11 ? { y: schMonth.y + 1, m: 0 } : { y: schMonth.y, m: schMonth.m + 1 };
  renderSchedule();
});
schEl('schToday').addEventListener('click', openScheduleView);
document.querySelectorAll('#schFilters input').forEach(box => box.addEventListener('change', () => {
  schShow[box.dataset.scope] = box.checked;
  renderSchedule();
}));
schEl('schForm').addEventListener('submit', e => {
  e.preventDefault();
  const date = schEl('schDate').value;
  const error = addSchedule(schEl('schScope').value, date, schEl('schTitle').value.trim(), schEl('schMemo').value.trim());
  schEl('schError').textContent = error;
  if (error) return;
  schEl('schTitle').value = '';
  schEl('schMemo').value = '';
  schSelected = date;
  const d = dayFromKey(date);
  schMonth = { y: d.getFullYear(), m: d.getMonth() }; // 다른 달 날짜로 추가해도 그 달로 옮겨 보여 준다
  showToast('일정을 추가했습니다');
  renderSchedule();
});
schEl('schTitle').addEventListener('input', () => { schEl('schError').textContent = ''; });
