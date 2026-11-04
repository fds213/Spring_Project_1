// 소개 사이트 > 2주 무료 체험 신청 (views/site/trial.jsp). 로그인 없이 연다 (요구분석서 ACC-01).
// 1 정보 입력 → 2 신청 확인 → 3 신청 완료. 비밀번호 확인과 이용 조건 동의는 화면에서만 확인하고 서버로 보내지 않는다.
//
//   POST /trial/api/apply  { companyName, seats, email, adminName, password }
//     → 200 { companyName, companyCode, employeeNo, email, seats, trialStartDate, trialLastDate, verificationRequired, mailSent }
//   POST /trial/api/verify/resend  → 200 { message } / 그 밖 { message } (다시 보내기는 이 세션이 신청한 업체에만 간다)
//     → 400 입력 오류·409 이미 쓴 이메일 { message, field } (field = 보내는 값의 이름) / 503 업체 코드 소진·500 그 밖 { message }
// 업체 코드와 관리자 사번은 서버가 정한다. 둘을 알아야 로그인할 수 있어 완료 화면에 크게 보인다.
(() => {
  const $ = id => document.getElementById(id);
  // 서버의 field 이름 → 입력 칸
  const FIELD_INPUT = { companyName: 'pCompany', seats: 'pUsers', email: 'pEmail', adminName: 'pAdminName', password: 'pPw' };
  const EMAIL = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/; // 서버(SiteService)와 같은 규칙
  const TRIAL_DAYS = 14; // 신청일이 첫날, 14일째 날까지 (서버 ServicePeriods)
  const LATER = '신청하면 정해집니다';
  let sending = false;

  // 칸을 틀린 것으로 표시하고 아래에 문구를 보인다. 문구가 비면 표시를 지운다
  function setError(input, message) {
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    const box = input.closest('.form-row').querySelector('.field-error');
    if (box) box.textContent = message || '';
  }

  const readForm = () => ({
    companyName: $('pCompany').value.trim(),
    seats: Number($('pUsers').value),
    email: $('pEmail').value.trim(),
    adminName: $('pAdminName').value.trim(),
    password: $('pPw').value,
  });

  // 1단계 확인: 서버와 같은 규칙에 비밀번호 확인을 더한다. 틀린 칸이 있으면 첫 칸으로 초점
  function validate(f) {
    const wrong = [];
    const check = (id, message) => { setError($(id), message); if (message) wrong.push(id); };
    check('pCompany', !f.companyName ? '업체명을 입력해 주세요' : f.companyName.length > 200 ? '업체명은 200자 이하로 입력해 주세요' : '');
    check('pUsers', Number.isInteger(f.seats) && f.seats >= 1 && f.seats <= 10 ? '' : '이용 인원은 관리자를 포함해 1~10명입니다');
    check('pEmail', !f.email ? '이메일을 입력해 주세요' : f.email.length > 254 || !EMAIL.test(f.email) ? '이메일 형식이 맞지 않습니다' : '');
    check('pAdminName', !f.adminName ? '관리자 이름을 입력해 주세요' : f.adminName.length > 100 ? '관리자 이름은 100자 이하로 입력해 주세요' : '');
    check('pPw', f.password.length < 8 ? '비밀번호는 8자 이상이어야 합니다'
      : new TextEncoder().encode(f.password).length > 72 ? '비밀번호가 너무 깁니다 (영문 72자, 한글 24자까지)' : '');
    const pw2 = $('pPw2').value;
    check('pPw2', !pw2 ? '비밀번호를 한 번 더 입력해 주세요' : pw2 !== f.password ? '비밀번호가 서로 다릅니다' : '');
    if (wrong.length) $(wrong[0]).focus();
    return wrong.length === 0;
  }

  // 단계 바꾸기: 지금 단계만 보이고, 위 단계 표시가 따라간다
  function go(step) {
    ['step1', 'step2', 'step3'].forEach((id, i) => { $(id).hidden = i + 1 !== step; });
    document.querySelectorAll('#stepper li').forEach(li => {
      const n = Number(li.dataset.step);
      li.classList.toggle('on', n === step);
      li.classList.toggle('done-step', n < step);
      if (n === step) li.setAttribute('aria-current', 'step'); else li.removeAttribute('aria-current');
    });
    $('stepper').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  const dot = iso => iso.replace(/-/g, '.'); // 2026-11-02 → 2026.11.02
  function trialRange() {
    const start = new Date();
    const last = new Date(start.getFullYear(), start.getMonth(), start.getDate() + TRIAL_DAYS - 1);
    const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    return { start: iso(start), last: iso(last) };
  }

  // 1 → 2: 입력을 확인하고 신청 내용을 보여 준다
  $('step1').addEventListener('submit', e => {
    e.preventDefault();
    const f = readForm();
    if (!validate(f)) return;
    const range = trialRange();
    $('sCompany').textContent = f.companyName;
    $('sCode').textContent = LATER;
    $('sAdmin').textContent = `${f.adminName} (사번은 ${LATER})`;
    $('sEmail').textContent = f.email;
    $('sUsers').textContent = `${f.seats}명 (관리자 포함)`;
    $('sTrial').textContent = `${dot(range.start)} ~ ${dot(range.last)} (${TRIAL_DAYS}일)`;
    $('trialError').hidden = true;
    go(2);
  });
  ['pCompany', 'pUsers', 'pEmail', 'pAdminName', 'pPw', 'pPw2'].forEach(id => $(id).addEventListener('input', () => setError($(id), '')));
  document.querySelectorAll('#step2 [data-go]').forEach(b => b.addEventListener('click', () => go(Number(b.dataset.go))));
  $('pAgree').addEventListener('change', () => setError($('pAgree'), ''));

  // 2 → 서버: 동의를 확인하고 보낸다. 칸이 틀렸다는 답이면 1단계 그 칸으로 돌아간다
  $('step2').addEventListener('submit', e => {
    e.preventDefault();
    if (sending) return;
    if (!$('pAgree').checked) { setError($('pAgree'), '이용 조건에 동의해 주세요'); $('pAgree').focus(); return; }
    const f = readForm();
    const btn = e.target.querySelector('button[type="submit"]');
    const error = $('trialError');
    sending = true;
    btn.disabled = true;
    btn.textContent = '신청하는 중…';
    error.hidden = true;
    api.post('/trial/api/apply', f)
      .done(showResult)
      .fail(xhr => {
        const message = apiMessage(xhr, '신청을 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.');
        const field = xhr.responseJSON && FIELD_INPUT[xhr.responseJSON.field];
        if ((xhr.status === 400 || xhr.status === 409) && field) {
          go(1);
          setError($(field), message);
          $(field).focus();
          return;
        }
        error.textContent = message;
        error.hidden = false;
      })
      .always(() => { sending = false; btn.disabled = false; btn.textContent = '무료 체험 시작'; });
  });

  // 3. 신청 완료: 업체 코드와 관리자 사번이 가장 먼저 보이게. 그룹웨어는 설치한 앱으로 들어가므로 앱 설치 버튼을 둔다.
  // 앱의 로그인 화면이 업체 코드를 채워 두게 이 브라우저에 저장한다
  function showResult(r) {
    try { localStorage.setItem('ieum-last-company', r.companyCode); } catch (e) { /* 저장소 막힘: 직접 입력 */ }
    $('pPw').value = '';
    $('pPw2').value = '';

    const node = (tag, cls, ...kids) => { const el = document.createElement(tag); if (cls) el.className = cls; el.append(...kids); return el; };
    const row = (label, value, cls) => node('tr', cls || '', node('th', '', label), node('td', '', value));
    const copy = node('button', 'btn btn-lg', '업체 코드·사번 복사');
    copy.type = 'button';
    copy.addEventListener('click', () => {
      const text = `업체 코드 ${r.companyCode} / 관리자 사번 ${r.employeeNo}`;
      if (navigator.clipboard) navigator.clipboard.writeText(text).then(() => { copy.textContent = '복사했습니다'; }, () => {});
    });
    // 헤더의 다운로드와 같은 동작(install.js): 설치 창을 띄우고, 띄울 수 없으면 설치 안내를 보여 준다
    const install = node('button', 'btn btn-primary btn-lg', 'IEUM 앱 설치하기');
    install.type = 'button';
    install.addEventListener('click', () => { if (typeof startDownload === 'function') startDownload(); });

    // 이메일 인증(ACC-06): 인증을 요구하면 메일 안내를 앞에, 다시 보내기를 버튼 줄에 둔다. 메일을 끈 환경(개발)에서는 지금처럼 바로 로그인한다
    const status = node('p', r.mailSent ? 'done' : 'done err',
      r.mailSent ? `${r.email} 주소로 인증 메일을 보냈습니다. 메일의 링크를 열어 [인증하기]를 누르면 로그인할 수 있습니다. 링크는 24시간 동안 쓸 수 있습니다.`
        : '인증 메일을 보내지 못했습니다. 잠시 뒤 아래 버튼으로 다시 보내 주세요.');
    status.setAttribute('role', 'status');
    const resend = node('button', 'btn btn-lg', '인증 메일 다시 보내기');
    resend.type = 'button';
    resend.addEventListener('click', () => {
      resend.disabled = true;
      api.post('/trial/api/verify/resend')
        .done(res => { status.className = 'done'; status.textContent = res.message; })
        .fail(xhr => { status.className = 'done err'; status.textContent = apiMessage(xhr, '인증 메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.'); })
        .always(() => { resend.disabled = false; });
    });

    $('step3').replaceChildren(
      node('h3', '', r.verificationRequired ? '메일로 이메일을 인증해 주세요' : '무료 체험을 시작했습니다'),
      ...(r.verificationRequired ? [status] : []),
      node('p', 'sub', `${r.companyName}의 관리자 계정이 만들어졌습니다. IEUM 앱을 설치한 뒤 아래 업체 코드와 관리자 사번, 신청할 때 정한 비밀번호로 로그인하세요.`),
      node('table', 'summary',
        row('업체 코드', r.companyCode, 'sum'),
        row('관리자 사번', r.employeeNo, 'sum'),
        row('업체명', r.companyName),
        row('담당자 이메일', r.email),
        row('이용 인원', `${r.seats}명 (관리자 포함, 체험 중 10명까지)`),
        row('무료 체험 기간', `${dot(r.trialStartDate)} ~ ${dot(r.trialLastDate)}`)),
      node('p', 'done', '업체 코드와 사번은 로그인할 때마다 씁니다. 꼭 적어 두세요. 결제는 체험 중 그룹웨어의 업체 관리 > 이용 결제에서 합니다.'),
      node('div', 'actions', ...(r.verificationRequired ? [resend] : []), copy, install));
    go(3);
    $('step3').focus({ preventScroll: true });
  }

  // 업체 코드·사번은 서버가 정한다: 입력 화면에는 잠긴 칸으로 안내만
  $('pCode').textContent = LATER;
  $('pAdminNo').textContent = LATER;
  go(1);
})();
