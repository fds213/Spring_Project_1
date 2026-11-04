<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  조직도 화면 (/employee/org, 업체 직원 모두. 사이드 메뉴 '회사 > 조직도'). 요구분석서 3.4 사원·조직: 직원은 조직도에서 부서별 구성원과 직급을 본다.
  이 주소를 받는 Controller는 사원 모듈 담당이 만든다: docs/front-screen-api.md 1장. 동작은 resources/js/employee/org.js.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="조직도" crumb="회사" menu="org">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/employee/org.js"></script>
    <script>openOrgView();</script>
  </jsp:attribute>
  <jsp:body>
    <%-- 조직도 전용 스타일 (공통 레이아웃에 화면별 CSS 칸이 없어서 본문 맨 앞에서 불러온다) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/employee/org.css">
    <section class="view on" id="view-org">
      <div class="page-head"><h1>조직도</h1></div>
      <form class="search-row" id="orgSearchForm" role="search">
        <input type="search" class="search-input" id="orgSearch" placeholder="이름·부서·직급으로 찾기" aria-label="조직도에서 찾기" autocomplete="off">
        <span class="org-result" id="orgResult" role="status"></span>
      </form>
      <div class="org-grid" id="orgList"></div>
    </section>
  </jsp:body>
</ui:layout>
