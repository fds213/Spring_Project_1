<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  회원 접수 화면 (/ops/inquiry-member, OPS-04 최소판). 업체 직원이 보낸 이음 장애 문의를 모든 업체에 걸쳐 보고 답변한다.
  화면 JS는 util.js(같이 쓰는 도구)와 ops/inquiry.js만 불러오고, 페이지가 열리면 openInquiryView('member')를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="회원 접수" crumb="문의" menu="ops-inquiry-member">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ops/inquiry.js"></script>
    <script>openInquiryView('member');</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-ops-inquiry-member">
      <%-- 회원 접수: 업체 직원이 보낸 이음 장애 문의. 답변은 메일이 아니라 직원의 '내가 보낸 문의'에 보인다 --%>
      <div class="page-head"><h1>회원 접수</h1></div>
      <div class="cards" id="memberFilters">
        <button type="button" class="card card-filter" data-filter="all" aria-pressed="true"><small>전체</small><strong id="mCntAll">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="SUP_RECEIVED" aria-pressed="false"><small>답변 대기</small><strong id="mCntWait">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="SUP_PROCESSING" aria-pressed="false"><small>답변 완료</small><strong id="mCntDone">0건</strong></button>
      </div>
      <div class="search-row">
        <input type="search" class="search-input" id="memberSearch" placeholder="업체·이름·제목·내용·접수번호로 검색" aria-label="회원 접수 검색" autocomplete="off">
        <select class="filter-select" id="memberPeriod" aria-label="접수 기간">
          <option value="all">전체 기간</option><option value="1">오늘</option><option value="7">최근 7일</option><option value="30">최근 30일</option>
        </select>
        <label class="inq-check"><input type="checkbox" id="memberStale"> 24시간 넘게 답변 없음</label>
        <span class="inq-hint" id="memberSearchInfo" role="status"></span>
      </div>
      <div class="table-wrap">
        <table class="table table-fixed">
          <colgroup><col style="width: 14%"><col style="width: 26%"><col style="width: 40%"><col style="width: 20%"></colgroup>
          <thead><tr><th>접수 시각</th><th>보낸 사람</th><th>제목</th><th>상태</th></tr></thead>
          <tbody id="memberList"></tbody>
        </table>
      </div>
    </section>
  </jsp:body>
</ui:layout>
