<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%-- 모델 속성: roomList (roomId, roomName), empList (empnum, name) --%>
<ui:layout title="채팅방 목록" crumb="채팅" menu="chat">
  <jsp:attribute name="scripts">
    <script>
      var dialog = document.getElementById('createDialog');
      document.getElementById('createBtn').addEventListener('click', function () { dialog.showModal(); });
      document.getElementById('cancelBtn').addEventListener('click', function () { dialog.close(); });
    </script>
  </jsp:attribute>
  <jsp:body>
    <style>
      /* 공통 틀과 겹치지 않게 .chat-rooms 안으로 범위를 좁힌다 */
      .chat-rooms ul { list-style: none; padding: 0; }
      .chat-rooms li { padding: 12px; border-bottom: 1px solid #ddd; }
      .chat-rooms li a { text-decoration: none; color: inherit; display: block; }
      .chat-rooms .room-list li:hover { background: #f5f5f5; }
      .chat-rooms .empty { color: #888; padding: 12px; }
      .chat-rooms dialog { width: 360px; border: 1px solid #ccc; border-radius: 8px; }
      .chat-rooms dialog input[type=text] { width: 100%; box-sizing: border-box; }
      .chat-rooms .emp-list { max-height: 240px; overflow-y: auto; border: 1px solid #ddd; }
      .chat-rooms .emp-list li { padding: 6px 10px; }
    </style>

    <div class="chat-rooms">
      <div class="page-head">
        <h1>채팅방 목록</h1>
        <button type="button" id="createBtn">채팅방 생성</button>
      </div>

      <dialog id="createDialog">
        <form method="post" action="${pageContext.request.contextPath}/chat/createroom">
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

      <ul class="room-list">
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
    </div>
  </jsp:body>
</ui:layout>
