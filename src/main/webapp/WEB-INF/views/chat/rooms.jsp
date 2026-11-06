<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<%--
  채팅 두 칸 화면: GET /chat/rooms(방 안 고름), GET /chat/room/{roomId}(오른쪽에 그 방).
  왼쪽 방 목록·검색(room-list.js), 오른쪽 대화(conversation.js). 방을 바꿀 때는 페이지를 다시 읽지 않고 주소만 바꾼다.
  900px 아래는 한 칸씩: 방을 고르기 전에는 목록, 고른 뒤에는 대화(위쪽 ← 로 목록).
  모델 속성: roomId(없을 수 있음), employeeId(나), empList(새 대화 창: employeeId, employeeName, departmentName)
  chat.css 와 채팅 스크립트(updates·room-list·conversation)는 공통 레이아웃이 불러 둔다.
--%>
<ui:layout title="채팅" crumb="소통" menu="chat">
  <jsp:attribute name="scripts">
    <script>
      (function () {
        var ctx = document.body.dataset.ctx || '';
        var app = document.getElementById('chatApp');
        var me = app.dataset.me;
        var convPane = document.getElementById('chatConvPane');
        var guide = document.getElementById('chatGuide');
        var conv = null;
        var pendingTitle = false;

        ChatUpdates.start();

        var rooms = ChatRoomList.create(document.getElementById('chatRoomsPane'), {
          activeId: app.dataset.roomId || null,
          onOpen: function (room) { openRoom(room.chatRoomId, room.displayName, room.roomType, true); },
          onUpdate: function () {
            // 주소로 바로 연 방은 목록이 온 뒤에야 이름을 안다
            if (conv && pendingTitle) {
              var room = rooms.get(conv.roomId);
              if (room) {
                conv.setTitle(room.displayName);
                document.title = room.displayName || '채팅';
                pendingTitle = false;
              }
            }
          }
        });

        function closeRoom(push) {
          if (conv) conv.destroy();
          conv = null;
          convPane.replaceChildren(guide);
          app.classList.remove('has-room');
          rooms.setActive(null);
          document.title = '채팅';
          if (push) history.pushState({ roomId: null }, '', '${ctx}/chat/rooms');
        }

        function openRoom(id, title, roomType, push) {
          id = String(id);
          if (conv && conv.roomId === id) {
            app.classList.add('has-room');
            conv.focus();
            return;
          }
          if (conv) conv.destroy();
          var known = rooms.get(id);
          pendingTitle = !title && !known;
          conv = ChatConversation.mount(convPane, {
            roomId: id,
            me: me,
            title: title || (known && known.displayName),
            roomType: roomType || (known && known.roomType),
            onBack: function () { closeRoom(true); rooms.focusSearch(); },
            onOpenRoom: function (otherId, otherTitle, otherType) { openRoom(otherId, otherTitle, otherType, true); }
          });
          app.classList.add('has-room');
          rooms.setActive(id);
          document.title = title || (known && known.displayName) || '채팅';
          if (push) history.pushState({ roomId: id }, '', '${ctx}/chat/room/' + encodeURIComponent(id));
        }

        window.addEventListener('popstate', function () {
          var match = /\/chat\/room\/(\d+)\/?$/.exec(location.pathname);
          if (match) openRoom(match[1], null, null, false);
          else closeRoom(false);
        });

        if (app.dataset.roomId) openRoom(app.dataset.roomId, null, null, false);
        history.replaceState({ roomId: app.dataset.roomId || null }, '');

        // ---------- 새 대화 창 (1:1 / 그룹) ----------
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

        function openCreate() {
          updateRoomType();
          dialog.showModal();
          employeeSearch.focus();
        }
        document.querySelectorAll('[data-chat-create]').forEach(function (btn) {
          btn.addEventListener('click', openCreate);
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
        // 채팅 패널의 '새 대화' 버튼은 /chat/rooms?new 로 온다
        if (new URLSearchParams(location.search).has('new') && !document.body.classList.contains('is-readonly')) {
          history.replaceState(history.state, '', '${ctx}/chat/rooms');
          openCreate();
        }
      })();
    </script>
  </jsp:attribute>
  <jsp:body>
    <div class="chat-app${not empty roomId ? ' has-room' : ''}" id="chatApp" data-me="${employeeId}" data-room-id="${roomId}">
      <aside class="chat-app-side" aria-labelledby="chatAppTitle">
        <div class="chat-app-head">
          <h1 id="chatAppTitle">채팅</h1>
          <button type="button" class="btn btn-primary" data-chat-create data-write>새 대화</button>
        </div>
        <div class="chat-app-rooms" id="chatRoomsPane"></div>
      </aside>
      <div class="chat-app-main" id="chatConvPane">
        <div class="chat-guide" id="chatGuide">
          <svg viewBox="0 0 24 24" width="40" height="40" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/></svg>
          <p>왼쪽에서 대화할 방을 고르세요.</p>
          <button type="button" class="btn" data-chat-create data-write>새 대화 시작</button>
        </div>
      </div>
    </div>

    <dialog class="modal chat-create-dialog" id="createDialog" aria-labelledby="createRoomTitle">
      <form id="createRoomForm" method="post" action="${ctx}/chat/createroom">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <h2 id="createRoomTitle">새 대화</h2>
        <div class="field">
          <label for="roomType">대화 종류</label>
          <select class="input" name="roomType" id="roomType">
            <option value="ROOM_DIRECT">1:1 대화</option>
            <option value="ROOM_GROUP">그룹 대화</option>
          </select>
        </div>
        <div class="field" id="roomNameField" hidden>
          <label for="roomName">그룹방 이름 (최대 200자)</label>
          <input class="input" type="text" name="roomName" id="roomName" placeholder="그룹방 이름" disabled>
        </div>
        <p class="chat-hint" id="memberHint"></p>
        <div class="field">
          <label for="chatEmployeeSearch">참여자 선택 (본인 제외)</label>
          <input class="input" type="search" id="chatEmployeeSearch" placeholder="이름 또는 부서로 찾기">
        </div>
        <ul class="emp-list">
          <c:forEach var="emp" items="${empList}">
            <li data-chat-employee><label>
              <input type="checkbox" name="members" value="${emp.employeeId}">
              <span><c:out value="${emp.employeeName}"/><c:if test="${not empty emp.departmentName}">
                (<c:out value="${emp.departmentName}"/>)</c:if></span>
            </label></li>
          </c:forEach>
          <c:if test="${empty empList}">
            <li class="empty">선택할 사원이 없습니다.</li>
          </c:if>
        </ul>
        <p class="chat-hint" id="chatEmployeeSearchEmpty" role="status" hidden>검색 결과가 없습니다.</p>
        <p class="chat-form-error" id="createRoomError" role="alert"></p>
        <div class="modal-actions">
          <button type="button" class="btn" id="cancelBtn">취소</button>
          <button type="submit" class="btn btn-primary" id="submitRoomBtn">대화 열기</button>
        </div>
      </form>
    </dialog>
  </jsp:body>
</ui:layout>
