<%@ page language="java" contentType="text/html; charset=UTF-8"
pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Insert title here</title>
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

        .input-area {
            width: 100%;
            height: 10%;
            border: 1px solid #616161;
            overflow-y: auto;
        }
    </style>
    <script>


        $(function() {
            // 접속 주소: /chatRoom?roomId=1 처럼 방 번호를 쿼리로 받는다
            var roomId = new URLSearchParams(location.search).get("roomId");
            var scheme = location.protocol === "https:" ? "wss://" : "ws://";
            var ws = new WebSocket(scheme + location.host + "${pageContext.request.contextPath}/chat/" + encodeURIComponent(roomId));

            ws.onopen = function() { console.log("연결됨 / 방 " + roomId); };
            ws.onclose = function(e) { console.log("연결 종료 code=" + e.code); };
            ws.onerror = function(e) { console.log("에러", e); };

            // 서버가 보낸 메시지(내가 보낸 것도 서버가 되돌려 보낸다)
            ws.onmessage = function(e){
                console.log("받음: " + e.data);
                var data = JSON.parse(e.data);

                if (data.type === "MEMBERS") {
                    // 접속 중인 사람 목록 갱신
                    $(".member-list").empty();
                    data.members.forEach(function(name) {
                        $(".member-list").append($("<div>").text(name));
                    });
                    $(".member-count").text(data.members.length);
                } else if (data.type === "MSG") {
                    $(".message-area").append($("<div>").text(data.sender + " : " + data.content));
                    $('.message-area').scrollTop($('.message-area')[0].scrollHeight);
                }
            }

            $(".input-area").on("keydown", function(e) {
                if (e.keyCode == 13) {
                    e.preventDefault();
                    var text = $(".input-area").text().trim();
                    if (text === "" || ws.readyState !== WebSocket.OPEN) return false;

                    console.log("보냄: " + text);
                    ws.send(text);
                    $(".input-area").text("");
                    return false;
                }
            })

        })
    </script>
</head>
<body>
<div class="member-box">
    <b>접속 중 (<span class="member-count">0</span>)</b>
    <div class="member-list"></div>
</div>
<div class="chat-box">
    <div class="message-area"></div>
    <div class="input-area" contenteditable="true"></div>
</div>
</body>
</html>