// 근무 > 일정 (views/schedule/calendar.jsp): 개인·부서·전사 일정을 달력으로 본다 (요구분석서 3.1 "일정·게시판").
// 달력에는 일정 외에 휴일과 본인의 연차(승인된 것만)가 함께 나온다. 다른 직원의 연차·근태는 보이지 않는다.
// 누가 무엇을 볼 수 있나: 개인 일정은 쓴 본인만, 부서 일정은 같은 부서 직원, 전사 일정은 업체 전체.
// 누가 쓸 수 있나: 개인은 누구나, 부서는 부서장·업체 관리자(자기 부서), 전사는 인사 담당자·업체 관리자. 지우기는 쓴 본인·업체 관리자.
// 이 규칙은 서버가 정하고 검사한다. 화면은 서버가 보내 준 구분(scopes)과 canDelete 로 선택지·버튼만 고른다.

const SCOPE_LABEL = { PERSONAL: '개인', DEPT: '부서', COMPANY: '전사' };
const SCH_LEAVE_LABEL = { FULL: '연차', AM: '오전 반차', PM: '오후 반차' };
const SCH_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const SCH_SHOW_MAX = 3; // 달력 칸에 보이는 일정 수. 나머지는 "+n개 더"
const SCH_TITLE_MAX = 40;
const SCH_MEMO_MAX = 200;

const schEl = id => document.getElementById(id);
const schNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const schDayKey = d => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
const schFromKey = k => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };

const schState = {
  month: null,  // { y, m } 화면에 보이는 달. 늘 data와 같은 달이다(응답이 와야 바뀐다)
  selected: '', // 화면에서 고른 날짜 'YYYY-MM-DD'
  show: { PERSONAL: true, DEPT: true, COMPANY: true, LEAVE: true },
  data: null,   // 서버에서 받은 그달 것
  pending: null, // 받아 오는 중인 달과 응답이 오면 고를 날짜 { month, selected }. 응답 전에는 month·selected를 바꾸지 않는다
  jumped: '',   // 앞뒤 달 칸을 눌러 달을 옮긴 직후(0.5초)의 그 날짜. 두 번 누르기를 바르게 처리하려고 둔다
  jumpTimer: null,
  reqSeq: 0,    // 달력 요청 번호. 가장 마지막에 보낸 요청의 응답만 쓴다
};
const schMonthParam = mo => `${mo.y}-${pad2(mo.m + 1)}`;
const schMonthOf = key => { const d = schFromKey(key); return { y: d.getFullYear(), m: d.getMonth() }; };
const schSameMonth = (a, b) => !!a && !!b && a.y === b.y && a.m === b.m;

// 화면을 열 때 (JSP 페이지가 열리면 부른다). "오늘" 버튼도 이것을 부른다
function openScheduleView() {
  const now = new Date();
  loadMonth({ y: now.getFullYear(), m: now.getMonth() }, schDayKey(now));
}

// 그달을 서버에서 받아, 응답이 오면 그때 보이는 달과 고른 날짜를 바꾸고 그린다.
// 응답 전에는 화면의 달·고른 날짜·자료를 그대로 둔다. 그래서 그사이 날짜를 누르거나 보기를 바꾸거나 [+ 일정 추가]를 눌러도
// 화면과 어긋나지 않고, 실패하면 아무것도 되돌릴 필요 없이 보던 화면 그대로다.
// 요청마다 번호를 붙여 가장 마지막에 보낸 요청의 응답만 쓴다(빠른 [>][>][<]에서 늦게 온 앞 응답은 성공·실패 모두 버린다)
function loadMonth(month, selected) {
  const seq = ++schState.reqSeq;
  schState.pending = { month, selected };
  api.get('/schedule/api/schedules', { month: schMonthParam(month) })
    .done(r => {
      if (seq !== schState.reqSeq) return;
      schState.month = month;
      schState.selected = schState.pending.selected; // 같은 달을 다시 받는 사이 날짜를 누르면 그 날짜가 들어 있다
      schState.pending = null;
      schState.data = r;
      drawSchedule();
    })
    .fail(xhr => {
      if (seq !== schState.reqSeq) return; // 그사이 새 요청을 보냈다 (그 응답이 따로 온다)
      schState.pending = null;
      showToast(apiMessage(xhr, '일정을 불러오지 못했습니다'));
    });
}

// 보고 있는 달을 다시 받아 그린다 (일정 추가·삭제 뒤). 받아 오는 중인 달이 있으면 그 달을 다시 받는다
function renderSchedule() {
  const p = schState.pending;
  if (p) return loadMonth(p.month, p.selected);
  if (!schState.month) return openScheduleView();
  loadMonth(schState.month, schState.selected);
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

// 날짜 고르기. 같은 달을 다시 받는 중(일정 추가·삭제 뒤)이면 응답이 와도 이 날짜가 남게 한다.
// 다른 달을 받는 중이면 응답이 오면 그 달로 넘어가므로 그대로 둔다
function schSelect(key) {
  schState.selected = key;
  const p = schState.pending;
  if (p && schSameMonth(p.month, schState.month)) p.selected = key;
  drawSchedule();
}

// 받아 둔 값으로 달력과 날짜 칸을 그린다 (보기 선택·날짜 선택은 서버에 다시 묻지 않는다)
function drawSchedule() {
  if (!schState.data) return;
  const { y, m } = schState.month;
  schEl('schMonthLabel').textContent = `${y}년 ${m + 1}월`;

  // 달력: 일요일부터 시작하는 7열
  const first = new Date(y, m, 1);
  const days = new Date(y, m + 1, 0).getDate();
  const cells = [];
  // 앞뒤 달의 날짜로 빈칸을 채운다 (연한 회색). 누르면 그 달로 넘어가 그 날짜를 고른다
  const otherDay = date => {
    const cell = schNode('button', 'sch-cell is-other', schNode('span', 'sch-num', String(date.getDate())));
    cell.type = 'button';
    cell.setAttribute('aria-label', `${date.getMonth() + 1}월 ${date.getDate()}일 (다른 달)`);
    cell.addEventListener('click', () => {
      const key = schDayKey(date);
      // 방금 앞뒤 달 칸을 눌러 달력이 바뀌었다고 잠깐 적어 둔다 (빠르게 한 번 더 누르면 이 날짜로 팝업을 연다)
      schState.jumped = key;
      clearTimeout(schState.jumpTimer);
      schState.jumpTimer = setTimeout(() => { schState.jumped = ''; }, 500);
      loadMonth({ y: date.getFullYear(), m: date.getMonth() }, key);
    });
    return cell;
  };
  for (let i = first.getDay(); i > 0; i--) cells.push(otherDay(new Date(y, m, 1 - i)));
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
    if (items.length > SCH_SHOW_MAX) cell.append(schNode('span', 'sch-more', `+${items.length - SCH_SHOW_MAX}개 더`));
    cell.addEventListener('click', () => schSelect(key));
    cell.addEventListener('dblclick', () => { schSelect(key); openAddDialog(key); }); // 두 번 누르면 그 날짜로 추가 팝업
    cells.push(cell);
  }
  for (let d = 1; cells.length % 7 !== 0; d++) cells.push(otherDay(new Date(y, m + 1, d)));
  schEl('schGrid').replaceChildren(...cells);

  renderDayPanel();
}

// 선택한 날짜의 목록 (추가는 팝업에서 한다)
function renderDayPanel() {
  const r = schState.data;
  const d = schFromKey(schState.selected);
  schEl('schDayTitle').textContent = `${d.getMonth() + 1}월 ${d.getDate()}일 (${SCH_WEEKDAYS[d.getDay()]})`;
  const items = schItemsOn(schState.selected);
  // 개수('2건')는 날짜 제목 바로 뒤에 둔다. JSP에 두지 않고 여기서 한 번 만든다
  let count = schEl('schDayCount');
  if (!count) {
    count = schNode('span', 'sch-day-count');
    count.id = 'schDayCount';
    schEl('schDayTitle').after(count);
  }
  count.textContent = items.length ? `${items.length}건` : '';
  schEl('schDayList').replaceChildren(...(items.length ? items.map(it => {
    // 구분 칩 + (제목, 그 아래 메모) + 삭제
    const body = schNode('div', 'sch-item-body', schNode('span', 'sch-title', it.title));
    if (it.schedule && it.schedule.memo) body.append(schNode('small', 'sch-memo', it.schedule.memo));
    const li = schNode('li', '', schNode('span', `sch-chip chip-${it.type.toLowerCase()}`, it.type === 'HOLIDAY' ? '휴일' : it.type === 'LEAVE' ? '연차' : SCOPE_LABEL[it.type]),
      body);
    if (it.schedule && it.schedule.canDelete) {
      const del = schNode('button', 'sch-del', '삭제');
      del.type = 'button';
      del.setAttribute('data-write', ''); // 읽기 전용 업체에서는 흐리게 두고 누르면 안내만 (common.js)
      del.addEventListener('click', () => deleteSchedule(it.schedule, del));
      li.append(del);
    }
    return li;
  }) : [schNode('li', 'empty-box', '이 날짜에는 일정이 없습니다')]));

  // 구분 선택지는 쓸 수 있는 것만 (서버가 정해 보낸다). 부서 일정은 내 부서 이름을 함께 보여 준다
  const scope = schEl('schScope');
  const keep = scope.value;
  scope.replaceChildren(...r.scopes.map(s => {
    const o = schNode('option', '', s === 'DEPT' ? `부서 (${r.dept || '-'})` : SCOPE_LABEL[s]);
    o.value = s;
    return o;
  }));
  if ([...scope.options].some(o => o.value === keep)) scope.value = keep;
}

// 일정 추가 팝업 열기: 날짜를 채우고, 제목·메모·안내는 비운다
function openAddDialog(dateKey) {
  if (!schState.data) return;
  // 읽기 전용 업체(체험이 끝나 결제하지 않음): 날짜 칸을 두 번 눌러 열 때도 막는다.
  // [+ 일정 추가] 버튼(data-write)을 대신 눌러 공통 안내(common.js)를 띄운다. 창을 다 쓴 뒤에야 저장이 막히지 않게
  if (document.body.classList.contains('is-readonly')) { schEl('schAddDay').click(); return; }
  schEl('schDate').value = dateKey || schState.selected;
  schEl('schTitle').value = '';
  schEl('schMemo').value = '';
  schEl('schError').textContent = '';
  const dlg = schEl('schDialog');
  if (!dlg.open) dlg.showModal();
  schEl('schTitle').focus();
}
schEl('schAddDay').addEventListener('click', () => openAddDialog(schState.selected));
schEl('schCancel').addEventListener('click', () => { schSkipRepeat = true; schEl('schDialog').close(); });

// 창(추가·삭제 확인)의 버튼을 빠르게 두 번 누르면, 첫 클릭에 창이 닫혀 두 번째 클릭이 창 아래의 달력 칸·목록에 떨어진다.
// 그러면 엉뚱한 날짜로 추가 팝업이 열리거나 고른 날짜가 바뀐다. 창을 닫은 뒤, 새로 누른 클릭(detail 1)이 오기 전까지
// 이어지는 클릭(detail 2 이상)은 칸에 닿기 전에(capture) 버린다
let schSkipRepeat = false;
// 이 화면 밖의 창(머리줄 비밀번호 변경, 체험 안내 등)도 닫힐 때 같은 문제가 있어, 어떤 창이든 닫히면 이어지는 클릭을 버린다
// (close 이벤트는 위로 퍼지지 않아 capture로 받는다)
document.addEventListener('close', e => { if (e.target instanceof HTMLDialogElement) schSkipRepeat = true; }, true);
['click', 'dblclick'].forEach(type => document.addEventListener(type, e => {
  if (!schSkipRepeat) return;
  if (e.detail < 2) { schSkipRepeat = false; return; }
  e.stopPropagation();
}, true));

// 삭제: 확인 창에서 [삭제]를 눌러야 지운다. 이미 지워진 일정(404, 다른 창·다른 사람이 먼저 지움)이면 달력을 다시 받는다
function deleteSchedule(s, btn) {
  const dlg = schEl('schDeleteDialog');
  schEl('schDeleteText').textContent = `'${s.title}' 일정을 지웁니다. 삭제한 일정은 되돌릴 수 없습니다.`;
  const openedAt = performance.now(); // 창을 연 시각
  dlg.querySelectorAll('button').forEach(b => {
    b.onclick = e => {
      // 목록 [삭제]를 두 번 눌러 두 번째 클릭이 막 열린 창의 버튼에 떨어진 경우: 확인 없이 지우지 않게 창을 그대로 둔다.
      // 휴대폰에 따라 두 번째 탭이 detail 1로 올 수 있어, 창을 연 지 0.4초 안의 마우스·탭도 버린다(키보드 Enter는 detail 0이라 그대로)
      if (e.detail > 1 || (e.detail === 1 && performance.now() - openedAt < 400)) return;
      schSkipRepeat = true;
      dlg.close();
      if (b.value !== 'ok') return;
      btn.disabled = true;
      api.post(`/schedule/api/schedules/${encodeURIComponent(s.id)}/delete`)
        .done(() => { showToast('일정을 삭제했습니다'); renderSchedule(); })
        .fail(xhr => {
          showToast(apiMessage(xhr));
          if (xhr.status === 404) renderSchedule(); // 이미 없는 일정: 목록에서 없앤다
          else btn.disabled = false;
        });
    };
  });
  dlg.showModal();
}

// 달을 옮기면 그달의 오늘(없으면 1일)을 선택한다 (받아 오는 것은 그달 것뿐이다).
// 받아 오는 중인 달이 있으면 그 달에서 한 달 더 옮긴다([>]를 빠르게 두 번 누르면 두 달 넘어가게)
const schMoveMonth = step => {
  const now = new Date();
  const base = (schState.pending && schState.pending.month) || schState.month || { y: now.getFullYear(), m: now.getMonth() };
  const d = new Date(base.y, base.m + step, 1);
  const month = { y: d.getFullYear(), m: d.getMonth() };
  loadMonth(month, schSameMonth(month, { y: now.getFullYear(), m: now.getMonth() }) ? schDayKey(now) : schDayKey(d));
};
// 앞뒤 달 칸을 두 번 빠르게 누르면, 첫 클릭에 달력이 새로 그려져 두 번째 클릭이 새 달의 다른 칸에 떨어진다.
// 그 두 번째 클릭은 칸에 닿기 전에(capture) 막고, 처음 누른 날짜로 추가 팝업을 연다
['click', 'dblclick'].forEach(type => schEl('schGrid').addEventListener(type, e => {
  if (!schState.jumped || e.detail < 2) return;
  e.stopPropagation();
  if (type === 'dblclick') openAddDialog(schState.jumped);
}, true));

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
      schSkipRepeat = true; // [추가]를 빠르게 두 번 눌렀을 때 두 번째 클릭이 닫힌 팝업 아래 달력에 떨어지지 않게
      schEl('schDialog').close(); // 팝업 닫기
      schEl('schTitle').value = '';
      schEl('schMemo').value = '';
      err.textContent = '';
      showToast('일정을 추가했습니다');
      loadMonth(schMonthOf(s.date), s.date); // 다른 달 날짜로 추가해도 그 달로 옮겨 보여 준다
    })
    .fail(xhr => { err.textContent = apiMessage(xhr); })
    .always(() => { if (btn) btn.disabled = false; });
});
schEl('schTitle').addEventListener('input', () => { schEl('schError').textContent = ''; });
