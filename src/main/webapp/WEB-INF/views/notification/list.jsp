<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  알림 목록 화면 (/notification/list, 모든 직원. 헤더의 종 아이콘). 요구분석서 3.4 업무 알림, 통합 계획서 4장 업무 알림.
  결재 요청·결과, 티켓 배정·처리 결과 알림을 본인 것만 보고, 누르면 읽음으로 바꾸고 그 화면으로 간다.
  이 주소를 받는 Controller 는 notification 모듈 담당이 만든다. 동작은 resources/js/notification/notification.js.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="알림" crumb="홈" menu="notification">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/notification/notification.js"></script>
    <script>openNotificationView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-notification">
      <div class="page-head">
        <h1>알림</h1>
        <div class="page-actions"><button type="button" class="btn" id="btnReadAll" data-write disabled>모두 읽음</button></div>
      </div>

      <div class="noti-tabs" role="tablist" aria-label="알림 거르기">
        <button type="button" class="noti-tab" role="tab" data-filter="ALL" aria-selected="true">전체</button>
        <button type="button" class="noti-tab" role="tab" data-filter="UNREAD" aria-selected="false">안 읽음 <span class="noti-count" id="notiUnread">0</span></button>
      </div>

      <section class="info-card noti-card">
        <ul class="noti-list" id="notiList" aria-live="polite"></ul>
        <div class="noti-more"><button type="button" class="btn" id="btnNotiMore" hidden>더 보기</button></div>
      </section>
    </section>
  </jsp:body>
</ui:layout>
