// Custom dropdown for <select data-ui="select">. A native select's open list can't be styled, so the select
// stays in the page (hidden) as the source of truth: other scripts keep reading .value, setting .disabled and
// adding options as before, and a button + list drawn over it shows the same state.
// - Opening slides the list down and turns the arrow up; choosing an option writes select.value and fires
//   'change' and 'input' on the select.
// - Keyboard: Enter/Space/Arrow keys open, Arrow Up/Down move, Enter picks, Escape or Tab closes.
// - A script that sets select.value itself must dispatch 'change' afterwards so the button redraws (hr.js does).
// The JSP version can use the same markup and file.
(function () {
  const CHEVRON = '<svg class="select-arrow" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
  const CHECK = '<svg class="select-check" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12 5 5 9-10"/></svg>';
  let openOne = null; // only one list open at a time

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

    // the field's label now points at the button, so clicking it focuses what the user sees
    const label = select.id && document.querySelector(`label[for="${select.id}"]`);
    if (label) {
      label.htmlFor = button.id;
      if (!label.id) label.id = `${id}Label`;
      list.setAttribute('aria-labelledby', label.id);
    }

    let active = -1; // index of the highlighted option while open
    const options = () => [...select.options].filter(o => o.value !== '');
    const items = () => [...list.children];

    function draw() {
      const chosen = select.selectedOptions[0];
      const empty = !chosen || chosen.value === '';
      // the empty option ("부서를 고르세요") is the placeholder, not a choice in the list
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
    // options added later (hr.js fills the departments) and disabled toggled by other scripts
    new MutationObserver(draw).observe(select, { childList: true, attributes: true, attributeFilter: ['disabled'] });
    select.form?.addEventListener('reset', () => setTimeout(draw));
    draw();
  }

  document.querySelectorAll('select[data-ui="select"]').forEach(enhance);
})();
