// 계정 만들기 (login.jsp 안의 #joinForm). 로그인 없이 쓰는 화면이라 layout.tag·common.js 없이 jQuery만 쓴다.
// 한 폼이 세 상태를 오간다(시안 hr.js와 같은 흐름):
//   신청: 업체 코드 확인 → 부서·직급·이름 → 이메일 인증 → 승인 요청 (버튼은 업체 확인과 이메일 인증을 마쳐야 켜진다)
//   승인 대기: "승인 요청을 하였습니다", 입력칸 잠김, 요청 취소 가능. 몇 초마다 상태를 묻는다
//   승인됨: "승인 완료되었습니다", 사번(잠김)·비밀번호·비밀번호 확인 → 계정 생성
// 요청 상태는 요청할 때 받은 requestId(무작위 키)로만 볼 수 있다(요구분석서 v1.6 ACC-04). 이 브라우저가 그 키를
// 기억해 두어 화면을 닫았다 열어도 이어진다. 다른 PC에서는 키가 없으므로 1시간 뒤 다시 요청한다.
// API (화면 명세 "가입 요청과 승인", 응답은 { success, message, data }):
//   GET  /join/company/{companyCode}     업체 이름·부서 목록
//   POST /join/email-code                인증 코드 보내기 { companyCode, email }
//   POST /join/email-verify              코드 확인 { email, code } → { emailToken }
//   POST /join                           승인 요청 { companyCode, name, deptId, rankName, email, emailToken } → { requestId, minutesLeft }
//   GET  /join/{requestId}               상태 { status: REQUESTED | APPROVED | REJECTED | EXPIRED, minutesLeft, empNo, wait }
//   POST /join/{requestId}/cancel        요청 취소 (쓰기는 모두 POST, v1.6 3.12)
//   POST /join/{requestId}/account       계정 생성 { password } → { empNo, status: ACTIVE | WAITING }
(function ($) {
  'use strict';

  const ctx = document.body.dataset.ctx || '';
  const $j = id => document.getElementById(id);
  const loginForm = $j('loginForm');
  const joinForm = $j('joinForm');
  if (!joinForm) return;

  // CSRF: the token from login.jsp's meta tags on every POST
  const csrfToken = $('meta[name="_csrf"]').attr('content');
  const csrfHeader = $('meta[name="_csrf_header"]').attr('content');
  function api(method, path, body) {
    return $.ajax({
      url: ctx + path,
      method,
      contentType: body ? 'application/json' : undefined,
      data: body ? JSON.stringify(body) : undefined,
      dataType: 'json',
      beforeSend: xhr => { if (csrfToken && csrfHeader && method !== 'GET') xhr.setRequestHeader(csrfHeader, csrfToken); },
    });
  }
  // the server's message for a refused request, or a general one when there is none (network down, 500)
  const failText = xhr => (xhr.responseJSON && xhr.responseJSON.message) || '잠시 뒤 다시 시도해 주세요';

  const REQUEST_KEY = 'ieum-join-request'; // { companyCode, requestId } of the request this browser made
  const loadMine = () => { try { return JSON.parse(localStorage.getItem(REQUEST_KEY)); } catch (e) { return null; } };
  const saveMine = v => { try { if (v) localStorage.setItem(REQUEST_KEY, JSON.stringify(v)); else localStorage.removeItem(REQUEST_KEY); } catch (e) { /* 저장소 막힘 */ } };

  const FIELDS = ['joinCompany', 'joinDept', 'joinRank', 'joinName', 'joinEmail', 'joinCode'];
  const validEmail = v => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
  let checkedCompany = ''; // the company code confirmed with 확인 ('' = not confirmed)
  let emailToken = ''; // from /join/email-verify, for the address in verifiedEmail
  let verifiedEmail = '';
  let codeSentTo = '';
  let state = null; // the last status of this browser's request (null = none)

  const typedCompany = () => $j('joinCompany').value.trim().toLowerCase();
  const typedEmail = () => $j('joinEmail').value.trim().toLowerCase();
  const emailVerifiedNow = () => !!verifiedEmail && typedEmail() === verifiedEmail;

  function setNote(id, text, ok) {
    const note = $j(id);
    note.textContent = text;
    note.hidden = !text && id === 'joinCompanyNote';
    note.classList.toggle('is-ok', !!text && ok === true);
    note.classList.toggle('is-bad', !!text && ok === false);
  }
  function setStatus(text, kind) {
    const s = $j('joinStatus');
    s.textContent = text;
    s.className = 'join-status' + (kind ? ' is-' + kind : '');
    s.hidden = !text;
  }
  // the department list holds the confirmed company's departments; before that only the hint, switched off
  function setDepts(list, keepId) {
    const sel = $j('joinDept');
    sel.replaceChildren(new Option(list ? '부서를 고르세요' : '업체 코드를 먼저 확인하세요', ''),
      ...(list || []).map(d => new Option(d.name, d.deptId)));
    sel.value = keepId && (list || []).some(d => String(d.deptId) === String(keepId)) ? keepId : '';
    sel.disabled = !list;
    sel.dispatchEvent(new Event('change')); // redraws the custom dropdown (select.js)
  }

  // ---------- 1) 업체 코드 확인 ----------
  function checkCompany(keepDeptId) {
    const code = typedCompany();
    if (!code) { setNote('joinCompanyNote', '업체 코드를 입력해 주세요', false); return $.Deferred().reject(); }
    $j('btnJoinCheck').disabled = true;
    return api('GET', '/join/company/' + encodeURIComponent(code))
      .done(res => {
        checkedCompany = code;
        const depts = (res.data && res.data.depts) || [];
        setNote('joinCompanyNote', `${res.data.companyName} · 부서 ${depts.length}개를 불러왔습니다`, true);
        setDepts(depts, keepDeptId);
      })
      .fail(xhr => {
        checkedCompany = '';
        setNote('joinCompanyNote', failText(xhr), false);
        setDepts(null);
      })
      .always(() => { $j('btnJoinCheck').disabled = !!state; syncRequestButton(); });
  }
  $j('btnJoinCheck').addEventListener('click', () => checkCompany());
  $j('joinCompany').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); checkCompany(); } });
  // a changed code needs 확인 again: the departments of the old one are cleared
  $j('joinCompany').addEventListener('input', () => {
    if (checkedCompany && typedCompany() === checkedCompany) return;
    checkedCompany = '';
    setNote('joinCompanyNote', '', null);
    setDepts(null);
    syncRequestButton();
  });

  // ---------- 2) 이메일 인증 (승인 요청 전) ----------
  function resetEmail() {
    emailToken = '';
    verifiedEmail = '';
    codeSentTo = '';
    $j('joinCode').value = '';
    $j('joinCode').hidden = true;
    $j('btnJoinSend').textContent = '인증 메일 보내기';
    $j('btnJoinSend').disabled = false;
    setNote('joinCodeNote', '', null);
  }
  $j('btnJoinSend').addEventListener('click', () => {
    const email = $j('joinEmail').value.trim();
    if (!validEmail(email)) { setNote('joinCodeNote', '이메일 형식을 확인해 주세요.', false); return; }
    $j('btnJoinSend').disabled = true;
    api('POST', '/join/email-code', { companyCode: typedCompany(), email })
      .done(() => {
        codeSentTo = email.toLowerCase();
        emailToken = '';
        verifiedEmail = '';
        $j('joinCode').value = '';
        $j('joinCode').hidden = false;
        $j('joinCode').focus();
        setNote('joinCodeNote', `${email}로 인증 코드를 보냈습니다. 받은 6자리를 넣어 주세요. (10분 안에)`, null);
        $j('btnJoinSend').textContent = '다시 보내기';
      })
      .fail(xhr => setNote('joinCodeNote', failText(xhr), false)) // 1분에 한 번·하루 5번을 넘기면 서버가 거절한다
      .always(() => { $j('btnJoinSend').disabled = emailVerifiedNow(); syncRequestButton(); });
  });
  $j('joinCode').addEventListener('input', () => {
    const code = $j('joinCode').value.trim();
    if (code.length !== 6 || typedEmail() !== codeSentTo) return;
    api('POST', '/join/email-verify', { email: codeSentTo, code })
      .done(res => {
        emailToken = res.data.emailToken;
        verifiedEmail = codeSentTo;
        $j('joinCode').hidden = true;
        $j('btnJoinSend').disabled = true;
        setNote('joinCodeNote', '이메일 인증을 마쳤습니다.', true);
      })
      .fail(xhr => setNote('joinCodeNote', failText(xhr), false))
      .always(syncRequestButton);
  });
  // a different address needs its own code
  $j('joinEmail').addEventListener('input', () => {
    if ((verifiedEmail || codeSentTo) && typedEmail() !== (verifiedEmail || codeSentTo)) resetEmail();
    syncRequestButton();
  });

  // ---------- 승인 요청 버튼: 업체 코드 확인과 이메일 인증을 모두 마치면 파란색 ----------
  function syncRequestButton() {
    const b = $j('btnJoinRequest');
    const company = !!checkedCompany && typedCompany() === checkedCompany;
    const email = emailVerifiedNow();
    const on = !state && company && email;
    b.disabled = !on;
    b.classList.toggle('btn-primary', on);
    const hint = $j('joinRequestHint');
    hint.hidden = !!state || on;
    hint.textContent = !company && !email ? '업체 코드 확인과 이메일 인증을 마치면 승인 요청을 할 수 있습니다'
      : !company ? '업체 코드를 확인하면 승인 요청을 할 수 있습니다' : '이메일을 인증하면 승인 요청을 할 수 있습니다';
  }
  function syncCreateButton() {
    const pw = $j('joinPw').value;
    $j('btnJoinCreate').disabled = !(pw.length >= 8 && pw === $j('joinPw2').value);
  }
  ['joinPw', 'joinPw2'].forEach(id => $j(id).addEventListener('input', syncCreateButton));

  // ---------- 상태별로 폼 그리기 ----------
  function render() {
    const waiting = !!state && state.status === 'REQUESTED';
    const approved = !!state && state.status === 'APPROVED';
    FIELDS.forEach(id => { $j(id).disabled = waiting || approved || (id === 'joinDept' && !checkedCompany); });
    $j('joinDept').dispatchEvent(new Event('change'));
    $j('btnJoinCheck').disabled = waiting || approved;
    $j('btnJoinSend').disabled = waiting || approved || emailVerifiedNow();
    $j('btnJoinRequest').hidden = approved;
    $j('btnJoinCancel').hidden = !waiting;
    $j('joinAccount').hidden = !approved;
    $j('btnJoinCreate').hidden = !approved;
    if (waiting) {
      setStatus(`승인 요청을 하였습니다. 업체 관리자나 부서장이 승인하면 이 화면에서 바로 계정을 만들 수 있습니다. (${state.minutesLeft}분 안에 승인되지 않으면 다시 요청해야 합니다)`, 'wait');
    } else if (approved) {
      setStatus(state.wait
        ? '승인 완료되었습니다. 구매 인원이 모두 차서, 계정을 만든 뒤 업체 관리자가 자리를 주면 로그인할 수 있습니다.'
        : '승인 완료되었습니다. 비밀번호를 정한 뒤 계정 생성을 눌러 주세요.', 'ok');
      $j('joinId').textContent = state.empNo;
    }
    syncRequestButton();
    syncCreateButton();
  }

  // ask the server how this browser's request stands; an ended request clears the key and says why
  function refresh() {
    const mine = loadMine();
    if (!mine) { state = null; render(); return; }
    api('GET', '/join/' + encodeURIComponent(mine.requestId))
      .done(res => {
        const s = res.data || {};
        if (s.status === 'REQUESTED' || s.status === 'APPROVED') {
          state = s;
        } else {
          state = null;
          saveMine(null);
          setStatus(s.status === 'REJECTED'
            ? '가입 요청이 반려되었습니다. 업체에 확인한 뒤 내용을 고쳐 다시 승인 요청을 해 주세요.'
            : '요청 뒤 1시간 안에 승인되지 않아 만료되었습니다. 다시 승인 요청을 해 주세요.', 'bad');
        }
        render();
      })
      .fail(xhr => {
        if (xhr.status === 404) { saveMine(null); state = null; setStatus('요청을 찾을 수 없습니다. 다시 승인 요청을 해 주세요.', 'bad'); render(); }
      });
  }

  // ---------- 승인 요청 / 요청 취소 / 계정 생성 ----------
  $j('btnJoinRequest').addEventListener('click', () => {
    const name = $j('joinName').value.trim();
    const deptId = $j('joinDept').value;
    const email = $j('joinEmail').value.trim();
    const error = $j('joinError');
    error.textContent = !deptId ? '부서를 골라 주세요' : !name ? '이름을 입력해 주세요'
      : !emailVerifiedNow() ? '이메일을 인증해 주세요' : '';
    if (error.textContent) return;
    $j('btnJoinRequest').disabled = true;
    api('POST', '/join', {
      companyCode: checkedCompany, name, deptId: Number(deptId), rankName: $j('joinRank').value.trim() || '사원', email, emailToken,
    })
      .done(res => {
        // what was sent is kept too, so the locked form can show it again after the screen was closed
        saveMine({ companyCode: checkedCompany, requestId: res.data.requestId, deptId, rankName: $j('joinRank').value.trim(), name, email });
        state = { status: 'REQUESTED', minutesLeft: res.data.minutesLeft };
        render();
      })
      .fail(xhr => { error.textContent = failText(xhr); syncRequestButton(); });
  });
  $j('btnJoinCancel').addEventListener('click', () => {
    const mine = loadMine();
    if (!mine) return;
    api('POST', '/join/' + encodeURIComponent(mine.requestId) + '/cancel').always(() => {
      saveMine(null);
      state = null;
      setStatus('', '');
      render();
    });
  });
  joinForm.addEventListener('submit', e => {
    e.preventDefault();
    const mine = loadMine();
    const pw = $j('joinPw').value;
    if (!mine || !state || state.status !== 'APPROVED' || pw.length < 8 || pw !== $j('joinPw2').value) return;
    $j('btnJoinCreate').disabled = true;
    api('POST', '/join/' + encodeURIComponent(mine.requestId) + '/account', { password: pw })
      .done(res => {
        saveMine(null);
        state = null;
        ['joinPw', 'joinPw2'].forEach(id => { $j(id).value = ''; });
        // back to the login form with the company code and the new 사번 filled in
        show(false);
        $j('loginCompany').value = mine.companyCode;
        $j('loginId').value = res.data.empNo;
        $j('loginError').textContent = res.data.status === 'ACTIVE'
          ? `계정이 만들어졌습니다. 사번 ${res.data.empNo}와 방금 정한 비밀번호로 로그인하세요.`
          : `계정이 만들어졌습니다(사번 ${res.data.empNo}). 구매 인원이 모두 차서 업체 관리자가 자리를 주면 로그인할 수 있습니다.`;
        $j('loginError').classList.add('is-ok');
        $j('loginPw').focus();
      })
      .fail(xhr => { $j('joinError').textContent = failText(xhr); syncCreateButton(); });
  });
  loginForm.addEventListener('input', () => $j('loginError').classList.remove('is-ok'));

  // ---------- 열고 닫기 ----------
  function show(open) {
    loginForm.hidden = open;
    joinForm.hidden = !open;
    if (!open) return;
    const mine = loadMine();
    if (mine) {
      // a request made earlier in this browser: filled in again as it was sent, the form follows its status
      $j('joinCompany').value = mine.companyCode;
      $j('joinRank').value = mine.rankName || '';
      $j('joinName').value = mine.name || '';
      $j('joinEmail').value = mine.email || '';
      verifiedEmail = (mine.email || '').toLowerCase();
      setNote('joinCodeNote', '이메일 인증을 마쳤습니다.', true);
      checkCompany(mine.deptId);
    } else if (!$j('joinCompany').value) {
      $j('joinCompany').value = $j('loginCompany').value;
    }
    refresh();
    $j('joinCompany').focus();
  }
  $j('btnJoin').addEventListener('click', () => show(true));
  $j('btnJoinBack').addEventListener('click', () => show(false));

  // while the form is open and the tab visible, ask every 5 seconds whether the request was approved
  setInterval(() => { if (!joinForm.hidden && !document.hidden && loadMine()) refresh(); }, 5000);
  setDepts(null);
})(jQuery);
