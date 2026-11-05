package com.ieum.gw.server;

import com.google.gson.JsonParseException;
import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.service.ChatService;
import com.ieum.gw.dto.LoginUser;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint(value = "/chat/room/{roomId}", configurator = SpringConfigurator.class)
public class ChatServerEndpoint {

    @Autowired
    private ChatService cService;

    @Autowired
    private ChatUpdateEndpoint chatUpdates;

    /** 권한이 없는 채팅 연결을 닫는다 */
    public void sessionClose(Session session) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "채팅 연결 권한이 없습니다."));
        } catch (IOException e) {
            System.err.println("채팅 연결 종료 실패: " + session.getId());
        }
    }

    private static final Gson GSON = new Gson();

    private static final Map<Long, Set<Session>> roomSessions = new ConcurrentHashMap<>();

    /** 전송 실패 이유를 보낸 연결에 알린다 */
    private void sendError(
            Session session, String clientMessageKey, String message) {

        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("clientMessageKey", clientMessageKey);
        error.put("message", message);

        send(session, GSON.toJson(error));
    }

    /** 방 참여 권한을 확인한 뒤 연결을 등록한다 */
    @OnOpen
    public void handleOpen(Session session, @PathParam("roomId") Long roomId) {

        if (!(session.getUserPrincipal() instanceof Authentication)) {
            sessionClose(session);
            return;
        }
        Authentication authentication = (Authentication) session.getUserPrincipal();
        if (!authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof LoginUser)) {
            sessionClose(session);
            return;
        }
        LoginUser user = (LoginUser) authentication.getPrincipal();
        if (!cService.canAccessRoom(user.getCompanyId(), roomId, user.getEmployeeId())) {
            sessionClose(session);
            return;
        }

        session.getUserProperties().put("roomId", roomId);
        session.getUserProperties().put("loginUser", user);
        session.getUserProperties().put("chatOutbox", new Outbox(session));
        roomSessions.compute(roomId, (id, sessions) -> {
            if (sessions == null) sessions = ConcurrentHashMap.newKeySet();
            sessions.add(session);
            return sessions;
        });
        sendMembers(roomId);
    }

    /** 해당 연결의 전송 대기열에 메시지를 넣는다 */
    private void send(Session session, String json) {
        Outbox outbox = (Outbox) session.getUserProperties().get("chatOutbox");
        if (outbox != null && session.isOpen()) outbox.offer(json);
    }

    /** 권한을 확인한 수신자에게 방 이벤트를 전달한다 */
    private void broadcast(Long roomId, Map<String, Object> payload) {
        Set<Session> sessions = roomSessions.get(roomId);
        if (sessions == null) return;

        List<Session> recipients = new ArrayList<>();
        List<Session> denied = new ArrayList<>();
        Set<String> names = new TreeSet<>();
        for (Session session : List.copyOf(sessions)) {
            if (!session.isOpen()) continue;
            LoginUser user = (LoginUser) session.getUserProperties().get("loginUser");
            if (user == null || !cService.canAccessRoom(user.getCompanyId(), roomId, user.getEmployeeId())) {
                denied.add(session);
                continue;
            }
            recipients.add(session);
            names.add(user.getName());
        }
        String json = GSON.toJson(payload == null ? Map.of("type", "MEMBERS", "members", names) : payload);
        recipients.forEach(session -> send(session, json));
        denied.forEach(this::sessionClose);
    }

    /** 현재 방에 연결된 참여자 이름을 전송한다 */
    private void sendMembers(Long roomId) {
        broadcast(roomId, null);
    }

    /** 텍스트 메시지를 저장하고 전송 결과를 전달한다 */
    @OnMessage
    public void handleMessage(String message, Session senderSession) {

        Long roomId =
                (Long) senderSession.getUserProperties().get("roomId");
        if (roomId == null) return;

        LoginUser user = (LoginUser) senderSession.getUserProperties().get("loginUser");
        if (user == null) return;

        ChatMessageDTO input;

        try {
            input = GSON.fromJson(message, ChatMessageDTO.class);
        } catch (JsonParseException e) {
            sendError(senderSession, null, "메시지 형식이 올바르지 않습니다.");
            return;
        }

        if (input == null) {
            sendError(senderSession, null, "메시지 형식이 올바르지 않습니다.");
            return;
        }

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setCompanyId(user.getCompanyId());
        dto.setChatRoomId(roomId);
        dto.setSenderEmployeeId(user.getEmployeeId());
        dto.setMessageType("MSG_TEXT");
        dto.setMessageText(input.getMessageText());
        dto.setClientMessageKey(input.getClientMessageKey());

        ChatService.MessageSaveResult result;
        try {
            result = cService.insertMessage(dto);
        } catch (AccessDeniedException e) {
            sessionClose(senderSession);
            return;
        } catch (IllegalArgumentException e) {
            sendError(senderSession, input.getClientMessageKey(), e.getMessage());
            return;
        }

        if (result.isInserted()) {
            publishMessage(result.getMessage(), user);
        } else {
            send(senderSession, GSON.toJson(messagePayload(result.getMessage(), user)));
        }
    }

    // 트랜잭션이 끝난 메시지만 Controller 또는 WebSocket 콜백에서 전달한다.
    public void publishMessage(ChatMessageDTO dto, LoginUser user) {
        broadcast(dto.getChatRoomId(), messagePayload(dto, user));
        chatUpdates.publishRoom(dto.getCompanyId(), dto.getChatRoomId());
    }

    /** 저장된 메시지를 기존 MSG 이벤트 형식으로 만든다 */
    private Map<String, Object> messagePayload(ChatMessageDTO dto, LoginUser user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MSG");
        payload.put("messageId", dto.getChatMessageId().toString());
        payload.put("clientMessageKey", dto.getClientMessageKey());
        payload.put("senderEmployeeId", user.getEmployeeId().toString());
        payload.put("sender", user.getName());
        payload.put("content", dto.getMessageText());

        if (dto.getAttachmentId() != null) {
            payload.put("attachmentId", dto.getAttachmentId().toString());
        }
        return payload;
    }

    /** 닫힌 연결을 제거하고 접속자 목록을 갱신한다 */
    @OnClose
    public void handleClose(Session session) {
        Long roomId = (Long) session.getUserProperties().get("roomId");
        if (roomId == null) return;
        Set<Session> remaining = roomSessions.computeIfPresent(roomId, (id, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
        if (remaining != null) sendMembers(roomId);
    }

    /** 웹소켓 오류와 연결 정보를 기록한다 */
    @OnError
    public void handleError(Session session, Throwable throwable, @PathParam("roomId") String roomId) {
        System.err.println("에러 발생 - 방 ID: " + roomId + ", 세션 ID: " + session.getId());
        throwable.printStackTrace();
    }

    private static class Outbox {
        private static final int MAX_PENDING = 64;
        private final Session session;
        private final RemoteEndpoint.Async remote;
        private final Queue<String> pending = new ArrayDeque<>();
        private boolean sending;
        private boolean overflow;

        Outbox(Session session) {
            this.session = session;
            this.remote = session.getAsyncRemote();
            remote.setSendTimeout(5_000);
        }

        /** 연결별로 한 건씩 비동기 전송하고, 뒤처진 연결의 대기열 크기를 제한한다 */
        void offer(String json) {
            synchronized (this) {
                if (overflow) return;
                if (sending) {
                    if (pending.size() == MAX_PENDING) {
                        overflow = true;
                        pending.clear();
                    } else {
                        pending.add(json);
                    }
                    return;
                }
                sending = true;
            }
            write(json);
        }

        /** 전송 완료 콜백에서 다음 메시지를 시작한다 */
        private void write(String json) {
            try {
                remote.sendText(json, result -> complete(result.isOK()));
            } catch (IllegalStateException e) {
                complete(false);
            }
        }

        /** 전송이 실패하거나 밀린 연결을 닫아 재접속 시 저장된 대화를 복구하게 한다 */
        private void complete(boolean success) {
            String next;
            synchronized (this) {
                if (!success) overflow = true;
                next = overflow ? null : pending.poll();
                if (next == null) sending = false;
                if (overflow) pending.clear();
                success = !overflow;
            }
            if (!success) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "채팅 전송 지연"));
                } catch (IOException | IllegalStateException e) {
                    System.err.println("채팅 연결 종료 실패: " + session.getId());
                }
            } else if (next != null) {
                write(next);
            }
        }
    }

}
