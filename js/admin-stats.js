// Operator: 운영 데이터. Draws only from the applications 신청 현황 shows: the trial signups (o.at) and the
// payments recorded on them (o.payments: the first payment, extensions and paying again after expiry).
// Load order: after ops.js and admin-inquiry.js (uses its h/span helpers); like admin-inquiry.js, nothing here
// runs at the top level, so it only needs them once a screen is drawn.
// Charts are plain HTML/CSS bars, no chart library: the page is a static PWA and two simple charts don't need one.

// With many companies the bars would run on forever: show the top 7 and fold the rest into one "그 외" bar
const SALES_TOP = 7;
const DAILY_DAYS = 7;

const localDayKey = d => `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;

// Every successful payment, with the company it belongs to, on the day it was made.
const okPayments = orders => orders.flatMap(o => (o.payments || []).filter(p => p.result === 'OK')
  .map(p => ({ company: o.company, kind: p.kind || 'FIRST', users: p.users ?? o.users, amount: p.amount, at: p.at })));

// --- one shared tooltip for every mark; contents are set with textContent only ---
function bindTip(el, rows) {
  const tip = document.getElementById('chartTip');
  const place = (x, y) => {
    tip.style.left = `${Math.max(8, Math.min(x + 14, window.innerWidth - tip.offsetWidth - 8))}px`;
    tip.style.top = `${Math.max(8, Math.min(y + 14, window.innerHeight - tip.offsetHeight - 8))}px`;
  };
  const show = (x, y) => {
    // value first and strong, the name after it: here the reader already has the series and wants the number
    tip.replaceChildren(...rows.map(([value, label, keyClass]) =>
      h('div', 'tip-row', ...(keyClass ? [h('i', `key ${keyClass}`)] : []), h('strong', '', value), h('span', '', label))));
    tip.hidden = false;
    place(x, y);
  };
  el.addEventListener('pointermove', e => show(e.clientX, e.clientY));
  el.addEventListener('pointerleave', () => { tip.hidden = true; });
  el.addEventListener('focus', () => { const r = el.getBoundingClientRect(); show(r.right, r.top); });
  el.addEventListener('blur', () => { tip.hidden = true; });
}

// Horizontal bars, one per paying company: its current monthly fee (names are long, so bars run sideways)
function drawSales(paid) {
  const box = document.getElementById('chSales');
  if (!paid.length) { box.replaceChildren(h('p', 'inq-hint', '아직 결제한 업체가 없습니다')); return []; }

  const sorted = paid.map(o => ({ name: o.company, users: o.users, total: o.total })).sort((a, b) => b.total - a.total);
  const rows = sorted.slice(0, SALES_TOP);
  if (sorted.length > SALES_TOP) {
    const rest = sorted.slice(SALES_TOP);
    rows.push({ name: `그 외 ${rest.length}곳`, users: rest.reduce((s, c) => s + c.users, 0), total: rest.reduce((s, c) => s + c.total, 0) });
  }

  const max = rows[0].total;
  box.replaceChildren(...rows.map(c => {
    // a button, not a div, so keyboard users can focus a bar and get the same tooltip
    const bar = h('button', 'bar');
    bar.type = 'button';
    bar.style.width = `${Math.max(1, (c.total / max) * 100)}%`;
    bar.setAttribute('aria-label', `${c.name}: 월 ${won(c.total)}, ${c.users}명`);
    bindTip(bar, [[won(c.total), c.name], [`${c.users}명`, '이용 인원']]);
    return h('div', 'bar-row', h('span', 'bar-label', c.name), h('div', 'bar-track', bar), h('span', 'bar-value', won(c.total)));
  }));
  return rows;
}

// Stacked columns, one per day: 체험 신청 on the baseline, 결제 완료 on top
function drawDaily(orders, pays) {
  const box = document.getElementById('chDaily');
  const legend = document.getElementById('chDailyLegend');
  const days = [];
  const today = new Date();
  for (let i = DAILY_DAYS - 1; i >= 0; i--) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
    days.push({ key: localDayKey(d), label: `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`, SIGNUP: 0, PAID: 0 });
  }
  const count = (iso, field) => { const day = days.find(x => x.key === localDayKey(new Date(iso))); if (day) day[field]++; };
  orders.forEach(o => count(o.at, 'SIGNUP'));
  pays.forEach(p => count(p.at, 'PAID'));

  // two series: the legend is always there, and the tooltip and table carry every number
  legend.replaceChildren(
    h('span', 'legend-item', h('i', 'key key-1'), '체험 신청'),
    h('span', 'legend-item', h('i', 'key key-2'), '결제 완료'));

  const top = Math.max(...days.map(d => d.SIGNUP + d.PAID));
  if (top === 0) { box.replaceChildren(h('p', 'inq-hint', '최근 7일 안에 들어온 신청과 결제가 없습니다')); return days; }
  const axisMax = Math.max(2, Math.ceil(top / 2) * 2); // even, so the middle tick is a whole number

  const plot = h('div', 'col-plot');
  for (const v of [0, axisMax / 2, axisMax]) {
    const pct = (v / axisMax) * 100;
    const tick = h('span', 'col-tick', String(v));
    tick.style.bottom = `${pct}%`;
    plot.append(tick);
    if (v > 0) { const line = h('div', 'col-grid'); line.style.bottom = `${pct}%`; plot.append(line); }
  }
  const cols = h('div', 'col-row');
  for (const d of days) {
    const total = d.SIGNUP + d.PAID;
    const stack = h('div', 'col-stack');
    stack.style.height = `${(total / axisMax) * 100}%`;
    const segs = [['SIGNUP', 'seg-1'], ['PAID', 'seg-2']].filter(([k]) => d[k] > 0);
    segs.forEach(([k, cls], i) => {
      const seg = h('div', `seg ${cls}${i === segs.length - 1 ? ' seg-top' : ''}`);
      seg.style.flexGrow = d[k];
      stack.append(seg);
    });
    const col = h('div', 'col');
    col.tabIndex = 0;
    col.setAttribute('aria-label', `${d.label}: 체험 신청 ${d.SIGNUP}건, 결제 완료 ${d.PAID}건`);
    bindTip(col, [[`${d.SIGNUP}건`, '체험 신청', 'key-1'], [`${d.PAID}건`, '결제 완료', 'key-2']]);
    col.append(stack);
    cols.append(col);
  }
  plot.append(cols);
  const labels = h('div', 'col-labels', ...days.map(d => h('span', '', d.label)));
  box.replaceChildren(plot, labels);
  return days;
}

// The same numbers as text, so nothing is only reachable by hovering
function drawTables(salesRows, days) {
  const table = (head, rows) => h('div', 'table-wrap', h('table', 'table',
    h('thead', '', h('tr', '', ...head.map(t => h('th', '', t)))),
    h('tbody', '', ...rows.map(r => h('tr', '', ...r.map(c => h('td', '', c)))))));
  document.getElementById('chTables').replaceChildren(
    ...(salesRows.length ? [h('p', 'inq-hint', '업체별 월 이용료'), table(['업체', '이용 인원', '월 이용료'], salesRows.map(c => [c.name, `${c.users}명`, won(c.total)]))] : []),
    h('p', 'inq-hint', '일별 체험 신청·결제 (최근 7일)'),
    table(['날짜', '체험 신청', '결제 완료'], days.map(d => [d.label, `${d.SIGNUP}건`, `${d.PAID}건`])));
}

// "이번 달 1일~오늘" 과 "지난달 1일~같은 날짜" 를 비교한다. 월 중간에 지난달 전체와 비교하면
// 이번 달은 아직 덜 지났으니 항상 줄어든 것처럼 보인다. 지난달이 더 짧으면(예: 31일 vs 30일) 그 달 말일까지만 센다.
function monthWindow(now, monthOffset) {
  const start = new Date(now.getFullYear(), now.getMonth() + monthOffset, 1);
  const daysInMonth = new Date(start.getFullYear(), start.getMonth() + 1, 0).getDate();
  const end = new Date(start.getFullYear(), start.getMonth(), Math.min(now.getDate(), daysInMonth), 23, 59, 59, 999);
  return [start, end];
}

// new paying seats = the headcount of first payments and of paying again after expiry; every successful payment counts toward
// the payment count and the sales
function sumWindow(pays, [start, end]) {
  const rows = pays.filter(p => { const t = new Date(p.at); return t >= start && t <= end; });
  return {
    users: rows.filter(p => p.kind === 'FIRST' || p.kind === 'RESTART').reduce((s, p) => s + p.users, 0),
    count: rows.filter(p => p.kind !== 'REFUND').length,
    sales: rows.reduce((s, p) => s + p.amount, 0),
  };
}

// prev 가 0 이면 증감률을 계산할 수 없다 (0 으로 나누기). 무한대 대신 "비교할 값이 없음" 이라고 말한다.
function deltaText(cur, prev, fmt) {
  if (prev === 0) return cur === 0 ? '지난달 같은 기간과 동일 (0)' : '지난달 같은 기간 데이터 없음';
  if (cur === prev) return '지난달 같은 기간 대비 변화 없음';
  const pct = Math.round(Math.abs(cur - prev) / prev * 100);
  const mark = cur > prev ? '▲' : '▼';
  return `지난달 같은 기간 대비 ${mark} ${pct === 0 ? '1% 미만' : pct + '%'} (지난달 ${fmt(prev)})`;
}

function renderStats() {
  const orders = loadStore(ORDERS_KEY, []);
  const pays = okPayments(orders);
  const now = new Date();
  const cur = sumWindow(pays, monthWindow(now, 0));
  const prev = sumWindow(pays, monthWindow(now, -1));

  const people = n => `${n.toLocaleString('ko-KR')}명`;
  const cases = n => `${n}건`;
  const put = (id, text) => { document.getElementById(id).textContent = text; };

  put('statsMonth', `${now.getFullYear()}년 ${now.getMonth() + 1}월 1일 ~ ${now.getMonth() + 1}월 ${now.getDate()}일`);
  put('stUsers', people(cur.users));
  put('stPaidCount', cases(cur.count));
  put('stSales', won(cur.sales));
  put('dUsers', deltaText(cur.users, prev.users, people));
  put('dPaidCount', deltaText(cur.count, prev.count, cases));
  put('dSales', deltaText(cur.sales, prev.sales, won));

  const salesRows = drawSales(orders.filter(o => trialState(o).status === 'PAID')); // read-only (expired) companies don't pay now
  const days = drawDaily(orders, pays);
  drawTables(salesRows, days);
}
