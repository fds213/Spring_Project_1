// 공통 스크립트: layout.tag·site.tag 가 jQuery 다음에 불러온다. 프론트 공통 담당만 고친다.
// 1) GET 이외의 모든 AJAX에 CSRF 토큰 헤더 (가이드 7장)   2) 401·403 응답 처리
// 3) 좁은 화면 메뉴 서랍   4) 배지 숫자 15초 조회 (GET /badges)   5) 읽기 전용 업체의 쓰기 동작 막기   6) 토스트
// 화면별 JS는 주소를 IEUM.url('/approval/list') 로 만들고, 결과는 { success, message, data } 로 받는다.
(function ($) {
  'use strict';

  const ctx = document.body.dataset.ctx || '';

  // ---------- 6) toast: short notice at the bottom right ----------
  let toastTimer = null;
  function toast(text) {
    const box = document.getElementById('toast');
    if (!box) return;
    box.textContent = text;
    box.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { box.hidden = true; }, 4000);
  }

  // ---------- 1) CSRF: the token from the layout's meta tags on every non-GET AJAX ----------
  const csrfToken = $('meta[name="_csrf"]').attr('content');
  const csrfHeader = $('meta[name="_csrf_header"]').attr('content');
  $(document).ajaxSend((e, xhr, opt) => {
    if (csrfToken && csrfHeader && !/^(GET|HEAD|OPTIONS)$/i.test(opt.type)) xhr.setRequestHeader(csrfHeader, csrfToken);
  });

  // ---------- 2) login gone → 401, no permission or stale CSRF token → 403 (가이드 9장) ----------
  $(document).ajaxError((e, xhr) => {
    if (xhr.status === 401) location.href = ctx + '/login?expired';
    else if (xhr.status === 403) toast('권한이 없거나 화면이 오래되었습니다. 새로고침한 뒤 다시 시도해 주세요.');
  });

  // ---------- 3) narrow screens: the hamburger opens the sidebar as a drawer ----------
  const btnMenu = document.getElementById('btnMenu');
  if (btnMenu) {
    const setMenu = open => {
      document.body.classList.toggle('menu-open', open);
      btnMenu.setAttribute('aria-expanded', open);
      btnMenu.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    };
    btnMenu.addEventListener('click', () => setMenu(!document.body.classList.contains('menu-open')));
    document.getElementById('backdrop').addEventListener('click', () => setMenu(false));
    document.addEventListener('keydown', e => { if (e.key === 'Escape') setMenu(false); });
    // widening the window past the breakpoint shows the sidebar normally, so drop the drawer state
    matchMedia('(max-width: 900px)').addEventListener('change', e => { if (!e.matches) setMenu(false); });
  }

  // ---------- 4) badges: one request every 15 s fills every [data-badge="<name>"] (가이드 7장 알림과 배지) ----------
  // 업체 직원: { notification, approval, chat }  /  이음 운영자: { application, inquiryGuest, inquiryCompany } (백엔드와 확정)
  const BADGE_MS = 15000;
  let badgeTimer = null;
  function refreshBadges() {
    if (!document.querySelector('[data-badge]')) return;
    $.getJSON(ctx + '/badges').done(res => {
      if (!res || !res.success) return;
      document.querySelectorAll('[data-badge]').forEach(el => {
        const n = Number((res.data || {})[el.dataset.badge]) || 0;
        el.textContent = n > 99 ? '99+' : n;
        el.hidden = n === 0;
      });
    });
  }
  function startBadges() {
    clearInterval(badgeTimer);
    refreshBadges();
    badgeTimer = setInterval(refreshBadges, BADGE_MS);
  }
  // a hidden tab stops asking; coming back asks right away
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) clearInterval(badgeTimer);
    else startBadges();
  });
  if (document.querySelector('[data-badge]')) startBadges();

  // ---------- 5) read-only company (trial over, unpaid): stop write actions and say why ----------
  // 화면은 data-write 가 붙은 버튼·링크를 흐리게 두고 누르면 안내만 한다. 실제로 막는 것은 서버(쓰기 요청 거절)다.
  document.addEventListener('click', e => {
    if (!document.body.classList.contains('is-readonly') || !e.target.closest('[data-write]')) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    toast(document.body.dataset.admin === 'true'
      ? '체험이 끝나 읽기 전용입니다. 이용 결제에서 결제하면 바로 이어서 쓸 수 있습니다.'
      : '체험이 끝나 읽기 전용입니다. 업체 관리자가 결제하면 바로 이어서 쓸 수 있습니다.');
  }, true);

  // what screen scripts use
  window.IEUM = {
    url: path => ctx + path,
    toast,
    refreshBadges, // 알림을 읽었거나 결재를 처리했을 때 15초를 기다리지 않고 다시 센다
  };
})(jQuery);
