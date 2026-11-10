<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  근태·연차 관리 화면 (/attendance/admin/list, 업체 관리자·인사 담당). 요구분석서 3.4·3.9.
  직원별 오늘 출퇴근, 그 달 출근일·지각·조퇴, 오늘 기준 남은 연차를 본다. 기록은 단말기가 남긴 것이 원본이라 여기서 고치지 않는다.
  연차 추가 부여(+)·되돌리기(-)는 직원 줄의 [연차]로 연다(기능명세서 '연차 세부'). 본인 줄에는 버튼이 없다(서버도 막는다).
  동작은 resources/js/attendance/attendance-admin.js.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="근태·연차 관리" crumb="인사 관리" menu="hr-attendance">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/attendance/attendance-admin.js"></script>
    <script>openAttendanceAdmin();</script>
  </jsp:attribute>
  <jsp:body>
    <%-- data-me: 내 사원 번호. 본인 줄에는 연차 부여 버튼을 두지 않는다 --%>
    <section class="view on" id="view-attendance-admin" data-me="${loginUser.employeeId}">
      <div class="page-head">
        <h1>근태·연차 관리</h1>
        <div class="page-actions"><a class="btn" href="${ctx}/attendance/admin/holiday">휴일 관리</a></div>
      </div>
      <div class="cards">
        <div class="card"><small>재직 직원</small><strong id="aaServing">-</strong></div>
        <div class="card"><small>오늘 출근</small><strong id="aaPunched">-</strong></div>
        <div class="card"><small>오늘 지각</small><strong id="aaLate">-</strong></div>
      </div>

      <section class="info-card table-gap" aria-label="직원별 근태">
        <div class="att-month att-month-center">
          <button type="button" class="btn" id="aaPrev" aria-label="이전 달">&lt;</button>
          <strong id="aaMonth"></strong>
          <button type="button" class="btn" id="aaNext" aria-label="다음 달">&gt;</button>
        </div>
        <div class="table-wrap att-table aa-table">
          <table class="table">
            <thead><tr><th>사번</th><th>이름</th><th>부서</th><th>단말기 번호</th><th>오늘</th><th>출근일</th><th>지각</th><th>조퇴</th><th>남은 연차</th><th>관리</th></tr></thead>
            <tbody id="aaList"></tbody>
          </table>
        </div>
      </section>
      <p class="muted join-scope">출근일·지각·조퇴는 고른 달, 출퇴근과 남은 연차는 오늘 기준입니다. 지각은 09:10 뒤 출근(10분 유예), 조퇴는 18:00 전 퇴근입니다.</p>
    </section>

    <%-- 연차 추가 부여: 더 주기(+)·되돌리기(-), 사유 필수. 아래는 그 직원의 부여 내역(최근 순) --%>
    <dialog class="modal aa-grant" id="aaGrantDialog" aria-labelledby="aaGrantTitle">
      <h2 id="aaGrantTitle">연차 추가 부여</h2>
      <p class="aa-grant-who" id="aaGrantWho"></p>
      <form class="leave-form" id="aaGrantForm" novalidate>
        <div class="leave-row"><label for="aaGrantKind">구분</label>
          <select class="filter-select" id="aaGrantKind"><option value="1">더 주기</option><option value="-1">되돌리기</option></select></div>
        <div class="leave-row"><label for="aaGrantDays">일수</label>
          <input class="input" id="aaGrantDays" type="number" min="0.5" max="30" step="0.5" inputmode="decimal" placeholder="0.5일 단위, 30일까지"></div>
        <div class="leave-row leave-row-top"><label for="aaGrantNote">사유</label>
          <textarea class="inq-text" id="aaGrantNote" maxlength="500" placeholder="예: 프로젝트 보상, 잘못 준 일수 되돌림"></textarea></div>
        <p class="form-error" id="aaGrantError" role="alert"></p>
        <div class="modal-actions">
          <button type="button" class="btn" id="aaGrantClose">닫기</button>
          <button type="submit" class="btn btn-primary" id="aaGrantSubmit" data-write>부여</button>
        </div>
      </form>
      <h3 class="aa-grant-head">부여 내역</h3>
      <div class="aa-grant-list" id="aaGrantHistory"></div>
    </dialog>
  </jsp:body>
</ui:layout>
