<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  신청 현황 화면 (/ops/application-status). 시안 views/ops/application.html 를 옮긴 것이다 (이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장).
  화면 JS도 시안 것을 그대로 쓴다: 서버 주소와 주고받는 값은 시안 API.md, 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="신청 현황" crumb="신청" menu="ops-application-status">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ops/application.js"></script>
    <script>openApplicationView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-ops-application-status">
      <div class="page-head"><h1>신청 현황</h1></div>
      <%-- 카드를 누르면 아래 표가 그 조건으로 걸러진다 --%>
      <div class="cards" id="appFilters">
        <button type="button" class="card card-filter" data-filter="all" aria-pressed="true"><small>전체 신청</small><strong id="cntAll">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="new" aria-pressed="false"><small>새 신청</small><strong id="cntNew">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="TRIAL" aria-pressed="false"><small>체험 중</small><strong id="cntTrial">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="PAID" aria-pressed="false"><small>결제 완료</small><strong id="cntPaid">0건</strong></button>
        <button type="button" class="card card-filter" data-filter="EXPIRED" aria-pressed="false"><small>읽기 전용</small><strong id="cntExpired">0건</strong></button>
      </div>
      <div class="table-wrap table-gap">
        <table class="table table-fixed">
          <%-- 열 너비 고정: 걸러진 행이 없어도 제목 줄 위치가 그대로다 --%>
          <colgroup><col style="width: 14%"><col style="width: 26%"><col style="width: 10%"><col style="width: 14%"><col style="width: 18%"><col style="width: 18%"></colgroup>
          <thead>
            <tr><th>신청 시각</th><th>업체</th><th class="num">인원</th><th class="num">월 이용료</th><th>관리자</th><th>상태</th></tr>
          </thead>
          <tbody id="appList"></tbody>
        </table>
      </div>


      <%-- 신청 상세: 신청 현황 표에서 행을 누르면 그 행 바로 아래에 펼쳐진다.
           구매 페이지 입력 순서대로 카드 3개(업체 정보 → 최초 관리자 계정 → 결제 정보), 1200px 이하에서는 한 줄로 쌓인다 --%>
      <template id="appDetailTpl">
        <div class="info-grid">
          <section class="info-card">
            <header class="info-card-head">
              <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v16"/><path d="M15 10h4a1 1 0 0 1 1 1v10"/><path d="M8 8h3M8 12h3M8 16h3M3 21h18"/></svg></span>
              <h2>업체 정보</h2>
            </header>
            <dl class="detail" data-part="company"></dl>
          </section>
          <section class="info-card">
            <header class="info-card-head">
              <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 20a8 8 0 0 1 16 0"/></svg></span>
              <div>
                <h2>최초 관리자 계정</h2>
                <p>신청할 때 바로 만들어진 계정입니다 (운영자 발급 없음, 요구분석서 v1.4)</p>
              </div>
            </header>
            <dl class="detail" data-part="admin"></dl>
          </section>
          <section class="info-card">
            <header class="info-card-head">
              <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/></svg></span>
              <h2>결제 정보</h2>
            </header>
            <dl class="detail" data-part="pay"></dl>
          </section>
        </div>
      </template>
    </section>
  </jsp:body>
</ui:layout>
