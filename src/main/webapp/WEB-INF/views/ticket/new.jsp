<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  장애·문의 접수 화면 (/ticket/new). 시안 views/ticket/new.html 를 옮긴 것이다 (이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장).
  화면 JS도 시안 것을 그대로 쓴다: 서버 주소와 주고받는 값은 시안 API.md, 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="장애·문의 접수" crumb="IT 서비스" menu="ticket-new">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ticket/it-service.js"></script>
    <script>openTicketNewView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-ticket-new">
      <%-- 장애·문의 접수: 우리 회사 IT 담당자에게 간다. 이음 운영자에게 보내는 것은 "이음 장애 문의" (ticket/it-service.js) --%>
      <div class="page-head"><h1>장애·문의 접수</h1></div>
      <div class="info-grid info-grid-2">
        <section class="info-card">
          <header class="info-card-head"><span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg></span><div><h2>접수 내용</h2><p>우리 회사 IT 담당자에게 전달됩니다</p></div></header>
          <form class="leave-form" id="ticketForm" novalidate>
            <div class="leave-row">
              <label for="ticketType">구분</label>
              <select class="filter-select" id="ticketType"><option value="INCIDENT">장애</option><option value="INQUIRY">문의</option></select>
            </div>
            <div class="leave-row"><label for="ticketTitle">제목</label><input class="input" id="ticketTitle" maxlength="60" autocomplete="off"></div>
            <div class="leave-row"><label for="ticketBody">내용</label><textarea class="inq-text" id="ticketBody" maxlength="1000"></textarea></div>
            <div class="leave-row"><label for="ticketAsset">관련 자산</label><select class="filter-select" id="ticketAsset"></select></div>
            <div class="leave-row"><label for="ticketBranch">접수 지점</label><select class="filter-select" id="ticketBranch"></select></div>
            <p class="bill-note" id="ticketBranchHint"></p>
            <p class="form-error" id="ticketError" role="alert"></p>
            <div class="bill-actions"><button type="submit" class="btn btn-primary" data-write>접수</button></div>
          </form>
        </section>
        <section class="info-card">
          <header class="info-card-head"><span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg></span><div><h2>안내</h2></div></header>
          <dl class="detail"><dt>접수하면</dt><dd>IT 담당자에게 배정되고 내 티켓에 올라갑니다</dd><dt>처리 상태</dt><dd>접수 → 처리 중 → 완료 순서로 바뀝니다</dd><dt>접수 지점</dt><dd>관련 자산이 있는 지점으로 채워지고, 접수한 뒤에는 바뀌지 않습니다</dd><dt>다른 문의</dt><dd>이음 운영자에게 알릴 장애는 "이음 장애 문의"로 보내세요</dd></dl>
        </section>
      </div>
    </section>
  </jsp:body>
</ui:layout>
