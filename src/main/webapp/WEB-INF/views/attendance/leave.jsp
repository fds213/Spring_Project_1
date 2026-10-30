<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  연차 화면 (/attendance/leave). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
  출퇴근 기록은 따로 근태 화면(/attendance/my)에 있다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="연차" crumb="근무" menu="leave">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/attendance/leave.js"></script>
    <script>openLeaveView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-leave">
      <%-- 연차: 로그인한 본인의 연차 현황과 이번 연차 기간의 휴가 신청 내역만 보여 준다 (attendance/leave.js) --%>
      <div class="page-head">
        <h1>연차</h1>
        <%-- 연차·휴가 신청은 전자결재의 휴가 신청서로 한다(요구분석서 3장 범위: 결재 휴가 양식, 최종 승인 때 연차 차감) --%>
        <div class="page-actions"><a class="btn btn-primary" href="${ctx}/approval/write" data-write>휴가 신청서 쓰기</a></div>
      </div>
      <div class="cards">
        <div class="card"><small>남은 연차</small><strong id="leaveLeft">-</strong></div>
        <div class="card"><small>이번 기간 부여</small><strong id="leaveGrant">-</strong></div>
        <div class="card"><small>사용 (승인됨)</small><strong id="leaveUsed">-</strong></div>
        <div class="card"><small>승인 대기</small><strong id="leavePending">-</strong></div>
      </div>
      <div class="info-grid info-grid-2 table-gap">
        <section class="info-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg></span>
            <div><h2>연차 현황</h2><p>15일 + 총 경력 3년마다 1일. 반차는 0.5일입니다</p></div>
          </header>
          <dl class="detail" id="leaveInfo"></dl>
        </section>
        <section class="info-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M14 3v6h6M8 13h8M8 17h5"/></svg></span>
            <div><h2>휴가 신청 내역</h2><p>이번 연차 기간에 전자결재로 신청한 휴가입니다</p></div>
          </header>
          <p class="bill-note">연차·휴가는 전자결재의 휴가 신청서로 신청합니다. 최종 승인되면 연차가 차감됩니다. 회수는 결재함에서 합니다.</p>
          <ul class="bill-history" id="leaveHistory"></ul>
        </section>
      </div>
    </section>
  </jsp:body>
</ui:layout>
