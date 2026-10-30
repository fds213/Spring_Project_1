<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  결재함 화면 (/approval/list). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버 주소와 주고받는 값은 docs/front-screen-api.md(프론트 제안), 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="결재함" crumb="전자결재" menu="approval-box">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/approval/approval.js"></script>
    <script>openApprovalBoxView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-approval-box">
      <%-- 결재함: 내 순번에 온 문서, 내가 올린 문서를 상태별로 본다. 휴가와 장비 문서를 한 목록에서 다룬다 (approval/approval.js) --%>
      <div class="page-head"><h1>결재함</h1><a href="${ctx}/approval/write" class="btn btn-primary" data-write>기안 작성</a></div>
      <div class="cards" id="eaTabs"><button type="button" class="card card-filter" data-tab="WAIT" aria-pressed="true"><small>내 순번 대기</small><strong id="eaCntWAIT">0건</strong></button><button type="button" class="card card-filter" data-tab="PROGRESS" aria-pressed="false"><small>진행</small><strong id="eaCntPROGRESS">0건</strong></button><button type="button" class="card card-filter" data-tab="DONE" aria-pressed="false"><small>완료</small><strong id="eaCntDONE">0건</strong></button><button type="button" class="card card-filter" data-tab="REJECTED" aria-pressed="false"><small>반려·회수</small><strong id="eaCntREJECTED">0건</strong></button><button type="button" class="card card-filter" data-tab="DRAFT" aria-pressed="false"><small>임시저장</small><strong id="eaCntDRAFT">0건</strong></button></div>
      <section class="info-card table-gap">
        <div class="table-wrap"><table class="table"><thead id="eaHead"></thead><tbody id="eaList"></tbody></table></div>
      </section>
    </section>
  </jsp:body>
</ui:layout>
