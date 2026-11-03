// 알림 목록 (views/notification/list.jsp). 모든 직원 (요구분석서 3.4 업무 알림, 통합 계획서 4장 업무 알림).
// 본인 알림만 본다(제목·내용·시각·읽음 여부). 누르면 읽음으로 바꾸고 결재·티켓 화면으로 간다. 메뉴 배지(종 아이콘)는 common.js 가 /badges 로 다시 센다.
// 읽음 저장도 쓰기라서 이용 기간이 끝난 업체(body.is-readonly)에서는 저장하지 않고 화면만 연다.
// 서버: NotificationController (notification 모듈)
//   GET  /notification/api/list?page=      → { page, pages, total, items: [{ notificationId, notificationType, title, message, approvalDocumentId, itTicketId, readAt, createdAt }] }
//   POST /notification/api/{id}/read       → 없거나 남의 알림이면 404
// util.js 의 h, span, whenFull, showToast 를 쓴다.
(() => {
  // 알림 종류(NotificationType)별 아이콘 색과 누르면 갈 화면. 결재는 결재함, 티켓 배정은 티켓 처리의 '내 담당' 카드, 처리 완료는 요청자의 내 티켓
  const TYPE = {
    NT_APPROVAL_REQUEST: { cls: 'is-approval', link: '/approval/list' },
    NT_APPROVAL_DONE: { cls: 'is-approval', link: '/approval/list' },
    NT_APPROVAL_REJECT: { cls: 'is-approval', link: '/approval/list' },
    NT_TICKET_ASSIGNED: { cls: 'is-ticket', link: '/ticket/admin/list?filter=MINE' },
    NT_TICKET_RESOLVED: { cls: 'is-ticket', link: '/ticket/mine' },
  };
  const ICON = {
    'is-approval': '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="m9 15 2 2 4-4"/>',
    'is-ticket': '<path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18v3h3l6.3-6.3a4 4 0 0 0 5.4-5.4l-2.4 2.4-2.6-.6-.6-2.6z"/>',
    'is-other': '<path d="M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3H4a4 4 0 0 0 2-3v-3a7 7 0 0 1 4-6"/>',
  };
  const ctx = document.body.dataset.ctx || '';
  const $ = id => document.getElementById(id);
  const state = { page: 1, pages: 1, items: [], seq: 0 };
  const readOnly = () => document.body.classList.contains('is-readonly');

  function openNotificationView() { load(true); }
  window.openNotificationView = openNotificationView;

  // first: 처음부터. 아니면 다음 쪽을 아래에 붙인다(더 보기)
  function load(first) {
    const my = ++state.seq;
    const page = first ? 1 : state.page + 1;
    api.get('/notification/api/list', { page })
      .done(r => {
        if (my !== state.seq) return;
        state.page = r.page || page;
        state.pages = r.pages || 1;
        // 더 보기 사이에 새 알림이 오면 쪽이 밀려 같은 알림이 다시 올 수 있어 이미 보인 것은 뺀다
        const seen = new Set(first ? [] : state.items.map(n => n.notificationId));
        state.items = (first ? [] : state.items).concat((r.items || []).filter(n => !seen.has(n.notificationId)));
        render();
      })
      .fail(xhr => showToast(apiMessage(xhr, '알림을 불러오지 못했습니다')));
  }

  function render() {
    $('notiList').replaceChildren(...(state.items.length ? state.items.map(item) : [h('li', 'noti-empty', '아직 받은 알림이 없습니다')]));
    $('btnNotiMore').hidden = state.page >= state.pages;
  }

  function item(n) {
    const type = TYPE[n.notificationType] || { cls: 'is-other', link: null };
    const unread = !n.readAt;
    const icon = h('span', `noti-ic ${type.cls}`);
    icon.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON[type.cls]}</svg>`;
    // message 에는 사용자가 쓴 문서·티켓 제목이 들어 있다: h() 는 글자로만 넣는다
    const btn = h('button', 'noti-item' + (unread ? ' is-unread' : ''), icon,
      h('span', 'noti-main',
        h('span', 'noti-head', span(n.title || '알림', 'noti-type'), span(whenFull(n.createdAt), 'noti-time')),
        h('strong', 'noti-title', n.message || '')),
      ...(unread ? [h('span', 'noti-dot', h('span', 'visually-hidden', '안 읽음'))] : []));
    btn.type = 'button';
    btn.addEventListener('click', () => open(n, type.link));
    return h('li', '', btn);
  }

  // 누르면: 안 읽었으면 읽음으로 바꾸고(읽기 전용 업체는 건너뜀), 갈 화면이 있으면 그리로
  function open(n, link) {
    const go = () => { if (link) location.href = ctx + link; };
    if (n.readAt || readOnly()) { go(); return; }
    api.post(`/notification/api/${encodeURIComponent(n.notificationId)}/read`)
      .done(() => {
        n.readAt = new Date().toISOString();
        if (window.IEUM && IEUM.refreshBadges) IEUM.refreshBadges();
        if (link) go(); else render();
      })
      .fail(xhr => { showToast(apiMessage(xhr, '읽음으로 바꾸지 못했습니다')); go(); });
  }

  $('btnNotiMore').addEventListener('click', () => load(false));
})();
