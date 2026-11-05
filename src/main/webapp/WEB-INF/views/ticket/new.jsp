<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  장애·문의 접수 화면 (/ticket/new). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="장애·문의 접수" crumb="IT 서비스" menu="ticket-new">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/ticket/it-service.js"></script>
    <script>openTicketNewView();</script>
  </jsp:attribute>
  <jsp:body>
    <%-- 접수 화면만의 모양 (넓은 작성 칸 + 좁은 안내 칸, 구분 버튼, 두 묶음) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/ticket/new.css">
    <section class="view on" id="view-ticket-new">
      <%-- 장애·문의 접수: 우리 회사 IT 담당자에게 간다 (ticket/it-service.js) --%>
      <div class="page-head"><h1>장애·문의 접수</h1></div>
      <div class="tk-write">
        <section class="info-card">
          <form class="tk-form" id="ticketForm" novalidate>
            <%-- 구분: 설명이 붙은 버튼으로 고른다. 값은 숨긴 select(ticketType)에 둔다 --%>
            <div class="tk-types" role="radiogroup" aria-label="구분">
              <button type="button" class="tk-type" role="radio" data-type="TK_INCIDENT" aria-checked="true">
                <strong><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 4 2.5 20h19L12 4z"/><path d="M12 10v4M12 17h.01"/></svg> 장애</strong>
                <small>고장, 작동 안 함, 접속 안 됨</small>
              </button>
              <button type="button" class="tk-type" role="radio" data-type="TK_QUESTION" aria-checked="false">
                <strong><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6M12 17h.01"/></svg> 문의</strong>
                <small>사용법, 설정, 요청</small>
              </button>
            </div>
            <select id="ticketType" hidden><option value="TK_INCIDENT">장애</option><option value="TK_QUESTION">문의</option></select>

            <div class="tk-group">
              <p class="tk-group-title">어디서 생겼나요</p>
              <div class="tk-row"><label for="ticketAsset">관련 자산</label><select class="filter-select" id="ticketAsset"></select></div>
              <div class="tk-row"><label for="ticketBranch">접수 지점</label><select class="filter-select" id="ticketBranch"></select></div>
              <div class="tk-row tk-row-sub"><span></span><p class="tk-hint" id="ticketBranchHint"></p></div>
            </div>

            <div class="tk-group tk-group-line">
              <p class="tk-group-title">무슨 일인가요</p>
              <div class="tk-row tk-row-top">
                <label for="ticketTitle">제목</label>
                <div><input class="input" id="ticketTitle" maxlength="60" autocomplete="off" placeholder="예: 노트북 화면이 켜지지 않아요"><p class="tk-count" id="ticketTitleCount">0 / 60</p></div>
              </div>
              <div class="tk-row tk-row-top">
                <label for="ticketBody">내용</label>
                <div><textarea class="inq-text tk-body" id="ticketBody" maxlength="1000" placeholder="언제부터, 어떤 증상인지, 오류 메시지가 있으면 그대로 적어 주세요"></textarea><p class="tk-count" id="ticketBodyCount">0 / 1000</p></div>
              </div>
            </div>

            <p class="form-error" id="ticketError" role="alert"></p>
            <div class="tk-actions">
              <%-- 무엇이 비었는지, 무엇을 접수하는지 한 줄 요약 --%>
              <p class="tk-summary" id="ticketSummary" aria-live="polite"></p>
              <button type="button" class="btn" id="ticketCancel">취소</button>
              <button type="submit" class="btn btn-primary" data-write>접수</button>
            </div>
          </form>
        </section>

        <aside class="tk-side">
          <%-- 진행 중인 내 티켓(접수·배정·처리 중): 같은 문제를 두 번 접수하지 않게 (GET /ticket/api/tickets, 내 티켓과 같은 자료) --%>
          <a class="info-card tk-open" id="ticketOpenCard" href="${ctx}/ticket/mine" hidden>
            <small>진행 중인 내 티켓</small>
            <strong><span id="ticketOpenCount">0</span><span class="unit">건</span></strong>
            <span class="tk-open-sub" id="ticketOpenSub">같은 문제면 새로 접수하지 말고 내 티켓에 댓글로 남겨 주세요</span>
          </a>
          <section class="info-card tk-guide">
            <h2><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg> 처리 순서</h2>
            <ol class="tk-steps"><li>접수</li><li>배정</li><li>처리 중</li><li>처리 완료</li><li>확인</li></ol>
            <ul class="tk-guide-list">
              <li>IT 담당자나 업체 관리자가 확인해 담당자를 정합니다.</li>
              <li>처리 완료되면 내 티켓에서 [확인]을 눌러 마무리합니다.</li>
              <li>접수 지점은 접수한 뒤 바뀌지 않습니다.</li>
            </ul>
          </section>
        </aside>
      </div>
    </section>

    <%-- 쓰던 내용이 있을 때 [취소]: 나갈지 묻는다 --%>
    <dialog class="modal" id="ticketLeaveDialog">
      <h2>작성 중인 내용이 사라집니다</h2>
      <p>접수하지 않고 나갈까요?</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">계속 쓰기</button>
        <button type="button" class="btn btn-primary" value="ok">나가기</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
