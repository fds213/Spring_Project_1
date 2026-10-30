<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  근태 단말기 화면 (/attendance/admin/device, 업체 관리자·인사 담당). 요구분석서 3.10, 기능명세서 1.9.
  업체의 지문 단말기 키를 발급·다시 발급하고(처음 발급할 때 단말기가 함께 등록된다), 직원을 단말기 사용자 번호에 연결한다.
  키 원문은 발급 직후 한 번만 보이고 DB에는 해시만 남는다. 동작은 resources/js/attendance/device-admin.js.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="근태 단말기" crumb="근무" menu="attendance-device">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/attendance/device-admin.js"></script>
    <script>openDeviceAdmin();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-attendance-device">
      <div class="page-head">
        <h1>근태 단말기</h1>
        <div class="page-actions"><a class="btn" href="${ctx}/attendance/simulator" target="_blank" rel="noopener">지문 시뮬레이터 열기 ↗</a></div>
      </div>
      <p class="muted join-scope">지문 단말기(시연용 시뮬레이터)가 업체를 알아보는 키와, 단말기에 등록된 사용자 번호와 직원의 연결을 관리합니다.</p>

      <div class="info-grid info-grid-2 table-gap">
        <%-- 두 카드 모두 평소에는 머리줄만 보이고, 버튼을 누르면 아래로 펼쳐진다(.dev-fold, device-admin.js 의 devFold).
             펼쳐진 동안은 머리줄 버튼 자리에 ^ 접기 버튼이 보인다 --%>
        <section class="info-card dev-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="15" r="4"/><path d="m10.8 12.2 9.2-9.2M17 6l3 3M14 9l2 2"/></svg></span>
            <div><h2>단말기 키</h2></div>
            <%-- 처음이면 단말기가 함께 등록되고, 이미 있으면 다시 발급한다(예전 키는 막힌다) --%>
            <button type="button" class="btn btn-primary dev-issue" id="btnIssueKey" aria-controls="devKeyBox" aria-expanded="false" data-write hidden>키 발급</button>
            <button type="button" class="icon-btn dev-collapse" id="btnKeyFold" aria-controls="devKeyBox" aria-expanded="true" aria-label="단말기 키 접기"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 15 6-6 6 6"/></svg></button>
          </header>
          <%-- 발급 직후에만 펼쳐진다. 다시 보여 줄 수 없으니 바로 복사해 두게 한다. 접으면 키 값도 지운다 --%>
          <div class="dev-fold" id="devKeyBox" inert>
            <div class="dev-fold-inner">
              <div class="dev-body">
                <div class="dev-key">
                  <p class="dev-key-label">새 단말기 키 (이 화면을 벗어나면 다시 볼 수 없습니다)</p>
                  <div class="input-action">
                    <input class="input" id="devKey" readonly aria-label="새 단말기 키">
                    <button type="button" class="btn" id="btnCopyKey">복사</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="info-card dev-card">
          <header class="info-card-head">
            <span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M17 8v6M14 11h6"/></svg></span>
            <div><h2>직원 연결</h2></div>
            <button type="button" class="btn btn-primary dev-issue" id="btnLinkOpen" aria-controls="devLinkBox" aria-expanded="false" data-write>연결</button>
            <button type="button" class="icon-btn dev-collapse" id="btnLinkFold" aria-controls="devLinkBox" aria-expanded="true" aria-label="직원 연결 접기"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 15 6-6 6 6"/></svg></button>
          </header>
          <div class="dev-fold" id="devLinkBox" inert>
            <div class="dev-fold-inner">
              <div class="dev-body">
                <form class="org-add" id="devLinkForm" novalidate>
                  <select class="input dev-employee" id="devEmployee" data-ui="select" aria-label="연결할 직원"></select>
                  <input class="input dev-no" id="devUserNo" inputmode="numeric" maxlength="4" placeholder="번호 예: 0006" aria-label="단말기 사용자 번호(숫자 4자리)" aria-describedby="devLinkError">
                  <button type="submit" class="btn btn-primary" id="btnLink" data-write>연결</button>
                </form>
                <p class="form-error" id="devLinkError" role="alert"></p>
              </div>
            </div>
          </div>
        </section>
      </div>

      <div class="table-wrap table-gap">
        <table class="table">
          <thead><tr><th>사용자 번호</th><th>사번</th><th>이름</th><th>부서</th><th>상태</th><th>관리</th></tr></thead>
          <tbody id="devPeople"></tbody>
        </table>
      </div>
    </section>
  </jsp:body>
</ui:layout>
