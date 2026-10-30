<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  소개 사이트: 2주 무료 체험 신청 (GET /trial, 로그인 없이 연다). 시안 trial.html 의 본문을 옮긴 것이다 (시안 탭 제목 "무료 체험 신청 - IEUM 그룹웨어").
  헤더·푸터·떠 있는 문의 버튼은 site.tag 가 넣는다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:site title="2주 무료 체험 신청" cta="false" mainClass="section" mainStyle="padding-top: 56px">
  <jsp:body>
      <div class="wrap" style="max-width: 760px">
        <div class="section-head" style="margin-bottom: 28px">
          <h2>2주 무료 체험 신청</h2>
          <p class="lead">결제 없이 바로 시작합니다. 신청하면 업체와 관리자 계정이 바로 만들어지고, 이메일 인증을 마치면 로그인할 수 있습니다. 관리자를 포함해 10명까지 써 볼 수 있고, 결제는 체험 중 언제든 그룹웨어 안에서 합니다. 결제하지 않으면 체험이 끝난 뒤 읽기 전용으로 바뀌며, 그때 결제해도 계정과 데이터는 그대로 이어집니다.</p>
        </div>

        <ol class="stepper" id="stepper">
          <li data-step="1">1. 정보 입력</li>
          <li data-step="2">2. 신청 확인</li>
          <li data-step="3">3. 이메일 인증</li>
        </ol>

        <%-- 1. 정보 입력 --%>
        <form class="box step" id="step1" novalidate>
          <h3>업체 정보</h3>
          <div class="form-grid">
            <div class="form-row"><label for="pCompany">업체명</label><input class="input" id="pCompany" autocomplete="organization"><span class="field-error"></span></div>
            <%-- 업체 코드는 입력받지 않는다: ieum + 임의 숫자로 자동 발급 (site/trial.js). 사번 칸처럼 잠긴 칸으로 미리 보여준다 --%>
            <div class="form-row"><label for="pCode">업체 코드</label><output class="input input-readonly" id="pCode" aria-describedby="pCodeHint"></output><span class="field-hint" id="pCodeHint">자동으로 정해지며, 사번과 함께 로그인할 때 씁니다</span><span class="field-error" id="pCodeError"></span></div>
            <%-- 무료 체험 양식이라 인원은 1~10명만 받는다 (체험 중 계정 한도 = 관리자 포함 10명). 유료 인원은 이용 결제 1단계에서 다시 정한다 --%>
            <div class="form-row"><label for="pUsers">이용 인원</label><input class="input" id="pUsers" type="number" min="1" max="10" value="10" inputmode="numeric" aria-describedby="pUsersHint"><span class="field-hint" id="pUsersHint">관리자 포함 최대 10명 (체험 중 계정 한도)</span><span class="field-error"></span></div>
            <div class="form-row"><label for="pEmail">담당자 이메일</label><input class="input" id="pEmail" type="email" autocomplete="email" placeholder="name@company.com" aria-describedby="pEmailHint"><span class="field-hint" id="pEmailHint">이 주소로 인증 메일이 갑니다. 인증을 마쳐야 로그인할 수 있습니다</span><span class="field-error"></span></div>
          </div>
          <h3 style="margin-top: 24px">최초 관리자 계정</h3>
          <p class="note" style="margin-top: -8px">체험이 시작되면 이 계정으로 로그인하고, 직원들이 로그인 화면의 계정 생성으로 요청한 계정을 승인합니다(관리자 포함 10명까지).</p>
          <div class="form-grid">
            <div class="form-row"><label for="pAdminName">관리자 이름</label><input class="input" id="pAdminName" autocomplete="name"><span class="field-error"></span></div>
            <%-- 사번은 입력받지 않는다: 가입 연도 + 0001로 자동으로 정해진다 (site/trial.js) --%>
            <div class="form-row"><label for="pAdminNo">관리자 사번</label><output class="input input-readonly" id="pAdminNo" aria-describedby="pAdminNoHint"></output><span class="field-hint" id="pAdminNoHint">자동으로 정해지며, 업체 코드와 함께 로그인할 때 씁니다</span></div>
            <div class="form-row"><label for="pPw">비밀번호</label><input class="input" id="pPw" type="password" autocomplete="new-password" placeholder="8자 이상"><span class="field-error"></span></div>
            <div class="form-row"><label for="pPw2">비밀번호 확인</label><input class="input" id="pPw2" type="password" autocomplete="new-password"><span class="field-error"></span></div>
          </div>
          <div class="actions"><a class="btn btn-lg" href="${ctx}/#pricing">가격 다시 보기</a><button class="btn btn-primary btn-lg" type="submit">다음: 신청 확인</button></div>
        </form>

        <%-- 2. 신청 확인 --%>
        <form class="box step" id="step2" novalidate hidden>
          <h3>신청 내용</h3>
          <table class="summary">
            <tr><th>업체명</th><td id="sCompany"></td></tr>
            <tr><th>업체 코드</th><td id="sCode"></td></tr>
            <tr><th>최초 관리자</th><td id="sAdmin"></td></tr>
            <tr><th>담당자 이메일</th><td id="sEmail"></td></tr>
            <tr><th>이용 인원</th><td id="sUsers"></td></tr>
            <tr><th>무료 체험 기간</th><td id="sTrial"></td></tr>
            <tr class="sum"><th>체험 중 요금</th><td>무료</td></tr>
          </table>
          <p class="note">결제는 체험 중 그룹웨어의 업체 관리 &gt; 이용 결제에서 합니다. 그때 유료로 쓸 인원과 월간·연간을 고르고(1인 월 4,000원, 첫 연간 10% 할인), 결제한 날부터 바로 유료로 바뀌고 10명 제한이 풀립니다. 자동 결제는 없고 만료 전에 직접 연장합니다.</p>
          <div class="form-row" style="margin-top: 16px"><label class="check"><input type="checkbox" id="pAgree"> 시연용 서비스 이용 조건에 동의합니다.</label><span class="field-error"></span></div>
          <div class="actions"><button class="btn btn-lg" type="button" data-go="1">이전</button><button class="btn btn-primary btn-lg" type="submit">무료 체험 시작</button></div>
        </form>

        <%-- 3. 결과: 신청 완료 + 이메일 인증 (요구분석서 v1.4 ACC-02·06). trial.html?verify=업체코드 로 다시 열 수 있다 --%>
        <div class="box step" id="step3" hidden aria-live="polite"></div>
      </div>
  </jsp:body>
</ui:site>
