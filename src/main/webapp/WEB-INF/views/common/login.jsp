<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%--
  로그인 (GET /login → common/login). 업체 직원과 이음 운영자가 같은 칸으로 로그인한다: 업체 코드 + 사번 + 비밀번호.
  이음은 업체 코드 ieum0000 인 이음 운영 업체이고, 그 계정에 ROLE_OPS 를 준다(요구분석서 v1.6).
  같은 화면에 "계정 만들기"(직원 가입 요청)가 있다: 업체 코드 확인 → 부서·직급·이름 → 이메일 인증 → 승인 요청 →
  승인되면 사번(자동)·비밀번호 → 계정 생성. 동작은 resources/js/join.js, API 는 화면 명세 "가입 요청과 승인"(/join/**, 로그인 없이).
  비밀번호 찾기(v1.6 ACC-07)는 화면 명세가 정해지면 이 화면에 더한다. 관리자 초기화·임시 비밀번호는 없다.

  백엔드와 맞출 것 (security-context.xml, 담당 1):
  - <security:form-login login-page="/login" login-processing-url="/login"
        username-parameter="username" password-parameter="password"
        authentication-failure-url="/login?error" default-target-url="/home" />
  - 업체 코드(companyCode)도 함께 받아야 한다: 사번은 업체 안에서만 유일하기 때문 (가이드 3장 사번 규칙).
    예: UsernamePasswordAuthenticationFilter 를 바꿔 "업체코드:사번"으로 합쳐 UserDetailsService 에 넘기는 방법
  - /login/**, /join/**, /resources/**, /manifest.json, /service-worker.js 는 permitAll (v1.6: 공개 주소는 하위 주소까지)
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>로그인</title>
  <meta name="theme-color" content="#ffffff">
  <%-- 계정 만들기의 AJAX POST 에 붙일 CSRF 토큰. join.js 가 읽는다 --%>
  <meta name="_csrf" content="${_csrf.token}">
  <meta name="_csrf_header" content="${_csrf.headerName}">
  <link rel="manifest" href="${ctx}/manifest.json">
  <link rel="icon" href="${ctx}/resources/icons/favicon.png" type="image/png">
  <link rel="apple-touch-icon" href="${ctx}/resources/icons/apple-touch-icon.png">
  <link rel="stylesheet" href="${ctx}/resources/css/common.css">
</head>
<%-- data-app-only="true": 설치한 IEUM 앱이 아닌 브라우저 탭에서는 다운로드 안내만 보인다(시연 때 켠다).
     개발 중에는 false 로 두어 브라우저에서 바로 로그인한다 --%>
<body data-ctx="${ctx}" data-app-only="false">
  <section class="login-screen" id="appOnlyScreen" hidden>
    <div class="login-box app-only">
      <div class="login-brand">
        <img src="${ctx}/resources/img/logo-mark.png" alt="" class="brand-mark">
        <img src="${ctx}/resources/img/logo-wordmark.png" alt="IEUM" class="brand-word">
      </div>
      <h1>IEUM 그룹웨어는 앱에서 사용합니다</h1>
      <p>앱을 설치하면 바탕화면이나 홈 화면의 IEUM 앱에서 바로 로그인할 수 있습니다.</p>
      <button type="button" class="btn btn-primary btn-block" data-download>IEUM 앱 다운로드</button>
    </div>
  </section>

  <section class="login-screen" id="loginScreen">
    <div class="login-box">
      <div class="login-brand">
        <img src="${ctx}/resources/img/logo-mark.png" alt="" class="brand-mark">
        <img src="${ctx}/resources/img/logo-wordmark.png" alt="IEUM" class="brand-word">
      </div>
      <form id="loginForm" action="${ctx}/login" method="post" novalidate>
        <sec:csrfInput />
        <div class="field">
          <label for="loginCompany">업체 코드</label>
          <input class="input" id="loginCompany" name="companyCode" autocomplete="organization" autocapitalize="off" spellcheck="false" placeholder="예: ieum0001" required>
        </div>
        <div class="field">
          <%-- 사번 = 등록 연도 + 4자리 번호(예: 20260002). 업체 안에서만 유일하므로 업체 코드와 함께 확인한다 --%>
          <label for="loginId">사번</label>
          <input class="input" id="loginId" name="username" autocomplete="username" inputmode="numeric" placeholder="예: 20260002" required>
        </div>
        <div class="field">
          <label for="loginPw">비밀번호</label>
          <input class="input" id="loginPw" name="password" type="password" autocomplete="current-password" required>
        </div>
        <%-- 어느 칸이 틀렸는지는 알려주지 않는다 --%>
        <p class="login-error" id="loginError" role="alert"><c:choose>
          <c:when test="${param.error ne null}">업체 코드, 사번 또는 비밀번호가 맞지 않습니다</c:when>
          <c:when test="${param.expired ne null}">로그인이 끝났습니다. 다시 로그인해 주세요</c:when>
          <%-- 체험 신청 이메일을 아직 인증하지 않은 업체 (요구분석서 v1.4 ACC-06): 서버가 /login?unverified 로 보낸다 --%>
          <c:when test="${param.unverified ne null}">신청할 때 적은 이메일을 아직 인증하지 않았습니다. 인증 메일의 링크를 누른 뒤 로그인해 주세요</c:when>
        </c:choose></p>
        <c:if test="${param.logout ne null}">
          <p class="login-help" role="status">로그아웃했습니다</p>
        </c:if>
        <button class="btn btn-primary btn-block" type="submit">로그인</button>
        <p class="login-help">처음이면 <button type="button" class="link-btn" id="btnJoin">계정 만들기</button></p>
      </form>

      <%-- 계정 만들기 (join.js): 신청 → 승인 대기 → 승인되면 계정 생성. 승인 요청 버튼(아래쪽, 로그인으로 돌아가기 위)은
           업체 코드 확인과 이메일 인증을 모두 마치기 전에는 회색으로 꺼져 있고, 마치면 파란색으로 켜진다 --%>
      <form id="joinForm" novalidate hidden>
        <h2 class="login-title">계정 만들기</h2>
        <p class="login-lead">정보를 적고 승인을 요청하세요. 업체 관리자나 부서장이 1시간 안에 승인하면 이 화면에서 바로 계정을 만들 수 있습니다.</p>
        <div class="field">
          <label for="joinCompany">업체 코드</label>
          <%-- 확인: 업체를 찾아 그 업체의 부서를 부서 목록에 불러온다 (GET /join/company/{companyCode}) --%>
          <div class="input-action">
            <input class="input" id="joinCompany" autocomplete="organization" autocapitalize="off" spellcheck="false" placeholder="예: ieum0001">
            <button type="button" class="btn" id="btnJoinCheck">확인</button>
          </div>
          <p class="emp-roles-hint join-code-note" id="joinCompanyNote" role="status" hidden></p>
        </div>
        <div class="field">
          <label for="joinDept">부서</label>
          <select class="input" id="joinDept" data-ui="select" disabled><option value="">업체 코드를 먼저 확인하세요</option></select>
        </div>
        <div class="field">
          <label for="joinRank">직급</label>
          <input class="input" id="joinRank" autocomplete="off" placeholder="예: 사원">
        </div>
        <div class="field">
          <label for="joinName">이름</label>
          <input class="input" id="joinName" autocomplete="name">
        </div>
        <%-- 이메일 인증: 받은 6자리 코드를 넣어야 승인 요청을 할 수 있다. 이메일을 바꾸면 다시 인증한다 --%>
        <div class="field">
          <label for="joinEmail">이메일</label>
          <div class="input-action">
            <input class="input" id="joinEmail" type="email" autocomplete="email" placeholder="name@company.com">
            <button type="button" class="btn" id="btnJoinSend">인증 메일 보내기</button>
          </div>
          <input class="input join-code-input" id="joinCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="인증 코드 6자리" aria-label="이메일 인증 코드" hidden>
          <p class="emp-roles-hint join-code-note" id="joinCodeNote" role="status"></p>
        </div>
        <p class="join-status" id="joinStatus" role="status" hidden></p>
        <%-- 승인된 뒤에만 보인다: 사번(잠김) · 비밀번호 · 비밀번호 확인 --%>
        <div class="join-account" id="joinAccount" hidden>
          <div class="field">
            <label for="joinId">사번</label>
            <output class="input emp-no" id="joinId"></output>
            <p class="emp-roles-hint">승인 순서대로 자동으로 정해졌습니다</p>
          </div>
          <div class="field">
            <label for="joinPw">비밀번호</label>
            <input class="input" id="joinPw" type="password" autocomplete="new-password" placeholder="8자 이상">
          </div>
          <div class="field">
            <label for="joinPw2">비밀번호 확인</label>
            <input class="input" id="joinPw2" type="password" autocomplete="new-password">
          </div>
        </div>
        <p class="login-error" id="joinError" role="alert"></p>
        <p class="emp-roles-hint join-request-hint" id="joinRequestHint">업체 코드 확인과 이메일 인증을 마치면 승인 요청을 할 수 있습니다</p>
        <button type="button" class="btn btn-request btn-block" id="btnJoinRequest" disabled>승인 요청</button>
        <button class="btn btn-primary btn-block" type="submit" id="btnJoinCreate" hidden disabled>계정 생성</button>
        <button class="btn btn-ghost btn-block" type="button" id="btnJoinCancel" hidden>요청 취소하고 다시 쓰기</button>
        <button class="btn btn-ghost btn-block" type="button" id="btnJoinBack">로그인으로 돌아가기</button>
      </form>
    </div>
  </section>

  <script src="${ctx}/resources/lib/jquery-3.7.1.min.js"></script>
  <script src="${ctx}/resources/js/install.js"></script>
  <script src="${ctx}/resources/js/password-toggle.js"></script>
  <script src="${ctx}/resources/js/select.js"></script>
  <script src="${ctx}/resources/js/join.js"></script>
  <script>
    // 빈 칸이면 서버로 보내지 않고 바로 알린다. 업체 코드는 소문자로 맞추고, 지난번 업체 코드를 채워 둔다
    (function () {
      var form = document.getElementById('loginForm');
      var company = document.getElementById('loginCompany');
      var error = document.getElementById('loginError');
      try { if (!company.value) company.value = localStorage.getItem('ieum-last-company') || ''; } catch (e) { /* 저장소 막힘 */ }
      form.addEventListener('submit', function (e) {
        company.value = company.value.trim().toLowerCase();
        var empty = ['loginCompany', 'loginId', 'loginPw'].some(function (id) { return !document.getElementById(id).value.trim(); });
        if (empty) {
          e.preventDefault();
          error.textContent = '업체 코드, 사번, 비밀번호를 모두 입력해 주세요';
          return;
        }
        try { localStorage.setItem('ieum-last-company', company.value); } catch (e2) { /* 저장소 막힘 */ }
      });
      // 앱에서만 쓰기: 설치한 앱(runningAsApp, install.js)이 아니고 ?preview 도 없으면 다운로드 안내만
      if (document.body.dataset.appOnly === 'true' && !runningAsApp && !new URLSearchParams(location.search).has('preview')) {
        document.getElementById('loginScreen').hidden = true;
        document.getElementById('appOnlyScreen').hidden = false;
      }
    })();
  </script>
</body>
</html>
