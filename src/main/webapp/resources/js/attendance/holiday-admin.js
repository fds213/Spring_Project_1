// 인사 관리 > 휴일 관리 (views/attendance/admin/holiday.jsp). 업체 관리자·인사 담당 (요구분석서 3.9).
// 휴가 신청의 날짜 계산(주말·휴일 빼기)과 일정 달력이 이 휴일을 쓴다. 휴일이 하나도 없는 해는 휴가 날짜를 계산하지 않는다.
//
//   GET  /attendance/admin/api/holidays?year=2026    → { year, readOnly, presetYears: [2026], items: [{ id, date, weekday, name }] }
//   POST /attendance/admin/api/holidays              { date, name } → { date, name }        409: 이미 휴일
//   POST /attendance/admin/api/holidays/{id}/delete  → { id }
//   POST /attendance/admin/api/holidays/preset       { year } → { year, added, skipped }   404: 그 해 목록 없음
// util.js 의 h, inqCell, emptyTr, showToast 를 쓴다.

const holState = { year: null, data: null };
const HOL_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function openHolidayView() {
  if (!holState.year) holState.year = new Date().getFullYear();
  const year = holState.year;
  document.getElementById('holYear').textContent = `${year}년`;
  api.get('/attendance/admin/api/holidays', { year })
    .done(d => { if (d.year === holState.year) renderHolidays(d); })
    .fail(xhr => showToast(apiMessage(xhr, '휴일을 불러오지 못했습니다')));
}

function renderHolidays(d) {
  holState.data = d;
  const preset = document.getElementById('btnHolPreset');
  preset.hidden = !d.presetYears.includes(d.year);
  preset.textContent = `${d.year}년 공휴일 한 번에 넣기`;
  preset.disabled = d.readOnly;

  const date = document.getElementById('holDate');
  date.min = `${d.year}-01-01`;
  date.max = `${d.year}-12-31`;
  document.querySelectorAll('#holAddForm input, #holAddForm button').forEach(x => { x.disabled = d.readOnly; });

  const rows = d.items.map(it => {
    const del = h('button', 'btn', '지우기');
    del.type = 'button';
    del.dataset.write = '';
    del.disabled = d.readOnly;
    del.addEventListener('click', () => askDeleteHoliday(it, del));
    const weekend = it.weekday === 0 || it.weekday === 6;
    return h('tr', weekend ? 'att-rest' : '',
      inqCell('날짜', it.date.slice(5).replace('-', '.')),
      inqCell('요일', HOL_WEEKDAYS[it.weekday]),
      inqCell('이름', it.name),
      inqCell('관리', del));
  });
  document.getElementById('holList').replaceChildren(...(rows.length ? rows
    : [emptyTr(4, `${d.year}년 휴일이 없습니다. 이 해에 걸친 휴가는 신청할 수 없습니다`)]));
}

function moveHolidayYear(step) {
  holState.year += step;
  document.getElementById('holError').textContent = '';
  openHolidayView();
}
document.getElementById('holPrev').addEventListener('click', () => moveHolidayYear(-1));
document.getElementById('holNext').addEventListener('click', () => moveHolidayYear(1));

// 하루 넣기
document.getElementById('holAddForm').addEventListener('submit', e => {
  e.preventDefault();
  const error = document.getElementById('holError');
  const date = document.getElementById('holDate').value;
  const name = document.getElementById('holName').value.trim();
  if (!date) { error.textContent = '날짜를 골라 주세요'; document.getElementById('holDate').focus(); return; }
  if (!name) { error.textContent = '휴일 이름을 적어 주세요'; document.getElementById('holName').focus(); return; }
  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  api.post('/attendance/admin/api/holidays', { date, name })
    .done(r => {
      showToast(`${r.date} ${r.name}을(를) 휴일로 넣었습니다`);
      error.textContent = '';
      document.getElementById('holName').value = '';
      holState.year = Number(r.date.slice(0, 4));
      openHolidayView();
    })
    .fail(xhr => { error.textContent = apiMessage(xhr); })
    .always(() => { btn.disabled = !!(holState.data && holState.data.readOnly); });
});
['holDate', 'holName'].forEach(id => document.getElementById(id).addEventListener('input', () => { document.getElementById('holError').textContent = ''; }));

// 그 해 법정 공휴일 한 번에
document.getElementById('btnHolPreset').addEventListener('click', e => {
  const btn = e.currentTarget;
  btn.disabled = true;
  api.post('/attendance/admin/api/holidays/preset', { year: holState.year })
    .done(r => {
      showToast(r.added ? `${r.year}년 공휴일 ${r.added}일을 넣었습니다${r.skipped ? ` (이미 있던 ${r.skipped}일은 건너뜀)` : ''}`
        : `${r.year}년 공휴일은 이미 모두 들어 있습니다`);
      openHolidayView();
    })
    .fail(xhr => { showToast(apiMessage(xhr)); btn.disabled = false; });
});

// 지우기: 확인 창. 확인을 누르면 창을 닫고 바로 지운다 (Esc·취소는 아무것도 하지 않는다)
let holPendingDelete = null;
function askDeleteHoliday(it, btn) {
  document.getElementById('holAskBody').textContent =
    `${it.date} ${it.name}을(를) 휴일에서 뺍니다. 이미 승인된 휴가의 차감 일수는 바뀌지 않고, 앞으로 신청하는 휴가부터 이 날을 근무일로 셉니다.`;
  holPendingDelete = () => {
    btn.disabled = true;
    api.post(`/attendance/admin/api/holidays/${encodeURIComponent(it.id)}/delete`)
      .done(() => { showToast(`${it.name}을(를) 지웠습니다`); openHolidayView(); })
      .fail(xhr => { showToast(apiMessage(xhr)); btn.disabled = false; });
  };
  document.getElementById('holAskDialog').showModal();
}
document.querySelectorAll('#holAskDialog .modal-actions button').forEach(b => b.addEventListener('click', () => {
  document.getElementById('holAskDialog').close();
  const run = holPendingDelete;
  holPendingDelete = null;
  if (b.value === 'ok' && run) run();
}));
