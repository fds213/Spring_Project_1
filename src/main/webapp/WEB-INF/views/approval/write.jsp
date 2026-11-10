<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  기안 작성 화면 (/approval/write). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
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
    <%-- 기안 작성 화면만의 모양 (넓은 작성 칸 + 좁은 안내 칸, 양식 탭, 구분 버튼, 결재선 한 줄) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/approval/write.css">
    <section class="view on" id="view-approval-write">
      <%-- 기안 작성: 휴가 신청서·장비 지급 신청서. 결재선은 최대 2단계(순서대로). 반려된 문서는 결재함에서 재기안하고, 쓰다 만 문서는 임시저장해 결재함에서 이어 쓴다 (approval/approval.js) --%>
      <div class="page-head"><h1>기안 작성</h1></div>
      <div class="ea-write">
        <section class="info-card">
          <%-- 양식: 카드 머리줄의 탭으로 고른다. 값은 숨긴 select(eaFormKind)에 둔다 --%>
          <div class="ea-tabs" role="tablist" aria-label="양식">
            <button type="button" class="ea-tab" role="tab" data-form="DOC_LEAVE" aria-selected="true">휴가 신청서</button>
            <button type="button" class="ea-tab" role="tab" data-form="DOC_EQUIPMENT" aria-selected="false">장비 지급 신청서</button>
          </div>
          <form class="ea-form" id="eaForm" novalidate>
            <select id="eaFormKind" hidden><option value="DOC_LEAVE">휴가 신청서</option><option value="DOC_EQUIPMENT">장비 지급 신청서</option></select>

            <div class="ea-group" id="eaLeaveFields">
              <p class="ea-group-title">휴가 내용</p>
              <div class="ea-row">
                <span class="ea-label" id="eaLeaveKindLabel">구분</span>
                <div class="ea-seg" id="eaLeaveSeg" role="radiogroup" aria-labelledby="eaLeaveKindLabel">
                  <button type="button" role="radio" data-kind="LV_FULL" aria-checked="true">연차 (종일)</button>
                  <button type="button" role="radio" data-kind="LV_AM" aria-checked="false">오전 반차</button>
                  <button type="button" role="radio" data-kind="LV_PM" aria-checked="false">오후 반차</button>
                </div>
                <select id="eaLeaveKind" hidden><option value="LV_FULL">연차 (종일)</option><option value="LV_AM">오전 반차</option><option value="LV_PM">오후 반차</option></select>
              </div>
              <div class="ea-row">
                <label for="eaLeaveFrom">기간</label>
                <div class="ea-range">
                  <input class="input" id="eaLeaveFrom" type="date" aria-label="시작일">
                  <span aria-hidden="true">~</span>
                  <input class="input" id="eaLeaveTo" type="date" aria-label="종료일">
                </div>
              </div>
              <div class="ea-row ea-row-sub"><span></span><p class="ea-days" id="eaDays" aria-live="polite"></p></div>
              <div class="ea-row"><label for="eaLeaveReason">사유</label><input class="input" id="eaLeaveReason" maxlength="100" autocomplete="off" placeholder="예: 가족 행사"></div>
            </div>

            <div class="ea-group" id="eaEquipFields" hidden>
              <p class="ea-group-title">장비 내용</p>
              <div class="ea-row"><label for="eaEquipKind">장비</label><select class="filter-select" id="eaEquipKind"></select></div>
              <div class="ea-row"><label for="eaEquipQty">수량</label><input class="input ea-qty" id="eaEquipQty" type="number" min="1" max="10" value="1" inputmode="numeric"></div>
              <div class="ea-row"><label for="eaEquipBranch">수령 지점</label><select class="filter-select" id="eaEquipBranch"></select></div>
              <div class="ea-row ea-row-sub" id="eaBranchHintRow" hidden><span></span><p class="ea-hint is-warn" id="eaBranchHint">등록된 지점이 없습니다. 업체 관리자가 지점 관리에서 지점을 먼저 등록해야 합니다.</p></div>
              <div class="ea-row ea-row-top">
                <label for="eaEquipReason">사유</label>
                <div><textarea class="inq-text" id="eaEquipReason" maxlength="200" placeholder="예: 신규 입사자 업무용 노트북"></textarea><p class="ea-count" id="eaEquipCount">0 / 200</p></div>
              </div>
            </div>

            <div class="ea-group">
              <p class="ea-group-title">첨부 <span>· 선택, 5개까지 (jpg·png·gif·pdf·docx·xlsx·pptx·hwp·hwpx, 파일당 10MB)</span></p>
              <input type="file" id="eaFiles" multiple accept=".jpg,.jpeg,.png,.gif,.pdf,.docx,.xlsx,.pptx,.hwp,.hwpx" data-write>
              <ul id="eaFileList"></ul>
              <p class="form-error" id="eaFileError" role="alert"></p>
            </div>
            
            <div class="ea-group ea-line-group">
              <p class="ea-group-title">결재선 <span>· 순서대로 승인하고, 최대 2단계입니다</span></p>
              <div class="ea-line">
                <span class="ea-me"><span class="ea-me-dot" aria-hidden="true">나</span>기안자</span>
                <span class="ea-arrow" aria-hidden="true">→</span>
                <select class="filter-select" id="eaAppr1" aria-label="1단계 결재자"></select>
                <span class="ea-arrow" aria-hidden="true">→</span>
                <select class="filter-select" id="eaAppr2" aria-label="2단계 결재자 (선택)"></select>
              </div>
              <p class="ea-hint" id="eaApprHint">같은 업체의 재직 중인 직원 중에서 고릅니다. 결재자는 이 문서만 승인·반려할 수 있습니다.</p>
            </div>

            <p class="form-error" id="eaError" role="alert"></p>
            <div class="ea-actions">
              <%-- 무엇을 제출하는지 한 줄 요약 (채울수록 문장이 완성된다) --%>
              <p class="ea-summary" id="eaSummary" aria-live="polite"></p>
              <button type="button" class="btn" id="eaCancel">취소</button>
              <button type="button" class="btn" id="eaDraftSave" data-write>임시저장</button>
              <button type="submit" class="btn btn-primary" data-write>제출</button>
            </div>
          </form>
        </section>

        <aside class="ea-side">
          <%-- 남은 연차: 휴가 신청서일 때만 보인다 --%>
          <section class="info-card ea-left" id="eaLeftCard">
            <small>남은 연차</small>
            <strong><span id="eaLeft">-</span><span class="unit">일</span></strong>
            <span class="ea-left-sub" id="eaLeftSub"></span>
          </section>
          <section class="info-card ea-guide">
            <h2><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg> 알아 두세요</h2>
            <ul class="ea-guide-list" id="eaGuide"></ul>
          </section>
        </aside>
      </div>
    </section>

    <%-- 제출 확인: 제출하면 회수할 수 없어 한 번 묻는다 --%>
    <dialog class="modal" id="eaSubmitDialog">
      <h2 id="eaSubmitTitle">제출할까요?</h2>
      <p id="eaSubmitText">제출하면 첫 결재자에게 넘어가고, 고치거나 회수할 수 없습니다.</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">취소</button>
        <button type="button" class="btn btn-primary" value="ok">제출</button>
      </div>
    </dialog>

    <%-- 쓰던 내용이 있을 때 [취소]: 나갈지 묻는다 --%>
    <dialog class="modal" id="eaLeaveDialog">
      <h2>작성 중인 내용이 사라집니다</h2>
      <p>제출하지 않고 나갈까요?</p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">계속 쓰기</button>
        <button type="button" class="btn btn-primary" value="ok">나가기</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
