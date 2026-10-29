<%@ tag language="java" pageEncoding="UTF-8" body-content="scriptless" trimDirectiveWhitespaces="true" %>
<%@ attribute name="key" required="true" description="이 항목의 menu 키 (가이드 3장)" %>
<%@ attribute name="path" required="true" description="컨텍스트 경로 뒤 주소. 예: /approval/list" %>
<%@ attribute name="current" description="지금 화면의 menu 키. 같으면 현재 화면으로 표시한다" %>
<%@ attribute name="write" type="java.lang.Boolean" description="새로 쓰는 동작이면 true. 읽기 전용 업체에서 흐리게 막는다" %>
<%-- 사이드 메뉴 항목 하나: <ui:nav key="approval-box" path="/approval/list" current="${menu}">결재함</ui:nav> --%>
<a href="${pageContext.request.contextPath}${path}" class="nav-item${key eq current ? ' on' : ''}"${key eq current ? ' aria-current="page"' : ''}${write ? ' data-write' : ''}><jsp:doBody /></a>
