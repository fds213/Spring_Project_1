<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  기안 작성 화면 (/approval/write). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버 주소와 주고받는 값은 docs/front-screen-api.md(프론트 제안), 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="기안 작성" crumb="전자결재" menu="approval-write">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/approval/approval.js"></script>
    <script>openApprovalWriteView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-approval-write">
      <%-- 기안 작성: 휴가 신청서·장비 지급 신청서. 결재선은 최대 2단계(순서대로). 임시저장은 이어서 쓰고, 반려·회수된 문서는 결재함에서 재기안한다 (approval/approval.js) --%>
      <div class="page-head"><h1>기안 작성</h1></div>
      <div class="info-grid info-grid-2">
        <section class="info-card">
          <header class="info-card-head"><span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg></span><div><h2><span id="eaFormTitle">휴가 신청서</span></h2><p>양식을 고르고 내용을 적은 뒤 결재선을 정합니다</p></div></header>
          <form class="leave-form" id="eaForm" novalidate>
            <div class="leave-row">
              <label for="eaFormKind">양식</label>
              <select class="filter-select" id="eaFormKind"><option value="LEAVE">휴가 신청서</option><option value="EQUIP">장비 지급 신청서</option></select>
            </div>
            <div id="eaLeaveFields">
              <div class="leave-row">
                <label for="eaLeaveKind">구분</label>
                <select class="filter-select" id="eaLeaveKind"><option value="FULL">연차 (종일)</option><option value="AM">오전 반차</option><option value="PM">오후 반차</option></select>
              </div>
              <div class="leave-row"><label for="eaLeaveFrom">시작일</label><input class="input" id="eaLeaveFrom" type="date"></div>
              <div class="leave-row"><label for="eaLeaveTo">종료일</label><input class="input" id="eaLeaveTo" type="date"></div>
              <div class="leave-row"><label for="eaLeaveReason">사유</label><input class="input" id="eaLeaveReason" maxlength="100" autocomplete="off" placeholder="선택"></div>
            </div>
            <div id="eaEquipFields" hidden>
              <div class="leave-row"><label for="eaEquipKind">장비</label><select class="filter-select" id="eaEquipKind"></select></div>
              <div class="leave-row"><label for="eaEquipQty">수량</label><input class="input" id="eaEquipQty" type="number" min="1" max="10" value="1" inputmode="numeric"></div>
              <div class="leave-row"><label for="eaEquipBranch">수령 지점</label><select class="filter-select" id="eaEquipBranch"></select></div>
              <div class="leave-row"><label for="eaEquipReason">사유</label><textarea class="inq-text" id="eaEquipReason" maxlength="200"></textarea></div>
            </div>
            <div class="leave-row"><label for="eaAppr1">1단계 결재</label><select class="filter-select" id="eaAppr1"></select></div>
            <div class="leave-row"><label for="eaAppr2">2단계 결재</label><select class="filter-select" id="eaAppr2"></select></div>
            <p class="form-error" id="eaError" role="alert"></p>
            <div class="bill-actions">
              <button type="button" class="btn" id="btnEaDraft" data-write>임시저장</button>
              <button type="submit" class="btn btn-primary" data-write>제출</button>
            </div>
            <p class="bill-note">결재선은 최대 2단계이고 순서대로 승인합니다. 결재자는 부서장·인사 담당·업체 관리자입니다.</p>
          </form>
        </section>
        <section class="info-card">
          <header class="info-card-head"><span class="ic" aria-hidden="true"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg></span><div><h2>안내</h2></div></header>
          <dl class="detail" id="eaGuide"></dl>
        </section>
      </div>
    </section>
  </jsp:body>
</ui:layout>
