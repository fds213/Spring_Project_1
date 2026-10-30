<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  이용 결제 화면 (/subscription/billing). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="이용 결제" crumb="업체 관리" menu="admin-billing">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/subscription/billing.js"></script>
    <script>renderBilling();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-admin-billing">
      <%-- 이용 결제: 업체 관리자가 결제·연장한다. 결제한 날부터 유료 이용 (남은 체험 기간은 이어지지 않음, PAY-01·02) --%>
      <div class="page-head"><h1>이용 결제</h1></div>
      <div class="cards">
        <div class="card"><small>이용 상태</small><strong id="billStatus">-</strong></div>
        <div class="card"><small>요금제</small><strong id="billPlan">-</strong></div>
        <div class="card"><small id="billEndLabel">체험 만료일</small><strong id="billEnd">-</strong></div>
        <div class="card"><small>이용 인원</small><strong id="billUsersNow">-</strong></div>
      </div>
      <div class="info-grid info-grid-2 table-gap">
        <section class="info-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v16"/><path d="M15 10h4a1 1 0 0 1 1 1v10"/><path d="M8 8h3M8 12h3M8 16h3M3 21h18"/></svg></span>
            <h2>이용 정보</h2>
          </header>
          <dl class="detail" id="billInfo"></dl>
          <div class="bill-history-box" id="billHistoryBox" hidden>
            <h3 class="bill-sub">결제 이력</h3>
            <ul class="bill-history" id="billHistory"></ul>
          </div>
        </section>
        <section class="info-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/></svg></span>
            <div><h2 id="billPayTitle">결제</h2><p id="billHint"></p></div>
          </header>
          <%-- 자동 결제 없이 관리자가 매번 월간·연간을 골라 직접 결제한다(SUB-03).
               체험 중·체험 만료: 첫 결제(결제한 날부터) / 유료 이용 중: 연장(만료일 다음 날부터, 인원 그대로).
               1. 요금제 선택 → 2. 결제 → 3. 결제 완료. 토스 결제가 승인되면 운영자 승인 없이 바로 반영한다(PAY-02) --%>
          <div class="bill-pay" id="billPay">
            <ol class="bill-steps" id="billSteps">
              <li data-step="1">1. 요금제 선택</li>
              <li data-step="2">2. 결제</li>
              <li data-step="3">3. 결제 완료</li>
            </ol>
            <%-- 1. 요금제 선택: 월간·연간과 (첫 결제일 때) 인원 --%>
            <div class="bill-step" id="billStep1">
              <fieldset class="plan-pick">
                <legend>요금제</legend>
                <label class="plan-opt">
                  <input type="radio" name="payPlan" value="MONTHLY" checked>
                  <span class="plan-opt-name">월간</span>
                  <span class="plan-opt-price" id="planPriceMONTHLY"></span>
                  <span class="plan-opt-note">1개월 · 할인 없음</span>
                </label>
                <label class="plan-opt">
                  <input type="radio" name="payPlan" value="ANNUAL">
                  <span class="plan-opt-name">연간</span>
                  <span class="plan-opt-price" id="planPriceANNUAL"></span>
                  <span class="plan-opt-note" id="planNoteANNUAL"></span>
                </label>
              </fieldset>
              <div class="bill-seats" id="payUsersBox">
                <label for="payUsers">이용 인원</label>
                <div class="bill-seats-row"><input class="input" id="payUsers" type="number" min="1" max="500" inputmode="numeric" aria-describedby="payUsersHint"><span>명</span></div>
                <p class="bill-note" id="payUsersHint"></p>
                <p class="form-error" id="payUsersError" role="alert"></p>
              </div>
              <%-- 고른 요금제로 계산한 금액과 이용 기간 --%>
              <dl class="pay-summary" id="payPreview"></dl>
              <div class="bill-actions"><button type="button" class="btn btn-primary" id="btnPayNext">다음: 결제</button></div>
            </div>
            <%-- 2. 결제: 실제로는 토스페이먼츠 테스트 결제창을 열고, 서버가 승인 결과와 금액을 확인한 뒤에만 반영한다 (PAY-01·02) --%>
            <div class="bill-step" id="billStep2" hidden>
              <p class="notice-warn">토스 테스트 결제창은 백엔드 연동 뒤에 붙습니다. 지금은 결제 성공·실패를 골라 흐름만 확인합니다. 카드 정보는 받지 않습니다.</p>
              <dl class="detail" id="billOrder"></dl>
              <label class="agree"><input type="checkbox" id="billAgree"> 시연용 서비스 이용 조건에 동의합니다.</label>
              <p class="form-error" id="billError" role="alert"></p>
              <div class="bill-actions">
                <button type="button" class="btn" id="btnPayBack">이전</button>
                <button type="button" class="btn" id="btnBillFail">결제 실패로 진행</button>
                <button type="button" class="btn btn-primary" id="btnBillOk">결제 성공으로 진행</button>
              </div>
              <p class="muted" id="billBusy" aria-live="polite"></p>
            </div>
            <%-- 3. 결제 완료: 결제가 바로 반영되어, 반영된 기간과 금액을 보여 준다 --%>
            <div class="bill-step" id="billStep3" hidden>
              <p class="notice-ok" id="payDoneText"></p>
              <dl class="detail" id="payDone"></dl>
            </div>
          </div>
        </section>
      </div>
    </section>
  </jsp:body>
</ui:layout>
