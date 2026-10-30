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
      <span class="sim-badge">시연용 단말기</span>
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

      <%-- 한 화면에 한 단계: 사람 → 출근·퇴근 → 지문 → 결과(몇 초 뒤 처음으로). simulator.js 의 simGo 가 바꾼다 --%>
      <ol class="sim-steps" id="simSteps" aria-label="진행 단계">
        <li data-step="1">사람</li>
        <li data-step="2">출근·퇴근</li>
        <li data-step="3">지문</li>
      </ol>

      <section class="sim-panel sim-step" data-step="1">
        <p class="sim-title">1. 지문을 댈 사람을 고르세요</p>
        <div class="sim-people" id="simPeople"></div>
      </section>

      <section class="sim-panel sim-step" data-step="2" hidden>
        <p class="sim-title">2. 출근·퇴근을 고르세요</p>
        <p class="sim-chosen" id="simChosenPerson"></p>
        <div class="sim-types" role="group" aria-label="출근 또는 퇴근">
          <button type="button" class="sim-type" data-type="IN">출근</button>
          <button type="button" class="sim-type" data-type="OUT">퇴근</button>
        </div>
        <div class="sim-step-actions"><button type="button" class="btn" data-go="1">이전</button></div>
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
        <div class="sim-step-actions"><button type="button" class="btn" data-go="2">이전</button></div>
      </section>

      <section class="sim-panel sim-step sim-result idle" data-step="done" id="simResult" aria-live="polite" hidden>
        <div class="sim-big"></div>
        <div id="simResultSub"></div>
        <button type="button" class="btn btn-primary" data-go="1">처음으로</button>
        <small class="sim-hint" id="simBackHint"></small>
      </section>

      <%-- 첫 단계에서만: 이번에 찍은 기록과 키 바꾸기 --%>
      <div class="sim-first-only">
        <section class="sim-panel">
          <p class="sim-title">이번에 찍은 기록</p>
          <ul class="sim-log" id="simLog"><li class="muted">기록이 없습니다</li></ul>
        </section>
        <div class="sim-foot">
          <button type="button" class="btn" id="simChangeKey">단말기 키 바꾸기</button>
        </div>
      </div>
    </div>
  </div>
  <script src="${ctx}/resources/js/attendance/simulator.js"></script>
</body>
</html>
