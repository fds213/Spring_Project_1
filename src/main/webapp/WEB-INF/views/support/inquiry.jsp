<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%--
  이음 장애 문의 화면 (/support/inquiry, OPS-04 최소판). 직원이 이음 운영팀에 문의를 보내고 '내가 보낸 문의'에서 답변을 본다.
  업체 관리자에게는 같은 업체 직원들이 보낸 문의가 더 보인다(읽기만, 서버는 /support/admin/**).
  화면 JS는 util.js(같이 쓰는 도구)와 support/inquiry.js만 불러오고, 페이지가 열리면 openSupportInquiryView()를 부른다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="이음 장애 문의" crumb="IT 서비스" menu="support-inquiry">
  <jsp:attribute name="scripts">
    <script src="${ctx}/resources/js/util.js"></script>
    <script src="${ctx}/resources/js/support/inquiry.js"></script>
    <script>openSupportInquiryView();</script>
  </jsp:attribute>
  <jsp:body>
    <section class="view on" id="view-support-inquiry">
      <div class="page-head"><h1>이음 장애 문의</h1></div>
      <section class="info-card">
        <header class="info-card-head">
          <div>
            <h2>이음 운영팀에 문의하기</h2>
            <p>이음 서비스 자체의 오류를 이음 운영팀에 알립니다. 우리 회사의 장비·계정 문제는 장애·문의 접수로 보내 주세요.</p>
          </div>
        </header>
        <form class="inq-body" id="supportForm" novalidate>
          <div class="field">
            <label for="supportTitle">제목</label>
            <input class="input" id="supportTitle" maxlength="100" autocomplete="off" placeholder="예: 결재함 화면이 열리지 않습니다">
          </div>
          <div class="field">
            <label for="supportBody">내용</label>
            <textarea class="inq-text" id="supportBody" maxlength="4000" placeholder="언제부터, 어떤 화면에서, 어떤 오류가 나는지 적어 주세요"></textarea>
          </div>
          <p class="form-error" id="supportError" role="alert"></p>
          <div class="inq-actions">
            <button type="submit" class="btn btn-primary" id="supportSend" data-write>문의 보내기</button>
          </div>
        </form>
      </section>

      <div class="page-head" style="margin-top: 28px"><h2>내가 보낸 문의</h2></div>
      <div class="table-wrap">
        <table class="table table-fixed">
          <colgroup><col style="width: 20%"><col style="width: 60%"><col style="width: 20%"></colgroup>
          <thead><tr><th>보낸 시각</th><th>제목</th><th>상태</th></tr></thead>
          <tbody id="supportList"></tbody>
        </table>
      </div>

      <sec:authorize access="hasRole('ADMIN')">
        <div class="page-head" style="margin-top: 28px"><h2>우리 회사 문의</h2></div>
        <div class="table-wrap">
          <table class="table table-fixed">
            <colgroup><col style="width: 16%"><col style="width: 18%"><col style="width: 46%"><col style="width: 20%"></colgroup>
            <thead><tr><th>보낸 시각</th><th>보낸 사람</th><th>제목</th><th>상태</th></tr></thead>
            <tbody id="supportCompanyList"></tbody>
          </table>
        </div>
      </sec:authorize>
    </section>
  </jsp:body>
</ui:layout>
