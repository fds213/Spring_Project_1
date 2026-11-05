<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%-- 모델 속성: roomList (chatRoomId, displayName, unreadCount), empList (employeeId, employeeName) --%>
<ui:layout title="채팅방 목록" crumb="채팅" menu="chat">
  <jsp:attribute name="scripts">
    <script>
      (function () {
        var dialog = document.getElementById('createDialog');
        var form = document.getElementById('createRoomForm');
        var roomType = document.getElementById('roomType');
        var roomName = document.getElementById('roomName');
        var submitBtn = document.getElementById('submitRoomBtn');
        var cancelBtn = document.getElementById('cancelBtn');
        var error = document.getElementById('createRoomError');
        var employeeSearch = document.getElementById('chatEmployeeSearch');
        var employees = Array.from(form.querySelectorAll('[data-chat-employee]'));
        var searchEmpty = document.getElementById('chatEmployeeSearchEmpty');
        var submitting = false;

        function filterEmployees() {
          var keyword = employeeSearch.value.trim().toLocaleLowerCase();
          var hasMatches = false;
          employees.forEach(function (row) {
            var matches = row.textContent.toLocaleLowerCase().includes(keyword);
            row.hidden = !matches && !row.querySelector('input').checked;
            hasMatches = hasMatches || matches;
          });
          searchEmpty.hidden = employees.length === 0 || hasMatches;
        }

        employeeSearch.addEventListener('input', filterEmployees);
        employeeSearch.addEventListener('keydown', function (event) {
          if (event.key === 'Enter' && !event.isComposing) event.preventDefault();
        });
        employees.forEach(function (row) {
          row.querySelector('input').addEventListener('change', filterEmployees);
        });

        function updateRoomType() {
          var group = roomType.value === 'ROOM_GROUP';
          document.getElementById('roomNameField').hidden = !group;
          roomName.disabled = !group;
          roomName.required = group;
          document.getElementById('memberHint').textContent = group
            ? '본인 외 두 명 이상을 선택해 주세요. 본인은 자동으로 참여합니다.'
            : '상대를 한 명 선택해 주세요. 기존 1:1 대화가 있으면 이어서 엽니다.';
          error.textContent = '';
        }

        document.getElementById('createBtn').addEventListener('click', function () {
          updateRoomType();
          dialog.showModal();
        });
        cancelBtn.addEventListener('click', function () { dialog.close(); });
        dialog.addEventListener('cancel', function (event) {
          if (submitting) event.preventDefault();
        });
        roomType.addEventListener('change', updateRoomType);

        form.addEventListener('submit', function (event) {
          if (submitting) {
            event.preventDefault();
            return;
          }
          var count = form.querySelectorAll('input[name="members"]:checked').length;
          error.textContent = '';
          var group = roomType.value === 'ROOM_GROUP';
          if (group ? count < 2 : count !== 1) {
            event.preventDefault();
            error.textContent = group
              ? '그룹 대화는 본인 외 두 명 이상 선택해 주세요.'
              : '1:1 대화는 상대를 한 명만 선택해 주세요.';
            return;
          }
          if (group) {
            roomName.value = roomName.value.trim();
            if (!roomName.value || Array.from(roomName.value).length > 200) {
              event.preventDefault();
              error.textContent = '그룹방 이름을 1~200자로 입력해 주세요.';
              roomName.focus();
              return;
            }
          }
          submitting = true;
          submitBtn.disabled = true;
          cancelBtn.disabled = true;
          submitBtn.textContent = '여는 중…';
        });

        window.addEventListener('pageshow', function () {
          submitting = false;
          submitBtn.disabled = false;
          cancelBtn.disabled = false;
          submitBtn.textContent = '대화 열기';
        });
        updateRoomType();
        filterEmployees();

        var list = document.getElementById('chatRoomList');
        var unreadTotal = document.getElementById('chatUnreadTotal');
        var empty = document.getElementById('chatRoomEmpty');
        window.addEventListener('ieum:chat-unread', function (event) {
          var snapshot = event.detail;
          unreadTotal.textContent = '안 읽은 채팅 ' + snapshot.total + '개';
          var current = new Map(Array.from(list.querySelectorAll('[data-room-id]'))
            .map(function (row) { return [row.dataset.roomId, row]; }));
          snapshot.rooms.forEach(function (room) {
            var id = String(room.chatRoomId);
            var row = current.get(id);
            if (!row) {
              row = document.createElement('li');
              row.dataset.roomId = id;
              var link = document.createElement('a');
              link.href = list.dataset.roomBase + encodeURIComponent(id);
              var name = document.createElement('span');
              name.dataset.roomName = '';
              var badge = document.createElement('span');
              badge.dataset.roomUnread = '';
              badge.className = 'badge';
              link.append(name, ' ', badge);
              row.append(link);
              list.append(row);
            }
            row.querySelector('[data-room-name]').textContent = room.displayName || '';
            var badge = row.querySelector('[data-room-unread]');
            badge.textContent = '안 읽은 메시지 ' + room.unreadCount + '개';
            badge.hidden = room.unreadCount === 0;
            current.delete(id);
          });
          current.forEach(function (row) { row.remove(); });
          empty.hidden = snapshot.rooms.length !== 0;
        });
      })();
    </script>
    <script src="${pageContext.request.contextPath}/resources/js/chat/updates.js"
            data-context-path="${pageContext.request.contextPath}"></script>
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

      <dialog id="createDialog" aria-labelledby="createRoomTitle">
        <form id="createRoomForm" method="post" action="${pageContext.request.contextPath}/chat/createroom">
          <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
          <h3 id="createRoomTitle">채팅방 만들기</h3>
          <p>
            <label for="roomType">대화 종류</label>
            <select name="roomType" id="roomType">
              <option value="ROOM_DIRECT">1:1 대화</option>
              <option value="ROOM_GROUP">그룹 대화</option>
            </select>
          </p>
          <p id="roomNameField" hidden>
            <label for="roomName">그룹방 이름 (최대 200자)</label>
            <input type="text" name="roomName" id="roomName" placeholder="그룹방 이름" disabled>
          </p>
          <p id="memberHint"></p>
          <p>참여자 선택 (본인 제외)</p>
          <label for="chatEmployeeSearch">직원 검색</label>
          <input type="search" id="chatEmployeeSearch" placeholder="이름 또는 부서">
          <ul class="emp-list">
            <c:forEach var="emp" items="${empList}">
              <li data-chat-employee><label>
                <input type="checkbox" name="members" value="${emp.employeeId}">
                <c:out value="${emp.employeeName}"/>
                <c:if test="${not empty emp.departmentName}">
                  (<c:out value="${emp.departmentName}"/>)
                </c:if>
              </label></li>
            </c:forEach>
            <c:if test="${empty empList}">
              <li class="empty">선택할 사원이 없습니다.</li>
            </c:if>
          </ul>
          <p id="chatEmployeeSearchEmpty" role="status" hidden>검색 결과가 없습니다.</p>
          <p id="createRoomError" role="alert"></p>
          <p>
            <button type="submit" id="submitRoomBtn">대화 열기</button>
            <button type="button" id="cancelBtn">취소</button>
          </p>
        </form>
      </dialog>

      <p id="chatUnreadTotal" role="status" aria-live="polite"></p>
      <ul id="chatRoomList" class="room-list" data-room-base="${pageContext.request.contextPath}/chat/room/">
        <c:forEach var="room" items="${roomList}">
          <li data-room-id="${room.chatRoomId}">
            <a href="${pageContext.request.contextPath}/chat/room/${room.chatRoomId}">
              <span data-room-name><c:out value="${room.displayName}"/></span>
              <span class="badge" data-room-unread
                    <c:if test="${room.unreadCount eq 0}">hidden</c:if>>안 읽은 메시지 <c:out value="${room.unreadCount}"/>개</span>
            </a>
          </li>
        </c:forEach>
        <li id="chatRoomEmpty" class="empty"
            <c:if test="${not empty roomList}">hidden</c:if>>참여 중인 채팅방이 없습니다.</li>
      </ul>
    </div>
  </jsp:body>
</ui:layout>
