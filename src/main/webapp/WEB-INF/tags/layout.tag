<%@ tag language="java" pageEncoding="UTF-8" body-content="scriptless" trimDirectiveWhitespaces="true" %>
<%@ attribute name="title" required="true" description="페이지 제목. 탭과 앱 창 제목, 위치 표시의 마지막 칸에 쓴다" %>
<%@ attribute name="menu" description="3장의 menu 키. 사이드 메뉴에서 이 항목을 현재 화면으로 표시한다" %>
<%@ attribute name="crumb" description="위치 표시의 앞부분(메뉴 묶음 이름). 비우면 위치 표시를 그리지 않는다" %>
<%@ attribute name="scripts" fragment="true" description="화면 전용 JS. jQuery와 common.js 다음에 들어간다" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  그룹웨어·이음 운영자 공통 3단 틀 (헤더 / 사이드 메뉴 / 본문). 프론트 공통 담당만 고친다.
  화면 JSP는 이 태그 안에 본문만 쓴다:

    <ui:layout title="결재함" crumb="전자결재" menu="approval-box">
      <jsp:attribute name="scripts"><script src="${pageContext.request.contextPath}/resources/js/approval/approval.js"></script></jsp:attribute>
      <jsp:body> ...본문... </jsp:body>
    </ui:layout>

  이 틀이 쓰는 서버 값 (백엔드와 확정, 가이드 9장):
  - loginUser: Spring Security principal (저장소 com.ieum.gw.dto.LoginUser). 저장소에 있는 name, empnum 만 읽는다
  - loginInfo (request 속성, 없어도 됨): companyName, deptName, rankName, headDeptName(내가 부서장인 부서, 아니면 null).
    사원 모듈이 @ControllerAdvice 등으로 넣으면 헤더(회사명·부서·직급)와 사이드바(부서장의 가입 승인 메뉴)에 쓰인다.
    없으면 업체명은 비우고 부서·직급 자리에 사번을 보여준다 (LoginUser 에 없는 값을 읽으면 화면이 500 오류로 깨지므로 따로 받는다)
  - 역할: ROLE_EMP, ROLE_IT, ROLE_HR, ROLE_ADMIN, ROLE_OPS(이음 운영자) 중 한 사람에 하나
    (hasRole 은 ROLE_ 접두사를 붙여 찾는다)
  - companyBilling (request 속성, 업체 직원만): status(TRIAL / PAID / EXPIRED), paid(지금, 만료면 마지막 기간이 유료면 true), endDate("2026.11.08"), daysLeft(0 = 마지막 날)
  - todayPunch (request 속성, 업체 직원만): inTime("08:52"), outTime(없으면 비움)
  채팅(업체 직원만): chat.css 와 채팅 스크립트(updates·room-list·conversation)를 모든 화면에 불러 둔다.
  채팅 화면(menu="chat")이 아니면 오른쪽 아래 채팅 버튼·메시지 패널을 붙인다(#chatDock, chat/dock.js).
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<%-- request 범위: 이 태그 안의 화면 본문에서도 ${loginUser.name} 처럼 쓸 수 있다 --%>
<sec:authentication property="principal" var="loginUser" scope="request" />
<sec:authorize access="hasRole('OPS')" var="isOps" />
<sec:authorize access="hasRole('ADMIN')" var="isAdmin" />
<c:set var="readOnly" value="${not isOps and companyBilling.status eq 'EXPIRED'}" />
<%-- 유료 기간이 끝난 업체. 만료 뒤 재구매는 이번 제외라 결제를 안내하지 않는다(띠: header.jspf, 쓰기 안내: common.js) --%>
<c:set var="paidExpired" value="${readOnly and companyBilling.paid}" />
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%-- 설치한 앱 창은 앞에 "IEUM - "를 붙여 보여 주므로 제목에는 화면 이름만 쓴다 --%>
  <title><c:out value="${title}" /></title>
  <meta name="theme-color" content="#ffffff">
  <%-- AJAX POST 등에 붙일 CSRF 토큰. common.js 가 읽는다 (가이드 7장) --%>
  <meta name="_csrf" content="${_csrf.token}">
  <meta name="_csrf_header" content="${_csrf.headerName}">
  <link rel="manifest" href="${ctx}/manifest.json">
  <link rel="icon" href="${ctx}/resources/icons/favicon.png" type="image/png">
  <link rel="apple-touch-icon" href="${ctx}/resources/icons/apple-touch-icon.png">
  <%-- 디자인 2안 글꼴 (IBM Plex Sans KR). 오프라인이면 시스템 글꼴로 보인다 --%>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="${ctx}/resources/css/common.css">
  <c:if test="${not isOps}"><link rel="stylesheet" href="${ctx}/resources/css/chat/chat.css"></c:if>
</head>
<%-- data-ctx: JS가 주소 앞에 붙일 컨텍스트 경로. is-readonly: 체험·유료 기간이 끝난 업체(쓰기 동작을 흐리게) --%>
<body data-ctx="${ctx}" data-mode="${isOps ? 'OPS' : 'EMPLOYEE'}" data-admin="${isAdmin}" data-paid-expired="${paidExpired}"${readOnly ? ' class="is-readonly"' : ''}>
  <div class="app-shell">
    <%@ include file="/WEB-INF/views/common/header.jspf" %>
    <div class="app-body">
      <%@ include file="/WEB-INF/views/common/sidebar.jspf" %>
      <div class="backdrop" id="backdrop"></div>

      <main class="content">
        <c:if test="${not empty crumb}">
          <p class="crumb"><c:out value="${crumb}" /> &gt; <span class="crumb-current"><c:out value="${title}" /></span></p>
        </c:if>
        <jsp:doBody />
      </main>
    </div>
  </div>

  <div class="toast" id="toast" role="status" hidden></div>
  <c:if test="${not isOps and menu ne 'chat'}">
    <div class="chat-dock" id="chatDock" data-me="${loginUser.employeeId}"></div>
  </c:if>

  <%-- 헤더의 "비밀번호 변경" 창. 현재 비밀번호가 맞아야 새 비밀번호(8자 이상, 두 번 같게)로 바꾼다 (common.js 7) --%>
  <dialog class="modal" id="pwChangeDialog">
    <form id="pwChangeForm" novalidate>
      <h2>비밀번호 변경</h2>
      <div class="field">
        <label for="pwCur">현재 비밀번호</label>
        <input class="input" id="pwCur" type="password" autocomplete="current-password">
      </div>
      <div class="field">
        <label for="pwChgNew">새 비밀번호</label>
        <input class="input" id="pwChgNew" type="password" autocomplete="new-password" placeholder="8자 이상">
      </div>
      <div class="field">
        <label for="pwChgNew2">새 비밀번호 확인</label>
        <input class="input" id="pwChgNew2" type="password" autocomplete="new-password">
      </div>
      <p class="login-error" id="pwChgError" role="alert"></p>
      <div class="modal-actions"><button type="button" class="btn" value="cancel">취소</button><button type="submit" class="btn btn-primary" id="btnPwChangeOk" disabled>바꾸기</button></div>
    </form>
  </dialog>

  <script src="${ctx}/resources/lib/jquery-3.7.1.min.js"></script>
  <script src="${ctx}/resources/js/common.js"></script>
  <script src="${ctx}/resources/js/password-toggle.js"></script>
  <%-- 선택 상자 부품: <select data-ui="select"> 를 디자인 2안 모양으로 바꾼다 --%>
  <script src="${ctx}/resources/js/select.js"></script>
  <script src="${ctx}/resources/js/install.js"></script>
  <c:if test="${not isOps}">
    <script src="${ctx}/resources/js/chat/updates.js"></script>
    <script src="${ctx}/resources/js/chat/room-list.js"></script>
    <script src="${ctx}/resources/js/chat/conversation.js"></script>
    <c:if test="${menu ne 'chat'}"><script src="${ctx}/resources/js/chat/dock.js"></script></c:if>
  </c:if>
  <jsp:invoke fragment="scripts" />
  <%@ include file="/WEB-INF/views/subscription/trial-notice.jspf" %>
</body>
</html>
