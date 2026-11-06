<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  인증 메일의 링크로 여는 확인 화면 (GET /trial/verify, ACC-06. 로그인 없이 연다).
  링크를 열기만 해서는 인증되지 않는다: 메일 보안 검사가 링크를 먼저 열어도 쓰이지 않게 [인증하기]가 POST로 인증한다.
  링크 값은 주소의 # 뒤에 있어 서버로 오지 않는다. resources/js/site/verify.js 가 읽어 보낸다.
  data-app-allowed: 설치한 IEUM 앱 창에서 열려도 로그인 화면으로 넘기지 않는다(install.js). 앱을 설치한 PC는 메일 링크를 앱 창으로 연다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:site title="이메일 인증" mainClass="section" mainStyle="padding-top: 56px">
  <jsp:attribute name="scripts"><script src="${ctx}/resources/js/site/verify.js"></script></jsp:attribute>
  <jsp:body>
      <div class="wrap" style="max-width: 560px" data-app-allowed>
        <div class="box">
          <h3>이메일 인증</h3>
          <p class="sub" id="verifyGuide">아래 버튼을 누르면 이메일 인증을 마치고 로그인할 수 있습니다.</p>
          <p class="done" id="verifyResult" role="status" hidden></p>
          <div class="actions">
            <button class="btn btn-primary btn-lg" type="button" id="btnVerify">인증하기</button>
            <a class="btn btn-lg" href="${ctx}/login" id="linkLogin" hidden>로그인하러 가기</a>
          </div>
        </div>
      </div>
  </jsp:body>
</ui:site>
