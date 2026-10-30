<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  내 장비 화면 (/asset/mine). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버 주소와 주고받는 값은 docs/front-screen-api.md(프론트 제안), 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="내 장비" crumb="IT 서비스" menu="asset-mine">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ticket/it-service.js"></script>
    <script>openAssetMineView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-asset-mine">
      <%-- 내 장비: 나에게 지급된 장비와 지급을 기다리는 신청 (ticket/it-service.js) --%>
      <div class="page-head"><h1>내 장비</h1><button type="button" class="btn btn-primary" id="btnAssetRequest" data-write>장비 지급 신청</button></div>
      <div class="cards">
        <div class="card"><small>사용 중인 장비</small><strong id="assetCntUse">0개</strong></div>
        <div class="card"><small>지급 대기</small><strong id="assetCntWait">0건</strong></div>
      </div>
      <section class="info-card table-gap">
        <header class="info-card-head"><span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg></span><div><h2>내 장비</h2><p>나에게 지급된 장비입니다</p></div></header>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>장비</th><th>일련번호</th><th>배치 지점</th><th>지급일</th><th>지급 티켓</th></tr></thead>
          <tbody id="assetList"></tbody>
        </table></div>
      </section>
      <section class="info-card table-gap">
        <header class="info-card-head"><span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg></span><div><h2>지급 대기 중인 신청</h2><p>결재가 끝나 IT 담당자의 지급을 기다리는 신청입니다</p></div></header>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>티켓</th><th>신청 장비</th><th>수령 지점</th><th>상태</th></tr></thead>
          <tbody id="assetWaitList"></tbody>
        </table></div>
      </section>
    </section>
  </jsp:body>
</ui:layout>
