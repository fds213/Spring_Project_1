<%@ page language="java" contentType="text/html; charset=UTF-8"
pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<%-- 모델 속성: roomId, employeeId, messageList (오래된 메시지부터 최대 50개), roomMembers (참여자 이름) --%>
<%-- 방 이름은 방 목록과 같은 주소(GET /chat/unread)에서 받아 위쪽 제목에 넣는다 --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<ui:layout title="채팅방" crumb="채팅" menu="chat">
  <jsp:attribute name="scripts">
    <script>
        $(function() {
            // 방 번호는 컨트롤러가 모델(roomId, long)로 넘긴다
            var roomId = ${roomId};
            var employeeId = "${employeeId}";
            var scheme = location.protocol === "https:" ? "wss://" : "ws://";
            var ws;
            var reconnectTimer;
            var leaving = false;
            var recovering = true;
            var pendingMessages = [];
            var pendingSend = null;
            var pendingImage = null;
            var imageInput = $("#chatImage");
            // 고른 이미지(보내기 전 미리보기). 보내기에 성공하거나 ✕를 누를 때까지 남는다
            var attachFile = null;
            var attachUrl = null;
            var uploading = false;
            var lastMessageId = "0";
            var displayedMessageIds = new Set();
            var area = $(".message-area");
            var list = $("#chatList");
            var loadingOlder = false;
            var readInFlight = false;
            var readBlocked = false;
            var lastReadId = "0";
            var otherLastReadId = "0";
            var receiptTimer;
            var receiptInFlight = false;
            var receiptStopped = false;
            // 사용자가 맨 아래를 보고 있는지. 이미지·글꼴을 늦게 불러와 대화 높이가 바뀌어도 맨 아래를 지킨다
            var stickToBottom = true;

            /** 서버 시각(밀리초 숫자·숫자 글자·날짜 글자)을 밀리초로 바꾼다. 없으면 지금 시각을 쓴다 */
            function toMillis(value) {
                var millis = typeof value === "number" ? value
                    : /^\d+$/.test(String(value)) ? Number(value) : Date.parse(value);
                return isNaN(millis) || !millis ? Date.now() : millis;
            }

            /** 메시지 한 줄(이름, 말풍선이나 이미지, 시각)을 만든다 */
            function messageRow(message) {
                var mine = String(message.senderId) === employeeId;
                var created = new Date(toMillis(message.createdAt));
                var row = $("<div>").addClass("chat-msg").toggleClass("is-mine", mine).attr({
                    "data-message-id": message.id,
                    "data-sender-id": String(message.senderId),
                    "data-created": String(created.getTime())
                });
                var content;
                if (message.attachmentId) {
                    var src = "${ctx}/chat/room/" + roomId + "/messages/" + encodeURIComponent(message.id) + "/image";
                    content = $("<a>").addClass("chat-image-link").attr({
                        href: src, target: "_blank", rel: "noopener",
                        "aria-label": (message.sender || "") + " 님이 보낸 이미지 원본 보기"
                    }).append($("<img>").addClass("chat-image").attr({ src: src, alt: "채팅 이미지" }));
                } else {
                    // dir=auto: 아랍어처럼 오른쪽에서 쓰는 글도 이름·시각과 섞이지 않고 제 방향으로 보인다
                    content = $("<div>").addClass("chat-bubble").attr("dir", "auto").text(message.text || "");
                }
                var meta = $("<div>").addClass("chat-meta").append($("<time>").addClass("chat-time")
                    .attr("datetime", created.toISOString())
                    .text(created.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })));
                return row.append(
                    $("<div>").addClass("chat-sender").text(message.sender || ""),
                    $("<div>").addClass("chat-line").append(content, meta));
            }

            /** 날짜 구분선, 같은 사람의 연속 메시지 묶기, 같은 분의 시각 숨기기, 빈 방 안내를 다시 맞춘다 */
            function layoutRows() {
                list.children(".chat-date").remove();
                var prev = null;
                list.children("[data-message-id]").each(function() {
                    var row = $(this);
                    var time = Number(this.getAttribute("data-created"));
                    var day = new Date(time).toDateString();
                    var sender = this.getAttribute("data-sender-id");
                    if (!prev || prev.day !== day) {
                        row.before($("<div>").addClass("chat-date").text(new Date(time).toLocaleDateString("ko-KR",
                            { year: "numeric", month: "long", day: "numeric", weekday: "long" })));
                    }
                    var continued = !!prev && prev.day === day && prev.sender === sender && time - prev.time < 5 * 60 * 1000;
                    row.toggleClass("is-continued", continued);
                    if (prev) {
                        prev.row.toggleClass("hide-time", continued
                            && Math.floor(prev.time / 60000) === Math.floor(time / 60000));
                    }
                    prev = { row: row, time: time, day: day, sender: sender };
                });
                if (prev) prev.row.removeClass("hide-time");
                $("#chatEmpty").prop("hidden", !!prev);
            }

            // JSP가 그린 처음 메시지(값만 담은 줄)를 화면 JS와 같은 모양으로 바꾼다
            list.children("[data-message-id]").each(function() {
                $(this).replaceWith(messageRow({
                    id: this.getAttribute("data-message-id"),
                    senderId: this.getAttribute("data-sender-id"),
                    sender: this.getAttribute("data-sender-name"),
                    text: this.textContent,
                    attachmentId: this.getAttribute("data-attachment-id"),
                    createdAt: this.getAttribute("data-created")
                }));
            });
            layoutRows();

            list.children("[data-message-id]").each(function() {
                var id = this.getAttribute("data-message-id");
                displayedMessageIds.add(id);
                if (BigInt(id) > BigInt(lastMessageId)) lastMessageId = id;
            });
            // 실시간 수신 순서가 뒤바뀌어도 재접속 조회가 앞선 메시지를 건너뛰지 않게 한다.
            var recoveryCursor = lastMessageId;
            area.scrollTop(area[0].scrollHeight);

            // 대화 높이가 바뀌면(이미지·글꼴 로딩, 창 크기) 맨 아래를 보던 사람은 계속 맨 아래에 둔다
            if (window.ResizeObserver) {
                new ResizeObserver(function() {
                    if (stickToBottom) area.scrollTop(area[0].scrollHeight);
                }).observe(list[0]);
            }

            // 이미지가 칸보다 작으면 늘리지 않고, 못 불러오면 안내 칸으로 바꾼다 (load·error 는 위로 퍼지지 않아 capture 로 받는다)
            area[0].addEventListener("load", function(e) {
                var img = e.target;
                if (!img.matches || !img.matches("img.chat-image")) return;
                $(img).toggleClass("is-small", img.naturalWidth <= img.clientWidth && img.naturalHeight <= img.clientHeight);
            }, true);
            area[0].addEventListener("error", function(e) {
                var img = e.target;
                if (!img.matches || !img.matches("img.chat-image")) return;
                $(img).closest(".chat-image-link").replaceWith(
                    $("<div>").addClass("chat-image-broken").text("이미지를 불러오지 못했습니다"));
            }, true);

            // 방 이름: 방 목록과 같은 응답에서 이 방을 찾아 제목에 넣는다 (1:1 방은 상대 이름)
            api.get("/chat/unread").done(function(unread) {
                var room = (unread.rooms || []).find(function(r) { return String(r.chatRoomId) === String(roomId); });
                if (!room || !room.displayName) return;
                $("#chatRoomName").text(room.displayName).attr("title", room.displayName);
                document.title = room.displayName;
            });

            $("#toggleMembers").on("click", function() {
                var open = !$(".chat-room").hasClass("show-members");
                $(".chat-room").toggleClass("show-members", open);
                $(this).attr("aria-expanded", String(open));
            });

            $("#loadOlder").on("click", function() {
                var button = $(this);
                var oldestId = list.children("[data-message-id]")
                    .first().attr("data-message-id");

                if (button.prop("disabled") || !oldestId) return;
                button.prop("disabled", true);
                loadingOlder = true;

                $.ajax({
                    url: "${ctx}/chat/room/" + roomId + "/messages",
                    data: { beforeMessageId: oldestId },
                    dataType: "json"
                }).done(function(messages) {
                    var previousHeight = area[0].scrollHeight;
                    var previousTop = area.scrollTop();

                    // 오름차순 결과를 앞에 붙이므로 뒤에서부터 처리한다.
                    for (var i = messages.length - 1; i >= 0; i--) {
                        var message = messages[i];
                        if (displayedMessageIds.has(message.chatMessageId)) continue;
                        displayedMessageIds.add(message.chatMessageId);

                        list.prepend(messageRow({
                            id: message.chatMessageId, senderId: message.senderEmployeeId,
                            sender: message.senderName, text: message.messageText,
                            attachmentId: message.attachmentId, createdAt: message.createdAt
                        }));
                    }

                    layoutRows();
                    updateReadReceipts();
                    area.scrollTop(previousTop + area[0].scrollHeight - previousHeight);
                    if (messages.length < 50) button.prop("hidden", true);
                }).fail(function(xhr) {
                    if (xhr.status === 401) {
                        location.href = "${ctx}/login";
                        return;
                    }
                    alert("이전 대화를 불러오지 못했습니다.");
                }).always(function() {
                    button.prop("disabled", false);
                    loadingOlder = false;
                });
            });

            function connectionStatus(text, ready) {
                $("#connectionStatus").text(text).attr("data-ready", String(ready));
                $(".input-area").attr("contenteditable", ready ? "true" : "false");
                $("#retrySend").prop("disabled", !ready);
                $("#sendText").prop("disabled", !ready);
                imageInput.prop("disabled", !ready || uploading);
            }

            function confirmSend(key, senderId) {
                if (!pendingSend || pendingSend.clientMessageKey !== key
                    || String(senderId) !== employeeId) return;
                if ($(".input-area").text().trim() === pendingSend.messageText) {
                    $(".input-area").text("");
                }
                pendingSend = null;
                $("#sendStatus").text("");
                $("#retrySend").prop("hidden", true);
            }

            function sendPending() {
                if (!pendingSend || leaving || recovering
                    || !ws || ws.readyState !== WebSocket.OPEN) return;
                try {
                    ws.send(JSON.stringify(pendingSend));
                    $("#sendStatus").text("서버 저장 확인 중입니다. 확인 전까지 입력 내용을 유지합니다.");
                } catch (e) {
                    $("#sendStatus").text("전송하지 못했습니다. 연결 후 다시 시도해 주세요.");
                }
                $("#retrySend").prop("hidden", false);
            }

            $("#retrySend").on("click", sendPending);

            /** 파일 크기를 "1.2MB"·"340KB"처럼 보인다 */
            function fileSize(bytes) {
                return bytes >= 1024 * 1024 ? (bytes / 1024 / 1024).toFixed(1) + "MB" : Math.max(1, Math.round(bytes / 1024)) + "KB";
            }

            /** 고른 이미지와 미리보기를 지운다 */
            function clearAttachment() {
                if (attachUrl) URL.revokeObjectURL(attachUrl);
                attachUrl = null;
                attachFile = null;
                pendingImage = null;
                imageInput.val("");
                $("#chatAttach").prop("hidden", true);
                $("#chatAttachThumb").removeAttr("src");
                $("#chatAttachName").text("").removeAttr("title");
                $("#chatAttachSize").text("");
            }

            // 이미지를 고르면 형식·크기를 바로 보고, 맞으면 입력 칸 위에 미리보기를 띄운다(보내기는 보내기 버튼·Enter)
            imageInput.on("change", function() {
                var file = this.files[0];
                if (!file) return; // 고르기 창을 그냥 닫으면 이미 고른 이미지를 그대로 둔다
                $("#imageStatus").text("");
                if (file.size === 0 || file.size > 10 * 1024 * 1024 || !/\.(jpe?g|png)$/i.test(file.name)) {
                    this.value = "";
                    $("#imageStatus").text("10MB 이하의 JPG·PNG 이미지 한 장을 골라 주세요.");
                    return;
                }
                clearAttachment();
                attachFile = file;
                attachUrl = URL.createObjectURL(file);
                $("#chatAttachThumb").attr("src", attachUrl);
                $("#chatAttachName").text(file.name).attr("title", file.name);
                $("#chatAttachSize").text(fileSize(file.size));
                $("#chatAttach").prop("hidden", false);
                $(".input-area").trigger("focus");
            });
            $("#chatAttachRemove").on("click", function() {
                if (uploading) return;
                clearAttachment();
                $("#imageStatus").text("");
                $(".input-area").trigger("focus");
            });

            /** 고른 이미지를 올린다. 성공하면 미리보기를 지우고 done 을 부른다(같이 쓴 글을 이미지 다음에 보내려고) */
            function sendImage(done) {
                if (!attachFile || uploading || leaving || recovering
                    || !ws || ws.readyState !== WebSocket.OPEN) return;
                if (!pendingImage) pendingImage = crypto.randomUUID();
                var data = new FormData();
                data.append("clientMessageKey", pendingImage);
                data.append("image", attachFile);
                uploading = true;
                imageInput.prop("disabled", true);
                $("#chatAttach").addClass("is-sending");
                $("#imageStatus").text("이미지를 보내고 있습니다.");
                // CSRF 헤더는 공통 스크립트(common.js)가 붙인다
                $.ajax({
                    url: "${ctx}/chat/room/" + roomId + "/images",
                    method: "POST",
                    data: data,
                    processData: false,
                    contentType: false,
                    dataType: "json"
                }).done(function(message) {
                    appendMessage(message.chatMessageId, message.senderEmployeeId,
                        message.senderName, message.messageText, message.attachmentId, message.createdAt);
                    clearAttachment();
                    $("#imageStatus").text("");
                    markRead();
                    if (done) done();
                }).fail(function(xhr) {
                    // 실패하면 미리보기와 같이 쓴 글을 남겨 두어 보내기를 다시 누를 수 있게 한다(같은 키라 두 번 올라가지 않는다)
                    if (xhr.status === 401) {
                        location.href = "${ctx}/login";
                    } else if (xhr.status === 403 || xhr.status === 409) {
                        $("#imageStatus").text("이미지를 보낼 권한이 없거나 이용 기간이 끝났습니다.");
                    } else if (xhr.status === 400 || xhr.status === 413) {
                        $("#imageStatus").text("이미지 형식과 크기를 확인해 주세요. JPG·PNG 한 장, 최대 10MB입니다.");
                    } else {
                        $("#imageStatus").text("전송을 확인하지 못했습니다. 보내기를 다시 누르면 같은 이미지로 확인합니다.");
                    }
                }).always(function() {
                    uploading = false;
                    $("#chatAttach").removeClass("is-sending");
                    imageInput.prop("disabled", leaving || recovering || !ws || ws.readyState !== WebSocket.OPEN);
                });
            }

            function appendMessage(id, senderId, sender, content, attachmentId, createdAt) {
                if (displayedMessageIds.has(id)) return;
                var followLatest = !document.hidden && document.hasFocus() && atBottom();
                // 따라 내려가지 않을 때는 높이 변화로도 내려가지 않게 한다(안 본 메시지를 읽음으로 저장하지 않게)
                if (!followLatest) stickToBottom = false;
                displayedMessageIds.add(id);
                var row = messageRow({
                    id: id, senderId: senderId, sender: sender, text: content,
                    attachmentId: attachmentId, createdAt: createdAt
                });
                var next = list.children("[data-message-id]").filter(function() {
                    return BigInt(this.getAttribute("data-message-id")) > BigInt(id);
                }).first();
                if (next.length) next.before(row);
                else list.append(row);
                if (BigInt(id) > BigInt(lastMessageId)) lastMessageId = id;
                layoutRows();
                updateReadReceipts();
                if (followLatest) area.scrollTop(area[0].scrollHeight);
            }

            function updateReadReceipts() {
                list.children("[data-message-id]").each(function() {
                    var row = $(this);
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
                    url: "${ctx}/chat/room/" + roomId + "/read",
                    dataType: "json",
                    cache: false
                }).done(function(data) {
                    if (leaving || document.hidden) return;
                    otherLastReadId = data.lastReadMessageId;
                    updateReadReceipts();
                    $("#receiptStatus").text("");
                }).fail(function(xhr) {
                    if (leaving) return;
                    if (xhr.status === 401) {
                        leaving = true;
                        clearTimeout(reconnectTimer);
                        if (ws) ws.close(1000);
                        location.href = "${ctx}/login";
                    } else if (xhr.status === 403) {
                        receiptStopped = true;
                        $("#receiptStatus").text("상대 읽음 정보를 조회할 권한이 없습니다.");
                    } else {
                        $("#receiptStatus").text("상대 읽음 정보를 불러오지 못했습니다. 잠시 후 다시 확인합니다.");
                    }
                }).always(function() {
                    receiptInFlight = false;
                    if (!leaving && !document.hidden && !receiptStopped
                        && ws && ws.readyState === WebSocket.OPEN) {
                        receiptTimer = setTimeout(refreshReadReceipts, 5000);
                    }
                });
            }

            document.addEventListener("visibilitychange", refreshReadReceipts);

            function atBottom() {
                return area[0].scrollHeight - area.scrollTop() - area[0].clientHeight <= 2;
            }

            function markRead() {
                if (leaving || recovering || loadingOlder || readInFlight || readBlocked
                    || document.hidden || !document.hasFocus() || !atBottom()
                    || !ws || ws.readyState !== WebSocket.OPEN
                    || BigInt(lastMessageId) <= BigInt(lastReadId)) return;

                var messageId = lastMessageId;
                readInFlight = true;
                $("#retryRead").prop("hidden", true);
                var saved = false;

                $.ajax({
                    url: "${ctx}/chat/room/" + roomId + "/read",
                    method: "POST",
                    data: { messageId: messageId }
                }).done(function() {
                    lastReadId = messageId;
                    saved = true;
                    $("#readStatus").text("");
                }).fail(function(xhr) {
                    if (leaving) return;
                    if (xhr.status === 401) {
                        leaving = true;
                        clearTimeout(reconnectTimer);
                        if (ws) ws.close(1000);
                        location.href = "${ctx}/login";
                    } else if (xhr.status === 403 || xhr.status === 409) {
                        readBlocked = true;
                        $("#readStatus").text("읽음 처리가 허용되지 않습니다. 이용 상태와 권한을 확인해 주세요.");
                    } else {
                        $("#readStatus").text("읽음 정보를 저장하지 못했습니다.");
                        $("#retryRead").prop("hidden", false);
                    }
                }).always(function() {
                    readInFlight = false;
                    // 저장 중 새 메시지를 읽었으면 이어서 최신 위치를 저장한다.
                    if (saved) markRead();
                });
            }

            area.on("scroll", function() {
                stickToBottom = atBottom();
                markRead();
            });
            $("#retryRead").on("click", markRead);
            document.addEventListener("visibilitychange", markRead);
            window.addEventListener("focus", markRead);

            function scheduleReconnect() {
                if (leaving || reconnectTimer) return;
                connectionStatus("연결이 끊겼습니다. 잠시 후 다시 연결합니다.", false);
                reconnectTimer = setTimeout(function() {
                    reconnectTimer = null;
                    connect();
                }, 3000);
            }

            function recoverMessages(socket, cursor) {
                $.ajax({
                    url: "${ctx}/chat/room/" + roomId + "/messages/after",
                    data: { afterMessageId: cursor },
                    dataType: "json"
                }).done(function(messages) {
                    if (leaving || socket !== ws || socket.readyState !== WebSocket.OPEN) return;
                    messages.forEach(function(message) {
                        confirmSend(message.clientMessageKey, message.senderEmployeeId);
                        appendMessage(message.chatMessageId, message.senderEmployeeId,
                            message.senderName, message.messageText, message.attachmentId, message.createdAt);
                    });
                    if (messages.length > 0) {
                        recoveryCursor = messages[messages.length - 1].chatMessageId;
                    }
                    // 실시간 수신 ID가 아닌 조회 결과의 마지막 ID로 다음 페이지를 요청한다.
                    if (messages.length === 50) {
                        recoverMessages(socket, messages[messages.length - 1].chatMessageId);
                        return;
                    }
                    pendingMessages.forEach(function(message) {
                        appendMessage(message.messageId, message.senderEmployeeId,
                            message.sender, message.content, message.attachmentId);
                    });
                    pendingMessages = [];
                    recovering = false;
                    connectionStatus("연결됨", true);
                    sendPending();
                    markRead();
                    refreshReadReceipts();
                }).fail(function(xhr) {
                    if (leaving || socket !== ws || socket.readyState !== WebSocket.OPEN) return;
                    socket.close(1000);
                    if (xhr.status === 401) {
                        leaving = true;
                        location.href = "${ctx}/login";
                    } else if (xhr.status === 403) {
                        connectionStatus("채팅방 접근 권한이 없습니다.", false);
                    } else {
                        scheduleReconnect();
                    }
                });
            }

            function connect() {
                recovering = true;
                pendingMessages = [];
                connectionStatus("연결 및 대화 복구 중입니다.", false);
                var socket = new WebSocket(scheme + location.host + "${ctx}/chat/room/" + roomId);
                ws = socket;
                socket.onopen = function() { recoverMessages(socket, recoveryCursor); };
                socket.onclose = function(e) {
                    if (leaving || socket !== ws) return;
                    clearTimeout(receiptTimer);
                    connectionStatus("연결이 종료되었습니다.", false);
                    if (e.code === 1009 && pendingSend) {
                        pendingSend = null;
                        $("#retrySend").prop("hidden", true);
                        $("#sendStatus").text("메시지가 너무 길어 전송하지 못했습니다. 내용을 줄여 다시 보내 주세요.");
                    }
                    if (e.code !== 1000 && e.code !== 1008) scheduleReconnect();
                };
                socket.onmessage = function(e) {
                    if (leaving || socket !== ws) return;
                    var data = JSON.parse(e.data);
                    if (data.type === "MEMBERS") {
                        $(".member-list").empty();
                        data.members.forEach(function(name) {
                            $(".member-list").append($("<div>").text(name));
                        });
                        $(".member-count").text(data.members.length);
                    } else if (data.type === "MSG") {
                        confirmSend(data.clientMessageKey, data.senderEmployeeId);
                        if (recovering) pendingMessages.push(data);
                        else {
                            // 실시간 메시지에는 보낸 시각이 없어 받은 시각을 쓴다(서버가 저장한 직후에 보낸다)
                            appendMessage(data.messageId, data.senderEmployeeId,
                                data.sender, data.content, data.attachmentId);
                            markRead();
                        }
                    } else if (data.type === "ERROR") {
                        if (pendingSend && pendingSend.clientMessageKey === data.clientMessageKey) {
                            pendingSend = null;
                            $("#retrySend").prop("hidden", true);
                            $("#sendStatus").text("저장하지 못했습니다. 입력 내용을 확인한 뒤 다시 보내 주세요.");
                        }
                        alert(data.message);
                    }
                };
            }

            window.addEventListener("pagehide", function() {
                leaving = true;
                clearTimeout(reconnectTimer);
                clearTimeout(receiptTimer);
                reconnectTimer = null;
                if (ws) ws.close();
            });
            window.addEventListener("pageshow", function(e) {
                if (e.persisted) {
                    leaving = false;
                    connect();
                }
            });
            connect();

            /** 입력 칸의 글을 보낸다 (Enter 키와 보내기 버튼이 같이 쓴다) */
            function sendText() {
                var text = $(".input-area").text().trim();
                if (text === "" || pendingSend || recovering || !ws || ws.readyState !== WebSocket.OPEN) return;

                if (Array.from(text).length > 1000) {
                    $("#sendStatus").text("메시지는 1,000자까지 보낼 수 있습니다.");
                    return;
                }

                pendingSend = {
                    clientMessageKey: crypto.randomUUID(),
                    messageText: text
                };
                sendPending();
            }

            /** 보내기: 고른 이미지가 있으면 먼저 올리고, 성공한 뒤 같이 쓴 글을 보낸다 */
            function sendCompose() {
                if (attachFile) sendImage(sendText);
                else sendText();
            }

            $(".input-area").on("keydown", function(e) {
                if (e.keyCode == 13) {
                    e.preventDefault();
                    sendCompose();
                    return false;
                }
            });
            $("#sendText").on("click", function() {
                sendCompose();
                $(".input-area").trigger("focus");
            });
        });
    </script>
  </jsp:attribute>
  <jsp:body>
    <%-- 채팅 전용 스타일 (공통 레이아웃에 화면별 CSS 칸이 없어서 본문 맨 앞에서 불러온다) --%>
    <link rel="stylesheet" href="${ctx}/resources/css/chat/chat.css">
    <div class="chat-room">
      <section class="chat-main" aria-labelledby="chatRoomName">
        <header class="chat-head">
          <a class="btn btn-ghost chat-back" href="${ctx}/chat/rooms" aria-label="채팅방 목록으로">← 목록</a>
          <h1 class="chat-title" id="chatRoomName">채팅방</h1>
          <p id="connectionStatus" class="chat-connection" role="status" aria-live="polite">연결 중입니다.</p>
          <button type="button" class="btn chat-members-toggle" id="toggleMembers"
                  aria-expanded="false" aria-controls="chatMembers">참여자 <c:out value="${fn:length(roomMembers)}"/>명</button>
        </header>

        <div class="chat-notices">
          <p id="readStatus" role="status" aria-live="polite"></p>
          <button type="button" class="btn" id="retryRead" hidden>읽음 다시 저장</button>
          <p id="receiptStatus" role="status" aria-live="polite"></p>
          <p id="sendStatus" role="status" aria-live="polite"></p>
          <button type="button" class="btn" id="retrySend" hidden>전송 확인 다시 시도</button>
        </div>

        <div class="message-area">
          <button type="button" class="btn chat-load-older" id="loadOlder" <c:if test="${messageList.size() lt 50}">hidden</c:if>>이전 대화 더 보기</button>
          <p class="empty-note" id="chatEmpty" hidden>아직 대화가 없습니다. 첫 메시지를 보내 보세요.</p>
          <%-- 값만 담아 그리고, 화면 JS가 말풍선 모양으로 바꾼다(실시간 메시지와 같은 모양) --%>
          <div id="chatList">
            <c:forEach var="message" items="${messageList}">
              <div data-message-id="${message.chatMessageId}" data-sender-id="${message.senderEmployeeId}"
                   data-sender-name="<c:out value='${message.senderName}'/>" data-created="${message.createdAt.time}"
                   data-attachment-id="${message.attachmentId}" hidden><c:out value="${message.messageText}"/></div>
            </c:forEach>
          </div>
        </div>

        <div class="chat-compose">
          <%-- 고른 이미지 미리보기: 보내기 전에 확인하고 ✕로 뺄 수 있다 --%>
          <div class="chat-attach" id="chatAttach" hidden>
            <img class="chat-attach-thumb" id="chatAttachThumb" alt="보낼 이미지 미리보기">
            <div class="chat-attach-info">
              <span class="chat-attach-name" id="chatAttachName"></span>
              <span class="chat-attach-size" id="chatAttachSize"></span>
            </div>
            <button type="button" class="btn btn-ghost chat-attach-remove" id="chatAttachRemove" aria-label="고른 이미지 빼기">✕</button>
          </div>
          <p class="chat-compose-status" id="imageStatus" role="status" aria-live="polite"></p>
          <div class="chat-compose-row">
            <%-- 파일 칸은 눈에서 숨기고 그 label 을 그림 버튼으로 보인다. 키보드로는 숨긴 파일 칸에 그대로 닿는다 --%>
            <input class="chat-sr" id="chatImage" type="file" accept="image/jpeg,image/png" disabled>
            <label class="btn chat-attach-btn" for="chatImage" title="이미지 첨부 (JPG·PNG, 최대 10MB)" data-write>
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="1.8"/><path d="M21 16l-5-5-8 9"/></svg>
              <span class="chat-sr">이미지 첨부 (JPG·PNG, 최대 10MB)</span>
            </label>
            <div class="input-area" contenteditable="false" role="textbox" aria-label="메시지 입력"
                 data-placeholder="메시지 입력 (Enter로 보내기)"></div>
            <button type="button" class="btn btn-primary" id="sendText" data-write disabled>보내기</button>
          </div>
        </div>
      </section>

      <aside class="chat-members" id="chatMembers" aria-label="참여자">
        <h2>참여자 전체 (<c:out value="${fn:length(roomMembers)}"/>명)</h2>
        <ul>
          <c:forEach var="memberName" items="${roomMembers}">
            <li><c:out value="${memberName}"/></li>
          </c:forEach>
        </ul>
        <h2>접속 중 (<span class="member-count">0</span>명)</h2>
        <div class="member-list"></div>
      </aside>
    </div>
  </jsp:body>
</ui:layout>
