// 소개 사이트 > 이메일 인증 확인 (views/site/verify.jsp, ACC-06). 로그인 없이 연다.
//   POST /trial/api/verify  { token } → 200 { message, alreadyVerified } / 400 잘못된·무효 링크, 410 24시간 지남 { message }
// 링크 값은 주소의 # 뒤에 있다(서버 접속 로그에 남지 않게). 버튼을 눌러야 보낸다.
(() => {
  const $ = id => document.getElementById(id);
  const token = location.hash.slice(1);
  const result = $('verifyResult');
  const button = $('btnVerify');
  const login = $('linkLogin');

  function show(message, ok) {
    result.textContent = message;
    result.className = ok ? 'done' : 'done err';
    result.hidden = false;
  }

  // 로그인 화면으로 가면 인증 전 거절 뒤 새 메일을 받을 수 있으니 실패에도 길을 남긴다
  function finish(message, ok) {
    show(message, ok);
    $('verifyGuide').hidden = true;
    button.hidden = true;
    login.hidden = false;
    if (ok) login.classList.add('btn-primary');
  }

  if (!token) {
    finish('인증 링크가 올바르지 않습니다. 메일의 링크를 다시 열어 주세요.', false);
    return;
  }

  button.addEventListener('click', () => {
    button.disabled = true;
    button.textContent = '인증하는 중…';
    api.post('/trial/api/verify', { token })
      .done(r => finish(r.message, true))
      .fail(xhr => {
        // 응답을 못 받은 경우만 다시 누를 수 있게 둔다. 링크가 틀렸다는 답이면 다시 눌러도 같다
        if (xhr.status === 400 || xhr.status === 410) {
          finish(apiMessage(xhr, '인증하지 못했습니다.'), false);
          return;
        }
        show(apiMessage(xhr, '인증하지 못했습니다. 잠시 뒤 다시 눌러 주세요.'), false);
        button.disabled = false;
        button.textContent = '인증하기';
      });
  });
})();
