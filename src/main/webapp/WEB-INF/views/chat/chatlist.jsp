<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%-- 임시 화면: 공통 레이아웃이 확정되면 교체한다. 모델 속성: roomList (roomId, roomName), empList (empnum, name) --%>
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
        dialog { width: 360px; border: 1px solid #ccc; border-radius: 8px; }
        dialog input[type=text] { width: 100%; box-sizing: border-box; }
        .emp-list { max-height: 240px; overflow-y: auto; border: 1px solid #ddd; }
        .emp-list li { padding: 6px 10px; }
    </style>
</head>
<body>
<header>
    <h2>채팅방 목록</h2>
    <span><b>${user.empnum}</b> 님</span>
</header>

<button type="button" id="createBtn">채팅방 생성</button>

<dialog id="createDialog">
    <form method="post" action="${pageContext.request.contextPath}/createroom.do">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <h3>채팅방 만들기</h3>
        <p><input type="text" name="roomName" id="roomName" placeholder="방 이름" maxlength="50" required></p>
        <p>참여자 선택</p>
        <ul class="emp-list">
            <c:forEach var="emp" items="${empList}">
                <li><label>
                    <input type="checkbox" name="members" value="${emp.empnum}">
                    <c:out value="${emp.name}"/> (<c:out value="${emp.empnum}"/>)
                </label></li>
            </c:forEach>
            <c:if test="${empty empList}">
                <li class="empty">선택할 사원이 없습니다.</li>
            </c:if>
        </ul>
        <p>
            <button type="submit">생성</button>
            <button type="button" id="cancelBtn">취소</button>
        </p>
    </form>
</dialog>

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
    var dialog = document.getElementById('createDialog');
    document.getElementById('createBtn').addEventListener('click', function () { dialog.showModal(); });
    document.getElementById('cancelBtn').addEventListener('click', function () { dialog.close(); });
</script>
</body>
</html>
