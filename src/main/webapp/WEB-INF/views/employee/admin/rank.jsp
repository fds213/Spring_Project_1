<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  직급 관리 화면 (/employee/admin/rank). 시안 views/employee/admin/rank.html 를 옮긴 것이다 (이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장).
  화면 JS도 시안 것을 그대로 쓴다: 서버 주소와 주고받는 값은 시안 API.md, 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="직급 관리" crumb="업체 관리" menu="admin-rank">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/employee/employee.js"></script>
    <script src="${ctx}/resources/js/employee/org-admin.js"></script>
    <script>openOrgAdmin('rank');</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-admin-rank">
      <div class="page-head"><h1>직급 관리</h1></div>
      <p class="muted join-scope">위에서 아래로 낮은 직급부터 높은 직급 순서입니다. 여기 직급이 계정 생성에 그대로 나오고, 직원이 있는 직급은 지울 수 없습니다. 이름을 바꾸면 그 직급의 직원과 연차 계산에 쓰는 경력 연수도 함께 따라갑니다. 새로 만든 직급의 경력은 0년으로 봅니다(시연용 값).</p>
      <form class="org-add" id="rankAddForm" novalidate>
        <input class="input" id="rankNewName" maxlength="20" autocomplete="off" placeholder="새 직급 이름 (예: 책임)" aria-label="새 직급 이름" aria-describedby="rankError">
        <button type="submit" class="btn btn-primary" data-write>직급 추가</button>
        <span class="muted org-count" id="rankCount"></span>
      </form>
      <p class="form-error" id="rankError" role="alert"></p>
      <div class="table-wrap">
        <table class="table org-table">
          <thead><tr><th>순서</th><th>직급</th><th>인원</th><th>연차 경력</th><th>관리</th></tr></thead>
          <tbody id="rankList"></tbody>
        </table>
      </div>
    </section>
  </jsp:body>
</ui:layout>
