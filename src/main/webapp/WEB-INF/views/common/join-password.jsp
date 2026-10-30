<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  비밀번호 찾기 (GET /join/password → common/join-password, 로그인 없이 연다). 시안 views/common/login.html 의 pwForm 를 옮긴 것이다.
  지금은 화면 모양만 있다. 업체 코드 확인·인증 메일·요청 처리(시안 employee/employee.js)와 서버 API는 담당이 붙인다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>비밀번호 찾기</title>
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
<body data-ctx="${ctx}">
  <section class="login-screen">
    <div class="login-box">
      <div class="login-brand">
        <img src="${ctx}/resources/img/logo-mark.png" alt="" class="brand-mark">
        <img src="${ctx}/resources/img/logo-wordmark.png" alt="IEUM" class="brand-word">
      </div>
      <%-- 비밀번호 찾기: 업체 코드·사번 → 가입할 때 쓴 이메일로 인증 코드 → 코드가 맞으면 새 비밀번호·확인으로 재설정 (employee/employee.js).
           서버는 비밀번호를 BCrypt로만 저장하므로 지금 비밀번호를 보여주지 않고 새로 정하게 한다 (10월 28일 결정) --%>
      <form id="pwForm" class="join-form" novalidate>
        <h2 class="login-title">비밀번호 찾기</h2>
        <p class="login-lead">업체 코드와 사번을 넣으면 가입할 때 쓴 이메일로 인증 코드를 보냅니다. 인증하면 새 비밀번호를 정할 수 있습니다.</p>
        <div class="field">
          <label for="pwCompany">업체 코드</label>
          <input class="input" id="pwCompany" autocomplete="organization" placeholder="예: ieum7431">
        </div>
        <div class="field">
          <label for="pwId">사번</label>
          <div class="input-action">
            <input class="input" id="pwId" autocomplete="username" inputmode="numeric" placeholder="예: 20260002">
            <button type="button" class="btn" id="btnPwSend">인증 메일 보내기</button>
          </div>
          <input class="input join-code-input" id="pwCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="인증 코드 6자리" aria-label="비밀번호 찾기 인증 코드" hidden>
          <p class="emp-roles-hint join-code-note" id="pwCodeNote" role="status"></p>
        </div>
        <%-- 인증이 끝난 뒤에만 보인다 --%>
        <div class="join-account" id="pwNewBox" hidden>
          <p class="join-status is-ok">본인 확인이 끝났습니다. 새 비밀번호를 정해 주세요.</p>
          <div class="field">
            <label for="pwNew">새 비밀번호</label>
            <input class="input" id="pwNew" type="password" autocomplete="new-password" placeholder="8자 이상">
          </div>
          <div class="field">
            <label for="pwNew2">새 비밀번호 확인</label>
            <input class="input" id="pwNew2" type="password" autocomplete="new-password">
          </div>
        </div>
        <p class="login-error" id="pwError" role="alert"></p>
        <button class="btn btn-primary btn-block" type="submit" id="btnPwReset" hidden disabled>비밀번호 재설정</button>
        <a class="btn btn-ghost btn-block" id="btnPwBack" href="${ctx}/login">로그인으로 돌아가기</a>
      </form>
    </div>
  </section>

  <script src="${ctx}/resources/js/install.js"></script>
  <script src="${ctx}/resources/js/password-toggle.js"></script>
</body>
</html>
