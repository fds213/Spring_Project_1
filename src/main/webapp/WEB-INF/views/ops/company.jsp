<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  업체 관리 화면 (/ops/company). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버 주소와 주고받는 값은 docs/front-screen-api.md(프론트 제안), 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="업체 관리" crumb="업체" menu="ops-company">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ops/company.js"></script>
    <script>openFirmView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-ops-company">
      <%-- 업체 관리: 체험 중인 업체를 포함한 모든 업체. 누르면 그 업체의 모든 정보(기본 정보, 관리자 계정, 계정 목록, 신청·결제 이력, 장애·문의 접수)가 아래로 펼쳐진다 --%>
      <div class="page-head"><h1>업체 관리</h1></div>
      <p class="inq-hint">업체를 누르면 상세 정보가 펼쳐집니다.</p>
      <div class="table-wrap table-gap">
        <table class="table table-fixed">
          <colgroup><col style="width: 24%"><col style="width: 18%"><col style="width: 12%"><col style="width: 16%"><col style="width: 14%"><col style="width: 16%"></colgroup>
          <thead><tr><th>업체</th><th>관리자</th><th class="num">이용 인원</th><th class="num">월 이용료</th><th>상태</th><th>가입일</th></tr></thead>
          <tbody id="firmList"></tbody>
        </table>
      </div>
    </section>
  </jsp:body>
</ui:layout>
