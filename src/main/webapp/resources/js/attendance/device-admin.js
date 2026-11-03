// 근무 > 근태 단말기 (views/attendance/admin/device.jsp). 업체 관리자·인사 담당 (요구분석서 3.10, 기능명세서 1.9).
// 단말기 키를 발급·다시 발급하고(처음이면 단말기가 함께 등록된다), 직원을 단말기 사용자 번호에 연결·해제한다.
//
//   GET  /attendance/admin/api/device              → { device: { name, companyName } | null, readOnly,
//                                                      people: [{ employeeId, employeeNo, name, dept, serving, userNo }],
//                                                      employees: [{ employeeId, employeeNo, name, dept }] (번호가 없는 재직 직원) }
//   POST /attendance/admin/api/device/key          → { key, deviceName, created }   키 원문은 이때 한 번만 온다
//   POST /attendance/admin/api/device/links        { employeeId, userNo } → { deviceId }
//   POST /attendance/admin/api/device/links/delete { employeeId } → { deviceId: '' }

const devEl = id => document.getElementById(id);
const devNode = (tag, cls, ...kids) => { const e = document.createElement(tag); if (cls) e.className = cls; e.append(...kids); return e; };
const devState = { data: null };

// 카드 아래 칸 펼치기·접기 (애니메이션은 common.css 의 .dev-fold). 접힌 칸은 inert 라 Tab 이 들어가지 않는다.
// 펼쳐진 동안 머리줄 버튼 대신 ^ 접기 버튼이 보이는 것도 CSS 가 한다
function devFold(id, open) {
  const box = devEl(id);
  box.classList.toggle('open', open);
  box.inert = !open;
  devEl(id === 'devKeyBox' ? 'btnIssueKey' : 'btnLinkOpen').setAttribute('aria-expanded', open);
}

// 화면을 열 때 (JSP 페이지가 열리면 부른다)
function openDeviceAdmin() {
  api.get('/attendance/admin/api/device')
    .done(renderDeviceAdmin)
    .fail(xhr => showToast(apiMessage(xhr, '단말기 정보를 불러오지 못했습니다')));
}

function renderDeviceAdmin(d) {
  devState.data = d;
  const issue = devEl('btnIssueKey');
  issue.hidden = false;
  issue.disabled = d.readOnly;

  // 연결할 직원 고르기: 번호가 없는 재직 직원
  const sel = devEl('devEmployee');
  sel.replaceChildren(devNode('option', '', d.employees.length ? '직원 고르기' : '연결할 직원이 없습니다'),
    ...d.employees.map(e => {
      const o = devNode('option', '', `${e.name} (${e.employeeNo}${e.dept ? ' · ' + e.dept : ''})`);
      o.value = e.employeeId;
      return o;
    }));
  sel.firstChild.value = '';
  const linkOpen = devEl('btnLinkOpen');
  linkOpen.disabled = d.readOnly || !d.device;
  linkOpen.title = !d.device ? '먼저 단말기 키를 발급해 단말기를 등록하세요' : '';
  if (linkOpen.disabled) setLinkOpen(false);
  devEl('btnLink').disabled = d.readOnly || !d.device;

  // 연결된 사람
  const rows = d.people.map(p => {
    const off = devNode('button', 'btn', '연결 해제');
    off.type = 'button';
    off.dataset.write = '';
    off.disabled = d.readOnly;
    off.addEventListener('click', () => devAsk(`${p.name}님의 단말기 번호 연결을 해제할까요?`,
      '해제해도 지금까지의 출퇴근 기록은 남습니다. 이 번호로 찍는 출퇴근은 더 받지 않습니다.', '연결 해제', () => {
        off.disabled = true;
        api.post('/attendance/admin/api/device/links/delete', { employeeId: p.employeeId })
          .done(() => { showToast(`${p.name}님의 연결을 해제했습니다`); openDeviceAdmin(); })
          .fail(xhr => { showToast(apiMessage(xhr)); off.disabled = false; });
      }));
    const tr = devNode('tr', '',
      devNode('td', '', p.userNo), devNode('td', '', p.employeeNo), devNode('td', '', p.name), devNode('td', '', p.dept || '-'),
      devNode('td', '', devNode('span', `status ${p.serving ? 'status-active' : 'status-waiting'}`, p.serving ? '재직' : '재직 아님')),
      devNode('td', '', off));
    tr.querySelectorAll('td').forEach((td, i) => { td.dataset.label = ['사용자 번호', '사번', '이름', '부서', '상태', '관리'][i]; });
    return tr;
  });
  devEl('devPeople').replaceChildren(...(rows.length ? rows
    : [devNode('tr', '', Object.assign(devNode('td', 'muted', '단말기에 연결된 직원이 없습니다'), { colSpan: 6 }))]));
}

// 키 발급: 이미 단말기가 있으면 예전 키가 막힌다는 것을 확인받는다
function issueKey() {
  const again = !!(devState.data && devState.data.device);
  const go = () => {
    const btn = devEl('btnIssueKey');
    btn.disabled = true;
    api.post('/attendance/admin/api/device/key')
      .done(r => {
        devEl('devKey').value = r.key;
        devFold('devKeyBox', true);
        showToast(r.created ? '단말기를 등록하고 키를 발급했습니다' : '키를 다시 발급했습니다');
        openDeviceAdmin();
      })
      .fail(xhr => { showToast(apiMessage(xhr)); btn.disabled = false; });
  };
  if (!again) return go();
  devAsk('단말기 키를 다시 발급할까요?', '예전 키를 넣어 둔 시뮬레이터는 바로 쓸 수 없게 되고, 새 키를 다시 입력해야 합니다.', '다시 발급', go);
}
devEl('btnIssueKey').addEventListener('click', issueKey);

// 접으면 애니메이션이 끝난 뒤 키 값도 지운다
devEl('btnKeyFold').addEventListener('click', () => {
  devFold('devKeyBox', false);
  setTimeout(() => { if (!devEl('devKeyBox').classList.contains('open')) devEl('devKey').value = ''; }, 300);
  devEl('btnIssueKey').focus();
});

devEl('btnCopyKey').addEventListener('click', () => {
  const input = devEl('devKey');
  const done = () => showToast('키를 복사했습니다. 시뮬레이터의 단말기 키 칸에 붙여 넣으세요');
  if (navigator.clipboard) navigator.clipboard.writeText(input.value).then(done, () => { input.select(); });
  else { input.select(); }
});

// 직원 연결: 머리줄 연결 버튼이 칸을 펼치고, ^ 버튼이 접는다
function setLinkOpen(open) {
  devFold('devLinkBox', open);
  if (!open) { devEl('devUserNo').value = ''; devEl('devLinkError').textContent = ''; }
}
devEl('btnLinkOpen').addEventListener('click', () => {
  setLinkOpen(true);
  (devEl('devEmployeeBtn') || devEl('devEmployee')).focus({ preventScroll: true }); // 꾸민 드롭다운(select.js)의 버튼
});
devEl('btnLinkFold').addEventListener('click', () => {
  setLinkOpen(false);
  devEl('btnLinkOpen').focus();
});

devEl('devLinkForm').addEventListener('submit', e => {
  e.preventDefault();
  const error = devEl('devLinkError');
  const employeeId = devEl('devEmployee').value;
  const userNo = devEl('devUserNo').value.trim();
  if (!employeeId) { error.textContent = '연결할 직원을 고르세요'; return; }
  if (!/^\d{4}$/.test(userNo)) { error.textContent = '단말기 사용자 번호는 숫자 4자리입니다'; devEl('devUserNo').focus(); return; }
  const btn = devEl('btnLink');
  btn.disabled = true;
  api.post('/attendance/admin/api/device/links', { employeeId: Number(employeeId), userNo })
    .done(() => {
      showToast(`사용자 번호 ${userNo}번과 연결했습니다`);
      setLinkOpen(false);
      openDeviceAdmin();
    })
    .fail(xhr => { error.textContent = apiMessage(xhr); btn.disabled = false; });
});
devEl('devUserNo').addEventListener('input', () => { devEl('devLinkError').textContent = ''; });

// 확인 창 (처음 쓸 때 만든다)
function devAsk(title, body, okLabel, onOk) {
  let dialog = devEl('devAskDialog');
  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'devAskDialog';
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
  dialog.onOk = onOk;
  if (!dialog.open) dialog.showModal();
}
