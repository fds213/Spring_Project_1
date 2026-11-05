// 모든 드롭다운을 같은 모양으로 그린다(디자인 2안). 기본 select의 펼친 목록은 운영체제마다 모양이 달라 꾸밀 수 없어서,
// select는 페이지에 (숨긴 채) 남아 값의 기준이 되고, 그 자리에 버튼과 목록을 그린다.
// 다른 스크립트는 전처럼 select의 .value·.selectedIndex·.disabled를 읽고 바꾸고 option을 더하면 된다(버튼이 따라 그려진다).
// 꾸민 뒤에 option.selected = true 로 직접 바꾸면 버튼이 따라 그려지지 않으니 .value 로 바꾼다(꾸미기 전 pickSelect 처럼은 괜찮다).
// select 를 replaceWith·remove 하지 말고 감싼 칸(.select)째 바꾼다(버튼·목록이 옆에 붙어 있다).
//
// - 대상: 화면에 있는 select와 나중에 스크립트가 더하는 select 모두.
//   빼는 것: multiple·size가 있는 목록, hidden인 select(다른 버튼이 값을 정하는 칸), data-ui="native"
// - 원래 select의 class·aria-label·data-write는 버튼에 옮겨 붙인다(크기 규칙과 읽기 전용 막기가 그대로 먹게).
// - 펼친 목록은 버튼 바로 아래(자리가 모자라면 위)에 화면 기준으로 띄운다. 표·창 안에서도 잘리지 않는다.
// - 안내 문구 option(값이 빈 "부서를 고르세요" 등)은 목록에 넣지 않는다. 안내 문구로 보는 것:
//   값이 빈 option 중 disabled·hidden·data-placeholder 인 것, 또는 required·data-ui="select" select의 빈 option.
//   그 밖의 빈 값 option("일반", "입력 안 함")은 고를 수 있는 항목이다.
// - 열면 목록이 펼쳐지고 화살표가 돈다. 고르면 select.value를 바꾸고 select에 'input'과 'change'를 보낸다.
// - 키보드: Enter/Space/화살표로 열고, 위·아래 화살표로 옮기고, Enter로 고르고, Escape나 Tab으로 닫는다.
(function () {
  const CHEVRON = '<svg class="select-arrow" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
  const CHECK = '<svg class="select-check" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10"/></svg>';
  const GAP = 6;       // 버튼과 목록 사이
  const EDGE = 8;      // 화면 가장자리 여백
  const LIST_MAX = 280; // 목록 최대 높이
  const VALUE = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value');
  const INDEX = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'selectedIndex');
  let opened = null; // 지금 열린 목록 { wrap, close }. 한 번에 하나만 연다
  const measure = document.createElement('canvas').getContext('2d'); // 글자 너비 재기

  const skip = s => s.multiple || s.size > 1 || s.hidden || s.dataset.ui === 'native' || s.classList.contains('select-native');

  function enhance(select) {
    if (skip(select)) return;
    const isPlaceholder = o => o.value === ''
      && (o.disabled || o.hidden || 'placeholder' in o.dataset || select.required || select.dataset.ui === 'select');

    // 감싸는 칸은 상자를 만들지 않는다(display: contents): 버튼이 원래 select 자리의 크기 규칙을 그대로 받는다
    const wrap = document.createElement('span');
    wrap.className = 'select';
    select.before(wrap);
    wrap.append(select);

    const id = select.id || `sel${Math.random().toString(36).slice(2, 8)}`;
    const button = document.createElement('button');
    button.type = 'button';
    // 원래 class(input·filter-select·크기 규칙)를 버튼이 받는다. 모양 class가 없으면 기본 입력칸 모양
    const look = [...select.classList];
    if (!look.includes('input') && !look.includes('filter-select')) look.unshift('input');
    button.className = [...look, 'select-trigger'].join(' ');
    button.id = `${id}Btn`;
    button.setAttribute('aria-haspopup', 'listbox');
    button.setAttribute('aria-expanded', 'false');
    ['aria-label', 'aria-describedby', 'data-write', 'title'].forEach(a => {
      if (select.hasAttribute(a)) button.setAttribute(a, select.getAttribute(a));
    });
    button.innerHTML = `<span class="select-value"></span>${CHEVRON}`;

    const list = document.createElement('ul');
    list.className = 'select-list';
    list.id = `${id}List`;
    list.setAttribute('role', 'listbox');
    button.setAttribute('aria-controls', list.id);
    wrap.append(button, list);

    select.classList.add('select-native');
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');

    // 칸의 label이 이제 버튼을 가리키므로, label을 누르면 사용자가 보는 버튼에 초점이 간다
    const label = select.id && document.querySelector(`label[for="${select.id}"]`);
    if (label) {
      label.htmlFor = button.id;
      if (!label.id) label.id = `${id}Label`;
      list.setAttribute('aria-labelledby', label.id);
    }

    let active = -1; // 열려 있는 동안 강조된 option의 번호
    const options = () => [...select.options].filter(o => !isPlaceholder(o));
    const items = () => [...list.children];

    function draw() {
      const chosen = select.selectedOptions[0];
      const empty = !chosen || isPlaceholder(chosen);
      button.querySelector('.select-value').textContent = chosen ? chosen.text : '';
      button.classList.toggle('is-placeholder', empty);
      button.disabled = select.disabled;
      list.replaceChildren(...options().map((o, i) => {
        const li = document.createElement('li');
        li.className = 'select-option';
        li.id = `${id}Opt${i}`;
        li.setAttribute('role', 'option');
        li.setAttribute('aria-selected', String(o.selected && !empty));
        if (o.disabled) li.setAttribute('aria-disabled', 'true');
        li.style.setProperty('--i', i);
        li.innerHTML = `<span></span>${CHECK}`;
        li.firstChild.textContent = o.text;
        // label 안에 있으면 고른 클릭이 label을 거쳐 버튼을 다시 누르지 않게 막는다
        li.addEventListener('click', e => { e.preventDefault(); pick(i); });
        li.addEventListener('mousemove', () => highlight(i));
        return li;
      }));
      if (select.disabled) close();
      fitWidth();
    }

    // 거르기 칸(filter-select)은 원래 select처럼 가장 긴 항목 너비를 지킨다(고를 때마다 칸 너비가 바뀌지 않게).
    // 입력칸(input)은 줄 너비를 채우므로 재지 않는다.
    // 너비는 CSS 변수(--fit-w)로만 넘긴다: 화면 CSS가 칸을 줄이라고 하면(.tk-row·.ea-row 의 min-width: 0) 그 규칙이 이긴다
    function fitWidth() {
      if (!button.classList.contains('filter-select')) return;
      const cs = getComputedStyle(button);
      measure.font = `${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
      const widest = Math.max(0, ...[...select.options].map(o => measure.measureText(o.text).width));
      // 글자 + 좌우 여백(10·10) + 화살표(18) + 사이(8) + 테두리(2)
      button.style.setProperty('--fit-w', `${Math.ceil(widest) + 48}px`);
    }

    // 다른 스크립트가 select.value·selectedIndex를 바꾸면 버튼도 다시 그린다
    [['value', VALUE], ['selectedIndex', INDEX]].forEach(([prop, d]) => {
      Object.defineProperty(select, prop, {
        configurable: true,
        get() { return d.get.call(this); },
        set(v) { d.set.call(this, v); draw(); },
      });
    });

    function highlight(i) {
      const all = items();
      if (!all.length) return;
      active = Math.max(0, Math.min(i, all.length - 1));
      all.forEach((li, k) => li.classList.toggle('is-active', k === active));
      button.setAttribute('aria-activedescendant', all[active].id);
      all[active].scrollIntoView({ block: 'nearest' });
    }

    // 목록을 버튼 아래에 화면 기준으로 둔다. 아래 자리가 모자라고 위가 더 넓으면 위로 연다
    function place() {
      const r = button.getBoundingClientRect();
      const below = window.innerHeight - r.bottom - GAP - EDGE;
      const above = r.top - GAP - EDGE;
      list.style.minWidth = `${r.width}px`;
      list.style.maxHeight = '';
      const natural = Math.min(list.scrollHeight, LIST_MAX);
      const up = natural > below && above > below;
      list.style.maxHeight = `${Math.max(120, Math.min(LIST_MAX, up ? above : below))}px`;
      list.classList.toggle('is-up', up);
      const height = list.offsetHeight;
      const top = up ? r.top - GAP - height : r.bottom + GAP;
      const left = Math.max(EDGE, Math.min(r.left, window.innerWidth - list.offsetWidth - EDGE));
      // 조상에 transform이 있으면 fixed 기준이 화면이 아니다: 같은 자리에 둔 빈 점으로 기준점을 재서 뺀다
      // (목록 자체를 재면 펼치는 애니메이션 중이라 어긋난다)
      const probe = document.createElement('span');
      probe.style.cssText = 'position:fixed;top:0;left:0;width:0;height:0;visibility:hidden';
      wrap.append(probe);
      const origin = probe.getBoundingClientRect();
      probe.remove();
      list.style.top = `${top - origin.top}px`;
      list.style.left = `${left - origin.left}px`;
    }

    function open() {
      if (select.disabled || wrap.classList.contains('open')) return;
      if (opened) opened.close();
      draw();
      wrap.classList.add('open');
      button.setAttribute('aria-expanded', 'true');
      place();
      const sel = options().findIndex(o => o.selected);
      highlight(sel >= 0 ? sel : 0);
      window.addEventListener('scroll', onMove, true);
      window.addEventListener('resize', onMove);
      opened = { wrap, close };
    }

    function close() {
      if (!wrap.classList.contains('open')) return;
      wrap.classList.remove('open');
      button.setAttribute('aria-expanded', 'false');
      button.removeAttribute('aria-activedescendant');
      window.removeEventListener('scroll', onMove, true);
      window.removeEventListener('resize', onMove);
      if (opened && opened.close === close) opened = null;
    }

    // 목록 안을 굴리는 것은 그대로 두고, 화면·표가 움직이면 따라간다. 열린 채 화면에서 빠졌으면(다시 그림) 닫는다
    function onMove(e) {
      if (!wrap.isConnected) { close(); return; }
      if (e && e.type === 'scroll' && list.contains(e.target)) return;
      place();
    }

    function pick(i) {
      const o = options()[i];
      if (!o || o.disabled) return;
      select.value = o.value;
      select.dispatchEvent(new Event('input', { bubbles: true }));
      select.dispatchEvent(new Event('change', { bubbles: true }));
      close();
      button.focus();
    }

    button.addEventListener('click', e => {
      e.preventDefault();
      if (wrap.classList.contains('open')) close(); else open();
    });
    button.addEventListener('keydown', e => {
      const isOpen = wrap.classList.contains('open');
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!isOpen) return open();
        highlight(active + (e.key === 'ArrowDown' ? 1 : -1));
      } else if (e.key === 'Home' && isOpen) {
        e.preventDefault(); highlight(0);
      } else if (e.key === 'End' && isOpen) {
        e.preventDefault(); highlight(items().length - 1);
      } else if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        if (isOpen) pick(active); else open();
      } else if (e.key === 'Escape' && isOpen) {
        e.preventDefault(); e.stopPropagation(); close(); // 창(dialog) 안이면 창까지 닫히지 않게
      } else if (e.key === 'Tab') {
        close();
      }
    });
    select.addEventListener('change', draw);
    // 나중에 더해지는 option과 다른 스크립트가 바꾸는 disabled
    new MutationObserver(draw).observe(select, { childList: true, attributes: true, attributeFilter: ['disabled'] });
    select.form?.addEventListener('reset', () => setTimeout(draw));
    draw();
    // 웹폰트가 아직 오지 않았으면 대신 쓰인 글꼴로 잰 너비라 좁을 수 있다: 다 오면 한 번 더 잰다
    if (document.fonts && document.fonts.status !== 'loaded') document.fonts.ready.then(fitWidth);
  }

  // 목록 밖을 누르면 열린 목록을 닫는다. select마다 달지 않고 하나만 둔다(다시 그리는 화면에서 리스너가 쌓이지 않게)
  document.addEventListener('pointerdown', e => {
    if (opened && !opened.wrap.contains(e.target)) opened.close();
  });

  // 지금 있는 select와, 화면 스크립트가 나중에 더하는 select(상세·창 안의 고르기 칸)를 모두 바꾼다
  const enhanceIn = root => {
    if (root.tagName === 'SELECT') enhance(root);
    else if (root.querySelectorAll) root.querySelectorAll('select').forEach(enhance);
  };
  enhanceIn(document);
  new MutationObserver(records => records.forEach(r => r.addedNodes.forEach(enhanceIn)))
    .observe(document.documentElement, { childList: true, subtree: true });
})();
