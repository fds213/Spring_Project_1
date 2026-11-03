<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  계정 생성 (GET /join → common/join, 로그인 없이 연다).
  업체 코드 확인·승인 요청·요청 취소는 employee/employee.js('계정 생성' 부분), 서버는 EmployeeJoinController(/join/api/**).
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>계정 생성</title>
  <meta name="theme-color" content="#ffffff">
  <link rel="manifest" href="${ctx}/manifest.json">
  <link rel="icon" href="${ctx}/resources/icons/favicon.png" type="image/png">
  <link rel="apple-touch-icon" href="${ctx}/resources/icons/apple-touch-icon.png">
  <%-- 디자인 2안 글꼴 (IBM Plex Sans KR). 오프라인이면 시스템 글꼴로 보인다 --%>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="${ctx}/resources/css/common.css">
  <%-- 승인 요청·취소(POST)에 붙이는 CSRF 값 (common.js 가 읽는다) --%>
  <meta name="_csrf" content="${_csrf.token}">
  <meta name="_csrf_header" content="${_csrf.headerName}">
</head>
<body data-ctx="${ctx}">
  <section class="login-screen">
    <div class="login-box">
      <div class="login-brand">
        <img src="${ctx}/resources/img/logo-mark.svg" alt="" class="brand-mark">
        <img src="${ctx}/resources/img/logo-wordmark.svg" alt="IEUM" class="brand-word">
      </div>
      <%-- 계정 만들기 (요구분석서 3장 단순화 ①): 업체 코드 확인 → 부서·직급·이름·이메일 → 비밀번호 → 승인 요청 →
           업체 관리자·부서장이 승인하면 사번이 배정되고 바로 로그인한다. 직원 이메일 인증 코드·요청 키·1시간 기한은 이번 범위에서 뺐다.
           승인 요청 버튼은 업체 확인과 비밀번호를 마쳐야 파란색으로 켜진다 --%>
      <form id="joinForm" class="join-form" novalidate>
        <h2 class="login-title">계정 생성</h2>
        <div class="field">
          <label for="joinCompany">업체 코드</label>
          <%-- 확인: 가입 활성화가 켜진 업체면 열린 부서와 직급을 목록에 불러온다. 꺼져 있거나 없는 코드면 같은 안내를 보인다 --%>
          <div class="input-action">
            <input class="input" id="joinCompany" autocomplete="organization" placeholder="예: ieum7431">
            <button type="button" class="btn" id="btnJoinCheck">확인</button>
          </div>
          <p class="emp-roles-hint join-code-note" id="joinCompanyNote" role="status" hidden></p>
        </div>
        <div class="field">
          <label for="joinDept">부서</label>
          <select class="input" id="joinDept" data-ui="select"><option value="">부서를 고르세요</option></select>
        </div>
        <div class="field">
          <label for="joinRank">직급</label>
          <select class="input" id="joinRank" data-ui="select"><option value="">직급을 고르세요</option></select>
        </div>
        <div class="field">
          <label for="joinName">이름</label>
          <input class="input" id="joinName" autocomplete="name">
        </div>
        <%-- 이메일: 비밀번호 찾기 인증 코드를 받을 주소. 이번 범위에서는 가입 때 인증하지 않는다 --%>
        <div class="field">
          <label for="joinEmail">이메일</label>
          <input class="input" id="joinEmail" type="email" autocomplete="email" placeholder="name@company.com">
        </div>
        <%-- 비밀번호는 요청할 때 정한다. 승인되면 이 비밀번호로 바로 로그인한다 --%>
        <div class="field">
          <label for="joinPw">비밀번호</label>
          <input class="input" id="joinPw" type="password" autocomplete="new-password" placeholder="8자 이상">
        </div>
        <div class="field">
          <label for="joinPw2">비밀번호 확인</label>
          <input class="input" id="joinPw2" type="password" autocomplete="new-password">
        </div>
        <p class="join-status" id="joinStatus" role="status" hidden></p>
        <%-- 승인된 뒤에만 보인다: 붙은 사번(잠김) --%>
        <div class="join-account" id="joinAccount" hidden>
          <div class="field">
            <label for="joinId">사번</label>
            <output class="input emp-no" id="joinId"></output>
            <p class="emp-roles-hint">승인 순서대로 자동으로 정해졌습니다. 업체 코드와 이 사번으로 로그인합니다</p>
          </div>
        </div>
        <p class="login-error" id="joinError" role="alert"></p>
        <p class="emp-roles-hint join-request-hint" id="joinRequestHint">업체 코드 확인과 비밀번호를 마치면 승인 요청을 할 수 있습니다</p>
        <button type="button" class="btn btn-request btn-block" id="btnJoinRequest" disabled>승인 요청</button>
        <button class="btn btn-primary btn-block" type="submit" id="btnJoinCreate" hidden>로그인하러 가기</button>
        <button class="btn btn-ghost btn-block" type="button" id="btnJoinCancel" hidden>요청 취소하고 다시 쓰기</button>
        <a class="btn btn-ghost btn-block" id="btnJoinBack" href="${ctx}/login">로그인으로 돌아가기</a>
      </form>
    </div>
  </section>

  <script src="${ctx}/resources/js/install.js"></script>
  <script src="${ctx}/resources/js/password-toggle.js"></script>
  <script src="${ctx}/resources/js/select.js"></script>
  <script src="${ctx}/resources/lib/jquery-3.7.1.min.js"></script>
  <script src="${ctx}/resources/js/common.js"></script>
  <script src="${ctx}/resources/js/util.js"></script>
  <script src="${ctx}/resources/js/employee/employee.js"></script>
</body>
</html>
