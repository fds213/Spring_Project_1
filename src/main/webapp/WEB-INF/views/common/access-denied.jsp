<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%--
  권한 없음 안내 (GET /access-denied → common/access-denied, 상태 403). Security 의 공통 접근 거절 처리가 화면 요청을 여기로 보낸다.
  AJAX 요청은 이 화면 없이 403 { success:false, message } 로 나가고, common.js 가 그 message 를 토스트로 보여 준다.
    기본       : 권한이 없는 화면 (담당 역할이 있는 사람만 여는 화면)
    ?expired   : CSRF 토큰 만료 (화면을 오래 열어 둔 뒤 보낸 요청). 이전 화면을 새로 고친 뒤 다시 하면 된다
  로그인한 사람만 오는 주소라 공통 틀(ui:layout)을 그대로 쓴다.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="expired" value="${param.expired ne null}" />
<ui:layout title="${expired ? '화면이 오래되었습니다' : '접근 권한이 없습니다'}">
  <jsp:attribute name="scripts">
    <%-- 이전 화면이 없으면(주소를 바로 연 경우) 돌아가기 버튼을 숨긴다 --%>
    <script>
      (function () {
        var back = document.getElementById('btnDeniedBack');
        if (history.length <= 1) back.hidden = true;
        back.addEventListener('click', function () { history.back(); });
      })();
    </script>
  </jsp:attribute>
  <jsp:body>
    <section class="denied" aria-labelledby="deniedTitle">
      <div class="denied-icon" aria-hidden="true">
        <c:choose>
          <%-- 시계: 오래된 화면 --%>
          <c:when test="${expired}">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
          </c:when>
          <%-- 자물쇠: 권한 없음 --%>
          <c:otherwise>
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>
          </c:otherwise>
        </c:choose>
      </div>
      <c:choose>
        <c:when test="${expired}">
          <h1 id="deniedTitle">화면이 오래되었습니다</h1>
          <p>화면을 연 지 오래되어 요청을 처리하지 못했습니다.<br>이전 화면으로 돌아가 새로 고친 뒤 다시 시도해 주세요.</p>
        </c:when>
        <c:otherwise>
          <h1 id="deniedTitle">접근 권한이 없습니다</h1>
          <p>이 화면은 담당 역할이 있는 사람만 볼 수 있습니다.<br>필요하면 업체 관리자에게 문의해 주세요.</p>
        </c:otherwise>
      </c:choose>
      <div class="denied-actions">
        <button type="button" class="btn" id="btnDeniedBack">이전 화면으로</button>
        <a class="btn btn-primary" href="${ctx}/home">홈으로</a>
      </div>
    </section>
  </jsp:body>
</ui:layout>
