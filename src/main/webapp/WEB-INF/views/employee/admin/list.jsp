<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  사원 관리 화면 (/employee/admin/list). 시안 views/employee/admin/list.html 를 옮긴 것이다 (이 주소를 받는 Controller는 모듈 담당이 만든다: docs/front-screen-api.md 1장).
  화면 JS도 시안 것을 그대로 쓴다: 서버 주소와 주고받는 값은 시안 API.md, 감싼 응답({ success, message, data })은 common.js 의 api 가 푼다.
  화면 JS는 util.js(같이 쓰는 도구)와 이 화면 모듈의 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="사원 관리" crumb="인사 관리" menu="hr-employee">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/employee/employee.js"></script>
    <script>renderEmployees();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-hr-employee">
      <%-- 사원 관리: 업체 관리자(또는 인사 담당자)가 직원 계정의 상태를 본다. 계정은 직원이 계정 생성에서 요청해 승인으로 생기고,
           체험 중에는 관리자 포함 10명, 결제하면 구매 인원까지다. 실제 한도는 서버가 계정 수를 세서 막는다 --%>
      <div class="page-head"><h1>사원 관리</h1></div>
      <%-- 사원 등록은 없다: 직원은 로그인 화면의 계정 생성로 승인을 요청하고, 승인되면 사번이 순서대로 붙는다 --%>
      <p class="muted join-scope">직원은 로그인 화면의 "계정 생성"로 승인을 요청합니다. 가입 승인에서 승인하면 사번이 순서대로 붙고, 직원이 비밀번호와 이메일 인증을 마치면 계정이 만들어집니다.</p>
      <div class="cards">
        <div class="card"><small>전체 계정</small><strong id="empTotal">-</strong></div>
        <div class="card"><small>사용 중</small><strong id="empActive">-</strong></div>
        <div class="card"><small>계정 생성 전</small><strong id="empPending">-</strong></div>
        <div class="card"><small>계정 한도</small><strong id="empLimit">-</strong></div>
      </div>

      <%-- 사용 중인 계정 수: 체험 중이면 10명 중, 결제 뒤에는 구매 인원 중 몇 명을 쓰는지 --%>
      <div class="seat-bar table-gap" id="seatBar" hidden>
        <div class="seat-head">
          <strong><span id="seatTitle">체험 중 계정</span> <span id="seatCount"></span></strong>
          <a href="${ctx}/subscription/billing" class="btn btn-primary" id="seatPay">결제하고 제한 풀기</a>
        </div>
        <div class="seat-meter" id="seatMeter" role="meter" aria-label="체험 중 계정 사용" aria-valuemin="0"><span id="seatFill"></span></div>
        <p id="seatText"></p>
      </div>

      <div class="table-wrap table-gap">
        <table class="table">
          <thead>
            <tr><th>이름</th><th>부서·직급</th><th>계정 형태</th><th>인사 정보</th><th>상태</th><th>등록일</th><th>관리</th></tr>
          </thead>
          <tbody id="empList"></tbody>
        </table>
      </div>
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
