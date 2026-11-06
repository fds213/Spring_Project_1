<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  근태 화면 (/attendance/my). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
  연차는 따로 연차 화면(/attendance/leave)에 있다.
  근태 정정: '퇴근 누락' 날의 [정정 요청]으로 퇴근 시각을 요청하고, 부서장(없거나 본인이면 업체 관리자)이 '처리할 근태 정정'에서 승인·반려한다.
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
      <%-- 근태: 로그인한 본인의 출퇴근 기록과 정정 요청. 다른 직원 것은 내가 처리할 정정 요청만 보인다 (attendance/attendance.js) --%>
      <div class="page-head"><h1>근태</h1></div>
      <div class="cards">
        <div class="card"><small>오늘 근태</small><strong id="attToday">-</strong></div>
        <div class="card"><small>이번 달 지각</small><strong id="attLate">-</strong></div>
        <div class="card"><small>이번 달 조퇴</small><strong id="attEarly">-</strong></div>
      </div>
      <%-- 처리할 근태 정정: 내가 처리할 대기 요청이 있을 때만 보인다 (부서장·업체 관리자) --%>
      <section class="info-card table-gap" id="attInbox" aria-labelledby="attInboxTitle" hidden>
        <header class="info-card-head"><h2 id="attInboxTitle">처리할 근태 정정</h2></header>
        <div class="table-wrap att-inbox">
          <table class="table">
            <thead><tr><th>요청자</th><th>날짜</th><th>출근</th><th>요청 퇴근</th><th>사유</th><th>요청일</th><th>관리</th></tr></thead>
            <tbody id="attInboxList"></tbody>
          </table>
        </div>
      </section>
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

    <%-- 퇴근 누락 정정 요청: 그날 퇴근 시각과 사유 --%>
    <dialog class="modal att-corr" id="attCorrDialog" aria-labelledby="attCorrTitle">
      <h2 id="attCorrTitle">퇴근 누락 정정 요청</h2>
      <p id="attCorrWho"></p>
      <form class="leave-form" id="attCorrForm" novalidate>
        <div class="leave-row"><label for="attCorrOut">퇴근 시각</label><input class="input" id="attCorrOut" type="time" step="60"></div>
        <div class="leave-row leave-row-top"><label for="attCorrReason">사유</label>
          <textarea class="inq-text" id="attCorrReason" maxlength="500" placeholder="예: 퇴근할 때 단말기를 찍지 못했습니다"></textarea></div>
        <p class="form-error" id="attCorrError" role="alert"></p>
        <div class="modal-actions">
          <button type="button" class="btn" id="attCorrCancel">취소</button>
          <button type="submit" class="btn btn-primary" id="attCorrSubmit" data-write>요청</button>
        </div>
      </form>
    </dialog>

    <%-- 정정 반려: 사유 필수 (요청자에게 보인다) --%>
    <dialog class="modal att-corr" id="attRejectDialog" aria-labelledby="attRejectTitle">
      <h2 id="attRejectTitle">정정 요청을 반려할까요?</h2>
      <p id="attRejectWho"></p>
      <form class="leave-form" id="attRejectForm" novalidate>
        <div class="leave-row leave-row-top"><label for="attRejectReason">반려 사유</label>
          <textarea class="inq-text" id="attRejectReason" maxlength="500" placeholder="요청한 직원에게 보입니다"></textarea></div>
        <p class="form-error" id="attRejectError" role="alert"></p>
        <div class="modal-actions">
          <button type="button" class="btn" id="attRejectCancel">취소</button>
          <button type="submit" class="btn btn-danger-line" id="attRejectSubmit" data-write>반려</button>
        </div>
      </form>
    </dialog>
  </jsp:body>
</ui:layout>
