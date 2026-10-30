<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  가입 승인 화면 (/employee/join). 이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장.
  서버가 감싸 보낸 응답({ success, message, data })은 common.js 의 api 가 푼다(docs/front-screen-api.md 2장).
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="가입 승인" crumb="인사 관리" menu="hr-join">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/employee/employee.js"></script>
    <script>renderJoin();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-hr-join">
      <%-- 가입 승인: 업체 관리자는 모든 요청, 부서장은 자기 부서 요청. 승인하면 바로 계정이 만들어진다 (employee/employee.js) --%>
      <div class="page-head"><h1>가입 승인 <span class="page-code" id="joinCompanyCode"></span></h1></div>
      <p class="muted join-scope" id="joinScope"></p>
      <%-- 가입 활성화: 켜 둔 동안만 직원이 계정 생성에서 업체 코드를 확인할 수 있다. 부서장은 자기 부서를 바로 열고,
           업체 관리자는 버튼을 누르면 아래에 부서 목록이 펼쳐져 고른 부서만 연다. 고른 시간이 지나면 저절로 꺼지고,
           꺼져 있으면 업체 코드를 넣어도 아무 반응이 없다 --%>
      <section class="join-open" id="joinOpenBox">
        <div class="join-open-head">
          <div>
            <h2>가입 활성화</h2>
            <p class="join-note" id="joinOpenNote"></p>
          </div>
          <div class="join-open-actions">
            <select class="input" id="joinOpenTime" data-ui="select" aria-label="켜 둘 시간">
              <option value="10">10분</option>
              <option value="30">30분</option>
              <option value="60" selected>1시간</option>
              <option value="180">3시간</option>
              <option value="1440">하루</option>
            </select>
            <button type="button" class="btn btn-primary" id="btnJoinOpen" data-write>가입 활성화</button>
          </div>
        </div>
        <%-- 업체 관리자만: 가입 활성화를 누르면 펼쳐지는 부서 목록 --%>
        <div class="join-open-pick" id="joinOpenPick" hidden>
          <p class="join-open-pick-title">열 부서를 고르세요</p>
          <div class="join-open-checks" id="joinOpenChecks"></div>
          <div class="join-open-pick-actions">
            <button type="button" class="btn" id="btnJoinPickAll">전체 선택</button>
            <button type="button" class="btn" id="btnJoinPickCancel">취소</button>
            <button type="button" class="btn btn-primary" id="btnJoinPickOk" data-write disabled>선택한 부서 열기</button>
          </div>
        </div>
        <ul class="join-open-list" id="joinOpenList" hidden></ul>
      </section>
      <div class="cards">
        <div class="card"><small>승인 대기</small><strong id="joinOpen">-</strong></div>
        <div class="card"><small>사용 중</small><strong id="joinActive">-</strong></div>
        <div class="card"><small>더 승인할 수 있음</small><strong id="joinRoom">-</strong></div>
      </div>
      <p class="join-note table-gap" id="joinNote"></p>

      <h2>가입 요청</h2>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr><th>이름</th><th>부서·직급</th><th>이메일</th><th>요청 시각</th><th>처리</th></tr>
          </thead>
          <tbody id="joinList"></tbody>
        </table>
      </div>
      <p class="empty-note" id="joinEmpty" hidden>처리할 가입 요청이 없습니다.</p>

    </section>

    <%-- 사원 관리·가입 승인이 같이 쓰는 창(계정 상태 변경, 자리 없음). JSP에서는 두 화면 JSP 끝에 각각 넣는다 --%>
    <%-- 사원 관리: 직원 계정 비활성화·다시 사용 확인 (hr.js가 제목·본문·버튼 글자를 채운다) --%>
    <dialog class="modal" id="empStateDialog">
      <h2></h2>
      <p></p>
      <div class="modal-actions"><button type="button" class="btn" value="cancel">취소</button><button type="button" class="btn btn-primary" value="ok"></button></div>
    </dialog>

    <%-- 사원 관리·가입 승인: 자리가 다 찼는데 승인하거나 활성화하려고 할 때 --%>
    <dialog class="modal" id="seatDialog">
      <h2>계정을 더 만들 수 없습니다</h2>
      <p></p>
      <div class="modal-actions"><button type="button" class="btn" value="close">닫기</button><button type="button" class="btn btn-primary" value="pay">결제하러 가기</button></div>
    </dialog>
  </jsp:body>
</ui:layout>
