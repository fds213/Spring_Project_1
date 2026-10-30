// 업체 관리 > 이용 결제 화면 (subscription 모듈, 화면 조각 views/subscription/billing.html).
// 업체 관리 > 이용 결제 (requirements v1.5 PAY-01·02, SUB-01·03·05). No automatic renewal: the admin pays
// directly, choosing monthly or annual each time. 금액과 기간은 서버가 계산한다 (견적 API).
// - trial, or read-only after it: 첫 결제 with headcount and billing e-mail, starts today
// - paid and running: 연장, adds to the current expiry date; the headcount may change and the new one starts
//   the day after the current period, never below the accounts in use
// - paid period over (read-only): 다시 결제, starts today, headcount and e-mail can change
// Steps: 1. 요금제 선택 → 2. 결제 (Toss test payment in the real project, mock here) → 3. 결제 완료.
// Every payment applies as soon as Toss approves it, with no operator step (요구분석서 PAY-02·SUB-03·SUB-07).
// 인원 증설(CAP-01·02)은 이번 구현 범위에서 빠졌다(요구분석서 3장).
//
// 서버 API (시안은 demo/subscription.js가 답한다. 보기는 PERM_SUB_VIEW, 결제는 PERM_PAYMENT = 업체 관리자):
//   GET  /subscription/api/billing   이 업체의 이용 상태 (신청 정보가 없으면 404)
//        { company, companyCode, status: TRIAL|PAID|EXPIRED, paid, plan: MONTHLY|ANNUAL|null, end(마지막 날), daysLeft,
//          trialFrom, trialEnd, serviceFrom, users, plannedUsers, seatChange: { from, users }|null, failedPayments,
//          lastRejected: { decidedAt, kind, amount, reason }|null, pricePerUser, trialSeats, maxUsers, accountsInUse, billEmail,
//          mode: FIRST|EXTEND|RESTART (지금 결제하면), nextFrom(연장이면 새 기간 첫날)|null, seatBusy(진행 중인 증설), canPay,
//          payments: [결제 한 건...] 최신순 }
//   GET  /subscription/api/quote?plan=MONTHLY|ANNUAL&users=n   견적
//        → { mode, plan, months, users, pct, why, amount, from, to, formula }   (인원·요금제가 맞지 않으면 400)
//   POST /subscription/api/payments  { plan, users, email(연장이 아닐 때), mode, from, amount, success(시안 전용 모의 결과) }
//        → 결제 한 건. 서버가 견적을 다시 계산해 mode·from·amount가 다르면 409 (화면은 다시 견적을 받는다).
//          진행 중인 인원 증설이 있으면 409, 인원이 사용 중인 계정 수보다 적거나 이메일이 틀리면 400. 읽기 전용이어도 결제는 된다
// 결제 한 건: { id, at, kind: FIRST|EXTEND|RESTART|RENEW|ADD|REFUND, plan, months, pct, users, amount, from, to,
//              result: OK|FAILED|REJECTED, reason, decidedAt }   (날짜는 모두 ISO 문자열)
// 결제가 반영되면 'subscription:changed' 알림을 document에 보낸다 (읽기 전용·체험 띠·사원 한도처럼 화면 밖에서 다시 그릴 것이 있다).
// 실제 프로젝트에서는 토스 승인 금액을 견적과 맞춰 보고, 같은 확인 요청이 다시 와도 결제를 한 번만 기록한다.

const BILL_STATUS = { TRIAL: '체험 중', PAID: '이용 중', EXPIRED: '읽기 전용' };
const BILL_PLAN = { MONTHLY: '월간', ANNUAL: '연간' };
// what the payment card is called in each mode (same words as the history). RENEW only in data saved by the auto-renewal version
const MODE_TEXT = { FIRST: '첫 결제', EXTEND: '연장', RESTART: '다시 결제', RENEW: '정기 결제', ADD: '증설 결제', REFUND: '증설 환불' };
const BILL_RESULT = { OK: { label: '결제 완료', cls: 'paid' }, REJECTED: { label: '반려', cls: 'rejected' }, FAILED: { label: '결제 실패', cls: 'failed' } };
// 증설 환불은 결제 결과와 따로 "환불"로 보인다
const billResultOf = p => (p.kind === 'REFUND' ? { label: '환불', cls: 'expired' } : BILL_RESULT[p.result] || BILL_RESULT.FAILED);
const billDday = n => (n === 0 ? 'D-day' : `D-${n}`);

let billState = null; // GET /subscription/api/billing 의 응답
let billStep = 1; // which step is showing; back to 1 whenever the page is opened
let billQuote = null; // the quote made at step 1 → 2, so step 2 pays exactly what was shown
let billDone = ''; // id of the payment just made on this page, shown at step 3 until the page is left
let billNote = ''; // 다시 그린 뒤 2단계에 보여 줄 안내 (모의 결제 실패)
let billQuoteSeq = 0; // 늦게 온 견적 응답이 새 입력을 덮지 않게

const fillDetail = (id, rows) => document.getElementById(id).replaceChildren(...rows.flatMap(([k, v]) => {
  const dt = document.createElement('dt'); dt.textContent = k;
  const dd = document.createElement('dd'); dd.textContent = v;
  return [dt, dd];
}));
const checkedPlan = () => document.querySelector('input[name="payPlan"]:checked').value;

// 화면을 열 때와 무언가 바뀌었을 때: 이용 상태를 새로 받아 그리고, 인원 증설도 새로 받는다
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
  // the D-day sits in the label so the date fits on one line in the card
  card('billEndLabel', `${s.paid ? '만료일' : '체험 만료일'} · ${s.status === 'EXPIRED' ? '만료됨' : billDday(s.daysLeft)}`);
  card('billEnd', ymd(s.end));
  const change = s.seatChange;
  card('billUsersNow', change ? `${s.users}명 → ${s.plannedUsers}명` : `${s.users}명`);
  fillDetail('billInfo', [
    ['업체명', s.company],
    ['업체 코드', s.companyCode],
    ['이용 인원', change ? `${s.users}명 (${ymd(change.from)}부터 ${change.users}명)` : `${s.users}명`],
    ['1인 월 이용료', won(s.pricePerUser)],
    ['결제 안내 이메일', s.billEmail || '-'],
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
  // the payment just made on this page: step 3 until the page is left
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
      RESTART: '이용 기간이 끝나 읽기 전용입니다. 결제하면 그날부터 바로 다시 시작하고, 계정과 데이터는 그대로입니다',
    }[mode];
  // a payment an operator rejected in the version that had that step: say so (with the reason) until the next one
  const last = s.lastRejected;
  const rejected = document.getElementById('billRejected');
  rejected.hidden = !last;
  if (last) {
    rejected.textContent = `${ymd(last.decidedAt)}에 ${MODE_TEXT[last.kind]} 신청(${won(last.amount)})이 반려되었습니다. ` +
      `사유: ${last.reason} · 결제한 금액은 취소됩니다. 내용을 고쳐 다시 결제할 수 있습니다.`;
  }
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
    // the billing e-mail only when a new period starts today; the headcount every time
    document.getElementById('payEmailBox').hidden = mode === 'EXTEND';
    // filled once per visit, so a re-render (e.g. a change from another tab) doesn't wipe what is being typed
    const users = document.getElementById('payUsers');
    if (users.dataset.for !== s.companyCode) {
      users.value = s.plannedUsers;
      document.getElementById('payEmail').value = s.billEmail;
      users.dataset.for = s.companyCode;
    }
    const min = `최소 ${s.accountsInUse}명(지금 사용 중인 계정 수)`;
    document.getElementById('payUsersHint').textContent = mode === 'EXTEND'
      ? `지금 ${s.users}명 · 바꾼 인원은 새 기간(${ymd(s.nextFrom)})부터 적용 · ${min}`
      : min + (mode === 'FIRST' ? ` · 결제하면 체험 중 ${s.trialSeats}명 제한이 풀립니다` : '');
    renderPayPreview();
  } else if (billStep === 3) {
    showPayDone(shown);
  } else if (billStep === 2) {
    const q = billQuote;
    fillDetail('billOrder', [
      ['구분', MODE_TEXT[q.mode]],
      ['요금제', `${BILL_PLAN[q.plan]} (${q.months}개월)`],
      ['이용 인원', q.mode === 'EXTEND' && q.users !== s.users ? `${q.users}명 (${ymd(q.from)}부터)` : `${q.users}명`],
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

// step 1: both plans' prices and the chosen plan's amount and period, updated while the headcount is typed (서버 견적)
function renderPayPreview() {
  const n = Number(document.getElementById('payUsers').value);
  const ok = validUsers(n);
  const users = ok ? n : billState.plannedUsers;
  const my = ++billQuoteSeq;
  jQuery.when(api.get('/subscription/api/quote', { plan: 'MONTHLY', users }), api.get('/subscription/api/quote', { plan: 'ANNUAL', users }))
    .done(([month], [year]) => {
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
document.getElementById('payEmail')?.addEventListener('input', () => { document.getElementById('payEmailError').textContent = ''; });

// step 1 → 2: check the headcount (and the e-mail for a new period), then fix the quote to pay (서버 견적)
document.getElementById('btnPayNext')?.addEventListener('click', () => {
  const s = billState;
  const btn = document.getElementById('btnPayNext');
  if (!s || btn.disabled) return;
  const usersEl = document.getElementById('payUsers');
  const emailEl = document.getElementById('payEmail');
  const n = Number(usersEl.value);
  const accounts = s.accountsInUse;
  // 서버도 같은 검사를 다시 한다. 화면은 먼저 알려 줄 뿐이다
  const usersError = !validUsers(n) ? `이용 인원은 1~${s.maxUsers}명으로 입력해 주세요`
    : n < accounts ? `지금 계정이 ${accounts}명이라 ${accounts}명보다 적게 정할 수 없습니다` : '';
  document.getElementById('payUsersError').textContent = usersError;
  if (usersError) return usersEl.focus();
  let email = '';
  if (s.mode !== 'EXTEND') {
    email = emailEl.value.trim();
    const emailError = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? '' : '이메일 형식을 확인해 주세요';
    document.getElementById('payEmailError').textContent = emailError;
    if (emailError) return emailEl.focus();
  }
  btn.disabled = true;
  api.get('/subscription/api/quote', { plan: checkedPlan(), users: n })
    .done(quote => {
      // 그 사이 다른 탭에서 결제해 구분이 바뀌었다: 처음부터 다시
      if (quote.mode !== s.mode) {
        renderBilling();
        showToast('결제 상태가 바뀌어 금액을 다시 계산했습니다. 확인하고 다시 진행해 주세요');
        return;
      }
      if (quote.mode !== 'EXTEND') quote.email = email;
      billQuote = quote;
      billStep = 2;
      document.getElementById('billAgree').checked = false; // agreed again for every payment
      drawBilling();
    })
    .fail(xhr => { document.getElementById('payUsersError').textContent = apiMessage(xhr); })
    .always(() => { btn.disabled = false; });
});
document.getElementById('btnPayBack')?.addEventListener('click', () => { billStep = 1; if (billState) drawBilling(); });
// opening the page (or another page) starts again from step 1 with fresh values
window.addEventListener('hashchange', () => {
  billStep = 1;
  billQuote = null;
  billDone = '';
  billNote = '';
  delete document.getElementById('payUsers').dataset.for;
});

// Mock payment: buttons lock while "processing" so a double click can't record two payments
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
  api.post('/subscription/api/payments', {
    plan: q.plan, users: q.users, mode: q.mode, from: q.from, amount: q.amount, ...(q.email ? { email: q.email } : {}), success,
  })
    .done(p => {
      if (p.result === 'OK') {
        billDone = p.id; // step 3: the payment just applied
        showToast(p.kind === 'EXTEND' ? `연장했습니다. 만료일이 ${ymd(p.to)}로 늘었습니다`
          : p.kind === 'FIRST' ? `결제했습니다. 오늘부터 ${ymd(p.to)}까지 유료로 쓸 수 있습니다`
          : `다시 결제했습니다. ${ymd(p.to)}까지 쓸 수 있습니다`);
        // paying ends read-only at once, and the account limit of the trial is gone (사원 관리·가입 승인)
        document.dispatchEvent(new CustomEvent('subscription:changed'));
      } else {
        billNote = '모의 결제에 실패했습니다. 결제는 반영되지 않았고, 다시 시도할 수 있습니다.';
      }
      renderBilling();
    })
    .fail(xhr => {
      // the state changed since step 1 (e.g. it paid in another tab): quote again instead of paying
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

// Step 3: the payment just applied
function showPayDone(p) {
  const s = billState;
  document.getElementById('payDoneText').textContent = {
    FIRST: `결제했습니다. 오늘부터 ${ymd(p.to)}까지 ${BILL_PLAN[p.plan]} 요금제로 쓰고, 체험 중 ${s.trialSeats}명이던 계정 한도가 풀렸습니다. 계정과 데이터는 그대로입니다.`,
    EXTEND: `연장했습니다. 만료일이 ${ymd(p.to)}로 늘었습니다.` + (p.users !== s.users ? ` 이용 인원은 새 기간(${ymd(p.from)})부터 ${p.users}명입니다.` : ''),
    RESTART: `다시 결제했습니다. 오늘부터 ${ymd(p.to)}까지 쓸 수 있고, 계정과 데이터는 그대로입니다.`,
  }[p.kind] || `결제했습니다. ${ymd(p.to)}까지 쓸 수 있습니다.`;
  fillDetail('payDone', [
    ['상태', `결제 완료 (${whenFull(p.at)})`],
    ['구분', MODE_TEXT[p.kind]],
    ['요금제', `${BILL_PLAN[p.plan]} (${p.months}개월)`],
    ['이용 인원', `${p.users}명`],
    ['결제 금액', `${won(p.amount)} (${p.users}명 × ${won(s.pricePerUser)} × ${p.months}개월${p.pct ? ` × ${100 - p.pct}%` : ''})`],
    ['이용 기간', `${ymd(p.from)} ~ ${ymd(p.to)}`],
  ]);
}

// Payment history, newest first (서버가 최신순으로 준다)
function renderPayHistory() {
  const pays = billState.payments;
  document.getElementById('billHistoryBox').hidden = !pays.length;
  document.getElementById('billHistory').replaceChildren(...pays.map(p => {
    const li = document.createElement('li');
    const what = document.createElement('span');
    const plan = p.plan ? ` ${BILL_PLAN[p.plan]}` : '';
    what.textContent = `${ymd(p.at)} · ${MODE_TEXT[p.kind] || '첫 결제'}${plan} · ${won(p.amount)}${p.users ? ` (${p.users}명)` : ''}` +
      (p.result === 'OK' && p.from && p.to ? ` · ${ymd(p.from)} ~ ${ymd(p.to)}` : '') +
      (p.result === 'REJECTED' && p.reason ? ` · 사유: ${p.reason}` : '');
    const result = document.createElement('span');
    const r = billResultOf(p); // 증설 결제·환불 have their own labels
    result.className = `status status-${r.cls}`;
    result.textContent = r.label;
    li.append(what, result);
    return li;
  }));
}
