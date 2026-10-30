// 모든 비밀번호 칸에 눈 버튼을 붙인다. 처음에는 가려져 있고(빗금 친 눈),
// 누르면 비밀번호가 보이고 빗금이 없어진다. 페이지는 평범한
// <input type="password">만 쓴다. 버튼은 이 파일이 붙이므로 JSP에 따로 넣을 것이 없다.
const EYE_ICON =
  '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
  '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>' +
  '<path class="pw-slash" d="M4 4l16 16"/></svg>';

function addPasswordToggle(input) {
  const wrap = document.createElement('span');
  wrap.className = 'pw-field';
  input.replaceWith(wrap);
  wrap.append(input);

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'pw-toggle';
  btn.setAttribute('aria-pressed', 'false');
  btn.setAttribute('aria-label', '비밀번호 보기');
  btn.innerHTML = EYE_ICON;
  wrap.append(btn);

  btn.addEventListener('click', () => {
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    btn.setAttribute('aria-pressed', show);
    btn.setAttribute('aria-label', show ? '비밀번호 숨기기' : '비밀번호 보기');
  });
}

document.querySelectorAll('input[type="password"]').forEach(addPasswordToggle);
