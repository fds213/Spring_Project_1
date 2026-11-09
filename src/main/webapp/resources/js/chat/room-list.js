// 채팅 방 목록 + 검색. 두 칸 채팅 화면(왼쪽)과 오른쪽 아래 채팅 패널이 같이 쓴다.
//   const rooms = ChatRoomList.create(칸, { onOpen(room) { ... }, activeId });
//   rooms.setActive(방 번호);  rooms.get(방 번호);  rooms.focusSearch();  rooms.destroy();
// 목록은 ChatUpdates(updates.js)가 나눠 주는 "ieum:chat-unread"(GET /chat/unread)로 그린다. 검색은 방 이름(1:1 방은 상대 이름).
(function () {
    "use strict";

    const ctx = document.body.dataset.ctx || "";
    const GROUP_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.2"/><path d="M3 19c.6-3.2 3-5 6-5s5.4 1.8 6 5"/><circle cx="17" cy="9" r="2.4"/><path d="M16 14.2c2.6.2 4.4 1.8 5 4.8"/></svg>';

    /** 마지막 메시지 시각: 오늘은 "오후 3:12", 어제는 "어제", 올해는 "11월 3일", 그 전은 "2025. 3. 2." */
    function when(value) {
        if (value == null) return "";
        const date = new Date(typeof value === "number" || /^\d+$/.test(String(value)) ? Number(value) : value);
        if (isNaN(date)) return "";
        const now = new Date();
        const startOfDay = d => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
        const days = Math.round((startOfDay(now) - startOfDay(date)) / 86400000);
        if (days <= 0) return date.toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });
        if (days === 1) return "어제";
        if (date.getFullYear() === now.getFullYear()) return date.toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
        return date.toLocaleDateString("ko-KR");
    }

    function preview(room) {
        if (!room.lastMessageType) return "아직 대화가 없습니다";
        const body = room.lastMessageType === "MSG_IMAGE" ? "사진을 보냈습니다" : (room.lastMessageText || "");
        return (room.lastMessageMine ? "나: " : "") + body.replace(/\s+/g, " ");
    }

    function avatar(room) {
        const span = document.createElement("span");
        span.className = "chat-avatar tone-" + (Number(room.chatRoomId) % 6);
        span.setAttribute("aria-hidden", "true");
        if (room.roomType === "ROOM_GROUP") span.innerHTML = GROUP_ICON;
        else span.textContent = Array.from((room.displayName || "?").trim())[0] || "?";
        return span;
    }

    function create(container, opts) {
        opts = opts || {};
        let activeId = opts.activeId != null ? String(opts.activeId) : null;
        let rooms = [];
        let loaded = false;
        const byId = new Map();

        const root = document.createElement("div");
        root.className = "chat-dm";
        root.innerHTML =
            '<div class="chat-dm-search">' +
            '  <input class="input" type="search" placeholder="이름·방 이름 검색" aria-label="채팅방 검색 (방 이름, 1:1 상대 이름)">' +
            '</div>' +
            '<p class="chat-dm-empty" role="status">채팅방을 불러오는 중입니다.</p>' +
            '<ul class="chat-dm-list" aria-label="채팅방"></ul>';
        container.replaceChildren(root);
        const search = root.querySelector("input");
        const list = root.querySelector("ul");
        const empty = root.querySelector(".chat-dm-empty");

        function row(room) {
            const id = String(room.chatRoomId);
            const li = document.createElement("li");
            li.dataset.roomId = id;
            const link = document.createElement("a");
            link.className = "chat-dm-row";
            link.href = `${ctx}/chat/room/${encodeURIComponent(id)}`;
            const body = document.createElement("span");
            body.className = "chat-dm-body";
            const name = document.createElement("span");
            name.className = "chat-room-name";
            name.dir = "auto";
            const line = document.createElement("span");
            line.className = "chat-dm-line";
            const text = document.createElement("span");
            text.className = "chat-dm-preview";
            text.dir = "auto";
            const time = document.createElement("span");
            time.className = "chat-dm-time";
            line.append(text, time);
            body.append(name, line);
            const badge = document.createElement("span");
            badge.className = "badge";
            link.append(avatar(room), body, badge);
            li.append(link);
            link.addEventListener("click", function (e) {
                if (!opts.onOpen || e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return;
                e.preventDefault();
                opts.onOpen(byId.get(id));
            });
            return li;
        }

        function fill(li, room) {
            const name = room.displayName || "(이름 없음)";
            li.querySelector(".chat-room-name").textContent = name;
            li.querySelector(".chat-dm-row").title = name;
            li.querySelector(".chat-dm-preview").textContent = preview(room);
            const time = when(room.lastMessageAt);
            li.querySelector(".chat-dm-time").textContent = time ? "· " + time : "";
            const count = room.unreadCount || 0;
            const badge = li.querySelector(".badge");
            badge.textContent = count > 999 ? "999+" : String(count);
            const spoken = document.createElement("span");
            spoken.className = "chat-sr";
            spoken.textContent = "안 읽은 메시지 " + count.toLocaleString("ko-KR") + "개";
            badge.append(spoken);
            badge.hidden = count === 0;
            li.classList.toggle("is-unread", count > 0);
        }

        function applyFilter() {
            const keyword = search.value.trim().toLocaleLowerCase();
            let shown = 0;
            list.querySelectorAll("li[data-room-id]").forEach(function (li) {
                const room = byId.get(li.dataset.roomId);
                const match = !keyword || (room.displayName || "").toLocaleLowerCase().includes(keyword);
                li.hidden = !match;
                if (match) shown++;
            });
            empty.hidden = loaded && shown > 0;
            if (!empty.hidden) {
                empty.textContent = !loaded ? "채팅방을 불러오는 중입니다."
                    : rooms.length === 0 ? "참여 중인 채팅방이 없습니다." : "검색 결과가 없습니다.";
            }
        }

        function markActive() {
            list.querySelectorAll("li[data-room-id]").forEach(function (li) {
                const on = li.dataset.roomId === activeId;
                li.classList.toggle("is-active", on);
                const link = li.querySelector("a");
                if (on) link.setAttribute("aria-current", "true");
                else link.removeAttribute("aria-current");
            });
        }

        function update(snapshot) {
            loaded = true;
            rooms = (snapshot && snapshot.rooms) || [];
            const current = new Map(Array.from(list.querySelectorAll("li[data-room-id]"))
                .map(li => [li.dataset.roomId, li]));
            byId.clear();
            rooms.forEach(function (room) {
                const id = String(room.chatRoomId);
                byId.set(id, room);
                let li = current.get(id);
                if (!li) li = row(room);
                current.delete(id);
                list.append(li);
                fill(li, room);
            });
            current.forEach(li => li.remove());
            applyFilter();
            markActive();
            if (opts.onUpdate) opts.onUpdate(snapshot);
        }

        function onUnread(event) { update(event.detail); }
        window.addEventListener("ieum:chat-unread", onUnread);
        search.addEventListener("input", applyFilter);
        search.addEventListener("keydown", function (e) {
            // Enter: 검색 결과가 하나면 그 방을 연다
            if (e.key !== "Enter" || e.isComposing) return;
            e.preventDefault();
            const shown = Array.from(list.querySelectorAll("li[data-room-id]:not([hidden]) a"));
            if (shown.length === 1) shown[0].click();
        });
        if (window.ChatUpdates && ChatUpdates.last()) update(ChatUpdates.last());

        return {
            setActive: function (id) { activeId = id != null ? String(id) : null; markActive(); },
            get: function (id) { return byId.get(String(id)); },
            focusSearch: function () { search.focus(); },
            destroy: function () {
                window.removeEventListener("ieum:chat-unread", onUnread);
                root.remove();
            }
        };
    }

    window.ChatRoomList = { create: create, when: when };
})();
