<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  비회원 접수 화면 (/ops/inquiry-guest). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="비회원 접수" crumb="문의" menu="ops-inquiry-guest">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ops/inquiry.js"></script>
    <script>openInquiryView('guest');</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-ops-inquiry-guest">
      <%-- 비회원 접수: 소개 사이트 도입 문의(WEB-02). 답변은 메일로 나간다. 발송 성공 뒤에만 "답변 완료" --%>
      <div class="page-head"><h1>비회원 접수</h1></div>
      <div class="cards" id="guestFilters">
        <button type="button" class="card card-filter" data-filter="all" aria-pressed="true"><small>전체 (스팸 제외)</small><strong id="gCntAll">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="WAIT" aria-pressed="false"><small>답변 대기</small><strong id="gCntWait">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="FAILED" aria-pressed="false"><small>발송 실패</small><strong id="gCntFailed">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="DONE" aria-pressed="false"><small>답변 완료</small><strong id="gCntDone">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="SPAM" aria-pressed="false"><small>스팸 숨김</small><strong id="gCntSpam">0건</strong></button>
      </div>
      <div class="search-row">
        <input type="search" class="search-input" id="guestSearch" placeholder="이름·이메일·제목·내용·접수번호로 검색" aria-label="비회원 접수 검색" autocomplete="off">
        <select class="filter-select" id="guestPeriod" aria-label="접수 기간">
          <option value="all">전체 기간</option><option value="1">오늘</option><option value="7">최근 7일</option><option value="30">최근 30일</option>
        </select>
        <label class="inq-check"><input type="checkbox" id="guestStale"> 24시간 넘게 답변 없음</label>
        <span class="inq-hint" id="guestSearchInfo" role="status"></span>
      </div>
      <div class="table-wrap">
        <table class="table table-fixed">
          <colgroup><col style="width: 14%"><col style="width: 26%"><col style="width: 40%"><col style="width: 20%"></colgroup>
          <thead><tr><th>접수 시각</th><th>보낸 사람</th><th>제목</th><th>상태</th></tr></thead>
          <tbody id="guestList"></tbody>
        </table>
      </div>
    </section>
  </jsp:body>
</ui:layout>
