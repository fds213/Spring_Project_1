// 인사 관리 > 사원 관리 (views/employee/admin/list.jsp)·가입 승인 (views/employee/join.jsp), 그리고 계정 생성·비밀번호 찾기
// (views/common/join.jsp, join-password.jsp. 아직 이 파일을 불러오지 않는다: 백엔드 API가 생기면 그 주소에 맞춰 붙인다).
// 계정이 생기는 흐름, 자리 수, 비활성화·차단 규칙은 서버가 정하고 지킨다. 이번 구현 범위(요구분석서 3장 단순화 ①②):
// 승인되면 바로 사용, 구매 인원(체험은 관리자 포함 10명)이 차면 다음 승인을 거절한다. 1시간 기한·자리 대기·직원 이메일 인증은 없다.
// 이 화면은 서버가 준 값으로 그리고, 누를 수 있는 버튼도 서버가 준 값(actions, perms)으로 정한다. 안내 문구만 먼저 보여 준다.
//
// 서버 API (프론트 제안). 실패하면 { message }: 400 입력, 403 권한·읽기 전용, 404 없음, 409 그사이 바뀜, 410 다 쓴 인증, 429 너무 잦음
// 사원 관리 (업체 관리자·인사 담당):
//   GET    /employee/admin/api/employees                    → { perms: { admin, pay }, readOnly, caps, seats, counts, items: [직원] }
//            caps: { paid, active 쓸 수 있는 계정(체험 10명, 결제 뒤 구매 인원) } | null
//            seats: { used 사용 중 계정, full, room }   counts: { total, off 비활성·차단, seat }
//            직원: { empNo, name, email, dept, deptType, head, rank, role, empType, work, gender, status, at,
//                    actions: ['off'|'suspend'|'head'|'on'|'unsuspend'], canRetire }
//   POST   /employee/admin/api/employees/{empNo}/status       { action: off|suspend|on } → { name, status, wasBlocked, released }
//   POST   /employee/admin/api/employees/{empNo}/head         { head: true|false } → { name, dept, head }
//   POST   /employee/admin/api/employees/{empNo}/hr           { empType, work, gender } → { name, turnedOff, released }
// 가입 승인 (업체 관리자·부서장):
//   GET    /employee/api/join        → { perms: { admin }, readOnly, companyCode, myDept, caps, seats, canApprove(자리가 남았는지), nextEmpNo,
//            openings: [{ scope, all, until, by, byName, mine }], depts (업체 관리자만), requests: [요청] }
//            요청: { reqId, name, dept, deptType, rank, email, requestAt }
//   GET    /employee/api/join/count                           → { count }  (메뉴 배지: 내가 처리할 승인 대기 요청)
//   POST   /employee/api/join/openings          { depts (업체 관리자), minutes } → { depts, until }   (가입 활성화 켜기)
//   POST   /employee/api/join/openings/{scope}/delete        → { scope }  (한 부서 끄기)
//   POST   /employee/api/join/openings/delete                → { count }  (모두 끄기, 업체 관리자)
//   POST   /employee/api/join/requests/{reqId}/approve        → { name, empNo }   (구매 인원이 차 있으면 409)
//   POST   /employee/api/join/requests/{reqId}/reject         → { name }
// 로그인 화면 (로그인 없이):
//   GET    /join/api/companies/{code}          → { companyCode, companyName, depts (열린 부서), ranks }
//            가입할 수 없는 코드는 없는 코드와 똑같이 404(화면은 아무것도 보여 주지 않는다), 확인을 너무 많이 틀리면 429
//   POST   /join/api/companies/{code}/requests  { name, dept, rank, email, password } → 요청 상태
//   GET    /join/api/companies/{code}/requests/{reqId}  → 요청 상태 (5초마다)
//   POST   /join/api/companies/{code}/requests/{reqId}/delete → { ok }  (요청 취소)
//            요청 상태: { reqId, state: WAITING|APPROVED|ENDED, reason: REJECTED|OFF|OTHER|null, companyCode, companyName,
//                         name, dept, rank, email, empNo, depts, ranks }
//   POST   /join/api/password/code         { companyCode, empNo } → { sent: true }
//            계정이 있든 없든 똑같이 답한다 (ACC-07)
//   POST   /join/api/password/verify       { companyCode, empNo, code } → { resetToken }
//   POST   /join/api/password/reset        { resetToken, password } → { companyCode, empNo }
// 헤더 (로그인한 직원):
//   POST   /employee/api/password                     { current, password } → { ok }   (읽기 전용이어도 된다)
// 비밀번호 찾기 인증 코드: 6자리, 10분, 5번 틀리면 못 쓴다(410), 다시 보내기는 1분에 한 번(429).
// 브라우저 저장소를 쓰는 곳은 하나뿐이다: 계정 생성을 한 기기가 자기 요청(업체 코드·요청 번호)을 기억하는 MY_JOIN_KEY.
// 화면을 닫았다 열어도 요청을 이어 보려는 것이다. 쿠키나 페이지에 둔 요청 번호로 바꿔도 된다.

// ---------- 공통 코드 표시 글자 (팀 시트 "쌍용_이음_공통 코드 테이블" 기준) ----------
// 서버는 공통 코드 테이블에서 읽는다. 시트에 없는 값은 화면에서 정한 값을 그대로 쓴다
// (가입 흐름 상태 REQUESTED·REJECTED·APPROVED·WAITING·DELETED·CANCELED, 일반 부서).
// 계정 형태 (ROLE): 한 계정에 하나. IT 담당·인사 담당은 역할이 아니라 소속 부서의 부서 형태로 정한다.
const ROLE_CODE = { ROLE_OPR: '이음 운영자', ROLE_ADMIN: '업체 관리자', ROLE_USR: '업체 사용자' };
// 부서 형태 (DEPT_TYPE): 부서마다 하나 또는 없음(일반 부서, ''). 부서 관리(org-admin.js)에서 정한다.
// IT 부서 사람은 IT 담당 메뉴, 인사 부서 사람은 인사 담당 메뉴를 받는다. 운영 부서는 아직 따로 붙는 메뉴가 없다.
const DEPT_TYPE = { DEPT_IT: 'IT 부서', DEPT_HR: '인사 부서', DEPT_OPS: '운영 부서' };
const deptTypeLabel = type => DEPT_TYPE[type] || '일반';
// 사원 관리의 담당 표시 (부서 형태가 주는 것)
const DEPT_DUTY = { DEPT_IT: 'IT 담당', DEPT_HR: '인사 담당' };
// 고용 형태 (EMP_TYPE), 성별 (GENDER): 사원 관리 > 인사 정보에서 정한다. 성별은 넣지 않아도 된다
const EMP_TYPE = { EMP_REGULAR: '정규직', EMP_CONTRACT: '계약직', EMP_DISPATCH: '파견직', EMP_INTERN: '인턴' };
const GENDER = { GD_MALE: '남성', GD_FEMALE: '여성', GD_OTHER: '기타' };
// 상태 (STATUS): 휴직 중에도 계정은 쓸 수 있어야 해서 두 칸에 나눠 담는다.
//   계정 상태 status: ST_ACTIVE 사용 중 / ST_INACTIVE 비활성 / ST_SUSPENDED 차단 (+ 가입 흐름 값)
//   재직 상태 work:   ST_SERVING 재직 / ST_LEAVE 휴직 / ST_RETIRED 퇴사 (퇴사로 바꾸면 계정도 비활성이 된다)
const WORK_STATUS = { ST_SERVING: '재직', ST_LEAVE: '휴직', ST_RETIRED: '퇴사' };
// EXPIRED·APPROVAL_EXPIRED는 서버가 시간이 지난 요청·승인에 붙여 보내는 값이다
const EMP_STATUS = {
  REQUESTED: '승인 대기', EXPIRED: '요청 만료', REJECTED: '반려', APPROVED: '계정 생성 전',
  ST_ACTIVE: '사용 중', WAITING: '자리 대기', ST_INACTIVE: '비활성', ST_SUSPENDED: '차단', DELETED: '삭제',
  APPROVAL_EXPIRED: '승인 만료', CANCELED: '취소',
};
// 꺼진 계정 (로그인 불가, 자리 없음): 비활성과 차단
const offStatus = s => s === 'ST_INACTIVE' || s === 'ST_SUSPENDED';

// ---------- 화면 도구 ----------
const $j = id => document.getElementById(id);
const pathPart = encodeURIComponent; // 주소에 넣는 값
// 화면이 지금 보이는지 (view 섹션에 class on이 있고, hidden 요소 안에 있지 않다)
const viewShown = id => { const v = $j(id); return !!v && v.classList.contains('on') && !v.closest('[hidden]'); };
// 읽기 전용 업체인지 (버튼을 흐리게 하는 데만 쓴다. 막는 것은 서버가 403으로 한다)
const readOnlyUi = () => document.body.classList.contains('is-readonly');
const hhmm = iso => { const d = new Date(iso); return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`; };
const minsUntil = iso => Math.max(1, Math.ceil((new Date(iso).getTime() - Date.now()) / 60000));
const validEmail = v => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
// 요청이 끝날 때까지 누른 버튼을 끈다 (두 번 눌러 두 번 보내지 않게)
function whileSending(btn, req) {
  if (btn) btn.disabled = true;
  return req.always(() => { if (btn) btn.disabled = false; });
}

const cellOf = (label, ...parts) => {
  const td = document.createElement('td');
  td.dataset.label = label;
  td.append(...parts);
  return td;
};
const whoCell = e => {
  const who = document.createElement('div'); who.className = 'cell';
  const name = document.createElement('strong'); name.textContent = e.name;
  const id = document.createElement('span'); id.className = 'cell-sub'; id.textContent = e.empNo || e.email || '';
  who.append(name, id);
  return who;
};
const statusChip = e => {
  const s = document.createElement('span');
  s.className = `status status-${e.status.toLowerCase()}`;
  s.textContent = EMP_STATUS[e.status] || e.status;
  return s;
};
const writeButton = (label, aria, onClick, primary) => {
  const b = document.createElement('button');
  b.type = 'button';
  b.className = primary ? 'btn btn-primary' : 'btn';
  b.setAttribute('data-write', ''); // 읽기 전용인 동안 흐리게 하고 누르면 이유를 알려 준다
  if (readOnlyUi()) b.setAttribute('aria-disabled', 'true');
  b.textContent = label;
  b.setAttribute('aria-label', aria);
  b.addEventListener('click', onClick);
  return b;
};
// 읽기 전용이어도 되는 버튼: 비활성화·차단은 쓰는 계정을 줄이기만 하므로 요구분석서 3.11이 허용한다
// (인원을 줄여 다시 결제하려면 필요하기도 하다)
const allowedButton = (label, aria, onClick) => {
  const b = writeButton(label, aria, onClick);
  b.removeAttribute('data-write');
  b.removeAttribute('aria-disabled');
  return b;
};
const whenAt = iso => { const d = new Date(iso); if (Number.isNaN(d.getTime())) return '-'; return `${ymd(iso)} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`; };

// 마지막으로 받은 자리 정보 (자리 없음 창에 쓴다): { caps, admin }
let seatInfo = { caps: null, admin: false };

// ---------- 사원 관리 ----------
// 화면을 열 때 (JSP 페이지가 열리면 부른다)
function renderEmployees() {
  api.get('/employee/admin/api/employees')
    .done(drawEmployees)
    .fail(xhr => showToast(apiMessage(xhr, '사원 목록을 불러오지 못했습니다')));
}

function drawEmployees(d) {
  lastEmployees = d; // 다시 사용 전에 자리를 본다
  seatInfo = { caps: d.caps, admin: d.perms.admin };
  const { caps, counts } = d;
  const n = d.seats.used;
  const full = d.seats.full;
  const set = (id, v) => { $j(id).textContent = v; };

  set('empTotal', counts.off ? `${counts.total}명 (비활성·차단 ${counts.off})` : `${counts.total}명`);
  set('empActive', `${counts.seat}명`);
  set('empLimit', !caps ? '-' : d.readOnly ? '읽기 전용' : caps.paid ? `${caps.active}명 (구매 인원)` : `${caps.active}명 (체험 중)`);

  // 사용 중인 계정 수. 2자리 이하로 남으면 주황, 다 차면 빨강
  const bar = $j('seatBar');
  bar.hidden = !caps || d.readOnly; // 읽기 전용이면 어차피 승인할 수 없다
  if (!bar.hidden) {
    set('seatTitle', caps.paid ? '사용 중인 계정' : '체험 중 계정');
    set('seatCount', `${n} / ${caps.active}명`);
    const meter = $j('seatMeter');
    meter.setAttribute('aria-valuenow', n);
    meter.setAttribute('aria-valuemax', caps.active);
    meter.setAttribute('aria-valuetext', `${caps.active}명 중 ${n}명 사용`);
    $j('seatFill').style.width = `${Math.min(100, (n / caps.active) * 100)}%`;
    bar.classList.toggle('is-near', !full && caps.active - n <= 2);
    bar.classList.toggle('is-full', full);
    set('seatText', caps.paid
      ? (full
        ? `구매 인원 ${caps.active}명을 모두 쓰고 있어 더 승인할 수 없습니다. 쓰지 않는 직원을 비활성화하면 그만큼 다시 승인할 수 있습니다.`
        : `구매 인원 ${caps.active}명 중 ${caps.active - n}명 더 쓸 수 있습니다. 비활성 계정은 세지 않습니다.`)
      : (full
        ? '체험 중 쓸 수 있는 자리를 모두 썼습니다. 쓰지 않는 직원을 비활성화하면 그만큼 다른 직원을 승인할 수 있고, 결제하면 구매한 인원만큼 쓸 수 있습니다.'
        : `체험 중에는 관리자를 포함해 ${caps.active}명까지 쓸 수 있습니다. ${caps.active - n}명 더 승인할 수 있습니다.`));
    $j('seatPay').hidden = caps.paid || !d.perms.pay;
  }

  const rows = d.items.map(e => {
    const tr = document.createElement('tr');
    tr.classList.toggle('is-inactive', offStatus(e.status));
    const duty = DEPT_DUTY[e.deptType] || '';
    tr.append(
      cellOf('이름', whoCell(e)),
      cellOf('부서·직급', `${e.dept}${e.deptType ? ` (${DEPT_TYPE[e.deptType]})` : ''}${e.head ? ' · 부서장' : ''} · ${e.rank}`),
      cellOf('계정 형태', `${ROLE_CODE[e.role] || '-'}${duty && e.role !== 'ROLE_ADMIN' ? ` · ${duty}` : ''}`),
      cellOf('인사 정보', hrInfoText(e)),
      cellOf('상태', statusChip(e)),
      cellOf('등록일', ymd(e.at)),
      cellOf('관리', rowActions(e)),
    );
    return tr;
  });
  $j('empList').replaceChildren(...rows);
}

// 인사 정보 칸: 고용 형태 · 재직 상태, 넣었으면 성별
const hrInfoText = e => [EMP_TYPE[e.empType], WORK_STATUS[e.work], GENDER[e.gender]].filter(Boolean).join(' · ') || '-';

// 서버가 정한 버튼(actions)을 그린다. 비활성화·차단은 읽기 전용이어도 되고, 나머지는 쓰기 동작이다.
// 계정은 지우지 않는다(기록이 남아야 한다: AGENTS.md 이력 데이터, CFG-02)
const EMP_ACTIONS = {
  off: e => allowedButton('비활성화', `${e.name} 비활성화`, () => confirmState(e, 'off')),
  // 차단 (ST_SUSPENDED): 업체 관리자가 잘못된 행위로 계정을 막는다. 비활성화처럼 로그인을 막고 자리를 비우지만
  // "차단"으로 보여서 퇴사한 사람으로 오해하지 않는다
  suspend: e => allowedButton('차단', `${e.name} 차단`, () => confirmState(e, 'suspend')),
  head: e => writeButton(e.head ? '부서장 해제' : '부서장 지정', `${e.name} ${e.head ? '부서장 해제' : '부서장 지정'}`, ev => setHead(e, !e.head, ev.currentTarget)),
  on: e => writeButton('다시 사용', `${e.name} 다시 사용`, () => confirmState(e, 'on')),
  unsuspend: e => writeButton('차단 해제', `${e.name} 차단 해제`, () => confirmState(e, 'on')),
};
// 관리 칸: 계정 버튼, 그리고 인사 정보 (모든 계정. 업체 관리자와 본인 것도)
function rowActions(e) {
  const box = document.createElement('div');
  box.className = 'row-actions';
  box.append(...(e.actions || []).filter(a => EMP_ACTIONS[a]).map(a => EMP_ACTIONS[a](e)));
  box.append(writeButton('인사 정보', `${e.name} 인사 정보`, () => openHrInfo(e)));
  return box;
}

// 실패하면 이유를 알리고, 성공하든 실패하든 목록을 다시 받는다 (다른 탭에서 바뀌었을 수 있다)
const afterChange = req => req
  .fail(xhr => showToast(apiMessage(xhr)))
  .always(() => rerenderStaffViews());

// 부서장 지정: 부서마다 한 명 (요구분석서 3.7). 부서장은 그 부서의 가입 요청을 승인한다 (로그인할 때 HEAD 메뉴)
function setHead(e, on, btn) {
  afterChange(whileSending(btn, api.post(`/employee/admin/api/employees/${pathPart(e.empNo)}/head`, { head: on })))
    .done(r => showToast(r.head ? `${r.name}님을 ${r.dept} 부서장으로 지정했습니다. 그 부서의 가입 요청을 승인할 수 있습니다` : `${r.name}님의 부서장 지정을 해제했습니다`));
}


// mode: 'off' 비활성화 (ST_INACTIVE), 'suspend' 차단 (ST_SUSPENDED, 업체 관리자), 'on' 다시 사용 / 차단 해제
let stateTarget = null;
const STATE_TEXT = {
  off: { title: n => `${n} 계정을 비활성화할까요?`, ok: () => '비활성화',
    body: no => `사번 ${no}. 비활성화하면 이 직원은 로그인할 수 없고, 결재·게시글 같은 기록은 그대로 남습니다. 쓰던 자리가 비어 다른 직원을 승인할 수 있고, 나중에 다시 사용으로 되돌릴 수 있습니다.` },
  suspend: { title: n => `${n} 계정을 차단할까요?`, ok: () => '차단',
    body: no => `사번 ${no}. 잘못된 행위로 계정을 막을 때 씁니다. 차단하면 로그인할 수 없고 "차단된 계정"으로 안내됩니다. 기록은 그대로 남고 쓰던 자리가 비며, 업체 관리자가 차단 해제로 되돌릴 수 있습니다.` },
  on: { title: (n, e) => (e.status === 'ST_SUSPENDED' ? `${n} 계정의 차단을 해제할까요?` : `${n} 계정을 다시 사용할까요?`),
    ok: e => (e.status === 'ST_SUSPENDED' ? '차단 해제' : '다시 사용'),
    body: no => `사번 ${no}. 이 직원이 같은 사번으로 다시 로그인할 수 있고, 계정 한 자리를 씁니다.` },
};
function confirmState(e, mode) {
  if (mode === 'on') {
    // 퇴사한 사람은 인사 정보에서 바꾸기 전까지 꺼진 채로 둔다 (서버도 다시 본다)
    if (e.work === 'ST_RETIRED') return showToast(`${e.name}님은 퇴사 처리되어 있습니다. 인사 정보에서 재직 상태를 먼저 바꿔 주세요`);
    // 돌아오면 자리를 쓴다: 빈 자리가 없으면 먼저 알린다
    if (lastEmployees && lastEmployees.seats.full) return showSeatDialog();
  }
  stateTarget = { e, mode };
  const t = STATE_TEXT[mode];
  const dialog = $j('empStateDialog');
  dialog.querySelector('h2').textContent = t.title(e.name, e);
  dialog.querySelector('p').textContent = t.body(e.empNo);
  dialog.querySelector('[value="ok"]').textContent = t.ok(e);
  if (!dialog.open) dialog.showModal();
}
document.querySelectorAll('#empStateDialog button').forEach(b => b.addEventListener('click', () => {
  $j('empStateDialog').close();
  const target = stateTarget;
  stateTarget = null;
  if (b.value !== 'ok' || !target) return;
  const { e, mode } = target;
  afterChange(api.post(`/employee/admin/api/employees/${pathPart(e.empNo)}/status`, { action: mode })).done(r => {
    if (mode === 'on') {
      showToast(r.wasBlocked ? `${r.name} 계정의 차단을 해제했습니다` : `${r.name} 계정을 다시 사용합니다`);
      return;
    }
    const done = `${r.name} 계정을 ${mode === 'suspend' ? '차단' : '비활성화'}했습니다`;
    showToast(done + (r.released ? ` · 이 사람이 결재할 차례였던 휴가 ${r.released}건은 자동 반려했습니다` : ''));
  });
}));

// ---------- 인사 정보: 고용 형태 · 재직 상태 · 성별 (사원 관리, 인사 담당과 업체 관리자) ----------
// 퇴사로 바꾸면 쓰던 계정도 같이 비활성이 되어 로그인할 수 없다. 재직으로 돌려도 켜지지 않는다(다시 사용이 자리와 함께 켠다).
// 업체 관리자와 본인은 퇴사로 바꿀 수 없다 (canRetire).
let hrInfoTarget = null;
function openHrInfo(e) {
  hrInfoTarget = e;
  let dialog = $j('hrInfoDialog');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'hrInfoDialog';
    dialog.className = 'modal';
    const options = (map, blank) => (blank ? `<option value="">${blank}</option>` : '')
      + Object.entries(map).map(([k, v]) => `<option value="${k}">${v}</option>`).join('');
    dialog.innerHTML = '<h2></h2><p class="muted"></p><div class="hr-info-fields">'
      + `<label class="field"><span>고용 형태</span><select class="input" id="hrEmpType">${options(EMP_TYPE)}</select></label>`
      + `<label class="field"><span>재직 상태</span><select class="input" id="hrWork">${options(WORK_STATUS)}</select></label>`
      + `<label class="field"><span>성별 <span class="muted">(선택)</span></span><select class="input" id="hrGender">${options(GENDER, '입력 안 함')}</select></label>`
      + '</div><p class="form-error" id="hrInfoError" role="alert"></p>'
      + '<div class="modal-actions"><button type="button" class="btn" value="cancel">취소</button><button type="button" class="btn btn-primary" value="ok" data-write>저장</button></div>';
    dialog.querySelectorAll('.modal-actions button').forEach(b => b.addEventListener('click', () => {
      if (b.value === 'ok') return saveHrInfo(b);
      dialog.close();
      hrInfoTarget = null;
    }));
    document.body.append(dialog);
  }
  dialog.querySelector('h2').textContent = `${e.name}님 인사 정보`;
  dialog.querySelector('p').textContent = `사번 ${e.empNo} · ${e.dept} · ${e.rank}. 퇴사로 바꾸면 계정도 비활성이 되어 로그인할 수 없습니다.`;
  $j('hrEmpType').value = e.empType || 'EMP_REGULAR';
  $j('hrWork').value = e.work || 'ST_SERVING';
  $j('hrGender').value = e.gender || '';
  // 업체 관리자의 자리는 고정이고, 누구도 이것으로 자기 계정을 끌 수 없다
  document.querySelector('#hrWork option[value="ST_RETIRED"]').disabled = !e.canRetire;
  $j('hrInfoError').textContent = '';
  if (!dialog.open) dialog.showModal();
}
// 저장되면 창을 닫고, 안 되면 창 안에 이유를 보여 준다
function saveHrInfo(btn) {
  const e = hrInfoTarget;
  const dialog = $j('hrInfoDialog');
  if (!e) { dialog.close(); return; }
  const empType = $j('hrEmpType').value;
  const work = $j('hrWork').value;
  const gender = $j('hrGender').value;
  if (work === 'ST_RETIRED' && !e.canRetire) {
    $j('hrInfoError').textContent = '업체 관리자와 본인은 퇴사로 바꿀 수 없습니다';
    return;
  }
  whileSending(btn, api.post(`/employee/admin/api/employees/${pathPart(e.empNo)}/hr`, { empType, work, gender }))
    .done(r => {
      dialog.close();
      hrInfoTarget = null;
      const note = r.turnedOff ? ' 계정도 비활성으로 바꿨습니다' + (r.released ? ` · 결재할 차례였던 휴가 ${r.released}건은 자동 반려했습니다` : '') : '';
      showToast(`${r.name}님 인사 정보를 저장했습니다.${note}`);
      rerenderStaffViews();
    })
    .fail(xhr => {
      $j('hrInfoError').textContent = apiMessage(xhr);
      if (xhr.status === 404) rerenderStaffViews();
    });
}

// 자리 없음: 이유와, 체험 중인 업체 관리자에게는 결제 화면으로 가는 버튼
function showSeatDialog() {
  const dialog = $j('seatDialog');
  const { caps, admin } = seatInfo;
  const paid = caps && caps.paid;
  dialog.querySelector('h2').textContent = paid ? '승인할 수 있는 자리가 없습니다' : '계정을 더 쓸 수 없습니다';
  dialog.querySelector('p').textContent = paid
    ? `구매 인원 ${caps.active}명을 모두 쓰고 있습니다. 쓰지 않는 직원을 비활성화한 뒤 다시 해 주세요.`
    : `체험 중에는 관리자를 포함해 ${caps ? caps.active : 10}명까지 쓸 수 있습니다. 쓰지 않는 직원을 비활성화하면 그만큼 다시 승인할 수 있고, 결제하면 구매한 인원만큼 쓸 수 있습니다.`
      + (admin ? '' : ' 결제는 업체 관리자에게 요청하세요.');
  dialog.querySelector('[value="pay"]').hidden = paid || !admin;
  if (!dialog.open) dialog.showModal();
}
document.querySelectorAll('#seatDialog button').forEach(b => b.addEventListener('click', () => {
  $j('seatDialog').close();
  if (b.value === 'pay') location.href = pageHref('/subscription/billing');
}));


// ---------- 가입 승인 ----------
// 업체 관리자는 모든 요청을, 부서장은 자기 부서 요청만 본다 (서버가 골라 보낸다).
let lastEmployees = null; // 사원 관리에서 마지막으로 받은 값 (다시 사용 전에 자리를 본다)
let lastJoin = null; // 가입 승인에서 마지막으로 받은 값
let joinPickOpen = false; // 업체 관리자의 부서 목록이 펼쳐져 있다
const scopeLabel = x => (x.all ? '업체 전체' : x.scope);

// 화면을 열 때 (JSP 페이지가 열리면 부른다). 30초마다도 다시 그린다
function renderJoin() {
  api.get('/employee/api/join')
    .done(d => { lastJoin = d; drawJoin(d); })
    .fail(xhr => showToast(apiMessage(xhr, '가입 요청을 불러오지 못했습니다')));
}

// 가입 활성화 칸. 부서장은 버튼 하나로 자기 부서를 열고 닫는다. 업체 관리자가 누르면 아래에 부서 목록이 펼쳐지고, 고른 부서가 열린다
// (이미 열린 부서를 다시 고르면 새 시간으로). 업체 관리자는 열린 부서마다 끝나는 시각과 끄기 버튼을 보고, 부서장은 자기 부서만 본다.
function drawJoinOpen(d) {
  const admin = d.perms.admin;
  const list = d.openings;
  const btn = $j('btnJoinOpen');
  if (d.readOnly) btn.setAttribute('aria-disabled', 'true'); else btn.removeAttribute('aria-disabled');
  if (admin) {
    $j('joinOpenBox').classList.toggle('is-on', list.length > 0);
    $j('joinOpenNote').textContent = list.length
      ? `열린 부서 ${list.length}개: ${list.map(scopeLabel).join(', ')}. 이 동안 직원이 계정 생성에서 업체 코드를 확인하면 열린 부서로만 가입을 요청할 수 있고, 부서마다 고른 시간이 지나면 저절로 꺼집니다.`
      : '꺼져 있습니다. 가입 활성화를 누르면 부서 목록이 펼쳐지고, 고른 부서만 정한 시간 동안 열립니다. 꺼져 있으면 업체 코드를 넣어도 아무 반응이 없습니다.';
    btn.textContent = joinPickOpen ? '접기' : '가입 활성화';
    btn.classList.toggle('btn-primary', !joinPickOpen);
    btn.setAttribute('aria-expanded', joinPickOpen);
    btn.setAttribute('aria-controls', 'joinOpenPick');
    btn.setAttribute('aria-label', joinPickOpen ? '부서 목록 접기' : '가입 활성화: 열 부서 고르기');
    $j('joinOpenTime').disabled = false;
    $j('joinOpenPick').hidden = !joinPickOpen;
    if (joinPickOpen) drawJoinPick(d);
  } else {
    const dept = d.myDept;
    const mine = list.find(x => x.scope === dept || x.all);
    $j('joinOpenBox').classList.toggle('is-on', !!mine);
    $j('joinOpenNote').textContent = mine
      ? `켜져 있습니다 · ${hhmm(mine.until)}까지 (${minsUntil(mine.until)}분 남음)${!mine.mine ? `, ${mine.byName} 님이 켬` : ''}. 이 동안 직원이 계정 생성에서 업체 코드를 확인하면 ${dept || '내'} 부서로 가입을 요청할 수 있습니다.`
      : `꺼져 있습니다. 켜 둔 동안만 직원이 계정 생성에서 업체 코드를 확인할 수 있고, 꺼져 있으면 업체 코드를 넣어도 아무 반응이 없습니다. 켜면 ${dept || '내'} 부서가 열리고, 고른 시간이 지나면 저절로 꺼집니다.`;
    btn.textContent = mine ? '끄기' : '가입 활성화';
    btn.classList.toggle('btn-primary', !mine);
    btn.removeAttribute('aria-expanded');
    btn.removeAttribute('aria-controls');
    btn.setAttribute('aria-label', mine ? `${dept} 가입 활성화 끄기` : `${dept} 가입 활성화`);
    $j('joinOpenTime').disabled = !!mine;
    $j('joinOpenPick').hidden = true;
  }
  $j('joinOpenTime').dispatchEvent(new Event('change')); // 꾸민 드롭다운을 다시 그린다 (select.js)
  // 업체 관리자는 열린 부서를 여기서 하나씩 끈다. 부서장은 위의 한 줄만 본다
  const ul = $j('joinOpenList');
  const rows = admin ? list : [];
  ul.hidden = !rows.length;
  ul.replaceChildren(...rows.map(x => {
    const li = document.createElement('li');
    const text = document.createElement('span');
    text.textContent = `${scopeLabel(x)} · ${hhmm(x.until)}까지 (${minsUntil(x.until)}분 남음) · ${x.byName} 님이 켬`;
    li.append(text, writeButton('끄기', `${scopeLabel(x)} 가입 활성화 끄기`, ev => turnJoinOff([x], ev.currentTarget)));
    return li;
  }));
  if (rows.length > 1) {
    const li = document.createElement('li');
    li.className = 'join-open-all-off';
    li.append(writeButton('모두 끄기', '열린 부서 가입 활성화 모두 끄기', ev => turnJoinOff(rows, ev.currentTarget)));
    ul.append(li);
  }
}

// 업체 관리자의 펼친 목록: 부서마다 체크 칸 하나, 열린 부서는 끝나는 시각을 붙인다. 30초마다 다시 그려도 고른 것은 남긴다
function drawJoinPick(d) {
  const box = $j('joinOpenChecks');
  const ticked = new Set([...box.querySelectorAll('input:checked')].map(c => c.value));
  box.replaceChildren(...d.depts.map(dept => {
    const label = document.createElement('label');
    const c = document.createElement('input');
    c.type = 'checkbox';
    c.value = dept;
    c.checked = ticked.has(dept);
    const name = document.createElement('span');
    name.textContent = dept;
    label.append(c, name);
    const open = d.openings.find(x => x.scope === dept || x.all);
    if (open) {
      const s = document.createElement('small');
      s.textContent = `열림 · ${hhmm(open.until)}까지`;
      label.append(s);
    }
    return label;
  }));
  syncJoinPick();
}
function syncJoinPick() {
  const boxes = [...$j('joinOpenChecks').querySelectorAll('input')];
  const n = boxes.filter(c => c.checked).length;
  $j('btnJoinPickOk').disabled = n === 0;
  $j('btnJoinPickOk').textContent = n ? `선택한 부서 ${n}개 열기` : '선택한 부서 열기';
  $j('btnJoinPickAll').textContent = boxes.length && n === boxes.length ? '전체 해제' : '전체 선택';
}
$j('joinOpenChecks')?.addEventListener('change', syncJoinPick);
$j('btnJoinPickAll')?.addEventListener('click', () => {
  const boxes = [...$j('joinOpenChecks').querySelectorAll('input')];
  const all = boxes.every(c => c.checked);
  boxes.forEach(c => { c.checked = !all; });
  syncJoinPick();
});
$j('btnJoinPickCancel')?.addEventListener('click', () => {
  joinPickOpen = false;
  $j('joinOpenChecks').replaceChildren();
  if (lastJoin) drawJoinOpen(lastJoin);
  $j('btnJoinOpen').focus();
});
$j('btnJoinPickOk')?.addEventListener('click', () => {
  const depts = [...$j('joinOpenChecks').querySelectorAll('input:checked')].map(c => c.value);
  if (!depts.length) return;
  const minutes = Number($j('joinOpenTime').value) || 60;
  api.post('/employee/api/join/openings', { depts, minutes })
    .done(r => {
      joinPickOpen = false;
      $j('joinOpenChecks').replaceChildren();
      showToast(`${r.depts.join(', ')} 가입을 ${hhmm(r.until)}까지 열었습니다. 직원에게 업체 코드를 알려 주세요`);
    })
    .fail(xhr => showToast(apiMessage(xhr)))
    .always(() => { syncJoinPick(); renderJoin(); });
  $j('btnJoinPickOk').disabled = true; // 응답이 올 때까지 (그 뒤 syncJoinPick이 다시 정한다)
});

// openings: 끌 부서들 ({ scope, all }). 둘 이상이면 모두 끄기
function turnJoinOff(openings, btn) {
  const req = openings.length > 1 ? api.post('/employee/api/join/openings/delete') : api.post(`/employee/api/join/openings/${pathPart(openings[0].scope)}/delete`);
  whileSending(btn, req)
    .done(() => showToast(openings.length > 1 ? `열린 부서 ${openings.length}개의 가입 활성화를 모두 껐습니다` : `${scopeLabel(openings[0])} 가입 활성화를 껐습니다`))
    .fail(xhr => showToast(apiMessage(xhr)))
    .always(() => renderJoin());
}
$j('btnJoinOpen')?.addEventListener('click', () => {
  const d = lastJoin;
  if (!d) return;
  if (d.perms.admin) {
    // 업체 관리자는 부서 목록을 펼치거나 접는다. 여는 것은 "선택한 부서 열기"로 한다
    joinPickOpen = !joinPickOpen;
    if (!joinPickOpen) $j('joinOpenChecks').replaceChildren();
    drawJoinOpen(d);
    if (joinPickOpen) $j('joinOpenChecks').querySelector('input')?.focus();
    return;
  }
  const dept = d.myDept;
  if (!dept) return showToast('부서가 정해지지 않아 가입을 열 수 없습니다');
  const own = d.openings.find(x => x.scope === dept);
  const btn = $j('btnJoinOpen');
  if (own) return turnJoinOff([own], btn);
  const minutes = Number($j('joinOpenTime').value) || 60;
  whileSending(btn, api.post('/employee/api/join/openings', { minutes }))
    .done(r => showToast(`${r.depts[0]} 가입을 ${hhmm(r.until)}까지 열었습니다. 직원에게 업체 코드를 알려 주세요`))
    .fail(xhr => showToast(apiMessage(xhr)))
    .always(() => renderJoin());
});

function drawJoin(d) {
  seatInfo = { caps: d.caps, admin: d.perms.admin };
  drawJoinOpen(d);
  const { caps } = d;
  const admin = d.perms.admin;
  const n = d.seats.used;
  const open = d.requests;
  const set = (id, v) => { $j(id).textContent = v; };

  set('joinCompanyCode', d.companyCode);
  set('joinOpen', `${open.length}건`);
  set('joinActive', caps ? `${n} / ${caps.active}명` : `${n}명`);
  set('joinRoom', caps ? `${d.seats.room}명` : '-');
  set('joinScope', admin ? '직원의 가입 요청을 처리합니다.' : `${d.myDept || '내 부서'} 직원의 가입 요청만 처리합니다.`);
  set('joinNote', !caps ? '' : (caps.paid
    ? `구매 인원 ${caps.active}명까지 승인할 수 있고, 다 차면 더 승인할 수 없습니다.`
    : `체험 중에는 관리자를 포함해 ${caps.active}명까지 승인할 수 있습니다.`)
    + ` 승인하면 다음 사번(지금은 ${d.nextEmpNo})이 순서대로 붙고 바로 계정이 만들어집니다. 직원은 요청할 때 정한 비밀번호로 로그인합니다.`);

  const rows = d.requests.map(e => {
    const tr = document.createElement('tr');
    const actions = document.createElement('div');
    actions.className = 'row-actions';
    actions.append(
      writeButton('승인', `${e.name} 가입 승인`, () => openApprove(e), true),
      writeButton('반려', `${e.name} 가입 반려`, () => rejectJoin(e)),
    );
    tr.append(
      cellOf('이름', e.name),
      cellOf('부서·직급', `${e.dept} · ${e.rank}`),
      cellOf('이메일', e.email),
      cellOf('요청 시각', whenAt(e.requestAt)),
      cellOf('처리', actions),
    );
    return tr;
  });
  $j('joinList').replaceChildren(...rows);
  $j('joinEmpty').hidden = rows.length > 0;

  setJoinBadge(open.length);
}

// 승인: 붙을 사번을 보여 주는 창. 고를 역할은 없다: 승인된 직원은 모두 업체 사용자(ROLE_USR)이고,
// IT·인사 담당은 부서의 부서 형태로 정해진다 (공통 코드 시트)
let approveTarget = null;
function openApprove(e) {
  const d = lastJoin;
  if (!d) return;
  if (!d.canApprove) return showSeatDialog(); // 구매 인원이 다 찼다 (단순화 ②)
  approveTarget = e.reqId;
  let dialog = $j('approveDialog');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'approveDialog';
    dialog.className = 'modal';
    dialog.innerHTML = '<h2></h2><p></p>'
      + '<div class="modal-actions"><button type="button" class="btn" value="cancel">취소</button><button type="button" class="btn btn-primary" value="ok">승인</button></div>';
    dialog.querySelectorAll('.modal-actions button').forEach(b => b.addEventListener('click', () => {
      dialog.close();
      const id = approveTarget;
      approveTarget = null;
      if (b.value === 'ok' && id) approveJoin(id);
    }));
    document.body.append(dialog);
  }
  dialog.querySelector('h2').textContent = `${e.name}님의 가입을 승인할까요?`;
  dialog.querySelector('p').textContent = `${e.dept} · ${e.rank} · ${e.email}. 승인하면 사번 ${d.nextEmpNo}이 붙고 바로 계정이 만들어집니다. 직원은 요청할 때 정한 비밀번호로 로그인합니다.`
    + ({ DEPT_IT: ' IT 부서라 IT 담당 메뉴가 함께 보입니다.', DEPT_HR: ' 인사 부서라 인사 담당 메뉴가 함께 보입니다.' }[e.deptType] || '');
  if (!dialog.open) dialog.showModal();
}

function approveJoin(reqId) {
  afterChange(api.post(`/employee/api/join/requests/${pathPart(reqId)}/approve`))
    .done(r => showToast(`${r.name}님을 승인했습니다. 사번 ${r.empNo}로 계정이 만들어졌고 바로 로그인할 수 있습니다`));
}

// 반려: 요청이 끝난다. 직원 화면이 반려를 알려 주고 다시 요청할 수 있다
function rejectJoin(e) {
  askConfirm(`${e.name}님의 가입 요청을 반려할까요?`,
    `${e.dept} · ${e.email}. 반려하면 이 요청은 끝나고, 직원은 업체에 확인한 뒤 계정 생성에서 다시 요청할 수 있습니다.`, '반려', () => {
      afterChange(api.post(`/employee/api/join/requests/${pathPart(e.reqId)}/reject`))
        .done(r => showToast(`${r.name}님의 가입 요청을 반려했습니다`));
    });
}


// 처음 쓸 때 만드는 작은 확인 창 (반려 확인). danger: 확인 버튼이 빨강 (되돌릴 수 없는 동작)
// onOk가 없으면 알림만: 확인 버튼 하나 (예: 지울 수 없는 이유)
function askConfirm(title, body, okLabel, onOk, danger) {
  let dialog = $j('askDialog');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'askDialog';
    dialog.className = 'modal';
    dialog.innerHTML = '<h2></h2><p></p><div class="modal-actions"><button type="button" class="btn" value="cancel">취소</button><button type="button" class="btn btn-primary" value="ok"></button></div>';
    dialog.querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
      dialog.close();
      const fn = dialog.onOk;
      dialog.onOk = null;
      if (b.value === 'ok' && fn) fn();
    }));
    document.body.append(dialog);
  }
  dialog.querySelector('h2').textContent = title;
  dialog.querySelector('p').textContent = body;
  dialog.querySelector('[value="ok"]').textContent = okLabel;
  dialog.querySelector('[value="ok"]').className = danger ? 'btn btn-danger' : 'btn btn-primary';
  dialog.querySelector('[value="cancel"]').hidden = !onOk;
  dialog.onOk = onOk;
  if (!dialog.open) dialog.showModal();
}

// 가입 승인 메뉴의 배지: 내가 처리할 수 있는 승인 대기 요청 수
function setJoinBadge(count) {
  const badge = $j('joinCount');
  if (!badge) return;
  badge.textContent = count;
  badge.hidden = !count;
}
function refreshJoinBadge() {
  const badge = $j('joinCount');
  if (!badge) return;
  // 이 메뉴가 보이지 않는 계정(업체 관리자·부서장이 아님, 운영자)은 묻지 않는다
  const item = badge.closest('.nav-item');
  if (item && (item.hidden || item.closest('[data-mode][hidden]'))) return setJoinBadge(0);
  api.get('/employee/api/join/count').done(r => setJoinBadge(r.count));
}

// 사원 관리·가입 승인 중 열려 있는 화면과 배지를 다시 받는다 (상태를 바꾼 뒤에 부른다)
function rerenderStaffViews() {
  if (viewShown('view-hr-employee')) renderEmployees();
  if (viewShown('view-hr-join')) renderJoin(); else refreshJoinBadge();
}
// 새 요청이 들어왔는지 30초마다 본다. 탭이 보일 때만: 가입 승인이 열려 있으면 다시 그리고, 아니면 배지만.
// 탭으로 돌아오면 바로 한 번
function tickJoin() {
  const badge = $j('joinCount');
  if (document.hidden || !badge || badge.parentElement.closest('[hidden]')) return;
  if (viewShown('view-hr-join')) renderJoin(); else refreshJoinBadge();
}
setInterval(tickJoin, 30000);
document.addEventListener('visibilitychange', tickJoin);

// ---------- 로그인 화면: 계정 생성 ----------
// 한 양식, 세 상태: 요청(가입이 열린 동안 업체 확인, 입력, 비밀번호, 승인 요청) → 대기("승인 요청을 하였습니다") →
// 승인(계정이 만들어짐: 사번과 로그인으로 가는 버튼). 이번 범위(단순화 ①)는 직원 이메일 인증 코드 없이 요청한다.
// 이 기기는 자기 요청을 기억한다(MY_JOIN_KEY).
const loginForm = $j('loginForm');
const joinForm = $j('joinForm');
const JOIN_FIELDS = ['joinCompany', 'joinDept', 'joinRank', 'joinName', 'joinEmail', 'joinPw', 'joinPw2'];
let checkedCompany = ''; // 확인으로 맞춘 업체 코드 ('' = 아직)
let joinNotice = ''; // 지난 요청이 끝난 이유 (반려, 없음). 양식을 고치면 지운다

// 이 기기의 요청 { company, reqId }. 화면 JS가 브라우저 저장소를 쓰는 유일한 곳이다 (맨 위 주석)
const MY_JOIN_KEY = 'ieum-join-mine';
const loadMyJoin = () => { try { return JSON.parse(localStorage.getItem(MY_JOIN_KEY)); } catch { return null; } };
const saveMyJoin = v => { try { if (v) localStorage.setItem(MY_JOIN_KEY, JSON.stringify(v)); else localStorage.removeItem(MY_JOIN_KEY); } catch { /* 저장소가 막혔다 */ } };
const joinReqUrl = mine => `/join/api/companies/${pathPart(mine.company)}/requests/${pathPart(mine.reqId)}`;

// 부서·직급 목록은 확인한 업체의 것. 확인 전에는 안내 한 줄만 있고 꺼져 있다
function fillSelect(id, list, pick, code) {
  const sel = $j(id);
  const keep = sel.value;
  sel.replaceChildren(new Option(code ? pick : '업체 코드를 먼저 확인하세요', ''), ...list.map(d => new Option(d, d)));
  sel.value = list.includes(keep) ? keep : '';
  sel.dispatchEvent(new Event('change')); // 꾸민 드롭다운을 다시 그린다 (select.js)
}
function setDeptOptions(code, depts, ranks) {
  fillSelect('joinDept', code ? depts : [], '부서를 고르세요', code);
  fillSelect('joinRank', code ? ranks : [], '직급을 고르세요', code);
}
function setCompanyNote(text, ok) {
  const note = $j('joinCompanyNote');
  note.textContent = text;
  note.hidden = !text;
  note.classList.toggle('is-ok', !!text && ok);
  note.classList.toggle('is-bad', !!text && !ok);
}

// 확인: 가입이 열려 있으면 열린 부서와 직급을 불러온다. 아니면 화면에 아무것도 나오지 않는다(닫힌 업체와 없는 코드가 똑같이 보인다).
// 너무 많이 틀리면 서버가 막고(429) 언제 다시 할 수 있는지 알려 준다
function checkCompany() {
  const code = $j('joinCompany').value.trim().toLowerCase();
  if (!code) return;
  const btn = $j('btnJoinCheck');
  btn.disabled = true;
  api.get(`/join/api/companies/${pathPart(code)}`)
    .done(c => {
      if ($j('joinCompany').value.trim().toLowerCase() !== code) return; // 그사이 코드를 고쳤다
      checkedCompany = code;
      setCompanyNote(`${c.companyName} · 가입이 열린 부서 ${c.depts.length}개, 직급 ${c.ranks.length}개를 불러왔습니다`, true);
      setDeptOptions(code, c.depts, c.ranks);
      $j('joinDept').disabled = $j('joinRank').disabled = false;
      syncRequestButton();
      // select.js가 드롭다운을 켠 뒤에 (다음 틱에 다시 그린다)
      setTimeout(() => $j('joinDeptBtn')?.focus());
    })
    .fail(xhr => { if (xhr.status === 429) setCompanyNote(apiMessage(xhr), false); })
    .always(() => { btn.disabled = !!$j('btnJoinRequest').dataset.locked; });
}
$j('btnJoinCheck')?.addEventListener('click', checkCompany);
$j('joinCompany')?.addEventListener('keydown', e => {
  if (e.key === 'Enter') { e.preventDefault(); checkCompany(); }
});
// 코드를 바꾸면 다시 확인해야 한다: 예전 업체의 부서는 지운다
function onCompanyInput() {
  if (checkedCompany && $j('joinCompany').value.trim().toLowerCase() === checkedCompany) return;
  checkedCompany = '';
  setCompanyNote('', false);
  setDeptOptions('', [], []);
  $j('joinDept').disabled = $j('joinRank').disabled = true;
  syncRequestButton();
}
$j('joinCompany')?.addEventListener('input', onCompanyInput);
if (joinForm) {
  setDeptOptions('', [], []);
  $j('joinDept').disabled = $j('joinRank').disabled = true;
}

// 승인 요청 버튼: 업체 코드 확인, 이메일, 비밀번호(8자 이상, 두 번 같게)를 마칠 때까지 회색으로 꺼져 있다.
// 위의 한 줄이 무엇이 남았는지 알려 준다
const pwReady = () => $j('joinPw').value.length >= 8 && $j('joinPw').value === $j('joinPw2').value;
function syncRequestButton() {
  const b = $j('btnJoinRequest');
  const company = !!checkedCompany && $j('joinCompany').value.trim().toLowerCase() === checkedCompany;
  const email = validEmail($j('joinEmail').value.trim());
  const pw = pwReady();
  const on = !b.dataset.locked && !b.dataset.sending && company && email && pw;
  b.disabled = !on;
  b.classList.toggle('btn-primary', on);
  const hint = $j('joinRequestHint');
  hint.hidden = !!b.dataset.locked || on || !!b.dataset.sending;
  const missing = [!company && '업체 코드 확인', !email && '이메일', !pw && '비밀번호 정하기(8자 이상, 확인과 같게)'].filter(Boolean);
  hint.textContent = `승인 요청 전에 남은 것: ${missing.join(' · ')}`;
}

function setJoinStatus(text, kind) {
  const s = $j('joinStatus');
  s.textContent = text;
  s.className = `join-status${kind ? ` is-${kind}` : ''}`;
  s.hidden = !text;
}

// 끝난 요청의 이유 (서버의 reason)
const JOIN_ENDED = {
  OLD_FLOW: '이전 방식으로 처리된 요청입니다. 다시 승인 요청을 해 주세요.',
  OFF: '사용이 중지된 계정입니다. 업체 관리자에게 문의해 주세요.',
  REJECTED: '가입 요청이 반려되었습니다. 업체에 확인한 뒤 내용을 고쳐 다시 승인 요청을 해 주세요.',
  OTHER: '이 요청은 더 진행할 수 없습니다. 다시 승인 요청을 해 주세요.',
};

// 이 기기의 요청을 서버에 물어 양식을 그린다 (계정 생성을 열 때, 승인을 기다리는 동안 5초마다)
function loadJoinForm() {
  if (joinForm.hidden) return;
  const mine = loadMyJoin();
  if (!mine) return drawJoinForm(null, null);
  api.get(joinReqUrl(mine))
    .done(v => { if (sameJoin(mine)) drawJoinForm(mine, v); })
    .fail(xhr => { if (xhr.status === 404 && sameJoin(mine)) drawJoinForm(mine, null); });
}
// 응답이 오는 동안 요청을 취소하거나 바꾸지 않았는지
const sameJoin = mine => { const cur = loadMyJoin(); return !!cur && cur.company === mine.company && cur.reqId === mine.reqId; };

// 요청 상태 v로 양식을 그린다 (mine: 이 기기가 기억한 요청, v: 서버의 상태. 둘 다 null이면 새로 쓰는 양식)
function drawJoinForm(mine, v) {
  const waiting = !!v && v.state === 'WAITING';
  // approved = 승인으로 계정이 만들어졌다 (바로 로그인할 수 있다)
  const approved = !!v && v.state === 'APPROVED';
  // 보낸 요청의 칸은 끝날 때까지 잠근다
  JOIN_FIELDS.forEach(id => { $j(id).disabled = waiting || approved; });
  $j('btnJoinCheck').disabled = waiting || approved;
  $j('btnJoinRequest').dataset.locked = waiting || approved ? '1' : '';
  $j('btnJoinRequest').hidden = approved;
  $j('btnJoinCancel').hidden = !waiting;
  $j('joinAccount').hidden = !approved;
  $j('btnJoinCreate').hidden = !approved;
  $j('joinError').textContent = '';
  if (v) {
    // 화면을 다시 열면 보낸 요청으로 채운다. 이 업체로 보낸 요청이라 확인한 것으로 보고, 그사이 가입이 닫혔어도 부서는 보이게 둔다
    $j('joinCompany').value = v.companyCode;
    $j('joinName').value = v.name;
    checkedCompany = v.companyCode;
    setCompanyNote(v.companyName, true);
    setDeptOptions(v.companyCode, v.depts, v.ranks);
    $j('joinDept').value = v.dept;
    $j('joinDept').dispatchEvent(new Event('change')); // 꾸민 드롭다운을 다시 그린다 (select.js)
    $j('joinRank').value = v.rank;
    $j('joinRank').dispatchEvent(new Event('change'));
    $j('joinEmail').value = v.email;
    $j('joinPw').value = $j('joinPw2').value = waiting || approved ? '********' : '';
  }
  if (waiting) {
    setJoinStatus('승인 요청을 하였습니다. 업체 관리자나 부서장이 승인하면 바로 계정이 만들어집니다.', 'wait');
  } else if (approved) {
    setJoinStatus('승인되어 계정이 만들어졌습니다. 업체 코드와 아래 사번, 요청할 때 정한 비밀번호로 로그인하세요.', 'ok');
    $j('joinId').textContent = v.empNo;
  } else if (v) {
    joinNotice = JOIN_ENDED[v.reason] || JOIN_ENDED.OTHER;
    saveMyJoin(null);
  } else if (mine) {
    joinNotice = '요청을 찾을 수 없습니다. 다시 승인 요청을 해 주세요.';
    saveMyJoin(null);
  }
  // 끝난 요청의 이유는 양식을 고칠 때까지 남긴다 (다음에 다시 그릴 때는 보여 줄 요청이 없다)
  if (!waiting && !approved) setJoinStatus(joinNotice, joinNotice ? 'bad' : '');
  // 부서 목록은 확인한 업체이고 아직 고칠 수 있을 때만 켠다
  $j('joinDept').disabled = $j('joinRank').disabled = waiting || approved || !checkedCompany;
  syncRequestButton();
}

// 인증 메일 보내기 버튼 (비밀번호 찾기). 코드 규칙(6자리, 10분, 5번, 1분에 한 번)은 서버가 지킨다.
const RESEND_WAIT_S = 60;
// 보내기 버튼은 메일을 보낼 때마다 1분 쉬고, 남은 초를 버튼에 보여 준다
const resendTimers = new WeakMap();
function restSendButton(btn, label) {
  clearInterval(resendTimers.get(btn));
  let left = RESEND_WAIT_S;
  const tick = () => {
    if (left <= 0) { clearInterval(resendTimers.get(btn)); resendTimers.delete(btn); btn.disabled = false; btn.textContent = label; return; }
    btn.disabled = true;
    btn.textContent = `${label} (${left}초)`;
    left -= 1;
  };
  tick();
  resendTimers.set(btn, setInterval(tick, 1000));
}
function freeSendButton(btn, label) {
  clearInterval(resendTimers.get(btn));
  resendTimers.delete(btn);
  btn.disabled = false;
  btn.textContent = label;
}

$j('joinEmail')?.addEventListener('input', syncRequestButton);

['joinPw', 'joinPw2'].forEach(id => $j(id)?.addEventListener('input', syncRequestButton));

function showJoin(open) {
  loginForm.hidden = open;
  joinForm.hidden = !open;
  $j('pwForm').hidden = true;
  if (open) {
    if (!loadMyJoin()) { $j('joinCompany').value = $j('loginCompany').value; onCompanyInput(); }
    loadJoinForm();
    $j('joinCompany').focus();
  }
}
$j('btnJoin')?.addEventListener('click', () => showJoin(true));
$j('btnJoinBack')?.addEventListener('click', () => showJoin(false));

// 승인 요청: 모든 칸을 먼저 확인한다. 업체가 지금 받을 수 없으면 서버가 이유를 붙여 거절한다
$j('btnJoinRequest')?.addEventListener('click', () => {
  const company = $j('joinCompany').value.trim().toLowerCase();
  const name = $j('joinName').value.trim();
  const dept = $j('joinDept').value;
  const rank = $j('joinRank').value;
  const email = $j('joinEmail').value.trim();
  const error = $j('joinError');
  const pw = $j('joinPw').value;
  error.textContent = company !== checkedCompany ? '업체 코드를 다시 확인해 주세요'
    : !dept ? '부서를 골라 주세요' : !rank ? '직급을 골라 주세요' : !name ? '이름을 입력해 주세요' : !validEmail(email) ? '이메일 형식을 확인해 주세요'
      : pw.length < 8 ? '비밀번호는 8자 이상으로 정해 주세요'
        : pw !== $j('joinPw2').value ? '비밀번호 확인이 맞지 않습니다' : '';
  if (error.textContent) return;
  const b = $j('btnJoinRequest');
  b.dataset.sending = '1';
  syncRequestButton();
  api.post(`/join/api/companies/${pathPart(company)}/requests`, { name, dept, rank, email, password: pw })
    .done(v => {
      const mine = { company, reqId: v.reqId };
      saveMyJoin(mine);
      drawJoinForm(mine, v);
    })
    .fail(xhr => { error.textContent = apiMessage(xhr); })
    .always(() => { b.dataset.sending = ''; syncRequestButton(); });
});

// 요청 취소: 요청을 거두고 양식을 다시 쓸 수 있게 한다 (비밀번호는 다시 넣는다)
$j('btnJoinCancel')?.addEventListener('click', () => {
  const mine = loadMyJoin();
  const after = () => {
    saveMyJoin(null);
    drawJoinForm(null, null);
    $j('joinPw').value = $j('joinPw2').value = '';
    syncRequestButton();
  };
  if (!mine) return after();
  whileSending($j('btnJoinCancel'), api.post(`${joinReqUrl(mine)}/delete`))
    .done(after)
    .fail(xhr => { $j('joinError').textContent = apiMessage(xhr); });
});

// 로그인하러 가기 (승인으로 계정이 만들어진 뒤): 업체 코드와 사번을 채운 로그인 양식으로 돌아간다
joinForm?.addEventListener('submit', ev => {
  ev.preventDefault();
  const mine = loadMyJoin();
  if (!mine) return loadJoinForm();
  whileSending($j('btnJoinCreate'), api.get(joinReqUrl(mine)))
    .done(x => {
      if (x.state !== 'APPROVED') return drawJoinForm(mine, x);
      saveMyJoin(null);
      ['joinPw', 'joinPw2', 'joinName', 'joinEmail'].forEach(id => { $j(id).value = ''; });
      checkedCompany = '';
      setCompanyNote('', false);
      setDeptOptions('', [], []);
      showJoin(false);
      $j('loginCompany').value = mine.company;
      $j('loginId').value = x.empNo;
      $j('loginError').textContent = `계정이 만들어졌습니다. 사번 ${x.empNo}와 요청할 때 정한 비밀번호로 로그인하세요.`;
      $j('loginError').classList.add('is-ok');
      $j('loginPw').focus();
    })
    .fail(xhr => {
      if (xhr.status === 404) drawJoinForm(mine, null);
      else $j('joinError').textContent = apiMessage(xhr);
    });
});
$j('loginForm')?.addEventListener('input', () => $j('loginError').classList.remove('is-ok'));

// 승인을 기다리는 동안 5초마다 서버에 다시 묻는다. 계정 생성 양식이 있는 페이지에서만
joinForm?.addEventListener('input', () => { if (joinNotice && !loadMyJoin()) { joinNotice = ''; setJoinStatus('', ''); } });
if (joinForm) setInterval(() => { if (!joinForm.hidden && !document.hidden && loadMyJoin()) loadJoinForm(); }, 5000);

// ---------- 로그인 화면: 비밀번호 찾기 ----------
// 업체 코드 + 사번 → 계정을 만들 때의 이메일로 6자리 코드 → 맞으면 새 비밀번호(8자 이상, 두 번)로 바꾼다. 지금 비밀번호는 보여 주지 않는다
// (서버는 BCrypt 해시만 둔다). 운영자(ieum0000)는 대상이 아니다.
// 계정이 있든 없든 화면은 똑같이 답한다(ACC-07): 코드는 쓰는 계정에만 가고, 그 밖에는 맞는 코드가 없다.
const pwForm = $j('pwForm');
let pwTarget = null; // 코드를 보낸 { company, id }
let pwVerified = false;
let resetToken = ''; // 코드가 맞으면 서버가 주는 표. 새 비밀번호와 같이 보낸다
let pwVerifying = false;

function resetPwForm() {
  pwTarget = null;
  pwVerified = false;
  resetToken = '';
  ['pwCompany', 'pwId'].forEach(id => { $j(id).disabled = false; });
  $j('pwCode').value = '';
  $j('pwCode').hidden = true;
  $j('pwCodeNote').textContent = '';
  $j('pwCodeNote').classList.remove('is-ok');
  freeSendButton($j('btnPwSend'), '인증 메일 보내기');
  $j('pwNewBox').hidden = true;
  $j('btnPwReset').hidden = true;
  ['pwNew', 'pwNew2'].forEach(id => { $j(id).value = ''; });
  $j('pwError').textContent = '';
  syncPwReset();
}
function syncPwReset() {
  const pw = $j('pwNew').value;
  $j('btnPwReset').disabled = !(pwVerified && pw.length >= 8 && pw === $j('pwNew2').value);
}

function showPwFind(open) {
  loginForm.hidden = open;
  joinForm.hidden = true;
  pwForm.hidden = !open;
  if (open) {
    resetPwForm();
    $j('pwCompany').value = $j('loginCompany').value;
    $j('pwId').value = $j('loginId').value;
    ($j('pwCompany').value ? $j('pwId') : $j('pwCompany')).focus();
  }
}
$j('btnPwFind')?.addEventListener('click', () => showPwFind(true));
$j('btnPwBack')?.addEventListener('click', () => showPwFind(false));

$j('btnPwSend')?.addEventListener('click', () => {
  const company = $j('pwCompany').value.trim().toLowerCase();
  const id = $j('pwId').value.trim();
  const error = $j('pwError');
  error.textContent = !company || !id ? '업체 코드와 사번을 입력해 주세요' : '';
  if (error.textContent) return;
  const btn = $j('btnPwSend');
  btn.disabled = true;
  api.post('/join/api/password/code', { companyCode: company, empNo: id })
    .done(() => {
      pwTarget = { company, id };
      pwVerified = false;
      resetToken = '';
      $j('pwCode').value = '';
      $j('pwCode').hidden = false;
      $j('pwCode').focus();
      $j('pwCodeNote').classList.remove('is-ok');
      // 계정이 있든 없든 같은 문구 (계정이 있는지 드러나지 않게, ACC-07)
      $j('pwCodeNote').textContent = '등록된 이메일로 인증 코드를 보냈습니다. 받은 6자리를 10분 안에 넣어 주세요.';
      restSendButton(btn, '다시 보내기');
    })
    .fail(xhr => { error.textContent = apiMessage(xhr); btn.disabled = false; });
});
$j('pwCode')?.addEventListener('input', () => {
  const v = $j('pwCode').value.trim();
  if (v.length !== 6 || pwVerifying || !pwTarget) return;
  const sendBtn = $j('btnPwSend');
  pwVerifying = true;
  api.post('/join/api/password/verify', { companyCode: pwTarget.company, empNo: pwTarget.id, code: v })
    .done(r => {
      pwVerified = true;
      resetToken = r.resetToken;
      $j('pwCode').hidden = true;
      freeSendButton(sendBtn, '다시 보내기');
      sendBtn.disabled = true;
      ['pwCompany', 'pwId'].forEach(id => { $j(id).disabled = true; });
      $j('pwCodeNote').textContent = '이메일 인증을 마쳤습니다.';
      $j('pwCodeNote').classList.add('is-ok');
      $j('pwNewBox').hidden = false;
      $j('btnPwReset').hidden = false;
      $j('pwNew').focus();
    })
    .fail(xhr => {
      $j('pwCodeNote').textContent = apiMessage(xhr);
      if (xhr.status === 410) { $j('pwCode').value = ''; freeSendButton(sendBtn, '다시 보내기'); }
    })
    .always(() => { pwVerifying = false; syncPwReset(); });
});
// 다른 업체나 사번은 그 계정의 코드가 따로 필요하다
['pwCompany', 'pwId'].forEach(id => $j(id)?.addEventListener('input', () => { if (pwTarget) resetPwForm(); }));
['pwNew', 'pwNew2'].forEach(id => $j(id)?.addEventListener('input', () => {
  $j('pwError').textContent = '';
  syncPwReset();
}));

pwForm?.addEventListener('submit', ev => {
  ev.preventDefault();
  const pw = $j('pwNew').value;
  const error = $j('pwError');
  error.textContent = pw.length < 8 ? '비밀번호는 8자 이상으로 정해 주세요' : pw !== $j('pwNew2').value ? '비밀번호 확인이 맞지 않습니다' : '';
  if (error.textContent || !pwVerified || !resetToken) return;
  whileSending($j('btnPwReset'), api.post('/join/api/password/reset', { resetToken, password: pw }))
    .done(r => {
      ['pwCompany', 'pwId'].forEach(x => { $j(x).disabled = false; });
      showPwFind(false);
      $j('loginCompany').value = r.companyCode;
      $j('loginId').value = r.empNo;
      $j('loginPw').value = '';
      $j('loginError').textContent = '비밀번호를 바꿨습니다. 새 비밀번호로 로그인하세요.';
      $j('loginError').classList.add('is-ok');
      $j('loginPw').focus();
    })
    .fail(xhr => {
      const msg = apiMessage(xhr);
      // 410: 인증한 지 오래되었다. 처음부터 다시
      if (xhr.status === 410) resetPwForm();
      error.textContent = msg;
    })
    .always(syncPwReset);
});

// 헤더의 본인 비밀번호 변경은 common.js 7) 이 맡는다
