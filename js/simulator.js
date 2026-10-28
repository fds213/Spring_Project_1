// Prototype only: imitates a fingerprint reader. The reader matches the finger itself and sends only the
// user number registered on the device; fingerprint data never reaches IEUM. The real reader calls
// POST /api/attendance/punch and the server applies these rules; here they run in the browser and results
// go to localStorage so the groupware screen (index.html) can show them.
// The people on the device are the built-in demo admins (ops.js demoCompanies gives their accounts the same
// deviceUserId), so a punch here shows in the groupware header after signing in with the 시안용 계정 buttons.
const PEOPLE = [
  { deviceUserId: '0001', name: '김체험 (체험상사)', dept: '경영지원팀' },
  { deviceUserId: '0002', name: '박유료 (유료물산)', dept: '경영지원팀' },
  { deviceUserId: '0003', name: '정만료 (만료상사)', dept: '경영지원팀' },
  { deviceUserId: '0005', name: '정퇴사', dept: '영업팀', resigned: true },
  { name: '등록 안 된 사람', unregistered: true },
];
const STATUS_LABEL = { NORMAL: '정상', LATE: '지각', EARLY_LEAVE: '조퇴' };
const WORK_START = '09:00';
const WORK_END = '18:00';
const SCAN_MS = 700;

const pad = n => String(n).padStart(2, '0');
const todayKey = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const nowHM = () => { const d = new Date(); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };

function load(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
}
function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage blocked: the demo still works for this page */ }
}

let selected = null;
let scanning = false;

function scan() {
  if (scanning) return;
  if (!selected) {
    showResult('err', '사람을 먼저 골라 주세요', '실제 단말기에서는 지문을 대는 사람입니다');
    return;
  }
  scanning = true;
  const finger = document.getElementById('finger');
  finger.classList.add('scanning');
  document.getElementById('scanHint').textContent = '지문을 읽는 중…';
  setTimeout(() => {
    finger.classList.remove('scanning');
    document.getElementById('scanHint').textContent = '사람을 고른 뒤 지문 버튼을 누르세요';
    scanning = false;
    punch(selected);
  }, SCAN_MS);
}

function punch(person) {
  // the reader matches fingerprints itself: an unknown finger never reaches the server
  if (person.unregistered) {
    document.getElementById('request').textContent = '보낸 요청 없음 (단말기가 지문을 찾지 못하면 서버로 보내지 않음)';
    return showResult('err', '등록되지 않은 지문입니다', '단말기에 지문을 먼저 등록해 주세요');
  }

  const override = document.getElementById('useOverride').checked;
  const time = override ? document.getElementById('overrideTime').value : nowHM();
  const day = todayKey();
  showRequest(person, override ? `${day}T${time}:00` : null);

  if (person.resigned) return showResult('err', '출입할 수 없는 사용자입니다', '퇴사 처리된 사원입니다');

  // first punch of the day = clock in; every later punch = clock out (last one wins)
  const punches = load('ieum-demo-punches', {});
  const today = punches[day] || (punches[day] = {});
  const rec = today[person.deviceUserId];
  let type, status;
  if (!rec) {
    type = 'IN';
    status = time > WORK_START ? 'LATE' : 'NORMAL';
    today[person.deviceUserId] = { in: time, inStatus: status };
  } else {
    type = 'OUT';
    status = time < WORK_END ? 'EARLY_LEAVE' : 'NORMAL';
    rec.out = time;
    rec.outStatus = status;
  }
  save('ieum-demo-punches', punches);

  const log = load('ieum-demo-log', []);
  log.unshift({ day, time, name: person.name, type, status });
  save('ieum-demo-log', log.slice(0, 20));

  const label = type === 'IN' ? '출근' : '퇴근';
  showResult(status === 'NORMAL' ? 'ok' : 'warn', `${person.name}님 ${label} ${time}`, STATUS_LABEL[status]);
  renderLog();
}

function showResult(kind, big, sub) {
  const box = document.getElementById('result');
  box.className = `panel result ${kind}`;
  box.querySelector('.big').textContent = big;
  document.getElementById('resultSub').textContent = sub;
}

function showRequest(person, punchedAt) {
  const body = { deviceId: 'GATE-01', deviceUserId: person.deviceUserId, authType: 'FINGERPRINT' };
  if (punchedAt) body.punchedAt = punchedAt;
  document.getElementById('request').textContent =
    `POST /api/attendance/punch\nX-Device-Key: (업체별 단말기 키)\n\n${JSON.stringify(body, null, 2)}`;
}

function renderLog() {
  const log = load('ieum-demo-log', []).filter(l => l.day === todayKey());
  const ul = document.getElementById('log');
  ul.replaceChildren();
  if (!log.length) {
    const li = document.createElement('li');
    li.className = 'muted';
    li.textContent = '기록이 없습니다';
    ul.append(li);
    return;
  }
  for (const l of log.slice(0, 8)) {
    const li = document.createElement('li');
    const left = document.createElement('span');
    left.textContent = `${l.name} · ${l.type === 'IN' ? '출근' : '퇴근'}`;
    const right = document.createElement('span');
    right.className = 'muted';
    right.textContent = `${l.time} ${STATUS_LABEL[l.status]}`;
    li.append(left, right);
    ul.append(li);
  }
}

function tick() {
  const d = new Date();
  document.getElementById('clock').textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  document.getElementById('today').textContent = `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일`;
}

const list = document.getElementById('people');
for (const person of PEOPLE) {
  const b = document.createElement('button');
  b.className = 'person';
  b.type = 'button';
  b.setAttribute('aria-pressed', 'false');
  const name = document.createElement('span');
  name.textContent = person.name;
  const meta = document.createElement('small');
  meta.textContent = person.unregistered ? '단말기에 지문 없음' : `${person.dept} · 사용자 번호 ${person.deviceUserId}${person.resigned ? ' · 퇴사' : ''}`;
  b.append(name, meta);
  b.addEventListener('click', () => {
    selected = person;
    list.querySelectorAll('.person').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
  });
  list.append(b);
}

document.getElementById('finger').addEventListener('click', scan);

document.getElementById('btnReset').addEventListener('click', () => {
  const punches = load('ieum-demo-punches', {});
  delete punches[todayKey()];
  save('ieum-demo-punches', punches);
  save('ieum-demo-log', load('ieum-demo-log', []).filter(l => l.day !== todayKey()));
  showResult('idle', '지문을 대 주세요', '오늘 기록을 지웠습니다');
  renderLog();
});

tick();
setInterval(tick, 1000);
renderLog();
