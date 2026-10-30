<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  소개 사이트: 도입 문의 (GET /inquiry, 로그인 없이 연다).
  헤더·푸터·떠 있는 문의 버튼은 site.tag 가 넣는다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:site title="도입 문의" current="inquiry" mainClass="inq-page">
  <jsp:body>
      <%-- 입력: 위에 제목·안내, 아래에 폼 (한 줄 구성) --%>
      <div class="wrap inq-layout" id="inqLayout">
        <div class="inq-head">
          <h1>이음 도입 문의</h1>
          <p class="inq-lead">이음 도입과 관련하여 궁금한 점을 남겨 주시면, 담당자가 확인 후 이메일로 답변 드립니다.</p>
        </div>

        <dl class="inq-strip">
          <div><dt>답변 안내</dt><dd>영업일 기준 1~2일 이내에 입력하신 이메일로 회신합니다.</dd></div>
          <%-- 소개 페이지의 "무료 체험 시작" 버튼과 같은 주소 --%>
          <div><dt>무료 체험</dt><dd>14일 동안 무료로 사용해 볼 수 있습니다. <a href="${ctx}/trial">무료 체험 신청 →</a></dd></div>
          <div><dt>이용 중인 고객</dt><dd>그룹웨어의 IT 서비스 메뉴나 회사 관리자에게 문의해 주세요.</dd></div>
        </dl>

        <section class="inq-panel" aria-label="문의 입력">
          <form id="inqForm" novalidate>
            <p class="req-note"><span class="req" aria-hidden="true">*</span> 표시는 필수 입력 항목입니다.</p>
            <div class="form-grid">
              <div class="form-row">
                <label for="companyName">회사명 <span class="req" aria-hidden="true">*</span></label>
                <input class="input" id="companyName" name="companyName" autocomplete="organization" placeholder="회사명을 입력해 주세요" maxlength="50" required>
                <span class="field-error"></span>
              </div>
              <div class="form-row">
                <label for="name">이름 <span class="req" aria-hidden="true">*</span></label>
                <input class="input" id="name" name="name" autocomplete="name" placeholder="이름을 입력해 주세요" maxlength="20" required>
                <span class="field-error"></span>
              </div>
              <div class="form-row">
                <label for="email">이메일 <span class="req" aria-hidden="true">*</span></label>
                <input class="input" id="email" name="email" type="email" autocomplete="email" placeholder="name@company.com" maxlength="100" required>
                <span class="field-hint">답변은 이 주소로만 보내 드립니다.</span>
                <span class="field-error"></span>
              </div>
              <div class="form-row">
                <label for="phone">휴대전화 <span class="opt">(선택)</span></label>
                <input class="input" id="phone" name="phone" type="tel" inputmode="numeric" autocomplete="tel" placeholder="010-0000-0000" maxlength="13">
                <span class="field-error"></span>
              </div>
              <div class="form-row full">
                <label for="title">문의 제목 <span class="req" aria-hidden="true">*</span></label>
                <input class="input" id="title" name="title" placeholder="제목을 입력해 주세요" maxlength="50" required>
                <span class="field-error"></span>
              </div>
              <div class="form-row full">
                <label for="content">문의 내용 <span class="req" aria-hidden="true">*</span></label>
                <textarea class="textarea" id="content" name="content" maxlength="1000" placeholder="문의 내용을 입력해 주세요" required></textarea>
                <div class="field-foot">
                  <span class="field-error"></span>
                  <span class="counter" id="counter">0 / 1,000자</span>
                </div>
              </div>
            </div>

            <%-- 봇 막기: 사람에게는 안 보이는 칸. 값이 들어오면 서버가 저장하지 않는다 (reCAPTCHA 대신) --%>
            <div class="hp" aria-hidden="true">
              <label for="website">웹사이트</label>
              <input id="website" name="website" tabindex="-1" autocomplete="off">
            </div>

            <div class="inq-agree form-row">
              <div class="agree-line">
                <label class="check"><input type="checkbox" id="agree" name="agree" required> <span>[필수] 개인정보 수집 및 이용에 동의합니다.</span></label>
                <button type="button" class="link-btn" id="btnTerms" aria-expanded="false" aria-controls="terms">내용 보기</button>
              </div>
              <table class="terms" id="terms" hidden>
                <tr><th>수집 항목</th><td>회사명, 이름, 이메일, 휴대전화</td></tr>
                <tr><th>이용 목적</th><td>도입 문의 확인 및 이메일 답변</td></tr>
                <tr><th>보유 기간</th><td>답변 완료 후 1년 (기간이 지나면 파기합니다)</td></tr>
              </table>
              <p class="agree-note">동의하지 않으실 수 있으나, 동의하지 않으면 문의를 접수할 수 없습니다.</p>
              <span class="field-error"></span>
            </div>

            <div class="inq-submit">
              <button class="btn btn-primary btn-lg" type="submit" id="btnSubmit">문의 접수</button>
            </div>
          </form>
        </section>
      </div>

      <%-- 접수 완료: 위의 안내와 폼을 숨기고 이 화면만 보여준다 (같은 페이지, AJAX 성공 후) --%>
      <div class="wrap inq-done" id="inqDone" hidden tabindex="-1">
        <img class="done-mark" src="${ctx}/resources/img/logo-mark.png" alt="">
        <h2>문의가 접수되었습니다</h2>
        <p>입력하신 이메일로 접수 내용을 보내 드렸고, 답변도 같은 주소로 보내 드립니다. 주소가 맞는지 아래에서 확인해 주세요.</p>
        <p class="done-no">접수번호 <strong id="doneNo"></strong></p>
        <dl class="inq-summary" id="summary"></dl>
        <div class="done-actions">
          <button class="btn btn-lg" type="button" id="btnEdit">다시 작성하기</button>
          <a class="btn btn-primary btn-lg" href="${ctx}/">처음으로</a>
        </div>
      </div>
  </jsp:body>
</ui:site>
