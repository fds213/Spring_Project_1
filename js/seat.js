// 인원 증설: 유료로 이용 중인 업체가 이용 기간 도중에 이용 인원을 늘린다. 흐름은 증설 요청 → 결제 → 운영자 승인 → 증설 완료.
// 이용 인원 "변경"은 연장할 때 다음 기간부터 적용되지만(common.js, o.seatChanges), 증설은 연장을 기다리지 않고 지금부터 쓰게 하고
// 대신 오늘부터 이번 이용 기간의 만료일까지만 계산해서 추가로 결제한다 (요구분석서 CAP-02).
// 승인 전에는 인원이 늘지 않는다: 돈은 냈지만 운영자가 확인하기 전이라 계정 한도에 반영하지 않기 위해서다.
// 이 파일은 ops.js 뒤, common.js 앞에 로드된다 (loadStore, trialState, subscriptionOf, plannedUsers, won, ymd 등은 ops.js 에서 온다).
// 실제 프로젝트에서는 서버가 금액을 다시 계산하고, 요청 한 건당 결제를 한 번만 기록한다.
//
// [백엔드 담당자에게] 금액 계산은 서버가 한다. 이 파일의 seatQuote() 는 서버 계산식의 명세이자 화면 미리보기일 뿐이다.
// - 현재일은 서버 시간, 만료일은 DB 의 구독 정보로 구한다. 여기서는 브라우저 시계(Date.now())와 localStorage 값을 쓰는데,
//   실제로 쓰면 사용자가 PC 시계나 요청 값을 바꿔서 금액을 줄일 수 있다.
// - 프론트는 견적 API(예: GET /subscription/seats/quote?add=3)가 준 months, restDays, monthDays, amount 를 그대로 보여 준다.
// - 증설 요청 API 는 프론트가 보낸 금액을 받지 않고, 요청 시점에 서버가 다시 계산해 요청에 저장한다.
//   결제 API 는 그 저장된 금액으로만 결제하고, 요청 한 건당 한 번만 기록한다. 승인 API 는 같은 트랜잭션에서 인원을 늘린다.
// - 서버와 프론트 미리보기가 어긋나지 않게 아래 규칙을 팀에서 한 번에 정한다:
//   · 남은 기간은 오늘(포함)부터 만료일(포함)까지다
//   · 온전한 개월 수 × 월 이용료 + 남는 날 / 그 달의 실제 일수 × 월 이용료, 합계의 원 미만은 버린다
//   · 월간·연간 모두 할인 없이 같은 식이다. 만료일은 그대로이고, 늘어난 인원은 이후 연장에 포함된다
//   · 남은 기간이 없으면(만료일이 오늘이거나 이미 끝남) 증설을 막는다 (연장할 때 인원을 늘린다)
//   · 시간대는 한국 시간 하나로 통일한다

// 1인 월 이용료는 ops.js 의 PRICE_PER_USER 한 곳에서만 정한다
const SEAT_PRICE = PRICE_PER_USER;
const SEAT_MAX = 500;    // 이용 인원 상한 (common.js 의 이용 인원 입력과 같다)

// 한 번에 하나만 진행한다: 요청이 끝나기 전에 또 요청하면 결제·승인 순서가 섞인다
const seatActive = o => (o.seatRequests || []).find(r => r.status === 'REQUESTED' || r.status === 'PAID');
// 끝난 요청 중 업체 관리자가 아직 확인(ack)하지 않은 가장 최근 것. 승인·반려 결과를 한 번은 보여 주려는 용도다
const seatUnseen = o => (o.seatRequests || []).slice().reverse().find(r => !r.ack && !['REQUESTED', 'PAID'].includes(r.status));

// 증설 금액 = 늘린 인원 × 월 이용료 × 남은 기간. 할인은 없다.
// 남은 기간은 오늘(포함)부터 이번 이용 기간의 만료일(포함)까지다. 온전한 개월은 월 이용료 그대로 계산하고,
// 남는 날은 그 날이 속한 달의 실제 일수로 나눠 일할한다. 총액의 원 미만은 버린다.
// 월간·연간 모두 같은 식이다 (연간 구독이 6개월 남았을 때 2명 증설 = 2 × 4,000 × 6 = 48,000원).
function seatQuote(order, add) {
  const sub = subscriptionOf(order);
  const today = dayStart(Date.now());
  const end = sub ? dayAfter(sub.expire) : today; // 만료일 다음 날 0시 = 이번 이용 기간의 끝
  let months = 0;
  while (addMonths(today, months + 1) <= end) months++;
  const rest = addMonths(today, months);
  const restDays = Math.round((end - rest) / DAY_MS);
  const monthDays = Math.round((addMonths(today, months + 1) - rest) / DAY_MS); // 남는 날이 속한 달의 일수
  const days = Math.round((end - today) / DAY_MS);
  const next = plannedUsers(order) + add;
  return {
    days, months, restDays, monthDays,
    from: ymd(today), lastDay: sub ? ymd(sub.expire) : ymd(today), toUsers: order.users + add,
    // 정수끼리만 나눈다: 소수로 곱하면 4,000원이 3,999.99…로 나와서 1원이 빠진다
    amount: days > 0 ? Math.floor(add * SEAT_PRICE * (months * monthDays + restDays) / monthDays) : 0,
    nextAmount: next * SEAT_PRICE,
  };
}

// 화면·요청 내역에 쓰는 기간 문구와 계산식 (견적 q 와 저장된 요청 r 이 같은 필드를 가진다)
function seatSpanText(x) {
  const parts = [];
  if (x.months) parts.push(`${x.months}개월`);
  if (x.restDays) parts.push(`${x.restDays}일(그 달 ${x.monthDays}일 기준)`);
  return parts.join(' + ') || '0일';
}
function seatFormula(n, x) {
  const parts = [];
  if (x.months) parts.push(`${n}명 × ${won(SEAT_PRICE)} × ${x.months}개월`);
  if (x.restDays) parts.push(`${n}명 × ${won(SEAT_PRICE)} × ${x.restDays}/${x.monthDays}일`);
  return parts.join(' + ');
}

// -------------------- 업체 관리자 화면 (이용 결제) --------------------
const SEAT_STEP = { REQUESTED: 2, PAID: 3, APPROVED: 4, REJECTED: 4, CANCELED: 4 };
let seatOpen = false; // 진행 중인 요청이 없을 때 1단계(요청 입력)를 열어 둘지

const seatEl = id => document.getElementById(id);

function readSeatCount() { return Number(seatEl('seatAddCount').value); }

function seatCountError(order, n) {
  if (!Number.isInteger(n) || n < 1) return '늘릴 인원을 1명 이상으로 입력해 주세요';
  if (order.users + n > SEAT_MAX) return `이용 인원은 최대 ${SEAT_MAX}명입니다 (지금 ${order.users}명)`;
  return '';
}

// common.js 의 renderBilling() 이 결제 뒤(유료) 화면을 그릴 때마다 부른다
function renderSeatAdd(order) {
  const wrap = seatEl('seatBox');
  const box = seatEl('seatAdd');
  const active = seatActive(order);
  const unseen = active ? null : seatUnseen(order);
  const shown = active || unseen;
  // 체험 중에는 정식 전환 결제로, 이용이 끝난 뒤에는 다시 결제로 인원을 정한다. 증설은 유료 이용 중에만 한다
  const canAdd = trialState(order).status === 'PAID';
  wrap.hidden = !(canAdd || shown);
  box.hidden = !(shown || (seatOpen && canAdd));
  seatEl('btnSeatAdd').disabled = !!active || !canAdd;
  if (box.hidden) return;

  const step = shown ? SEAT_STEP[shown.status] : 1;
  document.querySelectorAll('#seatSteps li').forEach(li => {
    const k = Number(li.dataset.step);
    li.className = k === step ? 'on' : k < step ? 'done-step' : '';
  });
  [1, 2, 3, 4].forEach(n => { seatEl(`seatStep${n}`).hidden = n !== step; });

  if (step === 1) {
    renderSeatQuote(order);
  } else if (step === 2) {
    fillDetail('seatOrder', [
      ['증설', `${shown.add}명 (${shown.fromUsers}명 → ${shown.toUsers}명)`],
      ['증설 기간', `${shown.from} ~ ${shown.lastDay} (${seatSpanText(shown)})`],
      ['결제 금액', won(shown.amount)],
    ]);
  } else if (step === 3) {
    seatEl('seatWaitText').textContent = `결제가 끝났습니다. 운영자가 확인하고 승인하면 ${shown.add}명이 늘어납니다. 승인 전에는 이용 인원이 그대로입니다.`;
    fillDetail('seatWait', [
      ['증설', `${shown.add}명 (${shown.fromUsers}명 → ${shown.toUsers}명)`],
      ['결제 금액', `${won(shown.amount)} (${ymd(shown.paidAt)} 결제)`],
      ['상태', '운영자 승인 대기'],
    ]);
  } else {
    const done = shown.status === 'APPROVED';
    const text = seatEl('seatResultText');
    text.className = done ? 'notice-ok' : 'notice-warn';
    text.textContent = done ? `증설이 승인되어 이용 인원이 ${shown.toUsers}명이 되었습니다. 이후 연장에도 ${shown.toUsers}명이 포함됩니다.`
      : shown.status === 'REJECTED' ? `증설 요청이 반려되어 결제한 ${won(shown.amount)}을 환불 처리했습니다. 이용 인원은 ${shown.fromUsers}명 그대로입니다.`
      : '증설 요청을 취소했습니다. 결제된 금액은 없습니다.';
  }
}

function renderSeatQuote(order) {
  const n = readSeatCount();
  const err = seatCountError(order, n);
  const q = err ? null : seatQuote(order, n);
  const blocked = q && q.days < 1;
  fillDetail('seatQuote', [
    ['증설 후 이용 인원', q ? `${order.users}명 → ${q.toUsers}명` : '-'],
    ['증설 기간', q && !blocked ? `${q.from} ~ ${q.lastDay} (${seatSpanText(q)})` : '-'],
    ['추가 결제 금액', q && !blocked ? `${won(q.amount)} (${seatFormula(n, q)})` : '-'],
    ['다음 연장부터', q ? `월 ${won(q.nextAmount)}` : '-'],
  ]);
  seatEl('seatError').textContent = err || (blocked ? '이용 기간이 오늘로 끝나서 남은 기간이 없습니다. 연장할 때 이용 인원을 늘려 주세요' : '');
  seatEl('btnSeatRequest').disabled = !!err || !!blocked;
}

// 저장된 업체 주문에서 이 업체의 것을 새로 읽어 바꾼다 (다른 탭의 변경을 덮어쓰지 않으려고)
function withSeatOrder(change) {
  const orders = loadStore(ORDERS_KEY, []);
  const order = orders.find(o => o.companyCode === session.company);
  if (!order || order.status !== 'PAID') return null;
  change(order);
  saveStore(ORDERS_KEY, orders);
  return order;
}

seatEl('btnSeatAdd').addEventListener('click', () => {
  const order = findCompanyOrder(session.company);
  if (!order || seatActive(order)) return;
  seatOpen = true;
  seatEl('seatAddCount').value = 1;
  renderBilling();
  seatEl('seatAddCount').focus();
});
seatEl('seatAddCount').addEventListener('input', () => {
  const order = findCompanyOrder(session.company);
  if (order) renderSeatQuote(order);
});
seatEl('btnSeatCancel').addEventListener('click', () => { seatOpen = false; renderBilling(); });

// 1단계 → 2단계: 요청을 저장한다. 금액은 이때 계산해 요청에 적어 두고, 결제는 그 금액으로 한다
seatEl('btnSeatRequest').addEventListener('click', () => {
  const n = readSeatCount();
  let made = null;
  withSeatOrder(o => {
    if (seatActive(o) || seatCountError(o, n)) return;
    const q = seatQuote(o, n);
    if (q.days < 1) return;
    o.seatRequests = o.seatRequests || [];
    made = { id: `S-${Date.now()}`, at: new Date().toISOString(), add: n, fromUsers: o.users, toUsers: q.toUsers, from: q.from, lastDay: q.lastDay, months: q.months, restDays: q.restDays, monthDays: q.monthDays, days: q.days, amount: q.amount, status: 'REQUESTED', ack: false };
    o.seatRequests.push(made);
  });
  if (made) { seatOpen = false; seatEl('seatPayError').textContent = ''; }
  renderBilling();
});

// 2단계: 모의 결제. 버튼은 처리 중에 잠가서 더블클릭으로 두 번 결제되지 않게 한다
function paySeat(success) {
  const ok = seatEl('btnSeatPayOk');
  const fail = seatEl('btnSeatPayFail');
  const cancel = seatEl('btnSeatPayCancel');
  if (ok.disabled) return;
  seatEl('seatPayError').textContent = '';
  ok.disabled = fail.disabled = cancel.disabled = true;
  seatEl('seatBusy').textContent = '결제를 처리하고 있습니다…';
  setTimeout(() => {
    ok.disabled = fail.disabled = cancel.disabled = false;
    seatEl('seatBusy').textContent = '';
    withSeatOrder(o => {
      const r = (o.seatRequests || []).find(x => x.status === 'REQUESTED');
      if (!r) return; // 다른 탭에서 이미 결제했거나 취소함: 아무것도 기록하지 않는다
      const now = new Date().toISOString();
      o.payments = o.payments || [];
      o.payments.push({ at: now, kind: 'ADD', result: success ? 'OK' : 'FAILED', amount: r.amount, users: r.add });
      if (success) { r.status = 'PAID'; r.paidAt = now; }
    });
    renderBilling();
    if (!success) seatEl('seatPayError').textContent = '모의 결제에 실패했습니다. 다시 시도하거나 요청을 취소해 주세요.';
  }, 800);
}
seatEl('btnSeatPayOk').addEventListener('click', () => paySeat(true));
seatEl('btnSeatPayFail').addEventListener('click', () => paySeat(false));
seatEl('btnSeatPayCancel').addEventListener('click', () => {
  withSeatOrder(o => {
    const r = (o.seatRequests || []).find(x => x.status === 'REQUESTED'); // 결제 전 요청만 취소할 수 있다
    if (r) { r.status = 'CANCELED'; r.decidedAt = new Date().toISOString(); }
  });
  renderBilling();
});
seatEl('btnSeatDone').addEventListener('click', () => {
  withSeatOrder(o => (o.seatRequests || []).forEach(r => { if (!['REQUESTED', 'PAID'].includes(r.status)) r.ack = true; }));
  renderBilling();
});

// -------------------- 운영자 화면 (증설 승인) --------------------
const SEAT_LABEL = { REQUESTED: '결제 대기', PAID: '승인 대기', APPROVED: '증설 완료', REJECTED: '반려', CANCELED: '요청 취소' };
const SEAT_CLASS = { REQUESTED: 'waiting', PAID: 'requested', APPROVED: 'paid', REJECTED: 'failed', CANCELED: 'expired' };
let seatFilter = 'all';

const allSeatRequests = () => loadStore(ORDERS_KEY, []).flatMap(o => (o.seatRequests || []).map(r => ({ o, r })))
  .sort((a, b) => new Date(b.r.at) - new Date(a.r.at));

// 사이드 메뉴 배지: 운영자가 아직 처리하지 않은(결제까지 끝난) 요청 수
function refreshSeatBadge() {
  const n = allSeatRequests().filter(({ r }) => r.status === 'PAID').length;
  const el = document.getElementById('navSeatCount');
  el.textContent = n;
  el.hidden = n === 0;
}

// 승인: 이용 인원을 늘린다. 연장 때 다음 기간부터 바꾸기로 해 둔 인원(seatChanges)이 있으면 그 계획에도 같은 만큼 더한다
function decideSeat(id, approve) {
  const orders = loadStore(ORDERS_KEY, []);
  const hit = orders.map(o => ({ o, r: (o.seatRequests || []).find(x => x.id === id) })).find(x => x.r);
  if (!hit || hit.r.status !== 'PAID') return; // 이미 다른 탭에서 처리함
  const { o, r } = hit;
  r.decidedAt = new Date().toISOString();
  r.ack = false; // 업체 관리자가 결과를 한 번 확인하게 한다
  if (approve) {
    r.status = 'APPROVED';
    o.users += r.add;
    o.total = o.users * SEAT_PRICE;
    (o.seatChanges || []).forEach(c => { c.users += r.add; }); // 늘린 인원은 다음 연장에도 포함된다
  } else {
    // 반려하면 낸 돈을 돌려준다. 이용 중 해지·환불은 없지만(팀 결정 2026-10-27) 서비스를 못 받은 결제는 예외다
    r.status = 'REJECTED';
    o.payments = o.payments || [];
    o.payments.push({ at: r.decidedAt, kind: 'REFUND', result: 'OK', amount: -r.amount, users: r.add });
  }
  saveStore(ORDERS_KEY, orders);
  showToast(approve ? `${o.company}: ${r.add}명 증설을 승인했습니다` : `${o.company}: 증설을 반려하고 환불 처리했습니다`);
  renderSeatOps();
}

function renderSeatOps() {
  const rows = allSeatRequests();
  const count = (id, n) => { seatEl(id).textContent = `${n}건`; };
  count('cntSeatAll', rows.length);
  count('cntSeatPaid', rows.filter(({ r }) => r.status === 'PAID').length);
  count('cntSeatWait', rows.filter(({ r }) => r.status === 'REQUESTED').length);
  count('cntSeatDone', rows.filter(({ r }) => r.status === 'APPROVED').length);
  count('cntSeatRejected', rows.filter(({ r }) => r.status === 'REJECTED').length);
  document.querySelectorAll('#seatFilters .card-filter').forEach(b => b.setAttribute('aria-pressed', b.dataset.filter === seatFilter));
  refreshSeatBadge();

  const cell = (label, ...parts) => {
    const td = document.createElement('td');
    td.dataset.label = label; // 폰에서 값 앞에 붙는 열 이름
    const box = document.createElement('div');
    box.className = 'cell';
    box.append(...parts);
    td.append(box);
    return td;
  };
  const text = (tag, s, cls) => { const e = document.createElement(tag); e.textContent = s; if (cls) e.className = cls; return e; };

  const shown = rows.filter(({ r }) => seatFilter === 'all' || r.status === seatFilter);
  const trs = shown.map(({ o, r }) => {
    const tr = document.createElement('tr');
    const actions = [];
    if (r.status === 'PAID') {
      const ok = text('button', '승인', 'btn btn-primary');
      ok.type = 'button';
      ok.addEventListener('click', () => decideSeat(r.id, true));
      const no = text('button', '반려', 'btn');
      no.type = 'button';
      no.addEventListener('click', () => decideSeat(r.id, false));
      actions.push(ok, no);
    } else {
      actions.push(text('span', r.decidedAt ? when(r.decidedAt) : '-', 'cell-sub'));
    }
    tr.append(
      cell('요청 시각', text('span', when(r.at))),
      cell('업체', text('span', o.company), text('small', o.companyCode, 'cell-sub')),
      cell('증설', text('span', `+${r.add}명`), text('small', `${r.fromUsers}명 → ${r.toUsers}명`, 'cell-sub')),
      cell('결제 금액', text('span', won(r.amount)), text('small', `${seatSpanText(r)} 일할`, 'cell-sub')),
      cell('상태', text('span', SEAT_LABEL[r.status], `status status-${SEAT_CLASS[r.status]}`)),
      cell('처리', ...actions));
    return tr;
  });
  if (!trs.length) {
    const tr = document.createElement('tr');
    tr.className = 'row-empty';
    const td = document.createElement('td');
    td.colSpan = 6;
    td.textContent = rows.length ? '해당하는 증설 요청이 없습니다' : '아직 들어온 증설 요청이 없습니다';
    tr.append(td);
    trs.push(tr);
  }
  seatEl('seatList').replaceChildren(...trs);
}

document.querySelectorAll('#seatFilters .card-filter').forEach(b => b.addEventListener('click', () => {
  seatFilter = b.dataset.filter;
  renderSeatOps();
}));
refreshSeatBadge();
