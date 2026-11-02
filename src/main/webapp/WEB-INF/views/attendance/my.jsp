<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  근태 화면 (/attendance/my). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
  연차는 따로 연차 화면(/attendance/leave)에 있다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="근태" crumb="근무" menu="attendance">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/attendance/attendance.js"></script>
    <script>openAttendanceView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-attendance">
      <%-- 근태: 로그인한 본인의 출퇴근 기록만 보여 준다. 다른 직원의 근태와 승인은 없다 (attendance/attendance.js) --%>
      <div class="page-head"><h1>근태</h1></div>
      <div class="cards">
        <div class="card"><small>오늘 근태</small><strong id="attToday">-</strong></div>
        <div class="card"><small>이번 달 지각</small><strong id="attLate">-</strong></div>
        <div class="card"><small>이번 달 조퇴</small><strong id="attEarly">-</strong></div>
      </div>
      <div class="att-link table-gap" id="attLink"></div>
      <%-- 월별 근태 기록: 머리줄 없이 달 이동부터 (이름은 화면 낭독기용 aria-label 로만) --%>
      <section class="info-card table-gap" aria-label="월별 근태 기록">
        <div class="att-month att-month-center">
          <button type="button" class="btn" id="attPrev" aria-label="이전 달">&lt;</button>
          <strong id="attMonthLabel"></strong>
          <button type="button" class="btn" id="attNext" aria-label="다음 달">&gt;</button>
        </div>
        <div class="table-wrap att-table">
          <table class="table">
            <thead><tr><th>날짜</th><th>출근</th><th>퇴근</th><th>상태</th><th>비고</th></tr></thead>
            <tbody id="attList"></tbody>
          </table>
        </div>
      </section>
    </section>
  </jsp:body>
</ui:layout>
