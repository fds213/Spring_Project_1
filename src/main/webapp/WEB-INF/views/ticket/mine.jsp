<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  내 티켓 화면 (/ticket/mine). 시안 views/ticket/mine.html 를 옮긴 것이다 (이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장).
  화면 JS도 시안 것을 그대로 쓴다: 서버 주소와 주고받는 값은 시안 API.md, 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="내 티켓" crumb="IT 서비스" menu="ticket-mine">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ticket/it-service.js"></script>
    <script>openTicketMineView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-ticket-mine">
      <%-- 내 티켓: 내가 접수한 티켓의 처리 상태와 이력. 장비 지급 신청서가 승인되면 지급 티켓이 여기에 생긴다 (ticket/it-service.js) --%>
      <div class="page-head"><h1>내 티켓</h1><a href="${ctx}/ticket/new" class="btn btn-primary" data-write>접수</a></div>
      <div class="cards" id="ticketFilters"><button type="button" class="card card-filter" data-filter="all" aria-pressed="true"><small>전체</small><strong id="ticketCntAll">0건</strong></button><button type="button" class="card card-filter" data-filter="RECEIVED" aria-pressed="false"><small>접수</small><strong id="ticketCntReceived">0건</strong></button><button type="button" class="card card-filter" data-filter="IN_PROGRESS" aria-pressed="false"><small>처리 중</small><strong id="ticketCntProgress">0건</strong></button><button type="button" class="card card-filter" data-filter="DONE" aria-pressed="false"><small>완료</small><strong id="ticketCntDone">0건</strong></button></div>
      <section class="info-card table-gap">
        <div class="table-wrap"><table class="table">
          <thead><tr><th>번호</th><th>구분</th><th>제목</th><th>접수 지점</th><th>상태</th><th>접수일</th></tr></thead>
          <tbody id="ticketList"></tbody>
        </table></div>
      </section>
    </section>
  </jsp:body>
</ui:layout>
