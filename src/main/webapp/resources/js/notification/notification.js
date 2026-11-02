// 알림 목록 (views/notification/list.jsp). 모든 직원 (요구분석서 3.4 업무 알림, 통합 계획서 4장 업무 알림).
// 본인 알림만 본다. 누르면 읽음으로 바꾸고 그 화면(link)으로 간다. 메뉴 배지(종 아이콘의 숫자)는 common.js 가 /badges 로 다시 센다.
// 읽음 저장도 쓰기라서 이용 기간이 끝난 업체(body.is-readonly)에서는 저장하지 않고 화면만 연다.
//
// 서버 API (화면이 먼저 정한 제안. notification 모듈 담당이 정하면 그에 맞춘다):
//   GET  /notification/api/notifications?filter=ALL|UNREAD&page=
//        → { page, pages, unread, items: [{ id, type, title, body, link, read, at }] }
//        type: APPROVAL_REQUEST(결재 요청) | APPROVAL_RESULT(결재 결과) | TICKET_ASSIGNED(티켓 배정) | TICKET_RESULT(티켓 처리 결과)
//        link: 누르면 갈 화면 주소(컨텍스트 경로 뒤, 예: /approval/list). 없으면 읽음만 바꾼다
//   POST /notification/api/notifications/{id}/read
//   POST /notification/api/notifications/read-all
// util.js 의 h, span, whenFull, showToast 를 쓴다.
(() => {
  const TYPE = {
    APPROVAL_REQUEST: { label: '결재 요청', cls: 'is-approval' },
    APPROVAL_RESULT: { label: '결재 결과', cls: 'is-approval' },
    TICKET_ASSIGNED: { label: '티켓 배정', cls: 'is-ticket' },
    TICKET_RESULT: { label: '티켓 처리', cls: 'is-ticket' },
  };
  const ICON = {
    'is-approval': '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="m9 15 2 2 4-4"/>',
    'is-ticket': '<path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18v3h3l6.3-6.3a4 4 0 0 0 5.4-5.4l-2.4 2.4-2.6-.6-.6-2.6z"/>',
    'is-other': '<path d="M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3H4a4 4 0 0 0 2-3v-3a7 7 0 0 1 4-6"/>',
  };
  const ctx = document.body.dataset.ctx || '';
  const $ = id => document.getElementById(id);
  const state = { filter: 'ALL', page: 1, pages: 1, items: [], seq: 0 };
  const readOnly = () => document.body.classList.contains('is-readonly');

  function openNotificationView() { load(true); }
  window.openNotificationView = openNotificationView;

  // first: 처음부터 다시(탭을 바꿨을 때). 아니면 다음 쪽을 아래에 붙인다(더 보기)
  function load(first) {
    const my = ++state.seq;
    const page = first ? 1 : state.page + 1;
    api.get('/notification/api/notifications', { filter: state.filter, page })
      .done(r => {
        if (my !== state.seq) return;
        state.page = r.page || page;
        state.pages = r.pages || 1;
        state.items = first ? (r.items || []) : state.items.concat(r.items || []);
        $('notiUnread').textContent = r.unread || 0;
        render();
      })
      .fail(xhr => showToast(apiMessage(xhr, '알림을 불러오지 못했습니다')));
  }

  function render() {
    document.querySelectorAll('.noti-tab').forEach(t => t.setAttribute('aria-selected', t.dataset.filter === state.filter));
    const list = $('notiList');
    if (!state.items.length) {
      list.replaceChildren(h('li', 'noti-empty', state.filter === 'UNREAD' ? '안 읽은 알림이 없습니다' : '아직 받은 알림이 없습니다'));
    } else {
      list.replaceChildren(...state.items.map(item));
    }
    $('btnNotiMore').hidden = state.page >= state.pages;
    $('btnReadAll').disabled = !Number($('notiUnread').textContent);
  }

  function item(n) {
    const type = TYPE[n.type] || { label: '알림', cls: 'is-other' };
    const icon = h('span', `noti-ic ${type.cls}`);
    icon.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON[type.cls]}</svg>`;
    const btn = h('button', 'noti-item' + (n.read ? '' : ' is-unread'), icon,
      h('span', 'noti-main',
        h('span', 'noti-head', span(type.label, 'noti-type'), span(whenFull(n.at), 'noti-time')),
        h('strong', 'noti-title', n.title),
        ...(n.body ? [h('span', 'noti-body', n.body)] : [])),
      ...(n.read ? [] : [h('span', 'noti-dot', h('span', 'visually-hidden', '안 읽음'))]));
    btn.type = 'button';
    btn.addEventListener('click', () => open(n));
    return h('li', '', btn);
  }

  // 누르면: 안 읽었으면 읽음으로 바꾸고(읽기 전용 업체는 건너뜀), 갈 화면이 있으면 그리로
  function open(n) {
    const go = () => { if (n.link) location.href = ctx + n.link; };
    if (n.read || readOnly()) { go(); return; }
    api.post(`/notification/api/notifications/${encodeURIComponent(n.id)}/read`)
      .done(() => {
        n.read = true;
        $('notiUnread').textContent = Math.max(0, Number($('notiUnread').textContent) - 1);
        if (window.IEUM && IEUM.refreshBadges) IEUM.refreshBadges();
        if (n.link) go(); else render();
      })
      .fail(xhr => { showToast(apiMessage(xhr, '읽음으로 바꾸지 못했습니다')); go(); });
  }

  document.querySelectorAll('.noti-tab').forEach(t => t.addEventListener('click', () => {
    if (state.filter === t.dataset.filter) return;
    state.filter = t.dataset.filter;
    load(true);
  }));
  $('btnNotiMore').addEventListener('click', () => load(false));
  $('btnReadAll').addEventListener('click', e => {
    const b = e.currentTarget;
    b.disabled = true;
    api.post('/notification/api/notifications/read-all')
      .done(() => {
        showToast('모든 알림을 읽음으로 바꿨습니다');
        if (window.IEUM && IEUM.refreshBadges) IEUM.refreshBadges();
        load(true);
      })
      .fail(xhr => { showToast(apiMessage(xhr)); b.disabled = false; });
  });
})();
