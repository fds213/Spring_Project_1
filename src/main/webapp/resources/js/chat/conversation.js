// 채팅 대화 칸 하나. 두 칸 채팅 화면(오른쪽)과 오른쪽 아래 채팅 패널이 같이 쓴다.
//   const conv = ChatConversation.mount(칸, { roomId, me, title, roomType, onBack, onOpenRoom });
//   conv.setTitle('개발팀');  conv.destroy();   // 다른 방을 열기 전에 꼭 destroy 한다(웹소켓을 닫는다)
// 처음 50개는 GET /chat/room/{id}/messages, 이전 대화는 ?beforeMessageId=, 재접속 복구는 /messages/after,
// 실시간은 웹소켓 /chat/room/{id}, 읽음은 POST·GET /chat/room/{id}/read, 이미지는 POST /chat/room/{id}/images,
// 참여자는 GET /chat/room/{id}/members, 1:1 대화는 POST /chat/createroom (기존 방이 있으면 그 방).
(function ($) {
    "use strict";

    const ctx = document.body.dataset.ctx || "";
    let seq = 0;

    /** 서버 시각(밀리초 숫자·숫자 글자·날짜 글자)을 밀리초로 바꾼다. 없으면 지금 시각을 쓴다 */
    function toMillis(value) {
        const millis = typeof value === "number" ? value
            : /^\d+$/.test(String(value)) ? Number(value) : Date.parse(value);
        return isNaN(millis) || !millis ? Date.now() : millis;
    }

    /** 파일 크기를 "1.2MB"·"340KB"처럼 보인다 */
    function fileSize(bytes) {
        return bytes >= 1024 * 1024 ? (bytes / 1024 / 1024).toFixed(1) + "MB" : Math.max(1, Math.round(bytes / 1024)) + "KB";
    }

    function toast(text) {
        if (window.IEUM && IEUM.toast) IEUM.toast(text);
        else alert(text);
    }

    function goLogin() { location.href = `${ctx}/login?expired`; }

    const IMAGE_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="1.8"/><path d="M21 16l-5-5-8 9"/></svg>';
    const BACK_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>';

    function template(fileId) {
        return '' +
            '<header class="chat-head">' +
            '  <button type="button" class="btn btn-ghost chat-back" data-el="back" aria-label="채팅방 목록으로">' + BACK_ICON + '</button>' +
            '  <h2 class="chat-title" data-el="title" dir="auto"></h2>' +
            '  <p class="chat-connection" data-el="connection" role="status" aria-live="polite">연결 중입니다.</p>' +
            '  <div class="chat-members-wrap">' +
            '    <button type="button" class="btn chat-members-toggle" data-el="membersToggle" aria-expanded="false">참여자</button>' +
            '    <div class="chat-members-pop" data-el="membersPop" hidden><ul data-el="memberList"></ul></div>' +
            '  </div>' +
            '</header>' +
            '<div class="chat-notices">' +
            '  <p data-el="readStatus" role="status" aria-live="polite"></p>' +
            '  <button type="button" class="btn" data-el="retryRead" hidden>읽음 다시 저장</button>' +
            '  <p data-el="receiptStatus" role="status" aria-live="polite"></p>' +
            '  <p data-el="sendStatus" role="status" aria-live="polite"></p>' +
            '  <button type="button" class="btn" data-el="retrySend" hidden>전송 확인 다시 시도</button>' +
            '</div>' +
            '<div class="message-area" data-el="area">' +
            '  <button type="button" class="btn chat-load-older" data-el="loadOlder" hidden>이전 대화 더 보기</button>' +
            '  <p class="empty-note" data-el="empty" hidden>아직 대화가 없습니다. 첫 메시지를 보내 보세요.</p>' +
            '  <p class="empty-note" data-el="loading">대화를 불러오는 중입니다.</p>' +
            '  <div data-el="list"></div>' +
            '</div>' +
            '<div class="chat-compose">' +
            '  <div class="chat-attach" data-el="attach" hidden>' +
            '    <img class="chat-attach-thumb" data-el="attachThumb" alt="보낼 이미지 미리보기">' +
            '    <div class="chat-attach-info"><span class="chat-attach-name" data-el="attachName"></span><span class="chat-attach-size" data-el="attachSize"></span></div>' +
            '    <button type="button" class="btn btn-ghost chat-attach-remove" data-el="attachRemove" aria-label="고른 이미지 빼기">✕</button>' +
            '  </div>' +
            '  <p class="chat-compose-status" data-el="imageStatus" role="status" aria-live="polite"></p>' +
            '  <div class="chat-compose-row">' +
            '    <input class="chat-sr chat-image-input" id="' + fileId + '" data-el="image" type="file" accept="image/jpeg,image/png" disabled>' +
            '    <label class="btn chat-attach-btn" for="' + fileId + '" title="이미지 첨부 (JPG·PNG, 최대 10MB)" data-write>' + IMAGE_ICON +
            '      <span class="chat-sr">이미지 첨부 (JPG·PNG, 최대 10MB)</span></label>' +
            '    <div class="input-area" data-el="input" contenteditable="false" role="textbox" aria-label="메시지 입력" data-placeholder="메시지 입력 (Enter로 보내기)"></div>' +
            '    <button type="button" class="btn btn-primary" data-el="send" data-write disabled>보내기</button>' +
            '  </div>' +
            '</div>';
    }

    function mount(container, opts) {
        const roomId = String(opts.roomId);
        const employeeId = String(opts.me);
        const scheme = location.protocol === "https:" ? "wss://" : "ws://";
        const root = document.createElement("section");
        root.className = "chat-main chat-conv";
        root.innerHTML = template("chatImage" + (++seq));
        container.replaceChildren(root);
        const el = name => $(root.querySelector('[data-el="' + name + '"]'));

        let ws;
        let reconnectTimer;
        let leaving = false;
        let recovering = true;
        let ready = false;
        let pendingMessages = [];
        let pendingSend = null;
        let pendingImage = null;
        const imageInput = el("image");
        let attachFile = null;
        let attachUrl = null;
        let uploading = false;
        let lastMessageId = "0";
        let recoveryCursor = "0";
        const displayedMessageIds = new Set();
        const area = el("area");
        const list = el("list");
        const input = el("input");
        let loadingOlder = false;
        let readInFlight = false;
        let readBlocked = false;
        let lastReadId = "0";
        let otherLastReadId = "0";
        let receiptTimer;
        let receiptInFlight = false;
        let receiptStopped = false;
        let stickToBottom = true;
        let resizeObserver;
        const cleanups = [];

        function listen(target, type, handler, capture) {
            target.addEventListener(type, handler, capture);
            cleanups.push(() => target.removeEventListener(type, handler, capture));
        }

        function setTitle(title) {
            const text = title || "채팅방";
            el("title").text(text).attr("title", text);
            root.setAttribute("aria-label", text);
        }
        setTitle(opts.title);

        if (opts.onBack) el("back").on("click", opts.onBack);
        else el("back").prop("hidden", true);

        /** 메시지 한 줄(이름, 말풍선이나 이미지, 시각)을 만든다 */
        function messageRow(message) {
            const mine = String(message.senderId) === employeeId;
            const created = new Date(toMillis(message.createdAt));
            const row = $("<div>").addClass("chat-msg").toggleClass("is-mine", mine).attr({
                "data-message-id": String(message.id),
                "data-sender-id": String(message.senderId),
                "data-created": String(created.getTime())
            });
            let content;
            if (message.attachmentId) {
                const src = `${ctx}/chat/room/${roomId}/messages/${encodeURIComponent(message.id)}/image`;
                content = $("<a>").addClass("chat-image-link").attr({
                    href: src, target: "_blank", rel: "noopener",
                    "aria-label": (message.sender || "") + " 님이 보낸 이미지 원본 보기"
                }).append($("<img>").addClass("chat-image").attr({ src: src, alt: "채팅 이미지" }));
            } else {
                // dir=auto: 아랍어처럼 오른쪽에서 쓰는 글도 이름·시각과 섞이지 않고 제 방향으로 보인다
                content = $("<div>").addClass("chat-bubble").attr("dir", "auto").text(message.text || "");
            }
            const meta = $("<div>").addClass("chat-meta").append($("<time>").addClass("chat-time")
                .attr("datetime", created.toISOString())
                .text(created.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })));
            return row.append(
                $("<div>").addClass("chat-sender").text(message.sender || ""),
                $("<div>").addClass("chat-line").append(content, meta));
        }

        function fromServer(message) {
            return {
                id: String(message.chatMessageId), senderId: message.senderEmployeeId,
                sender: message.senderName, text: message.messageText,
                attachmentId: message.attachmentId, createdAt: message.createdAt
            };
        }

        /** 날짜 구분선, 같은 사람의 연속 메시지 묶기, 같은 분의 시각 숨기기, 빈 방 안내를 다시 맞춘다 */
        function layoutRows() {
            list.children(".chat-date").remove();
            let prev = null;
            list.children("[data-message-id]").each(function () {
                const row = $(this);
                const time = Number(this.getAttribute("data-created"));
                const day = new Date(time).toDateString();
                const sender = this.getAttribute("data-sender-id");
                if (!prev || prev.day !== day) {
                    row.before($("<div>").addClass("chat-date").text(new Date(time).toLocaleDateString("ko-KR",
                        { year: "numeric", month: "long", day: "numeric", weekday: "long" })));
                }
                const continued = !!prev && prev.day === day && prev.sender === sender && time - prev.time < 5 * 60 * 1000;
                row.toggleClass("is-continued", continued);
                if (prev) {
                    prev.row.toggleClass("hide-time", continued
                        && Math.floor(prev.time / 60000) === Math.floor(time / 60000));
                }
                prev = { row: row, time: time, day: day, sender: sender };
            });
            if (prev) prev.row.removeClass("hide-time");
            el("empty").prop("hidden", !!prev || !ready);
        }

        function scrollToBottom() { area.scrollTop(area[0].scrollHeight); }

        // 대화 높이가 바뀌면(이미지·글꼴 로딩, 창 크기) 맨 아래를 보던 사람은 계속 맨 아래에 둔다
        if (window.ResizeObserver) {
            resizeObserver = new ResizeObserver(function () {
                if (stickToBottom) scrollToBottom();
            });
            resizeObserver.observe(list[0]);
            resizeObserver.observe(area[0]);
        }

        // 이미지가 칸보다 작으면 늘리지 않고, 못 불러오면 안내 칸으로 바꾼다 (load·error 는 위로 퍼지지 않아 capture 로 받는다)
        area[0].addEventListener("load", function (e) {
            const img = e.target;
            if (!img.matches || !img.matches("img.chat-image")) return;
            $(img).toggleClass("is-small", img.naturalWidth <= img.clientWidth && img.naturalHeight <= img.clientHeight);
        }, true);
        area[0].addEventListener("error", function (e) {
            const img = e.target;
            if (!img.matches || !img.matches("img.chat-image")) return;
            $(img).closest(".chat-image-link").replaceWith(
                $("<div>").addClass("chat-image-broken").text("이미지를 불러오지 못했습니다"));
        }, true);

        // ---------- 참여자: 이름을 누르면 1:1 대화 버튼 (본인 제외) ----------
        const membersToggle = el("membersToggle");
        const membersPop = el("membersPop");
        function setMembersOpen(open) {
            membersPop.prop("hidden", !open);
            membersToggle.attr("aria-expanded", String(open));
        }
        membersToggle.on("click", function () { setMembersOpen(membersPop.prop("hidden")); });
        listen(document, "click", function (e) {
            if (!membersPop.prop("hidden") && !e.target.closest(".chat-members-wrap")) setMembersOpen(false);
        });
        root.addEventListener("keydown", function (e) {
            if (e.key === "Escape" && !membersPop.prop("hidden")) {
                e.stopPropagation();
                setMembersOpen(false);
                membersToggle.trigger("focus");
            }
        });

        function renderMembers(members) {
            membersToggle.text("참여자 " + members.length.toLocaleString("ko-KR") + "명");
            const ul = el("memberList").empty();
            members.forEach(function (m) {
                const me = String(m.employeeId) === employeeId;
                const li = $("<li>").addClass("chat-member");
                const label = (m.employeeName || "") + (m.departmentName ? " · " + m.departmentName : "");
                if (me) {
                    li.append($("<span>").addClass("chat-member-name").text(label + " (나)"));
                } else if (opts.roomType === "ROOM_DIRECT") {
                    li.append($("<span>").addClass("chat-member-name").text(label));
                } else {
                    const action = $("<button>").attr({ type: "button", "data-write": "" })
                        .addClass("btn btn-primary chat-member-direct").text("1:1 대화").prop("hidden", true)
                        .on("click", function () { openDirect(m.employeeId, m.employeeName, $(this)); });
                    const name = $("<button>").attr({ type: "button", "aria-expanded": "false" })
                        .addClass("chat-member-name is-button").text(label)
                        .on("click", function () {
                            const open = action.prop("hidden");
                            ul.find(".chat-member-direct").prop("hidden", true);
                            ul.find(".chat-member-name.is-button").attr("aria-expanded", "false");
                            action.prop("hidden", !open);
                            $(this).attr("aria-expanded", String(open));
                        });
                    li.append(name, action);
                }
                ul.append(li);
            });
        }

        function loadMembers() {
            $.ajax({ url: `${ctx}/chat/room/${roomId}/members`, dataType: "json", cache: false })
                .done(function (members) { if (!leaving) renderMembers(members || []); })
                .fail(function (xhr) { if (xhr.status === 401) goLogin(); });
        }

        /** 1:1 대화: 서버가 기존 방이나 새 방 주소로 보내 주므로(redirect) 그 주소에서 방 번호를 꺼내 연다 */
        function openDirect(otherId, otherName, button) {
            if (button.prop("disabled")) return;
            button.prop("disabled", true).text("여는 중…");
            const body = new URLSearchParams({ roomType: "ROOM_DIRECT", members: String(otherId) });
            const headers = { "Content-Type": "application/x-www-form-urlencoded" };
            const csrfHeader = $('meta[name="_csrf_header"]').attr("content");
            const csrfToken = $('meta[name="_csrf"]').attr("content");
            if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
            fetch(`${ctx}/chat/createroom`, { method: "POST", body: body, headers: headers, credentials: "same-origin" })
                .then(function (res) {
                    const match = /\/chat\/room\/(\d+)\/?$/.exec(new URL(res.url).pathname);
                    if (res.ok && match) {
                        if (window.ChatUpdates) ChatUpdates.refresh();
                        if (opts.onOpenRoom) opts.onOpenRoom(match[1], otherName, "ROOM_DIRECT");
                        else location.href = `${ctx}/chat/room/${match[1]}`;
                        return;
                    }
                    if (res.status === 401 || /\/login/.test(res.url)) { goLogin(); return; }
                    throw new Error(String(res.status));
                })
                .catch(function () {
                    toast("1:1 대화를 열지 못했습니다. 이용 상태를 확인하고 다시 시도해 주세요.");
                    button.prop("disabled", false).text("1:1 대화");
                });
        }

        // ---------- 이전 대화 ----------
        el("loadOlder").on("click", function () {
            const button = $(this);
            const oldestId = list.children("[data-message-id]").first().attr("data-message-id");
            if (button.prop("disabled") || !oldestId) return;
            button.prop("disabled", true);
            loadingOlder = true;
            $.ajax({
                url: `${ctx}/chat/room/${roomId}/messages`,
                data: { beforeMessageId: oldestId },
                dataType: "json"
            }).done(function (messages) {
                if (leaving) return;
                const previousHeight = area[0].scrollHeight;
                const previousTop = area.scrollTop();
                // 오름차순 결과를 앞에 붙이므로 뒤에서부터 처리한다.
                for (let i = messages.length - 1; i >= 0; i--) {
                    const message = fromServer(messages[i]);
                    if (displayedMessageIds.has(message.id)) continue;
                    displayedMessageIds.add(message.id);
                    list.prepend(messageRow(message));
                }
                layoutRows();
                updateReadReceipts();
                area.scrollTop(previousTop + area[0].scrollHeight - previousHeight);
                if (messages.length < 50) button.prop("hidden", true);
            }).fail(function (xhr) {
                if (xhr.status === 401) { goLogin(); return; }
                toast("이전 대화를 불러오지 못했습니다.");
            }).always(function () {
                button.prop("disabled", false);
                loadingOlder = false;
            });
        });

        function connectionStatus(text, isReady) {
            el("connection").text(text).attr("data-ready", String(isReady));
            input.attr("contenteditable", isReady ? "true" : "false");
            el("retrySend").prop("disabled", !isReady);
            el("send").prop("disabled", !isReady);
            imageInput.prop("disabled", !isReady || uploading);
        }

        function confirmSend(key, senderId) {
            if (!pendingSend || pendingSend.clientMessageKey !== key
                || String(senderId) !== employeeId) return;
            if (input.text().trim() === pendingSend.messageText) input.text("");
            pendingSend = null;
            el("sendStatus").text("");
            el("retrySend").prop("hidden", true);
        }

        function sendPending() {
            if (!pendingSend || leaving || recovering
                || !ws || ws.readyState !== WebSocket.OPEN) return;
            try {
                ws.send(JSON.stringify(pendingSend));
                el("sendStatus").text("서버 저장 확인 중입니다. 확인 전까지 입력 내용을 유지합니다.");
            } catch (e) {
                el("sendStatus").text("전송하지 못했습니다. 연결 후 다시 시도해 주세요.");
            }
            el("retrySend").prop("hidden", false);
        }
        el("retrySend").on("click", sendPending);

        // ---------- 이미지 첨부 ----------
        function clearAttachment() {
            if (attachUrl) URL.revokeObjectURL(attachUrl);
            attachUrl = null;
            attachFile = null;
            pendingImage = null;
            imageInput.val("");
            el("attach").prop("hidden", true);
            el("attachThumb").removeAttr("src");
            el("attachName").text("").removeAttr("title");
            el("attachSize").text("");
        }

        // 이미지를 고르면 형식·크기를 바로 보고, 맞으면 입력 칸 위에 미리보기를 띄운다(보내기는 보내기 버튼·Enter)
        imageInput.on("change", function () {
            const file = this.files[0];
            if (!file) return; // 고르기 창을 그냥 닫으면 이미 고른 이미지를 그대로 둔다
            el("imageStatus").text("");
            if (file.size === 0 || file.size > 10 * 1024 * 1024 || !/\.(jpe?g|png)$/i.test(file.name)) {
                this.value = "";
                el("imageStatus").text("10MB 이하의 JPG·PNG 이미지 한 장을 골라 주세요.");
                return;
            }
            clearAttachment();
            attachFile = file;
            attachUrl = URL.createObjectURL(file);
            el("attachThumb").attr("src", attachUrl);
            el("attachName").text(file.name).attr("title", file.name);
            el("attachSize").text(fileSize(file.size));
            el("attach").prop("hidden", false);
            input.trigger("focus");
        });
        el("attachRemove").on("click", function () {
            if (uploading) return;
            clearAttachment();
            el("imageStatus").text("");
            input.trigger("focus");
        });

        /** 고른 이미지를 올린다. 성공하면 미리보기를 지우고 done 을 부른다(같이 쓴 글을 이미지 다음에 보내려고) */
        function sendImage(done) {
            if (!attachFile || uploading || leaving || recovering
                || !ws || ws.readyState !== WebSocket.OPEN) return;
            if (!pendingImage) pendingImage = crypto.randomUUID();
            const data = new FormData();
            data.append("clientMessageKey", pendingImage);
            data.append("image", attachFile);
            uploading = true;
            imageInput.prop("disabled", true);
            el("attach").addClass("is-sending");
            el("imageStatus").text("이미지를 보내고 있습니다.");
            // CSRF 헤더는 공통 스크립트(common.js)가 붙인다
            $.ajax({
                url: `${ctx}/chat/room/${roomId}/images`,
                method: "POST",
                data: data,
                processData: false,
                contentType: false,
                dataType: "json"
            }).done(function (message) {
                if (leaving) return;
                appendMessage(fromServer(message));
                clearAttachment();
                el("imageStatus").text("");
                markRead();
                if (done) done();
            }).fail(function (xhr) {
                // 실패하면 미리보기와 같이 쓴 글을 남겨 두어 보내기를 다시 누를 수 있게 한다(같은 키라 두 번 올라가지 않는다)
                if (xhr.status === 401) goLogin();
                else if (xhr.status === 403 || xhr.status === 409) el("imageStatus").text("이미지를 보낼 권한이 없거나 이용 기간이 끝났습니다.");
                else if (xhr.status === 400 || xhr.status === 413) el("imageStatus").text("이미지 형식과 크기를 확인해 주세요. JPG·PNG 한 장, 최대 10MB입니다.");
                else el("imageStatus").text("전송을 확인하지 못했습니다. 보내기를 다시 누르면 같은 이미지로 확인합니다.");
            }).always(function () {
                uploading = false;
                el("attach").removeClass("is-sending");
                imageInput.prop("disabled", leaving || recovering || !ws || ws.readyState !== WebSocket.OPEN);
            });
        }

        function appendMessage(message) {
            if (displayedMessageIds.has(message.id)) return;
            const followLatest = visible() && atBottom();
            // 따라 내려가지 않을 때는 높이 변화로도 내려가지 않게 한다(안 본 메시지를 읽음으로 저장하지 않게)
            if (!followLatest) stickToBottom = false;
            displayedMessageIds.add(message.id);
            const row = messageRow(message);
            const next = list.children("[data-message-id]").filter(function () {
                return BigInt(this.getAttribute("data-message-id")) > BigInt(message.id);
            }).first();
            if (next.length) next.before(row);
            else list.append(row);
            if (BigInt(message.id) > BigInt(lastMessageId)) lastMessageId = message.id;
            layoutRows();
            updateReadReceipts();
            if (followLatest) scrollToBottom();
        }

        function updateReadReceipts() {
            list.children("[data-message-id]").each(function () {
                const row = $(this);
                row.find(".read-receipt").remove();
                if (this.getAttribute("data-sender-id") === employeeId
                    && BigInt(this.getAttribute("data-message-id")) <= BigInt(otherLastReadId)) {
                    row.find(".chat-meta").prepend($("<span>").addClass("read-receipt").text("읽음"));
                }
            });
        }

        function refreshReadReceipts() {
            clearTimeout(receiptTimer);
            if (leaving || recovering || document.hidden || receiptStopped
                || receiptInFlight || !ws || ws.readyState !== WebSocket.OPEN) return;
            receiptInFlight = true;
            $.ajax({
                url: `${ctx}/chat/room/${roomId}/read`,
                dataType: "json",
                cache: false
            }).done(function (data) {
                if (leaving || document.hidden) return;
                otherLastReadId = String(data.lastReadMessageId || "0");
                updateReadReceipts();
                el("receiptStatus").text("");
            }).fail(function (xhr) {
                if (leaving) return;
                if (xhr.status === 401) {
                    destroy();
                    goLogin();
                } else if (xhr.status === 403) {
                    receiptStopped = true;
                    el("receiptStatus").text("상대 읽음 정보를 조회할 권한이 없습니다.");
                } else {
                    el("receiptStatus").text("상대 읽음 정보를 불러오지 못했습니다. 잠시 후 다시 확인합니다.");
                }
            }).always(function () {
                receiptInFlight = false;
                if (!leaving && !document.hidden && !receiptStopped
                    && ws && ws.readyState === WebSocket.OPEN) {
                    receiptTimer = setTimeout(refreshReadReceipts, 5000);
                }
            });
        }
        listen(document, "visibilitychange", refreshReadReceipts);

        function atBottom() {
            return area[0].scrollHeight - area.scrollTop() - area[0].clientHeight <= 2;
        }

        /** 사람이 이 대화를 보고 있는지: 탭이 앞에 있고 창에 포커스가 있으며 칸이 화면에 보인다 */
        function visible() {
            return !document.hidden && document.hasFocus() && root.isConnected && root.getClientRects().length > 0;
        }

        function markRead() {
            if (leaving || recovering || loadingOlder || readInFlight || readBlocked
                || !visible() || !atBottom()
                || !ws || ws.readyState !== WebSocket.OPEN
                || BigInt(lastMessageId) <= BigInt(lastReadId)) return;
            const messageId = lastMessageId;
            readInFlight = true;
            el("retryRead").prop("hidden", true);
            let saved = false;
            $.ajax({
                url: `${ctx}/chat/room/${roomId}/read`,
                method: "POST",
                data: { messageId: messageId }
            }).done(function () {
                lastReadId = messageId;
                saved = true;
                el("readStatus").text("");
            }).fail(function (xhr) {
                if (leaving) return;
                if (xhr.status === 401) {
                    destroy();
                    goLogin();
                } else if (xhr.status === 403 || xhr.status === 409) {
                    readBlocked = true;
                    el("readStatus").text("읽음 처리가 허용되지 않습니다. 이용 상태와 권한을 확인해 주세요.");
                } else {
                    el("readStatus").text("읽음 정보를 저장하지 못했습니다.");
                    el("retryRead").prop("hidden", false);
                }
            }).always(function () {
                readInFlight = false;
                // 저장 중 새 메시지를 읽었으면 이어서 최신 위치를 저장한다.
                if (saved) markRead();
            });
        }

        area.on("scroll", function () {
            stickToBottom = atBottom();
            markRead();
        });
        el("retryRead").on("click", markRead);
        listen(document, "visibilitychange", markRead);
        listen(window, "focus", markRead);
        // 패널 안에서 대화 칸을 누르면(창 포커스가 이미 있으면 focus 가 안 온다) 그때 읽음을 저장한다
        root.addEventListener("pointerdown", function () { setTimeout(markRead, 0); });

        function scheduleReconnect() {
            if (leaving || reconnectTimer) return;
            connectionStatus("연결이 끊겼습니다. 잠시 후 다시 연결합니다.", false);
            reconnectTimer = setTimeout(function () {
                reconnectTimer = null;
                connect();
            }, 3000);
        }

        function recoverMessages(socket, cursor) {
            $.ajax({
                url: `${ctx}/chat/room/${roomId}/messages/after`,
                data: { afterMessageId: cursor },
                dataType: "json"
            }).done(function (messages) {
                if (leaving || socket !== ws || socket.readyState !== WebSocket.OPEN) return;
                messages.forEach(function (message) {
                    confirmSend(message.clientMessageKey, message.senderEmployeeId);
                    appendMessage(fromServer(message));
                });
                if (messages.length > 0) recoveryCursor = String(messages[messages.length - 1].chatMessageId);
                // 실시간 수신 ID가 아닌 조회 결과의 마지막 ID로 다음 페이지를 요청한다.
                if (messages.length === 50) {
                    recoverMessages(socket, String(messages[messages.length - 1].chatMessageId));
                    return;
                }
                pendingMessages.forEach(function (data) { appendMessage(fromSocket(data)); });
                pendingMessages = [];
                recovering = false;
                connectionStatus("연결됨", true);
                sendPending();
                markRead();
                refreshReadReceipts();
            }).fail(function (xhr) {
                if (leaving || socket !== ws || socket.readyState !== WebSocket.OPEN) return;
                socket.close(1000);
                if (xhr.status === 401) {
                    destroy();
                    goLogin();
                } else if (xhr.status === 403) {
                    connectionStatus("채팅방 접근 권한이 없습니다.", false);
                } else {
                    scheduleReconnect();
                }
            });
        }

        // 실시간 메시지에는 보낸 시각이 없어 받은 시각을 쓴다(서버가 저장한 직후에 보낸다)
        function fromSocket(data) {
            return {
                id: String(data.messageId), senderId: data.senderEmployeeId, sender: data.sender,
                text: data.content, attachmentId: data.attachmentId
            };
        }

        function connect() {
            if (leaving) return;
            recovering = true;
            pendingMessages = [];
            connectionStatus("연결 및 대화 복구 중입니다.", false);
            const socket = new WebSocket(`${scheme}${location.host}${ctx}/chat/room/${roomId}`);
            ws = socket;
            socket.onopen = function () { recoverMessages(socket, recoveryCursor); };
            socket.onclose = function (e) {
                if (leaving || socket !== ws) return;
                clearTimeout(receiptTimer);
                connectionStatus("연결이 종료되었습니다.", false);
                if (e.code === 1009 && pendingSend) {
                    pendingSend = null;
                    el("retrySend").prop("hidden", true);
                    el("sendStatus").text("메시지가 너무 길어 전송하지 못했습니다. 내용을 줄여 다시 보내 주세요.");
                }
                if (e.code !== 1000 && e.code !== 1008) scheduleReconnect();
            };
            socket.onmessage = function (e) {
                if (leaving || socket !== ws) return;
                const data = JSON.parse(e.data);
                // 접속 중 목록(MEMBERS)은 화면에서 뺐다. 참여자는 /members 로 받는다
                if (data.type === "MSG") {
                    confirmSend(data.clientMessageKey, data.senderEmployeeId);
                    if (recovering) pendingMessages.push(data);
                    else {
                        appendMessage(fromSocket(data));
                        markRead();
                    }
                } else if (data.type === "ERROR") {
                    if (pendingSend && pendingSend.clientMessageKey === data.clientMessageKey) {
                        pendingSend = null;
                        el("retrySend").prop("hidden", true);
                        el("sendStatus").text("저장하지 못했습니다. 입력 내용을 확인한 뒤 다시 보내 주세요.");
                    }
                    toast(data.message);
                }
            };
        }

        /** 처음 50개를 받아 그린 뒤 웹소켓을 연다(그 사이에 온 메시지는 연결 뒤 복구 조회가 채운다) */
        function loadFirst() {
            $.ajax({ url: `${ctx}/chat/room/${roomId}/messages`, dataType: "json", cache: false })
                .done(function (messages) {
                    if (leaving) return;
                    ready = true;
                    el("loading").remove();
                    messages.forEach(function (m) {
                        const message = fromServer(m);
                        displayedMessageIds.add(message.id);
                        list.append(messageRow(message));
                        if (BigInt(message.id) > BigInt(lastMessageId)) lastMessageId = message.id;
                    });
                    // 실시간 수신 순서가 뒤바뀌어도 재접속 조회가 앞선 메시지를 건너뛰지 않게 한다.
                    recoveryCursor = lastMessageId;
                    el("loadOlder").prop("hidden", messages.length < 50);
                    layoutRows();
                    scrollToBottom();
                    connect();
                })
                .fail(function (xhr) {
                    if (leaving) return;
                    if (xhr.status === 401) { goLogin(); return; }
                    el("loading").text(xhr.status === 403 || xhr.status === 404
                        ? "이 채팅방에 들어갈 수 없습니다." : "대화를 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.");
                    connectionStatus("", false);
                });
        }

        listen(window, "pagehide", function () {
            leaving = true;
            clearTimeout(reconnectTimer);
            clearTimeout(receiptTimer);
            reconnectTimer = null;
            if (ws) ws.close();
        });
        listen(window, "pageshow", function (e) {
            if (e.persisted && root.isConnected) {
                leaving = false;
                connect();
            }
        });

        // ---------- 보내기 ----------
        /** 입력 칸의 글을 보낸다 (Enter 키와 보내기 버튼이 같이 쓴다) */
        function sendText() {
            const text = input.text().trim();
            if (text === "" || pendingSend || recovering || !ws || ws.readyState !== WebSocket.OPEN) return;
            if (Array.from(text).length > 1000) {
                el("sendStatus").text("메시지는 1,000자까지 보낼 수 있습니다.");
                return;
            }
            pendingSend = { clientMessageKey: crypto.randomUUID(), messageText: text };
            sendPending();
        }

        /** 보내기: 고른 이미지가 있으면 먼저 올리고, 성공한 뒤 같이 쓴 글을 보낸다 */
        function sendCompose() {
            if (attachFile) sendImage(sendText);
            else sendText();
        }

        input.on("keydown", function (e) {
            // 한글 조합 중 Enter 는 글자 확정이라 보내지 않는다(조합 중 keyCode 는 229)
            if (e.keyCode === 13 && !e.originalEvent.isComposing) {
                e.preventDefault();
                sendCompose();
            }
        });
        el("send").on("click", function () {
            sendCompose();
            input.trigger("focus");
        });

        function destroy() {
            if (leaving && !root.isConnected) return;
            leaving = true;
            clearTimeout(reconnectTimer);
            clearTimeout(receiptTimer);
            reconnectTimer = null;
            const socket = ws;
            ws = null;
            if (socket) socket.close(1000);
            if (resizeObserver) resizeObserver.disconnect();
            if (attachUrl) URL.revokeObjectURL(attachUrl);
            cleanups.forEach(function (fn) { fn(); });
            root.remove();
        }

        loadMembers();
        loadFirst();

        return {
            roomId: roomId,
            setTitle: setTitle,
            focus: function () { input.trigger("focus"); },
            destroy: destroy
        };
    }

    window.ChatConversation = { mount: mount };
})(jQuery);
