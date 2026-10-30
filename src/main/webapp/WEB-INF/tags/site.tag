<%@ tag language="java" pageEncoding="UTF-8" body-content="scriptless" trimDirectiveWhitespaces="true" %>
<%@ attribute name="title" required="true" description="페이지 제목" %>
<%@ attribute name="description" description="검색 결과에 보일 한 줄 설명. 비우면 기본 문구" %>
<%@ attribute name="current" description="상단 메뉴 강조. 지금은 쓰지 않는다: 소개·체험 신청·문의 헤더를 똑같이 둔다 (2026-10-27)" %>
<%@ attribute name="cta" type="java.lang.Boolean" description="헤더의 '무료 체험' 버튼. 체험 신청 페이지만 false (자기 자신으로 가는 버튼이라)" %>
<%@ attribute name="fab" type="java.lang.Boolean" description="떠 있는 '도입 문의' 버튼. 소개 페이지만 true" %>
<%@ attribute name="mainClass" description="본문 main 의 class. 체험 신청은 section, 문의는 inq-page" %>
<%@ attribute name="mainStyle" description="본문 main 의 style (체험 신청의 위쪽 여백)" %>
<%@ attribute name="scripts" fragment="true" description="화면 전용 JS. site.js 다음에 들어간다" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  상품 소개·무료 체험 신청·도입 문의처럼 로그인 없이 여는 홈페이지형 틀. 프론트 공통 담당만 고친다.

    <ui:site title="2주 무료 체험 신청" cta="false">
      <jsp:attribute name="scripts"><script src="${pageContext.request.contextPath}/resources/js/trial.js"></script></jsp:attribute>
      <jsp:body> ...본문(main 안)... </jsp:body>
    </ui:site>

  주소(가이드 9장, 팀 확정 필요): 소개 /, 체험 신청 /trial, 도입 문의 /inquiry. Security 에서 permitAll 로 열어야 한다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><c:out value="${title}" /> - IEUM 그룹웨어</title>
  <meta name="description" content="<c:out value="${empty description ? '전자결재, 근태, IT 지원, 채팅을 한곳에서 쓰는 그룹웨어 IEUM' : description}" />">
  <meta name="theme-color" content="#ffffff">
  <%-- 체험 신청·문의 접수는 AJAX POST 라 CSRF 토큰이 필요하다 --%>
  <meta name="_csrf" content="${_csrf.token}">
  <meta name="_csrf_header" content="${_csrf.headerName}">
  <link rel="manifest" href="${ctx}/manifest.json">
  <link rel="icon" href="${ctx}/resources/icons/favicon.png" type="image/png">
  <link rel="apple-touch-icon" href="${ctx}/resources/icons/apple-touch-icon.png">
  <%-- 디자인 2안 글꼴 (IBM Plex Sans KR, 그룹웨어 틀과 같음). 오프라인이면 시스템 글꼴로 보인다 --%>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="${ctx}/resources/css/common.css">
  <link rel="stylesheet" href="${ctx}/resources/css/site.css">
</head>
<body class="site" data-ctx="${ctx}">
  <%@ include file="/WEB-INF/views/common/site-header.jspf" %>

  <main<c:if test="${not empty mainClass}"> class="<c:out value="${mainClass}" />"</c:if><c:if test="${not empty mainStyle}"> style="<c:out value="${mainStyle}" />"</c:if>>
    <jsp:doBody />
  </main>

  <%@ include file="/WEB-INF/views/common/site-footer.jspf" %>

  <c:if test="${fab}">
    <%-- 떠 있는 도입 문의 버튼: 소개 페이지에만. 푸터가 보이면 숨는다 (site.js) --%>
    <a class="fab-inquiry" id="fabInquiry" href="${ctx}/inquiry" aria-label="이음 도입 문의">
      <span class="fab-mark" aria-hidden="true"><img src="${ctx}/resources/img/logo-mark.png" alt=""></span>
      <span class="fab-text" aria-hidden="true"><small>이음</small><strong>도입 문의</strong></span>
    </a>
  </c:if>

  <script src="${ctx}/resources/lib/jquery-3.7.1.min.js"></script>
  <%-- common.js: AJAX의 CSRF 토큰과 401·403 처리 (배지·메뉴 서랍은 그 칸이 없는 이 틀에서는 아무 일도 하지 않는다) --%>
  <script src="${ctx}/resources/js/common.js"></script>
  <script src="${ctx}/resources/js/install.js"></script>
  <script src="${ctx}/resources/js/password-toggle.js"></script>
  <script src="${ctx}/resources/js/site.js"></script>
  <jsp:invoke fragment="scripts" />
</body>
</html>
