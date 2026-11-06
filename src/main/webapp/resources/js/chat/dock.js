// 채팅 밖 화면의 오른쪽 아래 채팅 버튼과 '메시지' 패널. 공통 레이아웃이 업체 직원의 채팅 화면이 아닌 곳에만 #chatDock 을 둔다.
// 버튼을 누르면 방 목록·검색이 뜨고, 방을 누르면 패널 안에서 바로 대화한다(← 로 목록).
// 새 대화는 채팅 화면의 새 대화 창으로, 크게 보기는 채팅 화면으로 간다. 열어 둔 상태와 방은 다른 화면으로 가도 이어진다.
(function () {
    "use strict";

    const dock = document.getElementById("chatDock");
    if (!dock || !window.ChatConversation || !window.ChatRoomList || !window.ChatUpdates) return;
    const ctx = document.body.dataset.ctx || "";
    const me = dock.dataset.me;
    const STORE = "ieum.chatDock";

    const ICON = {
        chat: '<svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/></svg>',
        expand: '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7"/></svg>',
        close: '<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>',
        pen: '<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>'
    };

    function read() {
        try { return JSON.parse(sessionStorage.getItem(STORE)) || {}; } catch (e) { return {}; }
    }
    function save(state) {
        try { sessionStorage.setItem(STORE, JSON.stringify(state)); } catch (e) { /* 저장 못 하면 이어지지만 않는다 */ }
    }

    document.body.classList.add("has-chat-dock");
    dock.innerHTML =
        '<button type="button" class="chat-fab" aria-expanded="false" aria-controls="chatDockPanel">' + ICON.chat +
        '  <span class="chat-sr">메시지</span><span class="badge chat-fab-badge" data-badge="chat" hidden></span></button>' +
        '<section class="chat-dock-panel" id="chatDockPanel" role="dialog" aria-labelledby="chatDockTitle" hidden>' +
        '  <header class="chat-dock-head">' +
        '    <div class="chat-dock-title"><h2 id="chatDockTitle">메시지</h2><span class="badge chat-dock-count" hidden></span></div>' +
        // 새 대화 버튼은 목록 위에 떠 있으면 방 줄을 가려서 위쪽 줄에 둔다(목록일 때만 보인다)
        `    <a class="chat-dock-icon" data-el="compose" href="${ctx}/chat/rooms?new"` + ' title="새 대화" aria-label="새 대화" data-write>' + ICON.pen + '</a>' +
        `    <a class="chat-dock-icon" data-el="expand" href="${ctx}/chat/rooms"` + ' title="채팅 화면에서 크게 보기" aria-label="채팅 화면에서 크게 보기">' + ICON.expand + '</a>' +
        '    <button type="button" class="chat-dock-icon" data-el="close" title="닫기" aria-label="메시지 닫기">' + ICON.close + '</button>' +
        '  </header>' +
        '  <div class="chat-dock-body">' +
        '    <div class="chat-dock-rooms" data-el="rooms"></div>' +
        '    <div class="chat-dock-conv" data-el="conv" hidden></div>' +
        '  </div>' +
        '</section>';

    const fab = dock.querySelector(".chat-fab");
    const panel = dock.querySelector(".chat-dock-panel");
    const part = name => panel.querySelector('[data-el="' + name + '"]');
    const roomsPane = part("rooms");
    const convPane = part("conv");
    const count = panel.querySelector(".chat-dock-count");
    let rooms = null;
    let conv = null;
    let state = read();

    function setExpandHref() {
        part("expand").href = conv ? `${ctx}/chat/room/${encodeURIComponent(conv.roomId)}` : `${ctx}/chat/rooms`;
    }

    function showList(focus) {
        if (conv) conv.destroy();
        conv = null;
        convPane.hidden = true;
        roomsPane.hidden = false;
        part("compose").hidden = false;
        if (rooms) rooms.setActive(null);
        state.roomId = null;
        save(state);
        setExpandHref();
        if (focus && rooms) rooms.focusSearch();
    }

    function openRoom(id, title, roomType, restoring) {
        if (conv) conv.destroy();
        roomsPane.hidden = true;
        part("compose").hidden = true;
        convPane.hidden = false;
        const known = rooms && rooms.get(id);
        conv = ChatConversation.mount(convPane, {
            roomId: id,
            me: me,
            title: title || (known && known.displayName),
            roomType: roomType || (known && known.roomType),
            onBack: function () { showList(true); },
            onOpenRoom: openRoom
        });
        state.roomId = String(id);
        state.title = title || (known && known.displayName) || "";
        state.roomType = roomType || (known && known.roomType) || "";
        save(state);
        setExpandHref();
        // 다른 화면에서 이어서 열 때는 그 화면의 포커스를 빼앗지 않는다
        if (!restoring) conv.focus();
    }

    // 버튼으로 열면 마지막에 보던 방을 이어서 연다. pageLoad: 다른 화면으로 옮겨 와 저절로 다시 여는 경우(포커스를 주지 않는다)
    function openPanel(pageLoad) {
        if (!panel.hidden) return;
        panel.hidden = false;
        fab.hidden = true;
        fab.setAttribute("aria-expanded", "true");
        ChatUpdates.start();
        if (!rooms) {
            rooms = ChatRoomList.create(roomsPane, {
                onOpen: function (room) { openRoom(String(room.chatRoomId), room.displayName, room.roomType); },
                onUpdate: function (snapshot) {
                    count.textContent = snapshot.total > 99 ? "99+" : String(snapshot.total);
                    count.hidden = !snapshot.total;
                    if (conv) {
                        const room = rooms.get(conv.roomId);
                        if (room) conv.setTitle(room.displayName);
                    }
                }
            });
        }
        state.open = true;
        save(state);
        if (state.roomId) openRoom(state.roomId, state.title, state.roomType, pageLoad);
        else {
            showList(false);
            if (!pageLoad) rooms.focusSearch();
        }
    }

    function closePanel() {
        if (panel.hidden) return;
        const hadFocus = panel.contains(document.activeElement);
        if (conv) conv.destroy();
        conv = null;
        panel.hidden = true;
        fab.hidden = false;
        fab.setAttribute("aria-expanded", "false");
        ChatUpdates.stop();
        state = { open: false, roomId: state.roomId, title: state.title, roomType: state.roomType };
        save(state);
        if (hadFocus) fab.focus();
    }

    fab.addEventListener("click", function () { openPanel(false); });
    part("close").addEventListener("click", closePanel);
    panel.addEventListener("keydown", function (e) {
        if (e.key === "Escape" && !e.defaultPrevented) closePanel();
    });
    // 크게 보기·새 대화로 채팅 화면에 가면 패널은 닫힌 것으로 둔다(채팅 화면에는 버튼이 없다)
    [part("expand"), part("compose")].forEach(function (link) {
        link.addEventListener("click", function () {
            save({ open: false, roomId: state.roomId, title: state.title, roomType: state.roomType });
        });
    });
    // 다른 화면에서 열어 둔 패널과 방을 이어서 연다
    if (state.open) openPanel(true);
})();
