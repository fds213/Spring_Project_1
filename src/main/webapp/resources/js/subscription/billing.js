// 업체 관리 > 이용 결제 (views/subscription/billing.jsp). 요구분석서 PAY-01·02, SUB-01·03.
// 자동 결제는 없고 업체 관리자가 매번 월간·연간을 골라 직접 결제한다. 금액과 기간은 서버가 계산한다(견적 API).
// - 체험 중이거나 체험이 끝난 뒤: 첫 결제. 인원을 정하고, 결제한 날부터 유료 이용
// - 유료 이용 중: 연장. 지금 인원 그대로 만료일 다음 날부터 이어진다(연장 때 인원 변경 SUB-07은 이번 범위에서 빠짐)
// 순서: 1. 요금제 선택 → 2. 결제 → 3. 결제 완료. 토스 결제가 승인되면 운영자 승인 없이 바로 반영한다(PAY-02).
// 이번 범위에서 빠진 것(요구분석서 3장): 인원 증설, 월간→연간 전환, 결제 안내 메일, 만료 후 재구매.
//
// 서버 API (프론트 제안. 결제는 업체 관리자):
//   GET  /subscription/api/billing   이 업체의 이용 상태 (신청 정보가 없으면 404)
//        { company, companyCode, status: TRIAL|PAID|EXPIRED, paid, plan: MONTHLY|ANNUAL|null, end(마지막 날), daysLeft,
//          trialFrom, trialEnd, serviceFrom, users, failedPayments, pricePerUser, trialSeats, maxUsers, accountsInUse,
//          mode: FIRST|EXTEND (지금 결제하면), nextFrom(연장이면 새 기간 첫날)|null, canPay, payments: [결제 한 건...] 최신순 }
//   GET  /subscription/api/quote?plan=MONTHLY|ANNUAL&users=n   견적
//        → { mode, plan, months, users, pct, why, amount, from, to, formula }   (인원·요금제가 맞지 않으면 400)
//   POST /subscription/api/payments  { plan, users, mode, from, amount, success }
//        → 결제 한 건. 서버가 견적을 다시 계산해 mode·from·amount가 다르면 409 (화면은 다시 견적을 받는다).
//          인원이 사용 중인 계정 수보다 적으면 400. 읽기 전용이어도 결제는 된다
//        success 는 토스 연동 전 임시 값이다(화면의 결제 성공·실패 버튼). 토스를 붙이면 빼고, 결제 결과는 서버가 토스 승인으로 정한다
// 결제 한 건: { id, at, kind: FIRST|EXTEND, plan, months, pct, users, amount, from, to, result: OK|FAILED }   (날짜는 ISO 문자열)

const BILL_STATUS = { TRIAL: '체험 중', PAID: '이용 중', EXPIRED: '읽기 전용' };
const BILL_PLAN = { MONTHLY: '월간', ANNUAL: '연간' };
const MODE_TEXT = { FIRST: '첫 결제', EXTEND: '연장' };
const BILL_RESULT = { OK: { label: '결제 완료', cls: 'paid' }, FAILED: { label: '결제 실패', cls: 'failed' } };
const billDday = n => (n === 0 ? 'D-day' : `D-${n}`);

let billState = null; // GET /subscription/api/billing 의 응답
let billStep = 1; // 보이는 단계. 페이지를 열면 1부터
let billQuote = null; // 1 → 2단계에서 정한 견적. 2단계는 보여 준 금액 그대로 결제한다
let billDone = ''; // 이 페이지에서 방금 한 결제의 id. 페이지를 떠나기 전까지 3단계에 보여 준다
let billNote = ''; // 다시 그린 뒤 2단계에 보여 줄 안내 (결제 실패)
let billQuoteSeq = 0; // 늦게 온 견적 응답이 새 입력을 덮지 않게

const fillDetail = (id, rows) => document.getElementById(id).replaceChildren(...rows.flatMap(([k, v]) => {
  const dt = document.createElement('dt'); dt.textContent = k;
  const dd = document.createElement('dd'); dd.textContent = v;
  return [dt, dd];
}));
const checkedPlan = () => document.querySelector('input[name="payPlan"]:checked').value;

// 화면을 열 때와 결제한 뒤: 이용 상태를 새로 받아 그린다
function renderBilling() {
  api.get('/subscription/api/billing')
    .done(s => { billState = s; drawBilling(); })
    .fail(xhr => {
      billState = null;
      if (xhr.status === 404) drawNoOrder();
      else showToast(apiMessage(xhr, '이용 정보를 불러오지 못했습니다'));
    });
}

function drawNoOrder() {
  ['billStatus', 'billPlan', 'billEnd', 'billUsersNow'].forEach(id => { document.getElementById(id).textContent = '-'; });
  fillDetail('billInfo', [['안내', '이 업체의 신청 정보가 없습니다']]);
  document.getElementById('billPay').hidden = true;
  document.getElementById('billHistoryBox').hidden = true;
}

function drawBilling() {
  const s = billState;
  const card = (id, v) => { document.getElementById(id).textContent = v; };
  card('billStatus', BILL_STATUS[s.status]);
  card('billPlan', s.paid ? BILL_PLAN[s.plan] || '월간' : '무료 체험');
  // 날짜가 카드 한 줄에 들어가도록 D-day 는 제목에 붙인다
  card('billEndLabel', `${s.paid ? '만료일' : '체험 만료일'} · ${s.status === 'EXPIRED' ? '만료됨' : billDday(s.daysLeft)}`);
  card('billEnd', ymd(s.end));
  card('billUsersNow', `${s.users}명`);
  fillDetail('billInfo', [
    ['업체명', s.company],
    ['업체 코드', s.companyCode],
    ['이용 인원', `${s.users}명`],
    ['1인 월 이용료', won(s.pricePerUser)],
    ['체험 기간', `${ymd(s.trialFrom)} ~ ${ymd(s.trialEnd)} (14일)`],
    ...(s.paid ? [
      ['요금제', BILL_PLAN[s.plan] || '월간'],
      ['이용 기간', `${ymd(s.serviceFrom)} ~ ${ymd(s.end)}`],
      ['결제 방식', '자동 결제 없음 · 만료 전에 직접 연장'],
    ] : []),
    ['계정 한도', s.paid ? `사용 중 ${s.users}명 (구매 인원)` : `관리자 포함 ${s.trialSeats}명 (결제하면 해제)`],
  ]);
  renderPayHistory();

  // 결제 권한이 없으면 결제 칸은 보이지 않는다
  document.getElementById('billPay').hidden = !s.canPay;
  if (!s.canPay) return;
  // 이 페이지에서 방금 한 결제: 페이지를 떠나기 전까지 3단계
  const done = billDone ? s.payments.find(p => p.id === billDone) : null;
  if (done) billStep = 3;
  else if (billStep === 3) billStep = 1;
  const mode = done ? done.kind : s.mode;
  document.getElementById('billPayTitle').textContent = done ? `${MODE_TEXT[mode]} 완료` : MODE_TEXT[mode];
  document.getElementById('billHint').textContent = done
    ? '결제가 끝나 바로 반영되었습니다. 결제 내역에서도 확인할 수 있습니다'
    : {
      FIRST: s.status === 'EXPIRED' ? '체험이 끝나 읽기 전용입니다. 결제하면 그날부터 바로 이어서 쓸 수 있습니다'
        : '체험 기간 중 언제든 결제할 수 있고, 결제한 날부터 유료 이용이 시작됩니다. 남은 체험 기간은 이어지지 않습니다',
      EXTEND: `만료일(${ymd(s.end)}) 다음 날부터 이어지도록 직접 연장합니다. 결제하면 바로 반영됩니다. 자동 결제는 없고, 해지·환불은 제공하지 않습니다`,
    }[mode];
  renderBillSteps(mode, done);
}

function renderBillSteps(mode, shown) {
  const s = billState;
  document.querySelectorAll('#billSteps li').forEach(li => {
    const k = Number(li.dataset.step);
    li.className = k === billStep ? 'on' : k < billStep ? 'done-step' : '';
  });
  [1, 2, 3].forEach(n => { document.getElementById(`billStep${n}`).hidden = n !== billStep; });
  if (billStep === 1) {
    // 인원은 첫 결제 때만 정한다. 연장은 지금 인원 그대로
    const users = document.getElementById('payUsers');
    const extend = mode === 'EXTEND';
    if (extend) users.value = s.users;
    else if (users.dataset.for !== s.companyCode) {
      // 한 번만 채운다 (다시 그려도 쓰던 값을 지우지 않게)
      users.value = Math.max(s.users, s.accountsInUse);
      users.dataset.for = s.companyCode;
    }
    users.disabled = extend;
    document.getElementById('payUsersHint').textContent = extend
      ? `연장은 지금 인원(${s.users}명) 그대로 이어집니다`
      : `최소 ${s.accountsInUse}명(지금 사용 중인 계정 수) · 결제하면 체험 중 ${s.trialSeats}명 제한이 풀립니다`;
    renderPayPreview();
  } else if (billStep === 3) {
    showPayDone(shown);
  } else if (billStep === 2) {
    const q = billQuote;
    fillDetail('billOrder', [
      ['구분', MODE_TEXT[q.mode]],
      ['요금제', `${BILL_PLAN[q.plan]} (${q.months}개월)`],
      ['이용 인원', `${q.users}명`],
      ['이용 기간', `${ymd(q.from)} ~ ${ymd(q.to)}`],
      ['결제 금액', `${won(q.amount)} (${q.formula})`],
      ['할인', q.pct ? `${q.pct}% · ${q.why}` : q.why],
      ['결제 실패 이력', s.failedPayments ? `${s.failedPayments}회` : '없음'],
    ]);
    document.getElementById('billError').textContent = billNote;
    document.getElementById('billBusy').textContent = '';
    billNote = '';
  }
}

const validUsers = n => Number.isInteger(n) && n >= 1 && n <= billState.maxUsers;

// 1단계: 두 요금제의 가격과 고른 요금제의 금액·기간. 인원을 바꾸는 동안 다시 계산한다 (서버 견적)
function renderPayPreview() {
  const n = Number(document.getElementById('payUsers').value);
  const ok = validUsers(n);
  const users = ok ? n : billState.users;
  const my = ++billQuoteSeq;
  jQuery.when(api.get('/subscription/api/quote', { plan: 'MONTHLY', users }), api.get('/subscription/api/quote', { plan: 'ANNUAL', users }))
    .done((month, year) => {
      if (my !== billQuoteSeq) return;
      document.getElementById('planPriceMONTHLY').textContent = `${won(month.amount)} / 월`;
      document.getElementById('planPriceANNUAL').textContent = `${won(year.amount)} / 년`;
      document.getElementById('planNoteANNUAL').textContent = `12개월 · ${year.pct}% 할인 (${year.why})`;
      const q = checkedPlan() === 'ANNUAL' ? year : month;
      fillDetail('payPreview', [
        ['결제 금액', ok ? `${won(q.amount)} (${q.formula})` : '-'],
        ['이용 기간', `${ymd(q.from)} ~ ${ymd(q.to)}${q.mode === 'EXTEND' ? ' (지금 만료일 다음 날부터 이어서)' : ' (결제한 날부터)'}`],
      ]);
    })
    .fail(xhr => {
      if (my !== billQuoteSeq) return;
      fillDetail('payPreview', [['결제 금액', '-'], ['안내', apiMessage(xhr, '금액을 계산하지 못했습니다')]]);
    });
}
document.getElementById('payUsers')?.addEventListener('input', () => {
  document.getElementById('payUsersError').textContent = '';
  if (billState) renderPayPreview();
});
document.querySelectorAll('input[name="payPlan"]').forEach(r => r.addEventListener('change', () => {
  if (billState) renderPayPreview();
}));

// 1 → 2단계: 인원을 확인하고, 결제할 견적을 정한다 (서버 견적)
document.getElementById('btnPayNext')?.addEventListener('click', () => {
  const s = billState;
  const btn = document.getElementById('btnPayNext');
  if (!s || btn.disabled) return;
  const usersEl = document.getElementById('payUsers');
  const n = Number(usersEl.value);
  const accounts = s.accountsInUse;
  // 서버도 같은 검사를 다시 한다. 화면은 먼저 알려 줄 뿐이다
  const usersError = !validUsers(n) ? `이용 인원은 1~${s.maxUsers}명으로 입력해 주세요`
    : n < accounts ? `지금 계정이 ${accounts}명이라 ${accounts}명보다 적게 정할 수 없습니다` : '';
  document.getElementById('payUsersError').textContent = usersError;
  if (usersError) return usersEl.focus();
  btn.disabled = true;
  api.get('/subscription/api/quote', { plan: checkedPlan(), users: n })
    .done(quote => {
      // 그사이 결제 구분이 바뀌었다(다른 창에서 결제 등): 처음부터 다시
      if (quote.mode !== s.mode) {
        renderBilling();
        showToast('결제 상태가 바뀌어 금액을 다시 계산했습니다. 확인하고 다시 진행해 주세요');
        return;
      }
      billQuote = quote;
      billStep = 2;
      document.getElementById('billAgree').checked = false; // 결제할 때마다 다시 동의한다
      drawBilling();
    })
    .fail(xhr => { document.getElementById('payUsersError').textContent = apiMessage(xhr); })
    .always(() => { btn.disabled = false; });
});
document.getElementById('btnPayBack')?.addEventListener('click', () => { billStep = 1; if (billState) drawBilling(); });

// 2단계 결제. 토스 연동 전 임시로 결제 성공·실패를 골라 흐름만 확인한다.
// 처리하는 동안 버튼을 막아 두 번 눌러 결제가 두 번 기록되지 않게 한다
function payBill(success) {
  const ok = document.getElementById('btnBillOk');
  const fail = document.getElementById('btnBillFail');
  const error = document.getElementById('billError');
  if (ok.disabled || !billQuote) return;
  if (!document.getElementById('billAgree').checked) {
    error.textContent = '이용 조건에 동의해야 결제할 수 있습니다';
    return;
  }
  error.textContent = '';
  ok.disabled = fail.disabled = true;
  document.getElementById('billBusy').textContent = '결제를 처리하고 있습니다…';
  const q = billQuote;
  api.post('/subscription/api/payments', { plan: q.plan, users: q.users, mode: q.mode, from: q.from, amount: q.amount, success })
    .done(p => {
      if (p.result === 'OK') {
        billDone = p.id; // 3단계: 방금 반영된 결제
        showToast(p.kind === 'EXTEND' ? `연장했습니다. 만료일이 ${ymd(p.to)}로 늘었습니다`
          : `결제했습니다. 오늘부터 ${ymd(p.to)}까지 유료로 쓸 수 있습니다`);
      } else {
        billNote = '결제에 실패했습니다. 결제는 반영되지 않았고, 다시 시도할 수 있습니다.';
      }
      renderBilling();
    })
    .fail(xhr => {
      // 1단계 뒤에 상태가 바뀌었다: 결제하지 않고 견적부터 다시
      if (xhr.status === 409) {
        billStep = 1;
        renderBilling();
        showToast(apiMessage(xhr));
        return;
      }
      error.textContent = apiMessage(xhr);
    })
    .always(() => {
      ok.disabled = fail.disabled = false;
      document.getElementById('billBusy').textContent = '';
    });
}
document.getElementById('btnBillOk')?.addEventListener('click', () => payBill(true));
document.getElementById('btnBillFail')?.addEventListener('click', () => payBill(false));

// 3단계: 방금 반영된 결제. 헤더의 체험·읽기 전용 띠는 다음 페이지부터 바뀐다(서버가 그린다)
function showPayDone(p) {
  const s = billState;
  document.getElementById('payDoneText').textContent = p.kind === 'EXTEND'
    ? `연장했습니다. 만료일이 ${ymd(p.to)}로 늘었습니다.`
    : `결제했습니다. 오늘부터 ${ymd(p.to)}까지 ${BILL_PLAN[p.plan]} 요금제로 쓰고, 체험 중 ${s.trialSeats}명이던 계정 한도가 풀렸습니다. 계정과 데이터는 그대로입니다.`;
  fillDetail('payDone', [
    ['상태', `결제 완료 (${whenFull(p.at)})`],
    ['구분', MODE_TEXT[p.kind]],
    ['요금제', `${BILL_PLAN[p.plan]} (${p.months}개월)`],
    ['이용 인원', `${p.users}명`],
    ['결제 금액', `${won(p.amount)} (${p.users}명 × ${won(s.pricePerUser)} × ${p.months}개월${p.pct ? ` × ${100 - p.pct}%` : ''})`],
    ['이용 기간', `${ymd(p.from)} ~ ${ymd(p.to)}`],
  ]);
}

// 결제 이력, 최신순 (서버가 최신순으로 준다)
function renderPayHistory() {
  const pays = billState.payments;
  document.getElementById('billHistoryBox').hidden = !pays.length;
  document.getElementById('billHistory').replaceChildren(...pays.map(p => {
    const li = document.createElement('li');
    const what = document.createElement('span');
    const plan = p.plan ? ` ${BILL_PLAN[p.plan]}` : '';
    what.textContent = `${ymd(p.at)} · ${MODE_TEXT[p.kind] || '결제'}${plan} · ${won(p.amount)}${p.users ? ` (${p.users}명)` : ''}` +
      (p.result === 'OK' && p.from && p.to ? ` · ${ymd(p.from)} ~ ${ymd(p.to)}` : '');
    const result = document.createElement('span');
    const r = BILL_RESULT[p.result] || BILL_RESULT.FAILED;
    result.className = `status status-${r.cls}`;
    result.textContent = r.label;
    li.append(what, result);
    return li;
  }));
}
