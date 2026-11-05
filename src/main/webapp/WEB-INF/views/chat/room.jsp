<%@ page language="java" contentType="text/html; charset=UTF-8"
pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%-- 모델 속성: roomId, employeeId, messageList (오래된 메시지부터 최대 50개) --%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="_csrf" content="<c:out value='${_csrf.token}'/>">
    <meta name="_csrf_header" content="<c:out value='${_csrf.headerName}'/>">
    <title>채팅</title>
    <script src="https://code.jquery.com/jquery-3.5.1.js"></script>

    <style>
        .chat-box {
            width: 500px;
            height: 500px;
            float: left;
        }

        .member-box {
            width: 150px;
            height: 500px;
            float: left;
            border: 1px solid #616161;
            overflow-y: auto;
            margin-right: 10px;
        }

        .message-area {
            width: 100%;
            height: 90%;
            border: 1px solid #616161;
            overflow-y: auto;
            word-break: break-all;
        }

        .chat-image {
            display: block;
            max-width: 100%;
            max-height: 240px;
        }

        .input-area {
            width: 100%;
            height: 10%;
            border: 1px solid #616161;
            overflow-y: auto;
        }
    </style>
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
            var imageButton = $("#sendImage");
            var lastMessageId = "0";
            var displayedMessageIds = new Set();
            var area = $(".message-area");
            var loadingOlder = false;
            var readInFlight = false;
            var readBlocked = false;
            var lastReadId = "0";
            var otherLastReadId = "0";
            var receiptTimer;
            var receiptInFlight = false;
            var receiptStopped = false;

            area.children("[data-message-id]").each(function() {
                var id = this.getAttribute("data-message-id");
                displayedMessageIds.add(id);
                if (BigInt(id) > BigInt(lastMessageId)) lastMessageId = id;
            });
            // 실시간 수신 순서가 뒤바뀌어도 재접속 조회가 앞선 메시지를 건너뛰지 않게 한다.
            var recoveryCursor = lastMessageId;
            area.scrollTop(area[0].scrollHeight);

            $("#loadOlder").on("click", function() {
                var button = $(this);
                var oldestId = area.children("[data-message-id]")
                    .first().attr("data-message-id");

                if (button.prop("disabled") || !oldestId) return;
                button.prop("disabled", true);
                loadingOlder = true;

                $.ajax({
                    url: "${pageContext.request.contextPath}/chat/room/"
                        + roomId + "/messages",
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

                        area.prepend($("<div>")
                            .attr("data-message-id", message.chatMessageId)
                            .attr("data-sender-id", String(message.senderEmployeeId))
                            .append(messageBody(message.chatMessageId, message.senderName, message.messageText, message.attachmentId)));
                    }

                    updateReadReceipts();
                    area.scrollTop(previousTop + area[0].scrollHeight - previousHeight);
                    if (messages.length < 50) button.prop("hidden", true);
                }).fail(function(xhr) {
                    if (xhr.status === 401) {
                        location.href = "${pageContext.request.contextPath}/login";
                        return;
                    }
                    alert("이전 대화를 불러오지 못했습니다.");
                }).always(function() {
                    button.prop("disabled", false);
                    loadingOlder = false;
                });
            });

            function connectionStatus(text, ready) {
                $("#connectionStatus").text(text);
                $(".input-area").attr("contenteditable", ready ? "true" : "false");
                $("#retrySend").prop("disabled", !ready);
                imageButton.prop("disabled", !ready || imageInput.prop("disabled"));
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

            function messageBody(id, sender, content, attachmentId) {
                var body = $("<span>").text(sender + " : ");
                if (attachmentId) {
                    body.append($("<img>").attr({
                        src: "${pageContext.request.contextPath}/chat/room/" + roomId
                            + "/messages/" + encodeURIComponent(id) + "/image",
                        alt: "채팅 이미지"
                    }).addClass("chat-image"));
                } else {
                    body.append(document.createTextNode(content || ""));
                }
                return body;
            }

            imageInput.on("change", function() { pendingImage = null; });
            imageButton.on("click", function() {
                if (leaving || recovering || !ws || ws.readyState !== WebSocket.OPEN
                    || imageInput.prop("disabled")) return;
                var file = imageInput[0].files[0];
                if (!file || file.size === 0 || file.size > 10 * 1024 * 1024
                    || !/\.(jpe?g|png)$/i.test(file.name)) {
                    $("#imageStatus").text("10MB 이하의 JPG·PNG 이미지 한 장을 선택해 주세요.");
                    return;
                }
                if (!pendingImage) pendingImage = crypto.randomUUID();
                var data = new FormData();
                data.append("clientMessageKey", pendingImage);
                data.append("image", file);
                var headers = {};
                headers[$("meta[name='_csrf_header']").attr("content")] =
                    $("meta[name='_csrf']").attr("content");
                imageInput.prop("disabled", true);
                imageButton.prop("disabled", true);
                $("#imageStatus").text("이미지를 보내고 있습니다.");
                $.ajax({
                    url: "${pageContext.request.contextPath}/chat/room/" + roomId + "/images",
                    method: "POST",
                    data: data,
                    processData: false,
                    contentType: false,
                    headers: headers,
                    dataType: "json"
                }).done(function(message) {
                    appendMessage(message.chatMessageId, message.senderEmployeeId,
                        message.senderName, message.messageText, message.attachmentId);
                    pendingImage = null;
                    imageInput.val("");
                    $("#imageStatus").text("");
                    markRead();
                }).fail(function(xhr) {
                    if (xhr.status === 401) {
                        location.href = "${pageContext.request.contextPath}/login";
                    } else if (xhr.status === 403 || xhr.status === 409) {
                        $("#imageStatus").text("이미지를 보낼 권한이 없거나 이용 기간이 끝났습니다.");
                    } else if (xhr.status === 400 || xhr.status === 413) {
                        $("#imageStatus").text("이미지 형식과 크기를 확인해 주세요. JPG·PNG 한 장, 최대 10MB입니다.");
                    } else {
                        $("#imageStatus").text("전송을 확인하지 못했습니다. 같은 파일을 다시 보내 확인할 수 있습니다.");
                    }
                }).always(function() {
                    imageInput.prop("disabled", false);
                    imageButton.prop("disabled", leaving || recovering || !ws || ws.readyState !== WebSocket.OPEN);
                });
            });

            function appendMessage(id, senderId, sender, content, attachmentId) {
                if (displayedMessageIds.has(id)) return;
                var followLatest = !document.hidden && document.hasFocus() && atBottom();
                displayedMessageIds.add(id);
                var row = $("<div>").attr("data-message-id", id)
                    .attr("data-sender-id", String(senderId))
                    .append(messageBody(id, sender, content, attachmentId));
                var next = area.children("[data-message-id]").filter(function() {
                    return BigInt(this.getAttribute("data-message-id")) > BigInt(id);
                }).first();
                if (next.length) next.before(row);
                else area.append(row);
                if (BigInt(id) > BigInt(lastMessageId)) lastMessageId = id;
                updateReadReceipts();
                if (followLatest) area.scrollTop(area[0].scrollHeight);
            }

            function updateReadReceipts() {
                area.children("[data-message-id]").each(function() {
                    var row = $(this);
                    row.children(".read-receipt").remove();
                    if (this.getAttribute("data-sender-id") === employeeId
                        && BigInt(this.getAttribute("data-message-id")) <= BigInt(otherLastReadId)) {
                        row.append($("<span>").addClass("read-receipt").text(" 읽음"));
                    }
                });
            }

            function refreshReadReceipts() {
                clearTimeout(receiptTimer);
                if (leaving || recovering || document.hidden || receiptStopped
                    || receiptInFlight || !ws || ws.readyState !== WebSocket.OPEN) return;
                receiptInFlight = true;

                $.ajax({
                    url: "${pageContext.request.contextPath}/chat/room/" + roomId + "/read",
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
                        location.href = "${pageContext.request.contextPath}/login";
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
                var headers = {};
                headers[$("meta[name='_csrf_header']").attr("content")] =
                    $("meta[name='_csrf']").attr("content");
                readInFlight = true;
                $("#retryRead").prop("hidden", true);
                var saved = false;

                $.ajax({
                    url: "${pageContext.request.contextPath}/chat/room/" + roomId + "/read",
                    method: "POST",
                    headers: headers,
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
                        location.href = "${pageContext.request.contextPath}/login";
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

            area.on("scroll", markRead);
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
                    url: "${pageContext.request.contextPath}/chat/room/"
                        + roomId + "/messages/after",
                    data: { afterMessageId: cursor },
                    dataType: "json"
                }).done(function(messages) {
                    if (leaving || socket !== ws || socket.readyState !== WebSocket.OPEN) return;
                    messages.forEach(function(message) {
                        confirmSend(message.clientMessageKey, message.senderEmployeeId);
                        appendMessage(message.chatMessageId, message.senderEmployeeId,
                            message.senderName, message.messageText, message.attachmentId);
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
                        location.href = "${pageContext.request.contextPath}/login";
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
                var socket = new WebSocket(scheme + location.host
                    + "${pageContext.request.contextPath}/chat/room/" + roomId);
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

            $(".input-area").on("keydown", function(e) {
                if (e.keyCode == 13) {
                    e.preventDefault();
                    var text = $(".input-area").text().trim();
                    if (text === "" || pendingSend || recovering || !ws || ws.readyState !== WebSocket.OPEN) return false;

                    if (Array.from(text).length > 1000) {
                        $("#sendStatus").text("메시지는 1,000자까지 보낼 수 있습니다.");
                        return false;
                    }

                    pendingSend = {
                        clientMessageKey: crypto.randomUUID(),
                        messageText: text
                    };
                    sendPending();
                    return false;
                }
            })

        })
    </script>
</head>
<body>
<div class="member-box">
    <details open>
        <summary>참여자 전체 (<c:out value="${fn:length(roomMembers)}"/>명)</summary>
        <ul>
            <c:forEach var="memberName" items="${roomMembers}">
                <li><c:out value="${memberName}"/></li>
            </c:forEach>
        </ul>
    </details>
    <b>접속 중 (<span class="member-count">0</span>)</b>
    <div class="member-list"></div>
</div>
<div class="chat-box">
    <p id="connectionStatus" role="status" aria-live="polite">연결 중입니다.</p>
    <p id="readStatus" role="status" aria-live="polite"></p>
    <p id="receiptStatus" role="status" aria-live="polite"></p>
    <button type="button" id="retryRead" hidden>읽음 다시 저장</button>
    <p id="sendStatus" role="status" aria-live="polite"></p>
    <button type="button" id="retrySend" hidden>전송 확인 다시 시도</button>
    <button type="button" id="loadOlder" <c:if test="${messageList.size() lt 50}">hidden</c:if>>이전 대화 더 보기</button>
    <div class="message-area">
        <c:forEach var="message" items="${messageList}">
            <div data-message-id="${message.chatMessageId}" data-sender-id="${message.senderEmployeeId}"><c:out value="${message.senderName}"/> :
                <c:choose>
                    <c:when test="${not empty message.attachmentId}">
                        <img class="chat-image" src="${pageContext.request.contextPath}/chat/room/${roomId}/messages/${message.chatMessageId}/image" alt="채팅 이미지">
                    </c:when>
                    <c:otherwise><c:out value="${message.messageText}"/></c:otherwise>
                </c:choose>
            </div>
        </c:forEach>
    </div>
    <label for="chatImage">이미지 (JPG·PNG, 최대 10MB)</label>
    <input id="chatImage" type="file" accept="image/jpeg,image/png">
    <button id="sendImage" type="button" disabled>이미지 보내기</button>
    <p id="imageStatus" role="status" aria-live="polite"></p>
    <div class="input-area" contenteditable="false" role="textbox" aria-label="메시지 입력"></div>
</div>
</body>
</html>
