<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  부서 관리 화면 (/employee/admin/dept). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버 주소와 주고받는 값은 docs/front-screen-api.md(프론트 제안), 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="부서 관리" crumb="업체 관리" menu="admin-dept">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/employee/employee.js"></script>
    <script src="${ctx}/resources/js/employee/org-admin.js"></script>
    <script>openOrgAdmin('dept');</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-admin-dept">
      <%-- 부서 관리·직급 관리 (업체 관리자, employee/org-admin.js): 추가·이름 변경·삭제·순서. 사람이 있는 부서·직급은 지울 수 없고,
           이름을 바꾸면 소속 직원과 기록도 같이 바뀐다. 여기 목록이 계정 생성·가입 활성화·사원 관리에 그대로 쓰인다 --%>
      <div class="page-head"><h1>부서 관리</h1></div>
      <p class="muted join-scope">여기 부서가 계정 생성과 가입 활성화에 그대로 나옵니다. 직원이 있는 부서는 지울 수 없고, 이름을 바꾸면 소속 직원과 게시판·일정 기록도 함께 바뀝니다. 부서 형태를 IT 부서로 정하면 그 부서 직원에게 IT 담당 메뉴가, 인사 부서로 정하면 인사 담당 메뉴가 보입니다(다음 화면을 열 때부터). 부서장은 사원 관리에서 정합니다.</p>
      <form class="org-add" id="deptAddForm" novalidate>
        <input class="input" id="deptNewName" maxlength="20" autocomplete="off" placeholder="새 부서 이름 (예: 인사팀)" aria-label="새 부서 이름" aria-describedby="deptError">
        <button type="submit" class="btn btn-primary" data-write>부서 추가</button>
        <span class="muted org-count" id="deptCount"></span>
      </form>
      <p class="form-error" id="deptError" role="alert"></p>
      <div class="table-wrap">
        <table class="table org-table">
          <thead><tr><th>순서</th><th>부서</th><th>부서 형태</th><th>인원</th><th>부서장</th><th>관리</th></tr></thead>
          <tbody id="deptList"></tbody>
        </table>
      </div>
    </section>
  </jsp:body>
</ui:layout>
