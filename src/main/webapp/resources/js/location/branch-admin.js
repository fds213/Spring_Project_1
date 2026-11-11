// 업체 관리 > 지점 관리 (views/location/admin/branch.jsp, 업체 관리자만). 요구분석서 3.4 지점.
// 업체의 지점을 추가·수정·사용 중지한다. 여기 '사용 중' 지점이 장비 신청의 수령 지점과 장애·문의 접수의 지점 목록에 나온다.
// 규칙 (서버 BranchService가 지키고, 화면은 먼저 알려 줄 뿐이다):
//   - 지점명 1~100자, 주소 1~500자, 상세 주소 0~200자(비우면 없음). 글자 수는 서버처럼 문자 단위로 센다.
//   - 같은 업체 안에서 지점명은 겹치지 않는다(사용 중지한 지점 포함, DB 고유 제약) → 409
//   - 사용 중지: 미지급 장비 요청(접수·배정·처리 중인 장비 지급 티켓)이 남은 지점은 서버가 거절한다 → 409, 그 문구를 그대로 보인다.
//     다시 사용으로 되돌리는 API는 없다.
//   - 읽기 전용 업체(이용 기간 끝남): 버튼은 흐리게 보이고(data-write, common.js) 서버가 409로 거절한다(ReadOnlyInterceptor).
// 서버 API (BranchController. 업체는 로그인 정보에서만 꺼낸다):
//   GET  /location/admin/api/branches                     → [{ branchId, branchName, address, detailAddress | null, useYn: 'Y' | 'N' }] (지점명순)
//   POST /location/admin/api/branches                     { branchName, address, detailAddress } → 성공만   400: 입력, 409: 같은 이름
//   POST /location/admin/api/branches/{branchId}/update   { branchName, address, detailAddress } → 성공만   404: 없는 지점, 409: 같은 이름
//   POST /location/admin/api/branches/{branchId}/disable  → 성공만 (이미 중지한 지점도 성공)                409: 미지급 장비 요청
// util.js 의 h, span, inqCell, emptyTr, writeBtn, showToast 를 쓴다.
(() => {
  const API = '/location/admin/api/branches';
  const $b = id => document.getElementById(id);
  const chars = s => [...s].length; // 서버(codePointCount)처럼 이모지 한 개를 1자로 센다
  // 입력 칸: [요청 키, 입력 id, '을/를' 붙은 이름, '은/는' 붙은 이름, 최대 글자, 필수]
  const FIELDS = [
    ['branchName', 'brName', '지점명을', '지점명은', 100, true],
    ['address', 'brAddress', '주소를', '주소는', 500, true],
    ['detailAddress', 'brDetail', '상세 주소를', '상세 주소는', 200, false],
  ];
  let branches = null; // 마지막으로 받은 목록
  let editing = null; // 수정 창에 연 지점 (추가 창이면 null)
  let formSeq = 0; // 창을 열 때마다 1씩: 저장 응답이 늦게 와도 그사이 다시 연 다른 창을 건드리지 않게
  let loadSeq = 0; // 늦게 온 옛 응답이 새 목록을 덮지 않게

  /** 지점 목록을 다시 받아 표를 그린다. */
  function load() {
    const seq = ++loadSeq;
    api.get(API)
      .done(list => {
        if (seq !== loadSeq) return;
        branches = list || [];
        render();
      })
      .fail(xhr => {
        if (seq !== loadSeq) return;
        $b('brCount').textContent = '';
        $b('brList').replaceChildren(emptyTr(4, apiMessage(xhr, '지점 목록을 불러오지 못했습니다. 잠시 뒤 다시 해 주세요')));
      });
  }

  /** 사용 중 지점을 위에, 사용 중지 지점을 아래에 그린다(각각 서버가 준 지점명순). */
  function render() {
    const active = branches.filter(b => b.useYn === 'Y');
    const stopped = branches.filter(b => b.useYn !== 'Y');
    $b('brCount').textContent = branches.length
      ? `사용 중 ${active.length}개` + (stopped.length ? ` · 사용 중지 ${stopped.length}개` : '') : '';
    const rows = [...active, ...stopped].map(rowOf);
    $b('brList').replaceChildren(...(rows.length ? rows
      : [emptyTr(4, '아직 지점이 없습니다. 지점을 추가하면 장비 신청과 장애·문의 접수에서 고를 수 있습니다')]));
  }

  /** 지점 한 줄: 이름, 주소(상세 주소는 아래 작게), 상태, 관리 버튼. */
  function rowOf(b) {
    const on = b.useYn === 'Y';
    const edit = writeBtn('수정', 'btn', () => openForm(b));
    edit.setAttribute('aria-label', `${b.branchName} 수정`);
    const actions = h('div', 'row-actions', edit);
    if (on) {
      const stop = writeBtn('사용 중지', 'btn btn-danger-line', btn => askDisable(b, btn));
      stop.setAttribute('aria-label', `${b.branchName} 사용 중지`);
      actions.append(stop);
    }
    return h('tr', on ? '' : 'is-inactive',
      inqCell('지점', h('strong', 'br-text', b.branchName)),
      inqCell('주소', span(b.address, 'br-text'), b.detailAddress ? h('small', 'cell-sub br-text', b.detailAddress) : null),
      inqCell('상태', span(on ? '사용 중' : '사용 중지', `status status-${on ? 'active' : 'retired'}`)),
      inqCell('관리', actions));
  }

  // ---------- 추가·수정 창 ----------
  /** 지점 추가(b 없음) 또는 수정(b) 창을 연다. */
  function openForm(b) {
    formSeq += 1;
    editing = b || null;
    $b('brDialogTitle').textContent = editing ? '지점 수정' : '지점 추가';
    $b('brSubmit').textContent = editing ? '저장' : '추가';
    $b('brName').value = editing ? editing.branchName : '';
    $b('brAddress').value = editing ? editing.address : '';
    $b('brDetail').value = editing ? (editing.detailAddress || '') : '';
    $b('brError').textContent = '';
    $b('brSubmit').disabled = false;
    $b('brDialog').showModal();
    $b('brName').focus();
  }

  /** 입력 칸의 값을 요청 본문으로 모은다(앞뒤 공백 제거). */
  const formBody = () => Object.fromEntries(FIELDS.map(([key, id]) => [key, $b(id).value.trim()]));

  /** 서버와 같은 기준으로 먼저 확인한다. 문제가 있으면 [입력 id, 안내], 없으면 null. */
  function formError(body) {
    for (const [key, id, obj, topic, max, required] of FIELDS) {
      if (required && !body[key]) return [id, `${obj} 입력해 주세요`];
      if (chars(body[key]) > max) return [id, `${topic} ${max}자 이하로 입력해 주세요`];
    }
    const same = (branches || []).find(x => x.branchName === body.branchName && (!editing || x.branchId !== editing.branchId));
    if (same) {
      return ['brName', same.useYn === 'Y' ? '같은 이름의 지점이 이미 있습니다'
        : '같은 이름의 사용 중지 지점이 있습니다. 다른 이름을 써 주세요'];
    }
    return null;
  }

  /** 수정 창에서 아무것도 바꾸지 않았는지. */
  const unchanged = (b, body) => b.branchName === body.branchName && b.address === body.address
    && (b.detailAddress || '') === body.detailAddress;

  $b('brForm').addEventListener('submit', e => {
    e.preventDefault();
    const btn = $b('brSubmit');
    if (btn.disabled) return; // 보내는 중
    const body = formBody();
    const error = formError(body);
    $b('brError').textContent = error ? error[1] : '';
    if (error) { $b(error[0]).focus(); return; }
    const target = editing;
    if (target && unchanged(target, body)) { $b('brDialog').close(); return; }
    btn.disabled = true; // 두 번 눌러 두 번 저장되지 않게
    const seq = formSeq;
    const sameForm = () => seq === formSeq && $b('brDialog').open; // 저장을 누른 그 창이 아직 열려 있는지
    (target ? api.post(`${API}/${encodeURIComponent(target.branchId)}/update`, body) : api.post(API, body))
      .done(() => {
        if (sameForm()) $b('brDialog').close();
        showToast(target ? `'${body.branchName}' 지점을 고쳤습니다` : `'${body.branchName}' 지점을 추가했습니다`);
        load();
      })
      .fail(xhr => {
        if (xhr.status === 401 || xhr.status === 403) return; // common.js 가 로그인 화면·안내로 처리한다
        if (xhr.status === 404) load(); // 다른 화면에서 바뀐 목록을 다시 받는다
        // 그사이 창을 닫았거나 다른 지점 창을 열었으면 그 창 대신 알림으로 알린다
        if (!sameForm()) { showToast(apiMessage(xhr)); return; }
        $b('brError').textContent = apiMessage(xhr);
        if (xhr.status === 409) $b('brName').focus();
      })
      .always(() => { if (seq === formSeq) btn.disabled = false; });
  });
  $b('brCancel').addEventListener('click', () => $b('brDialog').close());
  $b('brDialog').addEventListener('close', () => { editing = null; });
  FIELDS.forEach(([, id]) => $b(id).addEventListener('input', () => { $b('brError').textContent = ''; }));
  $b('brAddBtn').addEventListener('click', () => openForm(null));

  // ---------- 사용 중지 확인·거절 안내 창 ----------
  let pending = null; // [사용 중지]를 누르면 할 일 (안내만 할 때는 null)

  /** 확인 창을 연다. onOk가 없으면 안내만 하고 [확인] 하나만 보인다. */
  function ask(title, body, onOk) {
    const dialog = $b('brAskDialog');
    $b('brAskTitle').textContent = title;
    $b('brAskBody').textContent = body;
    dialog.querySelector('[value="cancel"]').hidden = !onOk;
    const ok = dialog.querySelector('[value="ok"]');
    ok.textContent = onOk ? '사용 중지' : '확인';
    ok.className = onOk ? 'btn btn-danger-line' : 'btn btn-primary';
    pending = onOk || null;
    if (!dialog.open) dialog.showModal();
  }

  $b('brAskDialog').querySelectorAll('.modal-actions button').forEach(b => b.addEventListener('click', () => {
    const run = b.value === 'ok' ? pending : null;
    pending = null;
    $b('brAskDialog').close();
    if (run) run();
  }));

  /** 사용 중지를 확인받고 보낸다. 서버가 거절하면(미지급 장비 요청 등) 그 문구를 창으로 보인다. */
  function askDisable(b, btn) {
    ask(`'${b.branchName}' 지점을 사용 중지할까요?`,
      '새 장비 신청과 장애·문의 접수의 지점 목록에서 빠집니다. 이미 지급한 장비와 접수한 티켓에는 그대로 남습니다. '
        + '사용 중지한 지점을 다시 사용으로 되돌리는 기능은 아직 없습니다.',
      () => {
        btn.disabled = true;
        api.post(`${API}/${encodeURIComponent(b.branchId)}/disable`)
          .done(() => { showToast(`'${b.branchName}' 지점을 사용 중지했습니다`); load(); })
          .fail(xhr => {
            btn.disabled = false;
            if (xhr.status === 401 || xhr.status === 403) return; // common.js 가 로그인 화면·안내로 처리한다
            // 그사이 다른 지점의 확인 창을 열었으면 그 창을 덮지 않고 알림으로 알린다
            if ($b('brAskDialog').open) showToast(apiMessage(xhr));
            else ask('사용 중지할 수 없습니다', apiMessage(xhr), null);
            if (xhr.status === 404) load();
          });
      });
  }

  // 화면을 열 때 (JSP 페이지가 열리면 부른다)
  window.openBranchAdmin = load;
})();
