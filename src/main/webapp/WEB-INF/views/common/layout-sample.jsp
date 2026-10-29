<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  layout.tag 사용 예시이자 틀 확인용 화면. 팀원은 이 모양대로 자기 모듈 JSP를 만든다.
  확인용 Controller 예: @GetMapping("/home") public String home() { return "common/layout-sample"; }
--%>
<ui:layout title="홈" menu="home">
  <jsp:attribute name="scripts">
    <%-- 화면 전용 JS가 있으면 여기에: <script src="${pageContext.request.contextPath}/resources/js/home.js"></script> --%>
  </jsp:attribute>
  <jsp:body>
    <div class="page-head"><h1>안녕하세요, <c:out value="${loginUser.name}" />님</h1></div>
    <div class="cards">
      <div class="card"><small>결재 대기</small><strong>0건</strong></div>
      <div class="card"><small>내 티켓</small><strong>0건</strong></div>
      <div class="card"><small>오늘 근태 (단말기 기록)</small><strong>기록 없음</strong></div>
    </div>
    <h2>공지사항</h2>
    <p class="muted">공통 틀이 제대로 보이면 헤더, 사이드 메뉴(내 역할에 맞는 항목만), 이 본문이 보입니다.</p>
  </jsp:body>
</ui:layout>
