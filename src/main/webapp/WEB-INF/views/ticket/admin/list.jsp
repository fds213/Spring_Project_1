<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  티켓 처리 화면 (/ticket/admin/list, IT 담당·업체 관리자). 요구분석서 3.4 IT 티켓, 통합 계획서 3장 B·C와 5장 티켓.
  장애·문의와 장비 지급 티켓을 배정하고 처리한다. 누를 수 있는 버튼은 서버가 준 값(actions)으로 정한다.
  이 주소를 받는 Controller 는 ticket 모듈 담당이 만든다. 동작은 resources/js/ticket/ticket-admin.js.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="티켓 처리" crumb="IT 서비스" menu="ticket-all">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ticket/ticket-admin.js"></script>
    <script>openTicketAdmin();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-ticket-admin">
      <div class="page-head"><h1>티켓 처리</h1></div>
      <p class="muted join-scope">IT 담당자는 미배정 티켓을 자신에게 배정해 처리하고, 업체 관리자는 담당자를 지정하거나 바꿉니다. 처리 완료한 티켓은 요청자가 내 티켓에서 확인하면 종료되고, 다시 요청하면 처리 중으로 돌아옵니다.</p>

      <%-- 카드를 누르면 아래 목록이 그 조건으로 걸러진다 --%>
      <div class="cards" id="taFilters">
        <button type="button" class="card card-filter" data-filter="UNASSIGNED" aria-pressed="true"><small>미배정</small><strong id="taCntUNASSIGNED">-</strong></button>
        <button type="button" class="card card-filter" data-filter="MINE" aria-pressed="false"><small>내 담당</small><strong id="taCntMINE">-</strong></button>
        <button type="button" class="card card-filter" data-filter="IN_PROGRESS" aria-pressed="false"><small>처리 중</small><strong id="taCntIN_PROGRESS">-</strong></button>
        <button type="button" class="card card-filter" data-filter="RESOLVED" aria-pressed="false"><small>확인 대기</small><strong id="taCntRESOLVED">-</strong></button>
        <button type="button" class="card card-filter" data-filter="ALL" aria-pressed="false"><small>전체</small><strong id="taCntALL">-</strong></button>
      </div>

      <form class="search-row" id="taSearchForm" role="search">
        <select class="input ta-type" id="taType" aria-label="구분">
          <option value="">전체 구분</option>
          <option value="INCIDENT">장애</option>
          <option value="INQUIRY">문의</option>
          <option value="EQUIPMENT">장비 지급</option>
        </select>
        <input type="search" class="search-input" id="taSearch" placeholder="번호·제목·요청자로 검색" aria-label="티켓 검색" autocomplete="off">
        <button type="submit" class="btn">검색</button>
      </form>

      <section class="info-card">
        <div class="table-wrap"><table class="table">
          <thead><tr><th>번호</th><th>구분</th><th>제목</th><th>요청자</th><th>접수 지점</th><th>담당자</th><th>상태</th><th>접수일</th></tr></thead>
          <tbody id="taList"></tbody>
        </table></div>
      </section>
      <nav class="pager" id="taPager" aria-label="티켓 목록 페이지" hidden></nav>
    </section>
  </jsp:body>
</ui:layout>
