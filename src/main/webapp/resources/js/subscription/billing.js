// 업체 관리 > 이용 결제 (views/subscription/billing.jsp). 요구분석서 PAY-01·02, SUB-01·03.
// 자동 결제는 없고 업체 관리자가 직접 결제한다. 금액과 기간은 서버가 계산한다(견적 API).
// - 체험 중이거나 체험이 끝난 뒤: 첫 결제. 인원을 정하고, 결제한 날부터 유료 이용
// - 유료 이용 중: 연장. 지금 인원 그대로 만료일 다음 날부터 이어진다(서버가 아직 mode=EXTEND를 주지 않는다, 아래 참고)
// 순서: 1. 요금제 선택 → 2. 결제(주문을 만들고 토스 결제창 열기) → 결제창이 이 화면으로 돌아옴
//       → 서버가 토스 승인을 확인 → 3. 결제 완료. 돌아온 것만으로는 유료가 되지 않는다(PAY-02).
// 지금은 첫 결제 월간만 된다(요구분석서 3장 범위 표). 연간·연장은 '핵심 뒤 추가'에서 서버와 같이 연다.
// 이번 범위에서 빠진 것: 인원 증설, 월간→연간 전환, 결제 안내 메일, 만료 후 재구매.

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
let billConfirming = null; // 토스에서 돌아와 승인을 확인하는 중인 값 { orderId, paymentKey, amount }
const BILLING_PATH = '/subscription/billing';

const fillDetail = (id, rows) => document.getElementById(id).replaceChildren(...rows.flatMap(([k, v]) => {
  const dt = document.createElement('dt'); dt.textContent = k;
  const dd = document.createElement('dd'); dd.textContent = v;
  return [dt, dd];
}));
const checkedPlan = () => document.querySelector('input[name="payPlan"]:checked').value;

// 화면을 열 때. 토스 결제창이 돌아온 주소면 그 처리부터 한다
function startBilling() {
  const q = new URLSearchParams(location.search);
  if (location.pathname.endsWith(`${BILLING_PATH}/success`)) {
    billConfirming = { orderId: q.get('orderId'), paymentKey: q.get('paymentKey'), amount: Number(q.get('amount')) };
    renderBilling().always(confirmToss);
  } else if (location.pathname.endsWith(`${BILLING_PATH}/fail`)) {
    failToss(q);
  } else {
    renderBilling();
  }
}

// 결제 정보를 주소에서 지운다. 새로 고쳐도 다시 처리하지 않게
const cleanBillingUrl = () => history.replaceState(null, '', IEUM.url(BILLING_PATH));

// 화면을 열 때와 결제한 뒤: 이용 상태를 새로 받아 그린다
function renderBilling() {
  return api.get('/subscription/api/billing')
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
    ...(s.trialFrom ? [['체험 기간', `${ymd(s.trialFrom)} ~ ${ymd(s.trialEnd)} (14일)`]] : []),
    ...(s.paid ? [
      ['요금제', BILL_PLAN[s.plan] || '월간'],
      ['이용 기간', `${ymd(s.serviceFrom)} ~ ${ymd(s.end)}`],
      ['결제 방식', '자동 결제 없음 · 만료 전에 직접 연장'],
    ] : []),
    ['계정 한도', s.paid ? `사용 중 ${s.users}명 (구매 인원)` : `관리자 포함 ${s.trialSeats}명 (결제하면 해제)`],
  ]);
  renderPayHistory();

  // 이 페이지에서 방금 한 결제: 페이지를 떠나기 전까지 3단계
  const done = billDone ? s.payments.find(p => p.id === billDone) : null;
  // 결제할 수 없으면 결제 칸은 보이지 않는다. 방금 결제해 유료가 됐거나 승인을 확인하는 중이면 그 결과를 보여 준다
  const showPay = s.canPay || done || billConfirming;
  document.getElementById('billPay').hidden = !showPay;
  if (!showPay) return;
  if (billConfirming) {
    billStep = 2;
    document.getElementById('billPayTitle').textContent = '결제 승인 확인';
    document.getElementById('billHint').textContent = '서버가 승인을 확인해야 유료로 바뀝니다';
    return renderBillSteps(s.mode, null);
  }
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
  [1, 2, 3].forEach(n => { document.getElementById(`billStep${n}`).hidden = n !== billStep || !!billConfirming; });
  document.getElementById('billConfirm').hidden = !billConfirming;
  if (billConfirming) return;
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

// 1단계: 요금제 가격과 금액·기간. 인원을 바꾸는 동안 다시 계산한다 (서버 견적)
function renderPayPreview() {
  const n = Number(document.getElementById('payUsers').value);
  const ok = validUsers(n);
  const users = ok ? n : billState.users;
  const my = ++billQuoteSeq;
  api.get('/subscription/api/quote', { plan: checkedPlan(), users })
    .done(q => {
      if (my !== billQuoteSeq) return;
      document.getElementById('planPriceMONTHLY').textContent = `${won(q.amount)} / 월`;
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
let billQuoteDelay = 0; // 타자 치는 동안은 모았다가 250ms 멈추면 견적을 받는다
document.getElementById('payUsers')?.addEventListener('input', () => {
  document.getElementById('payUsersError').textContent = '';
  clearTimeout(billQuoteDelay);
  billQuoteDelay = setTimeout(() => { if (billState) renderPayPreview(); }, 250);
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

// 2단계 결제: 서버에 주문을 만들고 받은 값으로 토스 결제창을 연다.
// 처리하는 동안 버튼을 막아 두 번 눌러 주문이 두 개 생기지 않게 한다
function payBill() {
  const ok = document.getElementById('btnBillOk');
  const error = document.getElementById('billError');
  if (ok.disabled || !billQuote) return;
  if (!document.getElementById('billAgree').checked) {
    error.textContent = '이용 조건에 동의해야 결제할 수 있습니다';
    return;
  }
  error.textContent = '';
  ok.disabled = true;
  document.getElementById('billBusy').textContent = '결제창을 열고 있습니다…';
  const q = billQuote;
  api.post('/subscription/api/orders', { plan: q.plan, users: q.users, amount: q.amount })
    .done(openToss)
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
      ok.disabled = false;
      document.getElementById('billBusy').textContent = '';
    });
}
document.getElementById('btnBillOk')?.addEventListener('click', payBill);

// 토스 결제창. 카드 정보는 토스 창에만 입력하고 이 서버로 오지 않는다. 끝나면 successUrl·failUrl 로 이 화면에 돌아온다
function openToss(order) {
  const error = document.getElementById('billError');
  if (typeof TossPayments !== 'function') {
    error.textContent = '토스 결제창을 불러오지 못했습니다. 네트워크를 확인하고 새로 고쳐 주세요';
    return;
  }
  try {
    TossPayments(order.clientKey).payment({ customerKey: TossPayments.ANONYMOUS }).requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: order.amount },
      orderId: order.orderNo,
      orderName: order.orderName,
      successUrl: location.origin + IEUM.url(`${BILLING_PATH}/success`),
      failUrl: location.origin + IEUM.url(`${BILLING_PATH}/fail`),
    }).catch(e => { error.textContent = tossErrorText(e); });
  } catch (e) {
    error.textContent = tossErrorText(e);
  }
}
const tossErrorText = e => (e && e.code === 'USER_CANCEL' ? '결제를 취소했습니다' : '결제창을 열지 못했습니다. 다시 시도해 주세요');

// 결제창이 successUrl로 돌아왔다: 결제 인증만 끝난 상태다. 서버가 토스에 승인을 요청·확인해야 유료가 된다(PAY-02)
function confirmToss() {
  const text = document.getElementById('billConfirmText');
  const retry = document.getElementById('btnConfirmRetry');
  retry.hidden = true;
  text.textContent = '결제 승인을 확인하고 있습니다. 이 화면을 닫지 말아 주세요…';
  api.post('/subscription/api/orders/confirm', billConfirming)
    .done(p => {
      billConfirming = null;
      billDone = p.id; // 3단계: 방금 반영된 결제
      cleanBillingUrl();
      showToast(`결제했습니다. ${ymd(p.to)}까지 유료로 쓸 수 있습니다`);
      renderBilling();
    })
    .fail(xhr => {
      // 보안 토큰이 바뀌었다(CSRF 403). 같은 토큰으로는 계속 거절되니 새로 고치게 한다. 주소에 값이 남아 있어 다시 확인된다
      if (!xhr.responseJSON && xhr.status === 403) {
        text.textContent = '화면이 오래되어 확인하지 못했습니다. 이 화면을 새로 고쳐 주세요';
        return;
      }
      // 승인 결과를 아직 모른다(서버가 retry로 알렸거나, 연결이 끊겨 답을 못 받았다).
      // 같은 값으로 다시 확인할 수 있다(서버가 한 번만 반영한다). 그래서 값을 지우지 않는다
      if (!xhr.responseJSON || xhr.responseJSON.retry) {
        text.textContent = apiMessage(xhr, '승인 결과를 받지 못했습니다. 다시 확인해 주세요');
        retry.hidden = false;
        return;
      }
      billConfirming = null;
      billStep = 1;
      cleanBillingUrl();
      showToast(apiMessage(xhr));
      renderBilling();
    });
}
document.getElementById('btnConfirmRetry')?.addEventListener('click', confirmToss);

// 결제창이 failUrl로 돌아왔다. 주소의 문구는 누구나 바꿀 수 있어 화면에 그대로 보이지 않는다
function failToss(q) {
  const code = q.get('code');
  cleanBillingUrl();
  showToast(code === 'PAY_PROCESS_CANCELED' ? '결제를 취소했습니다. 결제는 반영되지 않았습니다'
    : '결제에 실패했습니다. 결제는 반영되지 않았고, 다시 시도할 수 있습니다');
  api.post('/subscription/api/orders/fail', { orderId: q.get('orderId'), code, message: q.get('message') })
    .always(renderBilling);
}

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
