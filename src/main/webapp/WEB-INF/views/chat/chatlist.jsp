<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%-- 임시 화면: 공통 레이아웃이 확정되면 교체한다. 모델 속성: roomList (roomId, roomName) --%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>채팅방 목록</title>
    <style>
        body { font-family: sans-serif; max-width: 600px; margin: 24px auto; }
        header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
        ul { list-style: none; padding: 0; }
        li { padding: 12px; border-bottom: 1px solid #ddd; }
        li a { text-decoration: none; color: inherit; display: block; }
        li:hover { background: #f5f5f5; }
        .empty { color: #888; padding: 12px; }
    </style>
</head>
<body>
<header>
    <h2>채팅방 목록</h2>
    <span><b>${user.empnum}</b> 님</span>
</header>

<form id="createForm" method="post" action="${pageContext.request.contextPath}/chat/room/create">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
    <input type="hidden" name="roomName" id="roomName"/>
    <button type="button" id="createBtn">채팅방 생성</button>
</form>

<ul>
    <c:forEach var="room" items="${roomList}">
        <li>
            <a href="${pageContext.request.contextPath}/chat/room/${room.roomId}">
                <c:out value="${room.roomName}"/>
            </a>
        </li>
    </c:forEach>
    <c:if test="${empty roomList}">
        <li class="empty">참여 중인 채팅방이 없습니다.</li>
    </c:if>
</ul>

<script>
    // 임시: 방 이름만 입력받는다. 참여자 선택은 백엔드 구현 후 추가
    document.getElementById('createBtn').addEventListener('click', function () {
        var name = prompt('채팅방 이름을 입력하세요');
        if (!name || !name.trim()) return;
        document.getElementById('roomName').value = name.trim();
        document.getElementById('createForm').submit();
    });
</script>
</body>
</html>
