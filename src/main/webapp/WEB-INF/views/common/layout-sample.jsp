<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  홈 화면 (/home). HomeController(common)가 이 JSP를 연다. 화면은 이동희(프론트)가 맡는다(홍동국 협의).
  layout.tag 사용 예시도 겸한다: 팀원은 이 모양(scripts 조각 + 본문)대로 자기 모듈 JSP를 만든다.
  숫자·목록은 home.js 가 이미 있는 각 모듈 API로 채운다. 못 불러온 칸은 "-" 그대로 둔다(다른 칸은 계속 보인다).
    결재 대기·진행 중인 내 기안: GET /approval/api/box   내 티켓: GET /ticket/api/tickets
    남은 연차: GET /attendance/api/leaves   내 장비: GET /asset/api/assets/assigned
    이번 주 일정: GET /schedule/api/schedules?month=   공지: GET /board/api/notices
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="홈" menu="home">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/home.js"></script>
    <script>openHome();</script>
  </jsp:attribute>
  <jsp:body>
    <%-- 홈 전용 스타일 (공통 레이아웃에 화면별 CSS 칸이 없어서 본문 맨 앞에서 불러온다) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/home.css">
    <%-- data-emp: 내 사원 번호. 결재함에서 "내가 올린 문서"를 가를 때 쓴다(결재선에 내가 없으면 내가 올린 문서) --%>
    <div class="home" id="home" data-emp="${loginUser.employeeId}">

      <%-- 1. 인사 + 자주 쓰는 일 바로 가기. 인사말은 home.js 가 시간대(아침·오후·저녁)에 맞춰 바꾼다 --%>
      <div class="page-head home-head">
        <div>
          <h1><span id="homeHello">안녕하세요</span>, <c:out value="${loginUser.name}" />님</h1>
          <p class="home-date" id="homeDate"></p>
        </div>
        <div class="page-actions">
          <a href="${ctx}/approval/write" class="btn btn-primary" data-write>기안 작성</a>
          <a href="${ctx}/ticket/new" class="btn" data-write>장애·문의 접수</a>
        </div>
      </div>

      <%-- 2. 꼭 볼 공지 한 줄: 관리자가 맨 위 고정한 공지만. 지금은 POST 표에 고정 칸이 없어 늘 숨는다(고정 기능이 생기면 저절로 보인다) --%>
      <a class="home-pin" id="homePin" hidden>
        <span class="home-tag">고정</span>
        <span class="t" id="homePinTitle"></span>
        <span class="d" id="homePinDate"></span>
        <span class="go">보기 ›</span>
      </a>

      <%-- 3. 오늘 챙길 숫자 5개. 누르면 그 화면으로 간다. is-alert: 처리할 것이 있으면 파란 숫자.
           아랫줄(sub)은 챙길 일이 있을 때만 home.js 가 채운다(예: "휴가 신청서 외 1건", "신청 중 1일") --%>
      <div class="home-cards">
        <a class="home-card" id="cardWait" href="${ctx}/approval/list">
          <small>결재 대기</small><strong id="homeWait">-</strong><span class="sub" id="homeWaitSub"></span>
        </a>
        <a class="home-card" id="cardMine" href="${ctx}/approval/list">
          <small>진행 중인 내 기안</small><strong id="homeMine">-</strong><span class="sub" id="homeMineSub"></span>
        </a>
        <a class="home-card" id="cardTicket" href="${ctx}/ticket/mine">
          <small>내 티켓</small><strong id="homeTicket">-</strong><span class="sub" id="homeTicketSub"></span>
        </a>
        <a class="home-card" id="cardLeave" href="${ctx}/attendance/leave">
          <small>남은 연차</small><strong id="homeLeave">-</strong><span class="sub" id="homeLeaveSub"></span>
        </a>
        <a class="home-card" id="cardAsset" href="${ctx}/asset/mine">
          <small>내 장비</small><strong id="homeAsset">-</strong><span class="sub" id="homeAssetSub"></span>
        </a>
      </div>

      <%-- 4. 이번 주 일정(오늘부터 평일 5일) + 최근 공지 5개 --%>
      <div class="home-grid">
        <section class="info-card">
          <header class="info-card-head"><h2>이번 주 일정</h2><a href="${ctx}/schedule/calendar" class="more-link">일정 보기</a></header>
          <ul class="home-week" id="homeWeek"><li class="home-empty">불러오는 중…</li></ul>
        </section>
        <section class="info-card">
          <header class="info-card-head"><h2>공지사항</h2><a href="${ctx}/board/notice" class="more-link">더보기</a></header>
          <ul class="home-list" id="homeNotices"><li class="home-empty">불러오는 중…</li></ul>
        </section>
      </div>
    </div>
  </jsp:body>
</ui:layout>
