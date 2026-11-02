<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  지문 단말기 시뮬레이터 (/attendance/simulator, 로그인 없이 연다). 요구분석서 3.10, 기능명세서 1.9.
  실제 지문 장비가 아니라 시연용 단말기다. 이 화면이 단말기 역할을 하며, 출퇴근은 단말기 키(X-Device-Key)를 실어
  POST /api/attendance/punch 로 보낸다. 시각은 이음 서버가 받은 시각으로 남는다(화면 시계는 보여 주기만 한다).
  단말기 키는 이 브라우저에만 저장한다(단말기 설정과 같다). 동작은 resources/js/attendance/simulator.js.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>지문 단말기 시뮬레이터</title>
  <meta name="theme-color" content="#ffffff">
  <link rel="icon" href="${ctx}/resources/icons/favicon.png" type="image/png">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">
  <link rel="stylesheet" href="${ctx}/resources/css/common.css">
</head>
<body class="sim-page" data-ctx="${ctx}">
  <div class="sim">
    <div class="sim-head">
      <img src="${ctx}/resources/img/logo-mark.svg" alt="">
      <strong id="simTitle">지문 단말기</strong>
      <%-- 연결된 뒤에만 보인다. 기록 보기: 근태 기록 창 / 단말기 키 재설정: 저장한 키를 지우고 키 입력 칸으로 --%>
      <div class="sim-head-actions">
        <button type="button" class="btn sim-reset" id="simLogOpen" aria-haspopup="dialog" hidden>기록 보기</button>
        <button type="button" class="btn sim-reset" id="simChangeKey" hidden>단말기 키 재설정</button>
      </div>
    </div>

    <%-- 1) 단말기 키: 업체마다 발급한 키. 없거나 틀리면 이 칸만 보인다 --%>
    <section class="sim-panel" id="simConnect" hidden>
      <p class="sim-title">단말기 연결</p>
      <p class="sim-help">업체에 발급된 단말기 키를 입력하세요. 이 브라우저에만 저장됩니다.</p>
      <form class="input-action" id="simKeyForm" novalidate>
        <input class="input" id="simKey" type="password" autocomplete="off" placeholder="단말기 키" aria-label="단말기 키">
        <button class="btn btn-primary" type="submit" id="simKeyOk">연결</button>
      </form>
      <p class="form-error" id="simKeyError" role="alert"></p>
    </section>

    <div id="simMain" hidden>
      <section class="sim-panel sim-clock">
        <div class="sim-time" id="simClock">--:--:--</div>
        <div class="sim-date" id="simToday"></div>
        <div class="sim-device" id="simDevice"></div>
      </section>

      <%-- 한 화면에 한 단계: 사람 → 출근·퇴근 → 지문 → 결과(몇 초 뒤 처음으로). simulator.js 의 simGo 가 바꾼다.
           지난 단계(✓)를 누르면 그 단계로 돌아간다 --%>
      <ol class="sim-steps" id="simSteps" aria-label="진행 단계">
        <li data-step="1"><button type="button" data-go="1">사람</button></li>
        <li data-step="2"><button type="button" data-go="2">출근·퇴근</button></li>
        <li data-step="3"><button type="button" data-go="3">지문</button></li>
      </ol>

      <section class="sim-panel sim-step" data-step="1">
        <p class="sim-title">1. 지문을 댈 사람을 고르세요</p>
        <%-- 이름·사용자 번호·부서로 바로 찾는다. 한 명만 남으면 Enter 로 고른다 --%>
        <input class="input sim-search" id="simSearch" type="search" autocomplete="off"
               placeholder="이름, 사용자 번호, 부서로 찾기" aria-label="지문을 댈 사람 찾기" aria-controls="simPeople">
        <div class="sim-people" id="simPeople" role="list"></div>
        <p class="sim-empty muted" id="simEmpty" hidden>찾는 사람이 없습니다</p>
        <%-- 한 번에 5명. 더 있으면 페이지로 넘긴다 --%>
        <nav class="sim-pager" id="simPager" aria-label="사람 목록 페이지" hidden></nav>
      </section>

      <section class="sim-panel sim-step" data-step="2" hidden>
        <p class="sim-title">2. 출근·퇴근을 고르세요</p>
        <p class="sim-chosen" id="simChosenPerson"></p>
        <div class="sim-types" role="group" aria-label="출근 또는 퇴근">
          <button type="button" class="sim-type" data-type="IN">출근</button>
          <button type="button" class="sim-type" data-type="OUT">퇴근</button>
        </div>
      </section>

      <section class="sim-panel sim-step sim-scan" data-step="3" hidden>
        <p class="sim-title">3. 지문을 대 주세요</p>
        <p class="sim-chosen" id="simChosenPunch"></p>
        <button type="button" class="sim-finger" id="simFinger" aria-label="지문 대기">
          <svg viewBox="0 0 24 24" width="56" height="56" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true">
            <path d="M7 19.5c-1.2-2-1.8-4.4-1.8-7a6.8 6.8 0 0 1 13.6 0"/>
            <path d="M9.6 20.5c-.9-2.2-1.4-4.8-1.4-8a3.8 3.8 0 0 1 7.6 0c0 1.4-.1 2.7-.3 4"/>
            <path d="M12 12.5c0 3.2.5 5.9 1.6 8.3"/>
            <path d="M16.6 19.8c.6-1.7 1-3.7 1.1-5.8"/>
            <path d="M4.6 8.5A8.8 8.8 0 0 1 12 3.7c2.4 0 4.6.9 6.2 2.5"/>
          </svg>
        </button>
        <span class="sim-hint" id="simHint">지문 버튼을 누르세요</span>
      </section>

      <section class="sim-panel sim-step sim-result idle" data-step="done" id="simResult" aria-live="polite" hidden>
        <div class="sim-big"></div>
        <div id="simResultSub"></div>
        <button type="button" class="btn btn-primary" data-go="1">처음으로</button>
        <small class="sim-hint" id="simBackHint"></small>
      </section>
    </div>
  </div>

  <%-- 근태 기록: 이 화면을 연 뒤 찍은 것만, 최근 10건. 머리줄의 기록 보기로 연다 --%>
  <dialog class="modal sim-log-dialog" id="simLogDialog" aria-labelledby="simLogTitle">
    <div class="sim-log-head">
      <h2 id="simLogTitle">근태 기록</h2>
      <button type="button" class="icon-btn sim-log-x" id="simLogClose" aria-label="닫기">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>
      </button>
    </div>
    <%-- 찍은 기록이 없을 때는 빈 카드 가운데에 안내만 --%>
    <p class="sim-log-empty" id="simLogEmpty">기록이 없습니다</p>
    <ul class="sim-log" id="simLog" hidden></ul>
  </dialog>
  <script src="${ctx}/resources/js/attendance/simulator.js"></script>
</body>
</html>
