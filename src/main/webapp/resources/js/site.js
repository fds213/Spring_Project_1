// Prototype only: intro site. Shared form helpers (setError, clearOnInput, loadStore, saveStore) are used by
// trial.js and inquiry.js too. The inquiry form lives on its own page now (inquiry.html, js/inquiry.js).
const PRICE_PER_USER = 4000;
const won = n => `${n.toLocaleString('ko-KR')}원`;

function loadStore(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
}
function saveStore(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage blocked: the demo still runs for this page */ }
}

// Mark a field invalid with a message under it; empty message clears it.
function setError(input, message) {
  input.setAttribute('aria-invalid', message ? 'true' : 'false');
  const box = input.closest('.form-row').querySelector('.field-error');
  if (box) box.textContent = message;
}
function clearOnInput(form) {
  form.querySelectorAll('input, textarea').forEach(el => el.addEventListener('input', () => setError(el, '')));
}

// mobile menu
const siteHeader = document.getElementById('siteHeader');
const btnSiteMenu = document.getElementById('btnSiteMenu');
if (btnSiteMenu) {
  btnSiteMenu.addEventListener('click', () => {
    const open = siteHeader.classList.toggle('open');
    btnSiteMenu.setAttribute('aria-expanded', open);
  });
  document.querySelectorAll('.site-nav a').forEach(a => a.addEventListener('click', () => {
    siteHeader.classList.remove('open');
    btnSiteMenu.setAttribute('aria-expanded', 'false');
  }));
}

// price calculator: the slider and the number box stay in sync; the box allows more than the slider's max
const calcUsers = document.getElementById('calcUsers');
const calcRange = document.getElementById('calcRange');
if (calcUsers) {
  const show = (id, amount, per) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.replaceChildren(document.createTextNode(won(amount)));
    const unit = document.createElement('small');
    unit.textContent = per;
    el.append(unit);
  };
  // monthly, and the first annual purchase (12 months, 10% off)
  const render = n => {
    show('calcTotal', n * PRICE_PER_USER, '/ 월');
    show('calcAnnual', Math.floor(n * PRICE_PER_USER * 12 * 90 / 100), '/ 년');
  };
  const fromBox = () => {
    const n = Math.max(0, Math.floor(Number(calcUsers.value) || 0));
    if (calcRange) calcRange.value = Math.min(Math.max(n, calcRange.min), calcRange.max);
    render(n);
  };
  calcUsers.addEventListener('input', fromBox);
  if (calcRange) calcRange.addEventListener('input', () => { calcUsers.value = calcRange.value; render(Number(calcRange.value)); });
  fromBox();
}

// Hero: the groupware screen leans toward the mouse, and once it comes to rest the approval card pops up in
// front of it (it steps aside while the screen moves). First view: the screen eases in from flat, then the
// card arrives. Mouse on a wide layout only, and off for reduced motion: elsewhere the screen keeps its
// fixed lean and the card just stays.
const heroDevice = document.querySelector('.hero .device');
const heroCard = document.querySelector('.hero .float-card');
if (heroDevice && heroCard) {
  const hero = heroDevice.closest('.hero');
  const tiltable = matchMedia('(hover: hover) and (pointer: fine) and (min-width: 901px) and (prefers-reduced-motion: no-preference)');
  const REST = { x: -5, y: 2 }; // degrees: the resting lean, same as the CSS
  const MAX = 9;                // furthest lean toward the mouse
  let target = { ...REST };
  let cur = { ...REST };
  let frame = 0;
  let settle = 0;

  const apply = () => { heroDevice.style.transform = `perspective(1400px) rotateY(${cur.x.toFixed(2)}deg) rotateX(${cur.y.toFixed(2)}deg)`; };
  const showCard = () => heroCard.classList.remove('is-away');

  // ease toward the target each frame; when it gets there, wait a beat and bring the card back
  function step() {
    cur.x += (target.x - cur.x) * 0.1;
    cur.y += (target.y - cur.y) * 0.1;
    if (Math.abs(target.x - cur.x) + Math.abs(target.y - cur.y) > 0.05) {
      apply();
      frame = requestAnimationFrame(step);
      return;
    }
    cur = { ...target };
    apply();
    frame = 0;
    settle = setTimeout(showCard, 180);
  }

  function aim(x, y) {
    // a twitch of the mouse doesn't send the card away
    if (Math.abs(x - target.x) + Math.abs(y - target.y) < 0.4 && !frame) return;
    target = { x, y };
    heroCard.classList.add('is-away');
    clearTimeout(settle);
    if (!frame) frame = requestAnimationFrame(step);
  }

  const clamp = v => Math.max(-1, Math.min(1, v));
  hero.addEventListener('pointermove', e => {
    if (!tiltable.matches || e.pointerType !== 'mouse') return;
    const r = heroDevice.getBoundingClientRect();
    const nx = clamp((e.clientX - (r.left + r.width / 2)) / (r.width / 2));
    const ny = clamp((e.clientY - (r.top + r.height / 2)) / (r.height / 2));
    // rotateY > 0 turns the face right, rotateX > 0 turns it up: the screen faces the mouse
    aim(nx * MAX, -ny * MAX * 0.7);
  });
  hero.addEventListener('pointerleave', () => { if (tiltable.matches) aim(REST.x, REST.y); });

  // a soft light around the mouse, on the screenshot only (CSS draws it from --mx/--my).
  // offsetX/Y are measured on the picture itself, so the light stays under the mouse while it tilts
  heroDevice.addEventListener('pointermove', e => {
    if (!tiltable.matches || e.pointerType !== 'mouse') return;
    heroDevice.style.setProperty('--mx', `${e.offsetX}px`);
    heroDevice.style.setProperty('--my', `${e.offsetY}px`);
    heroDevice.classList.add('is-lit');
  });
  heroDevice.addEventListener('pointerleave', () => heroDevice.classList.remove('is-lit'));

  // window narrowed, or switched to touch: back to the still picture with the card shown
  tiltable.addEventListener('change', () => {
    if (tiltable.matches) return;
    cancelAnimationFrame(frame);
    frame = 0;
    clearTimeout(settle);
    cur = { ...REST };
    target = { ...REST };
    heroDevice.style.transform = '';
    heroDevice.classList.remove('is-lit');
    showCard();
  });

  if (tiltable.matches) {
    cur = { x: 0, y: 0 };
    target = { ...cur };
    apply();
    aim(REST.x, REST.y);
  }
}

// floating 도입 문의 button: hide it while the footer is on screen so it never covers the footer
const fab = document.getElementById('fabInquiry');
const siteFooter = document.querySelector('.site-footer');
if (fab && siteFooter && 'IntersectionObserver' in window) {
  new IntersectionObserver(([entry]) => fab.classList.toggle('is-hidden', entry.isIntersecting)).observe(siteFooter);
}
