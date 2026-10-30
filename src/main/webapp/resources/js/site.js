// 소개 사이트 공통 (site.tag 가 불러온다): 좁은 화면 메뉴, 가격 계산기, 도입 절차 애니메이션, 떠 있는 문의 버튼.
const PRICE_PER_USER = 4000;
const won = n => `${n.toLocaleString('ko-KR')}원`;


// 칸을 틀린 것으로 표시하고 아래에 문구를 보인다. 문구가 비면 표시를 지운다.
function setError(input, message) {
  input.setAttribute('aria-invalid', message ? 'true' : 'false');
  const box = input.closest('.form-row').querySelector('.field-error');
  if (box) box.textContent = message;
}

// 모바일 메뉴
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

// 가격 계산기: 슬라이더와 숫자 칸을 서로 맞춘다. 숫자 칸은 슬라이더 최대값보다 크게 넣을 수 있다
const calcUsers = document.getElementById('calcUsers');
const calcRange = document.getElementById('calcRange');
if (calcUsers) {
  const show = (id, amount, per) => {
    const el = document.getElementById(id);
    el.replaceChildren(document.createTextNode(won(amount)));
    const unit = document.createElement('small');
    unit.textContent = per;
    el.append(unit);
  };
  // 월간, 그리고 첫 연간 구매(12개월, 10% 할인)
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

// 히어로: 그룹웨어 화면이 마우스 쪽으로 기울고, 멈추면 결재 카드가 화면 앞으로 튀어나온다
// (화면이 움직이는 동안에는 비켜 있다). 처음 볼 때는 화면이 평평한 데서 서서히 기운 뒤 카드가
// 나온다. 넓은 화면의 마우스에서만 하고 동작 줄이기 설정이면 끈다: 그 밖에는 화면이 정해진 기울기로
// 있고 카드는 그대로 보인다.
const heroDevice = document.querySelector('.hero .device');
const heroCard = document.querySelector('.hero .float-card');
if (heroDevice && heroCard) {
  const hero = heroDevice.closest('.hero');
  const tiltable = matchMedia('(hover: hover) and (pointer: fine) and (min-width: 901px) and (prefers-reduced-motion: no-preference)');
  const REST = { x: -5, y: 2 }; // 도 단위: 멈춰 있을 때의 기울기, CSS와 같다
  const MAX = 9;                // 마우스 쪽으로 가장 많이 기우는 각도
  let target = { ...REST };
  let cur = { ...REST };
  let frame = 0;
  let settle = 0;

  const apply = () => { heroDevice.style.transform = `perspective(1400px) rotateY(${cur.x.toFixed(2)}deg) rotateX(${cur.y.toFixed(2)}deg)`; };
  const showCard = () => heroCard.classList.remove('is-away');

  // 매 프레임 목표 쪽으로 다가가고, 도착하면 잠깐 기다렸다가 카드를 다시 꺼낸다
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
    // 마우스를 살짝 움직인 정도로는 카드를 치우지 않는다
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
    // rotateY > 0 은 앞면을 오른쪽으로, rotateX > 0 은 위로 돌린다: 화면이 마우스를 바라본다
    aim(nx * MAX, -ny * MAX * 0.7);
  });
  hero.addEventListener('pointerleave', () => { if (tiltable.matches) aim(REST.x, REST.y); });

  // 마우스 둘레의 은은한 빛, 스크린샷 위에만 (CSS가 --mx/--my로 그린다).
  // offsetX/Y는 그림 자체에서 재므로 그림이 기울어도 빛이 마우스 밑에 머문다
  heroDevice.addEventListener('pointermove', e => {
    if (!tiltable.matches || e.pointerType !== 'mouse') return;
    heroDevice.style.setProperty('--mx', `${e.offsetX}px`);
    heroDevice.style.setProperty('--my', `${e.offsetY}px`);
    heroDevice.classList.add('is-lit');
  });
  heroDevice.addEventListener('pointerleave', () => heroDevice.classList.remove('is-lit'));

  // 창이 좁아졌거나 터치로 바뀌었다: 카드가 보이는 멈춘 그림으로 돌아간다
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

// 도입 절차: 각 원의 테두리 빛(css/site.css .flow li::after)은 흐르는 줄의 밝은 끝이 원에 닿을 때
// 시작한다. 두 애니메이션이 같은 시계(--flow-time, linear)로 돌아서, 지연은 원의 실제 위치로 계산한다:
// 흐르는 줄은 선 길이의 4분의 1 너비이고, 그 끝은 주기의 처음 85% 동안 선의 시작(0)에서
// 선 길이의 1.25배까지 간다.
const flowList = document.querySelector('.flow');
function syncFlowRings() {
  if (!flowList) return;
  const time = parseFloat(getComputedStyle(flowList).getPropertyValue('--flow-time')) || 4;
  const box = flowList.getBoundingClientRect();
  const line = box.width * 0.84; // .flow::before 는 8%에서 92%까지
  if (line <= 0) return;
  const lineLeft = box.left + box.width * 0.08;
  flowList.querySelectorAll('li').forEach(li => {
    const r = li.getBoundingClientRect();
    const x = r.left + r.width / 2 - 26 - lineLeft; // 고리의 왼쪽 끝, 선 시작에서 잰 값
    li.style.setProperty('--ring-delay', `${(0.85 * time * x / (1.25 * line)).toFixed(3)}s`);
  });
}
syncFlowRings();
window.addEventListener('resize', syncFlowRings);
// 이 구역이 화면에 보일 때만 빛을 돌린다 (그러지 않으면 원뿔 그라데이션 다섯 개를 매 프레임 다시 그린다)
if (flowList && 'IntersectionObserver' in window) {
  new IntersectionObserver(([entry]) => flowList.classList.toggle('is-paused', !entry.isIntersecting)).observe(flowList);
}

// 떠 있는 도입 문의 버튼: 바닥글이 화면에 보이는 동안 숨겨 바닥글을 가리지 않게 한다
const fab = document.getElementById('fabInquiry');
const siteFooter = document.querySelector('.site-footer');
if (fab && siteFooter && 'IntersectionObserver' in window) {
  new IntersectionObserver(([entry]) => fab.classList.toggle('is-hidden', entry.isIntersecting)).observe(siteFooter);
}
