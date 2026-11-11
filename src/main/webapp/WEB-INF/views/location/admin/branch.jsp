<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  지점 관리 화면 (/location/admin/branch, 업체 관리자만). 요구분석서 3.4 지점, 범위 표 '핵심 뒤 추가'.
  - 여기 지점이 장비 신청의 수령 지점과 장애·문의 접수의 지점 목록에 나온다(BranchService.getBranches).
  - 사용 중지한 지점은 새로 고르는 목록에서만 빠지고, 이미 지급한 자산·접수한 티켓에는 그대로 남는다.
  - 미지급 장비 요청이 남은 지점은 서버가 중지를 거절한다(409). 다시 사용으로 되돌리는 API는 없다.
  - 동작은 resources/js/location/branch-admin.js (서버 주소·응답은 그 파일 맨 위)
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="지점 관리" crumb="업체 관리" menu="admin-branch">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/location/branch-admin.js"></script>
    <script>openBranchAdmin();</script>
  </jsp:attribute>
  <jsp:body>
    <link rel="stylesheet" href="${ctx}/resources/css/location/branch.css">
    <section class="view on" id="view-admin-branch">
      <div class="page-head">
        <h1>지점 관리</h1>
        <div class="page-actions"><button type="button" class="btn btn-primary" id="brAddBtn" data-write>지점 추가</button></div>
      </div>
      <p class="muted join-scope">
        여기 지점이 장비 신청의 수령 지점과 장애·문의 접수의 지점 목록에 나옵니다.
        사용 중지한 지점은 새로 고르는 목록에서만 빠지고, 이미 지급한 장비와 접수한 티켓에는 그대로 남습니다.
      </p>
      <p class="muted br-count" id="brCount" aria-live="polite"></p>

      <div class="table-wrap">
        <table class="table br-table">
          <thead><tr><th>지점</th><th>주소</th><th>상태</th><th>관리</th></tr></thead>
          <tbody id="brList"></tbody>
        </table>
      </div>
    </section>

    <%-- 지점 추가·수정: 같은 창을 쓴다. [수정]으로 열면 제목과 저장 버튼 글자가 바뀌고 지금 값이 채워진다.
         글자 수(지점명 100·주소 500·상세 주소 200자)는 branch-admin.js 가 서버처럼 문자 단위로 센다.
         maxlength 는 이모지를 2자로 세어 정상 입력을 막으므로 두지 않는다 --%>
    <dialog class="modal br-dialog" id="brDialog" aria-labelledby="brDialogTitle">
      <h2 id="brDialogTitle">지점 추가</h2>
      <form id="brForm" novalidate>
        <div class="field">
          <label for="brName">지점명</label>
          <input class="input" id="brName" autocomplete="off" placeholder="예: 강남 본사" aria-describedby="brError" required>
        </div>
        <div class="field">
          <label for="brAddress">주소</label>
          <input class="input" id="brAddress" autocomplete="off" placeholder="예: 서울특별시 강남구 테헤란로 123" aria-describedby="brError" required>
        </div>
        <div class="field">
          <label for="brDetail">상세 주소 <span class="muted">(선택)</span></label>
          <input class="input" id="brDetail" autocomplete="off" placeholder="예: 5층" aria-describedby="brError">
        </div>
        <p class="form-error" id="brError" role="alert"></p>
        <div class="modal-actions">
          <button type="button" class="btn" id="brCancel">취소</button>
          <button type="submit" class="btn btn-primary" id="brSubmit" data-write>추가</button>
        </div>
      </form>
    </dialog>

    <%-- 사용 중지 확인과, 서버가 중지를 거절한 이유(미지급 장비 요청 등)를 보여 주는 창. 이유만 보일 때는 [확인] 하나 --%>
    <dialog class="modal" id="brAskDialog" aria-labelledby="brAskTitle">
      <h2 id="brAskTitle"></h2>
      <p id="brAskBody"></p>
      <div class="modal-actions">
        <button type="button" class="btn" value="cancel">취소</button>
        <button type="button" class="btn btn-danger-line" value="ok">사용 중지</button>
      </div>
    </dialog>
  </jsp:body>
</ui:layout>
