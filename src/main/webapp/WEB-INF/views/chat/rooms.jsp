<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
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

        /** 안 읽은 수를 배지에 넣는다. 눈에는 숫자(999 넘으면 999+), 화면 낭독기에는 "안 읽은 메시지 n개"로 보인다 */
        function setBadge(badge, count) {
          badge.textContent = count > 999 ? '999+' : String(count);
          var spoken = document.createElement('span');
          spoken.className = 'chat-sr';
          spoken.textContent = '안 읽은 메시지 ' + count.toLocaleString('ko-KR') + '개';
          badge.append(spoken);
          badge.hidden = count === 0;
          badge.closest('li').classList.toggle('is-unread', count > 0);
        }

        window.addEventListener('ieum:chat-unread', function (event) {
          var snapshot = event.detail;
          unreadTotal.textContent = snapshot.total > 0
            ? '안 읽은 채팅 ' + snapshot.total.toLocaleString('ko-KR') + '개' : '';
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
              name.className = 'chat-room-name';
              name.dir = 'auto';
              var badge = document.createElement('span');
              badge.dataset.roomUnread = '';
              badge.className = 'badge';
              link.append(name, badge);
              row.append(link);
            }
            list.append(row);
            var name = row.querySelector('[data-room-name]');
            name.textContent = room.displayName || '';
            name.title = room.displayName || '';
            setBadge(row.querySelector('[data-room-unread]'), room.unreadCount);
            current.delete(id);
          });
          current.forEach(function (row) { row.remove(); });
          list.append(empty);
          empty.hidden = snapshot.rooms.length !== 0;
        });
      })();
    </script>
    <script src="${ctx}/resources/js/chat/updates.js"
            data-context-path="${ctx}"></script>
  </jsp:attribute>
  <jsp:body>
    <%-- 채팅 전용 스타일 (공통 레이아웃에 화면별 CSS 칸이 없어서 본문 맨 앞에서 불러온다) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/chat/chat.css">

    <div class="chat-rooms">
      <div class="page-head">
        <h1>채팅방 목록</h1>
        <button type="button" class="btn btn-primary" id="createBtn" data-write>채팅방 생성</button>
      </div>

      <dialog class="modal chat-create-dialog" id="createDialog" aria-labelledby="createRoomTitle">
        <form id="createRoomForm" method="post" action="${ctx}/chat/createroom">
          <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
          <h2 id="createRoomTitle">채팅방 만들기</h2>
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

      <p class="chat-unread-total" id="chatUnreadTotal" role="status" aria-live="polite"></p>
      <ul id="chatRoomList" class="chat-room-list" data-room-base="${ctx}/chat/room/">
        <c:forEach var="room" items="${roomList}">
          <li data-room-id="${room.chatRoomId}"<c:if test="${room.unreadCount gt 0}"> class="is-unread"</c:if>>
            <a href="${ctx}/chat/room/${room.chatRoomId}">
              <span class="chat-room-name" data-room-name dir="auto" title="<c:out value='${room.displayName}'/>"><c:out value="${room.displayName}"/></span>
              <span class="badge" data-room-unread<c:if test="${room.unreadCount eq 0}"> hidden</c:if>><c:choose><c:when test="${room.unreadCount gt 999}">999+</c:when><c:otherwise>${room.unreadCount}</c:otherwise></c:choose><span class="chat-sr">안 읽은 메시지 ${room.unreadCount}개</span></span>
            </a>
          </li>
        </c:forEach>
        <li id="chatRoomEmpty" class="chat-room-empty"
            <c:if test="${not empty roomList}">hidden</c:if>>참여 중인 채팅방이 없습니다.</li>
      </ul>
    </div>
  </jsp:body>
</ui:layout>
