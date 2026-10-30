<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  근태·연차 화면 (/attendance/my). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="근태·연차" crumb="근무" menu="attendance">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/attendance/attendance.js"></script>
    <script>openAttendanceView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-attendance">
      <%-- 근태·연차: 로그인한 본인의 출퇴근 기록과 연차만 보여 준다. 다른 직원의 근태와 승인은 없다 (attendance/attendance.js) --%>
      <div class="page-head"><h1>근태·연차</h1></div>
      <div class="cards">
        <div class="card"><small>오늘 근태</small><strong id="attToday">-</strong></div>
        <div class="card"><small>이번 달 지각</small><strong id="attLate">-</strong></div>
        <div class="card"><small>이번 달 조퇴</small><strong id="attEarly">-</strong></div>
        <div class="card"><small>남은 연차</small><strong id="attLeft">-</strong></div>
      </div>
      <div class="att-link table-gap" id="attLink"></div>
      <div class="info-grid info-grid-2 table-gap">
        <section class="info-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg></span>
            <div><h2>월별 근태 기록</h2><p>지문 단말기에서 받은 출퇴근 기록입니다 (기본 근무 09:00~18:00)</p></div>
          </header>
          <div class="att-month">
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
        <section class="info-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg></span>
            <div><h2>연차·휴가</h2><p>15일 + 총 경력 3년마다 1일. 반차는 0.5일입니다</p></div>
          </header>
          <dl class="detail" id="leaveInfo"></dl>
          <%-- 연차·휴가 신청은 전자결재의 휴가 신청서로 한다(요구분석서 3장 범위: 결재 휴가 양식, 최종 승인 때 연차 차감) --%>
          <p class="bill-note">연차·휴가는 전자결재의 휴가 신청서로 신청합니다. 최종 승인되면 여기 사용 내역에 들어갑니다.</p>
          <div class="bill-actions"><a class="btn btn-primary" href="${ctx}/approval/write" data-write>휴가 신청서 쓰기</a></div>
          <h3 class="bill-sub">이번 연차 기간 사용 내역</h3>
          <ul class="bill-history" id="leaveHistory"></ul>
        </section>
      </div>
    </section>
  </jsp:body>
</ui:layout>
