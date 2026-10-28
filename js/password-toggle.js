// Every password field gets an eye button. Hidden by default (eye with a slash);
// pressing it shows the password and removes the slash. Pages only write a plain
// <input type="password">; this file adds the button, so JSP pages need nothing extra.
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
