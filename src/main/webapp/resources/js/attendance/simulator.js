// 지문 단말기 시뮬레이터 (views/attendance/simulator.jsp). 시연용 단말기 역할을 한다 (요구분석서 3.10, 기능명세서 1.9).
// 실제 단말기는 지문을 스스로 맞춰 보고, 이음에는 단말기에 등록된 사용자 번호만 보낸다(지문 정보는 보내지 않는다).
// 그래서 "등록 안 된 지문"은 서버로 보내지 않고 이 화면에서 끝난다.
//
//   GET  /api/attendance/device  (X-Device-Key) → { companyName, deviceName, people: [{ userNo, name, dept, serving }] }
//   POST /api/attendance/punch   (X-Device-Key) { userNo, type: IN | OUT } → { name, userNo, type, date, time, status }
//
// 출퇴근 시각은 서버가 받은 시각이다. 화면의 시계는 보여 주기만 하고 서버로 보내지 않는다.
// 단말기 키는 이 브라우저(localStorage)에만 둔다. 실제 단말기의 설정값과 같은 역할이다.

const SIM_KEY_STORE = 'ieum-device-key';
const SIM_STATUS = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴' };
const SIM_TYPE = { IN: '출근', OUT: '퇴근' };
const SIM_SCAN_MS = 700;
const simCtx = document.body.dataset.ctx || '';
const simEl = id => document.getElementById(id);
const simPad = n => String(n).padStart(2, '0');

const sim = { key: '', people: [], selected: null, type: 'IN', scanning: false, log: [] };

function simStoredKey() {
  try { return localStorage.getItem(SIM_KEY_STORE) || ''; } catch (e) { return ''; }
}
function simStoreKey(key) {
  try { if (key) localStorage.setItem(SIM_KEY_STORE, key); else localStorage.removeItem(SIM_KEY_STORE); } catch (e) { /* 저장소 막힘: 이 페이지에서만 쓴다 */ }
}

// 서버 요청: 실패하면 { status, message } 를 던진다
async function simFetch(method, path, body) {
  let res;
  try {
    res = await fetch(simCtx + path, {
      method,
      headers: Object.assign({ 'X-Device-Key': sim.key }, body ? { 'Content-Type': 'application/json' } : {}),
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw { status: 0, message: '이음 서버에 연결할 수 없습니다. 잠시 뒤 다시 시도해 주세요' };
  }
  let data = null;
  try { data = await res.json(); } catch (e) { /* 본문 없음 */ }
  if (!res.ok) throw { status: res.status, message: (data && data.message) || '단말기 요청을 처리하지 못했습니다' };
  return data;
}

// ---------- 1) 단말기 연결 ----------
function showConnect(message) {
  simEl('simConnect').hidden = false;
  simEl('simMain').hidden = true;
  simEl('simKeyError').textContent = message || '';
  simEl('simKey').value = '';
  simEl('simKey').focus();
}

async function connect(key) {
  sim.key = key;
  try {
    const info = await simFetch('GET', '/api/attendance/device');
    simStoreKey(key);
    sim.people = info.people;
    simEl('simTitle').textContent = `${info.companyName} · ${info.deviceName}`;
    simEl('simDevice').textContent = `등록된 사람 ${info.people.length}명`;
    renderPeople();
    simEl('simConnect').hidden = true;
    simEl('simMain').hidden = false;
    simGo(1);
  } catch (err) {
    // 키가 틀렸을 때만 저장한 키를 지운다 (서버가 잠깐 꺼진 것이면 키는 그대로 두고 다시 시도하게 한다)
    if (err.status === 401) simStoreKey('');
    showConnect(err.status === 401 ? '단말기 키가 맞지 않습니다' : err.message);
  }
}

simEl('simKeyForm').addEventListener('submit', e => {
  e.preventDefault();
  const key = simEl('simKey').value.trim();
  if (!key) { simEl('simKeyError').textContent = '단말기 키를 입력하세요'; return; }
  connect(key);
});
simEl('simChangeKey').addEventListener('click', () => { simStoreKey(''); sim.key = ''; showConnect(''); });

// ---------- 단계: 한 화면에 하나씩 (1 사람 → 2 출근·퇴근 → 3 지문 → 결과) ----------
const SIM_BACK_MS = 5000; // 결과를 보여 준 뒤 처음으로 돌아가기까지
let simBackTimer = 0;

function simGo(step) {
  clearTimeout(simBackTimer);
  document.querySelectorAll('.sim-step').forEach(s => { s.hidden = s.dataset.step !== String(step); });
  // 진행 표시: 지금 단계는 강조, 지난 단계는 완료 표시 (결과 화면에서는 셋 다 완료)
  const now = step === 'done' ? 4 : Number(step);
  document.querySelectorAll('#simSteps li').forEach(li => {
    const n = Number(li.dataset.step);
    li.classList.toggle('on', n === now);
    li.classList.toggle('done', n < now);
  });
  document.querySelector('.sim-first-only').hidden = step !== 1 && step !== '1';
  if (String(step) === '1') sim.selected = null;
  if (String(step) === '2') simEl('simChosenPerson').textContent = simWho(sim.selected);
  if (String(step) === '3') simEl('simChosenPunch').textContent = `${simWho(sim.selected)} · ${SIM_TYPE[sim.type]}`;
  if (step === 'done') {
    let left = SIM_BACK_MS / 1000;
    const hint = simEl('simBackHint');
    hint.textContent = `${left}초 뒤 처음 화면으로 돌아갑니다`;
    const tickBack = () => {
      left -= 1;
      if (left <= 0) return simGo(1);
      hint.textContent = `${left}초 뒤 처음 화면으로 돌아갑니다`;
      simBackTimer = setTimeout(tickBack, 1000);
    };
    simBackTimer = setTimeout(tickBack, 1000);
  }
}
const simWho = p => (p ? (p.unregistered ? p.name : `${p.name} (${p.userNo}번)`) : '');
document.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => simGo(b.dataset.go)));

// ---------- 1) 사람 고르기 → 2) 출근·퇴근 고르기 ----------
function renderPeople() {
  const list = simEl('simPeople');
  const people = sim.people.map(p => ({ ...p, unregistered: false }))
    .concat([{ name: '등록 안 된 사람', unregistered: true }]);
  list.replaceChildren(...people.map(p => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'sim-person';
    b.setAttribute('aria-pressed', 'false');
    const name = document.createElement('span');
    name.textContent = p.name;
    const meta = document.createElement('small');
    meta.textContent = p.unregistered ? '단말기에 지문 없음' : `${p.userNo}번${p.dept ? ' · ' + p.dept : ''}${p.serving ? '' : ' · 재직 아님'}`;
    b.append(name, meta);
    b.addEventListener('click', () => {
      sim.selected = p;
      simGo(2);
    });
    return b;
  }));
  sim.selected = null;
}

document.querySelectorAll('.sim-type').forEach(b => b.addEventListener('click', () => {
  sim.type = b.dataset.type;
  simGo(3);
}));

// ---------- 3) 지문 대기 → 서버로 체크 → 결과 ----------
function showResult(kind, big, sub) {
  const box = simEl('simResult');
  box.className = `sim-panel sim-step sim-result ${kind}`;
  box.querySelector('.sim-big').textContent = big;
  simEl('simResultSub').textContent = sub || '';
  simGo('done');
}

async function punch(person) {
  if (person.unregistered) {
    return showResult('err', '등록되지 않은 지문입니다', '단말기에 지문을 먼저 등록해 주세요');
  }
  try {
    const r = await simFetch('POST', '/api/attendance/punch', { userNo: person.userNo, type: sim.type });
    const hm = r.time.slice(0, 5);
    showResult(r.status === 'NORMAL' ? 'ok' : 'warn', `${r.name}님 ${SIM_TYPE[r.type]} ${hm}`, SIM_STATUS[r.status]);
    sim.log.unshift(`${hm} · ${r.name} · ${SIM_TYPE[r.type]} · ${SIM_STATUS[r.status]}`);
    renderLog();
  } catch (err) {
    if (err.status === 401) return showConnect('단말기 키가 맞지 않습니다. 다시 입력해 주세요');
    showResult('err', err.message, err.status === 409 ? '오늘 기록은 그대로입니다' : '');
  }
}

function scan() {
  if (sim.scanning) return;
  if (!sim.selected) return showResult('err', '사람을 먼저 골라 주세요', '실제 단말기에서는 지문을 대는 사람입니다');
  sim.scanning = true;
  const finger = simEl('simFinger');
  finger.classList.add('scanning');
  simEl('simHint').textContent = '지문을 읽는 중…';
  setTimeout(async () => {
    finger.classList.remove('scanning');
    simEl('simHint').textContent = '지문 버튼을 누르세요';
    await punch(sim.selected);
    sim.scanning = false;
  }, SIM_SCAN_MS);
}
simEl('simFinger').addEventListener('click', scan);

function renderLog() {
  const ul = simEl('simLog');
  if (!sim.log.length) return;
  ul.replaceChildren(...sim.log.slice(0, 10).map(t => {
    const li = document.createElement('li');
    li.textContent = t;
    return li;
  }));
}

// ---------- 시계 (보여 주기만) ----------
function tick() {
  const d = new Date();
  simEl('simClock').textContent = `${simPad(d.getHours())}:${simPad(d.getMinutes())}:${simPad(d.getSeconds())}`;
  simEl('simToday').textContent = `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 · 기록 시각은 이음 서버 기준`;
}
tick();
setInterval(tick, 1000);

const saved = simStoredKey();
if (saved) connect(saved); else showConnect('');
