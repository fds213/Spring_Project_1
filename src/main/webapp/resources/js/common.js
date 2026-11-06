// 공통 스크립트: layout.tag·site.tag 가 jQuery 다음에 불러온다. 프론트 공통 담당만 고친다.
// 1) GET 이외의 모든 AJAX에 CSRF 토큰 헤더 (가이드 7장)   2) 401·403 응답 처리
// 3) 좁은 화면 메뉴 서랍   4) 배지 숫자 15초 조회 (GET /badges)   5) 읽기 전용 업체의 쓰기 동작 막기   6) 토스트
// 7) 헤더의 본인 비밀번호 변경 창   8) 화면 JS(resources/js/<모듈>/*.js)가 쓰는 api·pageHref 등 전역 함수
// 화면별 JS는 주소를 IEUM.url('/approval/list') 로 만들고, 결과는 { success, message, data } 로 받는다.
(function ($) {
  'use strict';

  const ctx = document.body.dataset.ctx || '';

  // ---------- 6) 토스트: 오른쪽 아래에 잠깐 뜨는 안내 ----------
  let toastTimer = null;
  function toast(text) {
    const box = document.getElementById('toast');
    if (!box) return;
    box.textContent = text;
    box.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { box.hidden = true; }, 4000);
  }

  // ---------- 1) CSRF: GET 이외의 모든 AJAX에 레이아웃 meta 태그의 토큰을 붙인다 ----------
  const csrfToken = $('meta[name="_csrf"]').attr('content');
  const csrfHeader = $('meta[name="_csrf_header"]').attr('content');
  $(document).ajaxSend((e, xhr, opt) => {
    if (csrfToken && csrfHeader && !/^(GET|HEAD|OPTIONS)$/i.test(opt.type)) xhr.setRequestHeader(csrfHeader, csrfToken);
  });

  // ---------- 2) 로그인이 풀림 → 401, 권한 없음·CSRF 토큰 만료 → 403 (가이드 9장) ----------
  // 403 은 서버가 보낸 message("접근 권한이 없습니다", 토큰 만료면 "새로 고친 뒤 다시 시도")를 그대로 보여 준다. 없을 때만 아래 문구
  // 401 은 로그인 화면으로. AccountStatusInterceptor(#178)가 끊은 경우는 reason(disabled 비활성, changed 역할 바뀜)을 실어 보내
  // 로그인 화면이 그 안내를 보이고, 세션 만료(AjaxAwareEntryPoint)는 reason 이 없어 지금처럼 ?expired
  const LOGIN_REASON = ['disabled', 'changed'];
  $(document).ajaxError((e, xhr) => {
    if (xhr.status === 401) {
      const reason = xhr.responseJSON && xhr.responseJSON.reason;
      location.href = ctx + '/login?' + (LOGIN_REASON.includes(reason) ? reason : 'expired');
    }
    else if (xhr.status === 403) toast(apiMessage(xhr, '권한이 없거나 화면이 오래되었습니다. 새로고침한 뒤 다시 시도해 주세요.'));
  });

  // ---------- 3) 좁은 화면: 햄버거 버튼이 사이드바를 서랍처럼 연다 ----------
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
    // 창을 기준 너비보다 넓히면 사이드바가 원래대로 보이므로 서랍 상태를 푼다
    matchMedia('(max-width: 900px)').addEventListener('change', e => { if (!e.matches) setMenu(false); });
  }

  // 사이드바 왼쪽 아이콘 줄: 누르면 오른쪽 칸에 그 묶음의 메뉴만 보인다(페이지는 그대로).
  // 처음 열린 묶음은 지금 화면이 속한 것으로 서버가 정해 둔다(sidebar.jspf)
  // 넓은 화면 + 마우스(common.css '넓은 화면 + 마우스')에서는 메뉴 칸을 접어 두고, 아이콘에 마우스를 올리거나
  // 포커스·클릭하면 그 묶음의 메뉴 칸을 펼친다. 사이드바를 벗어나면 잠깐 뒤 접고 지금 화면의 묶음으로 되돌린다
  const sidebar = document.getElementById('sidebar');
  const flyout = matchMedia('(min-width: 901px) and (hover: hover) and (pointer: fine)');
  const showCat = name => document.querySelectorAll('.nav-cat').forEach(cat => { cat.hidden = cat.dataset.cat !== name; });
  const pageCat = (document.querySelector('.rail-item.on') || {}).dataset;
  let flyoutTimer = null;
  let suppressFocusOpen = false;
  function openFlyout(btn) {
    clearTimeout(flyoutTimer);
    showCat(btn.dataset.cat);
    document.querySelectorAll('.rail-item').forEach(b => b.classList.toggle('is-peek', b === btn));
    sidebar.classList.add('is-open');
  }
  function shutFlyout() {
    clearTimeout(flyoutTimer);
    sidebar.classList.remove('is-open');
    document.querySelectorAll('.rail-item.is-peek').forEach(b => b.classList.remove('is-peek'));
    if (pageCat) showCat(pageCat.cat);
  }
  /** now: 바로 닫는다(Esc·창 크기 바뀜). 아니면 잠깐 뒤에, 마우스·포커스가 사이드바 밖일 때만 닫는다 */
  function closeFlyout(now) {
    if (now) { shutFlyout(); return; }
    clearTimeout(flyoutTimer);
    flyoutTimer = setTimeout(() => {
      if (sidebar.matches(':hover') || sidebar.contains(document.activeElement)) return;
      shutFlyout();
    }, 200);
  }
  document.querySelectorAll('.rail-item').forEach(btn => {
    btn.addEventListener('click', () => {
      if (flyout.matches) { openFlyout(btn); return; }
      document.querySelectorAll('.rail-item').forEach(b => {
        const on = b === btn;
        b.classList.toggle('on', on);
        b.setAttribute('aria-pressed', on);
      });
      showCat(btn.dataset.cat);
    });
    btn.addEventListener('pointerenter', e => { if (flyout.matches && e.pointerType === 'mouse') openFlyout(btn); });
    btn.addEventListener('focus', () => {
      if (suppressFocusOpen) { suppressFocusOpen = false; return; }
      if (flyout.matches) openFlyout(btn);
    });
  });
  if (sidebar) {
    sidebar.addEventListener('pointerleave', () => { if (flyout.matches) closeFlyout(false); });
    sidebar.addEventListener('focusout', () => { if (flyout.matches) closeFlyout(false); });
    sidebar.addEventListener('keydown', e => {
      if (e.key === 'Escape' && sidebar.classList.contains('is-open')) {
        const peek = sidebar.querySelector('.rail-item.is-peek');
        closeFlyout(true);
        // 메뉴 칸 안에 있던 포커스를 아이콘으로 돌려준다(이때는 다시 펼치지 않는다)
        if (peek && document.activeElement !== peek) {
          suppressFocusOpen = true;
          peek.focus({ preventScroll: true });
        }
      }
    });
    // 창을 줄여 서랍 모드가 되면 펼침 상태를 푼다
    flyout.addEventListener('change', () => closeFlyout(true));
  }

  // ---------- 4) 배지: 15초마다 요청 한 번으로 모든 [data-badge="<name>"]를 채운다 (가이드 7장 알림과 배지) ----------
  // 업체 직원: { notification, approval, chat, join(가입 승인할 수 있는 사람만) }  /  이음 운영자: { application, inquiryGuest }
  // (메뉴의 data-badge 이름과 같다. 이번 범위에서 뺀 회원 접수·증설 승인 배지는 없다)
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
    }).fail(xhr => { if (xhr.status === 404) clearInterval(badgeTimer); }); // 아직 없는 주소면 더 묻지 않는다
  }
  function startBadges() {
    clearInterval(badgeTimer);
    refreshBadges();
    badgeTimer = setInterval(refreshBadges, BADGE_MS);
  }
  // 탭이 가려지면 묻지 않고, 돌아오면 바로 묻는다
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) clearInterval(badgeTimer);
    else startBadges();
  });
  if (document.querySelector('[data-badge]')) startBadges();

  // ---------- 5) 읽기 전용 업체(체험·유료 기간 끝남): 쓰기 동작을 막고 이유를 알린다 ----------
  // 화면은 data-write 가 붙은 버튼·링크를 흐리게 두고 누르면 안내만 한다. 실제로 막는 것은 서버(쓰기 요청 거절)다.
  document.addEventListener('click', e => {
    if (!document.body.classList.contains('is-readonly') || !e.target.closest('[data-write]')) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    // 유료가 끝난 업체의 재구매는 이번 제외라 결제를 안내하지 않는다
    toast(document.body.dataset.paidExpired === 'true'
      ? '이용 기간이 끝나 읽기 전용입니다. 자료 조회와 내려받기는 그대로 됩니다.'
      : document.body.dataset.admin === 'true'
      ? '체험이 끝나 읽기 전용입니다. 이용 결제에서 결제하면 바로 이어서 쓸 수 있습니다.'
      : '체험이 끝나 읽기 전용입니다. 업체 관리자가 결제하면 바로 이어서 쓸 수 있습니다.');
  }, true);

  // ---------- 7) 비밀번호 변경 (헤더 버튼, 창은 layout.tag; ACC-07) ----------
  // 현재 비밀번호가 맞아야 하고 새 비밀번호는 8자 이상을 두 번 같게. 읽기 전용이어도 된다(계정 보안). 맞는지는 서버가 본다.
  const pwDialog = document.getElementById('pwChangeDialog');
  if (pwDialog) {
    const $id = id => document.getElementById(id);
    const pwError = $id('pwChgError');
    const pwOk = $id('btnPwChangeOk');
    const syncPw = () => {
      const n = $id('pwChgNew').value;
      pwOk.disabled = !($id('pwCur').value && n.length >= 8 && n === $id('pwChgNew2').value);
    };
    $id('btnPwChange').addEventListener('click', () => {
      ['pwCur', 'pwChgNew', 'pwChgNew2'].forEach(id => { $id(id).value = ''; });
      pwError.textContent = '';
      syncPw();
      if (!pwDialog.open) pwDialog.showModal();
      $id('pwCur').focus();
    });
    ['pwCur', 'pwChgNew', 'pwChgNew2'].forEach(id => $id(id).addEventListener('input', () => { pwError.textContent = ''; syncPw(); }));
    pwDialog.querySelector('[value="cancel"]').addEventListener('click', () => pwDialog.close());
    $id('pwChangeForm').addEventListener('submit', e => {
      e.preventDefault();
      const cur = $id('pwCur').value;
      const n = $id('pwChgNew').value;
      pwError.textContent = !cur ? '현재 비밀번호를 입력해 주세요.' : n.length < 8 ? '새 비밀번호는 8자 이상으로 정해 주세요.'
        : n !== $id('pwChgNew2').value ? '새 비밀번호 확인이 맞지 않습니다.' : n === cur ? '지금 쓰는 비밀번호와 다르게 정해 주세요.' : '';
      if (pwError.textContent) return;
      pwOk.disabled = true;
      $.ajax({ url: ctx + '/employee/api/password', method: 'POST', contentType: 'application/json', dataType: 'json',
        data: JSON.stringify({ current: cur, password: n }) })
        .done(res => {
          if (res && res.success) {
            pwDialog.close();
            toast('비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 쓰세요');
          } else {
            pwError.textContent = (res && res.message) || '비밀번호를 바꾸지 못했습니다. 잠시 뒤 다시 해 주세요';
          }
        })
        .fail(xhr => {
          if (xhr.status === 401 || xhr.status === 403) return; // 2) 에서 처리
          pwError.textContent = (xhr.responseJSON && xhr.responseJSON.message) || '비밀번호를 바꾸지 못했습니다. 잠시 뒤 다시 해 주세요';
        })
        .always(syncPw);
    });
  }

  // ---------- 8) 화면 JS(resources/js/<모듈>/*.js)가 부르는 것 ----------
  // 화면 JS는 서버를 api.get('/approval/api/docs') 처럼 부르고, 성공하면 데이터 자체를, 실패하면 xhr 에서
  // apiMessage(xhr)로 안내 문구를 꺼낸다. 서버가 { success, message, data } 로 감싸 보내면 여기서 풀어 준다:
  //   success true → data 를 넘긴다 / success false → 실패로 바꾸고 message 를 안내 문구로 쓴다.
  // 감싸지 않은 JSON 을 보내도 그대로 넘긴다.
  const unwrap = req => req.then(res => {
    if (res && typeof res === 'object' && !Array.isArray(res) && 'success' in res) {
      return res.success ? res.data : $.Deferred().reject({ status: 200, responseJSON: { message: res.message } }).promise();
    }
    return res;
  });
  window.apiMessage = (xhr, fallback) =>
    (xhr && xhr.responseJSON && xhr.responseJSON.message) || fallback || '처리하지 못했습니다. 잠시 뒤 다시 해 주세요';
  // 데이터를 바꾸는 요청은 모두 POST다(AGENTS.md). 고치기는 같은 주소, 지우기는 주소 끝에 /delete 를 붙여 POST 한다
  window.api = {
    get: (path, query) => unwrap($.ajax({ url: ctx + path, method: 'GET', data: query, dataType: 'json' })),
    post: (path, body) => unwrap($.ajax({
      url: ctx + path, method: 'POST', dataType: 'json', contentType: 'application/json',
      data: body === undefined ? undefined : JSON.stringify(body),
    })),
  };
  // 화면 주소: 다른 화면으로 갈 때 쓴다 (location.href = pageHref('/approval/list'))
  window.pageHref = path => ctx + path;

  // 화면 스크립트가 쓰는 것
  window.IEUM = {
    url: path => ctx + path,
    toast,
    refreshBadges, // 알림을 읽었거나 결재를 처리했을 때 15초를 기다리지 않고 다시 센다
  };
})(jQuery);
