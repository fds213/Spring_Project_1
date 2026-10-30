<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  일정 화면 (/schedule/calendar). 시안 views/schedule/calendar.html 를 옮긴 것이다 (이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장).
  화면 JS도 시안 것을 그대로 쓴다: 서버 주소와 주고받는 값은 시안 API.md, 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="일정" crumb="근무" menu="schedule">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/schedule/calendar.js"></script>
    <script>openScheduleView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-schedule">
      <%-- 일정: 개인·부서·전사 일정과 휴일, 본인의 연차를 달력으로 본다 (schedule/calendar.js) --%>
      <div class="page-head"><h1>일정</h1></div>
      <div class="sch-layout">
        <section class="info-card">
          <div class="sch-toolbar">
            <div class="att-month">
              <button type="button" class="btn" id="schPrev" aria-label="이전 달">&lt;</button>
              <strong id="schMonthLabel"></strong>
              <button type="button" class="btn" id="schNext" aria-label="다음 달">&gt;</button>
              <button type="button" class="btn" id="schToday">오늘</button>
            </div>
            <div class="sch-filters" id="schFilters">
              <label><input type="checkbox" data-scope="PERSONAL" checked> <span class="sch-chip chip-personal">개인</span></label>
              <label><input type="checkbox" data-scope="DEPT" checked> <span class="sch-chip chip-dept">부서</span></label>
              <label><input type="checkbox" data-scope="COMPANY" checked> <span class="sch-chip chip-company">전사</span></label>
              <label><input type="checkbox" data-scope="LEAVE" checked> <span class="sch-chip chip-leave">내 연차</span></label>
            </div>
          </div>
          <div class="sch-week" aria-hidden="true"><span class="is-red">일</span><span>월</span><span>화</span><span>수</span><span>목</span><span>금</span><span class="is-blue">토</span></div>
          <div class="sch-grid" id="schGrid"></div>
        </section>
        <section class="info-card sch-side">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg></span>
            <h2 id="schDayTitle">-</h2>
          </header>
          <ul class="sch-day-list" id="schDayList"></ul>
          <form class="leave-form" id="schForm" novalidate>
            <h3 class="bill-sub">일정 추가</h3>
            <div class="leave-row"><label for="schScope">구분</label><select class="filter-select" id="schScope"></select></div>
            <div class="leave-row"><label for="schDate">날짜</label><input class="input" id="schDate" type="date"></div>
            <div class="leave-row"><label for="schTitle">제목</label><input class="input" id="schTitle" maxlength="40" autocomplete="off"></div>
            <div class="leave-row"><label for="schMemo">메모</label><input class="input" id="schMemo" maxlength="200" autocomplete="off" placeholder="선택"></div>
            <p class="form-error" id="schError" role="alert"></p>
            <div class="bill-actions"><button type="submit" class="btn btn-primary">추가</button></div>
          </form>
        </section>
      </div>
    </section>
  </jsp:body>
</ui:layout>
