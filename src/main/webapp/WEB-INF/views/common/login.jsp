<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%--
  로그인 (GET /login → common/login). 업체 직원과 이음 운영자가 같은 칸으로 로그인한다: 업체 코드 + 사번 + 비밀번호.
  이음도 업체 코드 ieum 을 가진 업체로 보고, 그 계정에는 ROLE_OPS 를 준다. 회원가입은 없다(계정은 관리자가 발급).

  백엔드와 맞출 것 (security-context.xml, 담당 1):
  - <security:form-login login-page="/login" login-processing-url="/login"
        username-parameter="username" password-parameter="password"
        authentication-failure-url="/login?error" default-target-url="/home" />
  - 업체 코드(companyCode)도 함께 받아야 한다: 사번은 업체 안에서만 유일하기 때문 (가이드 3장 사번 규칙).
    예: UsernamePasswordAuthenticationFilter 를 바꿔 "업체코드:사번"으로 합쳐 UserDetailsService 에 넘기는 방법
  - /login 과 /resources/** 는 permitAll
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>로그인</title>
  <meta name="theme-color" content="#ffffff">
  <link rel="manifest" href="${ctx}/manifest.json">
  <link rel="icon" href="${ctx}/resources/icons/favicon.png" type="image/png">
  <link rel="apple-touch-icon" href="${ctx}/resources/icons/apple-touch-icon.png">
  <%-- 디자인 2안 글꼴 (IBM Plex Sans KR). 오프라인이면 시스템 글꼴로 보인다 --%>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">
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
          <input class="input" id="loginCompany" name="companyCode" autocomplete="organization" autocapitalize="off" spellcheck="false" placeholder="예: ieum7431" required>
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
          <c:when test="${param.error ne null}">업체 코드, 사번 또는 비밀번호가 맞지 않습니다 (5번 연속 틀리면 10분 동안 잠깁니다)</c:when>
          <%-- LoginFailureHandler 가 보내는 값: 잠긴 계정 ?locked=남은 분(기능명세서 1.5), 비밀번호는 맞지만 비활성 계정 ?disabled.
               남은 분은 주소에서 오므로 숫자일 때만 보인다 --%>
          <c:when test="${param.locked ne null}">비밀번호를 5번 틀려 이 계정은 잠겼습니다. <c:choose><c:when test="${param.locked.matches('[0-9]{1,2}')}"><c:out value="${param.locked}" />분</c:when><c:otherwise>10분</c:otherwise></c:choose> 뒤에 다시 시도해 주세요</c:when>
          <c:when test="${param.disabled ne null}">사용이 중지된 계정입니다. 업체 관리자에게 문의해 주세요</c:when>
          <c:when test="${param.expired ne null}">로그인이 끝났습니다. 다시 로그인해 주세요</c:when>
          <%-- 체험 신청 이메일을 아직 인증하지 않은 업체 (요구분석서 v1.4 ACC-06): 서버가 /login?unverified 로 보낸다 --%>
          <c:when test="${param.unverified ne null}">신청할 때 적은 이메일을 아직 인증하지 않았습니다. 인증 메일의 링크를 누른 뒤 로그인해 주세요</c:when>
        </c:choose></p>
        <c:if test="${param.logout ne null}">
          <p class="login-help" role="status">로그아웃했습니다</p>
        </c:if>
        <button class="btn btn-primary btn-block" type="submit">로그인</button>
        <%-- 시안(10/28 결정): 직원 계정 생성과 이메일 인증 비밀번호 찾기. 둘 다 로그인 없이 여는 /join/** 아래에 둔다(화면·서버는 아직 없음) --%>
        <p class="login-help"><a class="link-btn" href="${ctx}/join">계정 생성</a> / <a class="link-btn" href="${ctx}/join/password">비밀번호 찾기</a></p>
      </form>
    </div>
  </section>

  <script src="${ctx}/resources/js/install.js"></script>
  <script src="${ctx}/resources/js/password-toggle.js"></script>
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
