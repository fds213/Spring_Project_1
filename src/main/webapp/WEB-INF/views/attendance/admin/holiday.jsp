<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  휴일 관리 화면 (/attendance/admin/holiday, 업체 관리자·인사 담당). 요구분석서 3.9.
  휴가 신청의 날짜 계산(주말·휴일 빼기)과 일정 달력이 이 휴일을 쓴다. 휴일이 하나도 없는 해는 휴가 날짜를 계산하지 않는다.
  동작은 resources/js/attendance/holiday-admin.js.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="휴일 관리" crumb="인사 관리" menu="hr-holiday">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/attendance/holiday-admin.js"></script>
    <script>openHolidayView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-holiday">
      <div class="page-head">
        <h1>휴일 관리</h1>
        <%-- 그 해 법정 공휴일 목록이 있을 때만 보인다 (이미 있는 날은 건너뛴다) --%>
        <div class="page-actions"><button type="button" class="btn btn-primary" id="btnHolPreset" data-write hidden>공휴일 한 번에 넣기</button></div>
      </div>
      <p class="muted join-scope">휴가를 신청하면 주말과 여기 휴일을 빼고 날짜를 셉니다. 휴일이 하나도 없는 해에 걸친 휴가는 신청할 수 없으니, 해마다 먼저 넣어 주세요.</p>

      <section class="info-card table-gap" aria-label="휴일 목록">
        <div class="att-month att-month-center">
          <button type="button" class="btn" id="holPrev" aria-label="이전 해">&lt;</button>
          <strong id="holYear"></strong>
          <button type="button" class="btn" id="holNext" aria-label="다음 해">&gt;</button>
        </div>
        <div class="dev-body hol-add">
          <form class="org-add" id="holAddForm" novalidate>
            <input class="input hol-date" id="holDate" type="date" aria-label="휴일 날짜" aria-describedby="holError">
            <input class="input" id="holName" maxlength="50" autocomplete="off" placeholder="이름 (예: 창립기념일)" aria-label="휴일 이름" aria-describedby="holError">
            <button type="submit" class="btn btn-primary" data-write>추가</button>
          </form>
          <p class="form-error" id="holError" role="alert"></p>
        </div>
        <div class="table-wrap att-table">
          <table class="table">
            <thead><tr><th>날짜</th><th>요일</th><th>이름</th><th>관리</th></tr></thead>
            <tbody id="holList"></tbody>
          </table>
        </div>
      </section>
    </section>

    <dialog class="modal" id="holAskDialog" aria-labelledby="holAskTitle">
      <h2 id="holAskTitle">휴일을 지울까요?</h2>
      <p id="holAskBody"></p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">취소</button>
        <button type="button" class="btn btn-primary" value="ok">지우기</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
