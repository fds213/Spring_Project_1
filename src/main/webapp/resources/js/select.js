// <select data-ui="select">용 꾸민 드롭다운. 기본 select의 펼친 목록은 꾸밀 수 없어서 select는
// 페이지에 (숨긴 채) 남아 값의 기준이 된다: 다른 스크립트는 전처럼 .value를 읽고 .disabled를 바꾸고
// option을 더하며, 그 위에 그린 버튼과 목록이 같은 상태를 보여 준다.
// - 열면 목록이 아래로 펼쳐지고 화살표가 위를 향한다. 고르면 select.value를 바꾸고 select에
//   'change'와 'input'을 보낸다.
// - 키보드: Enter/Space/화살표로 열고, 위·아래 화살표로 옮기고, Enter로 고르고, Escape나 Tab으로 닫는다.
// - select.value를 직접 바꾸는 스크립트는 뒤에 'change'를 보내야 버튼이 다시 그려진다 (employee.js가 그렇게 한다).
(function () {
  const CHEVRON = '<svg class="select-arrow" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
  const CHECK = '<svg class="select-check" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10"/></svg>';
  let openOne = null; // 한 번에 목록 하나만 연다

  function enhance(select) {
    const wrap = document.createElement('div');
    wrap.className = 'select';
    select.before(wrap);
    wrap.append(select);
    select.classList.add('select-native');
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');

    const id = select.id || `sel${Math.random().toString(36).slice(2, 8)}`;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'input select-trigger';
    button.id = `${id}Btn`;
    button.setAttribute('aria-haspopup', 'listbox');
    button.setAttribute('aria-expanded', 'false');
    button.innerHTML = `<span class="select-value"></span>${CHEVRON}`;
    const list = document.createElement('ul');
    list.className = 'select-list';
    list.id = `${id}List`;
    list.setAttribute('role', 'listbox');
    button.setAttribute('aria-controls', list.id);
    wrap.append(button, list);

    // 칸의 label이 이제 버튼을 가리키므로, label을 누르면 사용자가 보는 버튼에 초점이 간다
    const label = select.id && document.querySelector(`label[for="${select.id}"]`);
    if (label) {
      label.htmlFor = button.id;
      if (!label.id) label.id = `${id}Label`;
      list.setAttribute('aria-labelledby', label.id);
    }

    let active = -1; // 열려 있는 동안 강조된 option의 번호
    const options = () => [...select.options].filter(o => o.value !== '');
    const items = () => [...list.children];

    function draw() {
      const chosen = select.selectedOptions[0];
      const empty = !chosen || chosen.value === '';
      // 빈 option("부서를 고르세요")은 안내 문구라 목록에 넣지 않는다
      button.querySelector('.select-value').textContent = chosen ? chosen.text : '';
      button.classList.toggle('is-placeholder', empty);
      button.disabled = select.disabled;
      list.replaceChildren(...options().map((o, i) => {
        const li = document.createElement('li');
        li.className = 'select-option';
        li.id = `${id}Opt${i}`;
        li.setAttribute('role', 'option');
        li.setAttribute('aria-selected', String(o.selected && !empty));
        li.style.setProperty('--i', i);
        li.innerHTML = `<span></span>${CHECK}`;
        li.firstChild.textContent = o.text;
        li.addEventListener('click', () => pick(i));
        li.addEventListener('mousemove', () => highlight(i));
        return li;
      }));
      if (select.disabled) close();
    }

    function highlight(i) {
      const all = items();
      if (!all.length) return;
      active = Math.max(0, Math.min(i, all.length - 1));
      all.forEach((li, k) => li.classList.toggle('is-active', k === active));
      button.setAttribute('aria-activedescendant', all[active].id);
      all[active].scrollIntoView({ block: 'nearest' });
    }

    function open() {
      if (select.disabled || wrap.classList.contains('open')) return;
      if (openOne) openOne();
      draw();
      wrap.classList.add('open');
      button.setAttribute('aria-expanded', 'true');
      const sel = options().findIndex(o => o.selected);
      highlight(sel >= 0 ? sel : 0);
      openOne = close;
    }

    function close() {
      if (!wrap.classList.contains('open')) return;
      wrap.classList.remove('open');
      button.setAttribute('aria-expanded', 'false');
      button.removeAttribute('aria-activedescendant');
      if (openOne === close) openOne = null;
    }

    function pick(i) {
      const o = options()[i];
      if (!o) return;
      select.value = o.value;
      select.dispatchEvent(new Event('input', { bubbles: true }));
      select.dispatchEvent(new Event('change', { bubbles: true }));
      close();
      button.focus();
    }

    button.addEventListener('click', () => (wrap.classList.contains('open') ? close() : open()));
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
        e.preventDefault(); close();
      } else if (e.key === 'Tab') {
        close();
      }
    });
    document.addEventListener('pointerdown', e => { if (!wrap.contains(e.target)) close(); });

    select.addEventListener('change', draw);
    // 나중에 더해지는 option(employee.js가 부서를 채운다)과 다른 스크립트가 바꾸는 disabled
    new MutationObserver(draw).observe(select, { childList: true, attributes: true, attributeFilter: ['disabled'] });
    select.form?.addEventListener('reset', () => setTimeout(draw));
    draw();
  }

  document.querySelectorAll('select[data-ui="select"]').forEach(enhance);
})();
