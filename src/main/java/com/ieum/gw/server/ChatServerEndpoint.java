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

    public void sessionClose(Session session) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "채팅 연결 권한이 없습니다."));
        } catch (IOException e) {
            System.err.println("채팅 연결 종료 실패: " + session.getId());
        }
    }

    private static final Gson GSON = new Gson();

    private static final Map<Long, Set<Session>> roomSessions = new ConcurrentHashMap<>();

    private void sendError(
            Session session, String clientMessageKey, String message)
            throws IOException {

        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("clientMessageKey", clientMessageKey);
        error.put("message", message);

        send(session, GSON.toJson(error));
    }

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
        roomSessions.compute(roomId, (id, sessions) -> {
            if (sessions == null) sessions = Collections.synchronizedSet(new HashSet<>());
            sessions.add(session);
            return sessions;
        });
        sendMembers(roomId);
    }

    private void send(Session session, String json) throws IOException {
        synchronized (session) {
            if (session.isOpen()) session.getBasicRemote().sendText(json);
        }
    }

    // 권한을 잃은 연결은 메시지와 접속자 목록의 수신 대상에서 제외한다.
    private void broadcast(Long roomId, Map<String, Object> payload) {
        Set<Session> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        List<Session> denied = new ArrayList<>();
        synchronized (sessions) {
            Set<String> names = new TreeSet<>();
            List<Session> recipients = new ArrayList<>();
            for (Session session : sessions) {
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
            for (Session session : recipients) {
                try {
                    send(session, json);
                } catch (IOException | IllegalStateException e) {
                    System.err.println("채팅 전송 실패: " + session.getId());
                }
            }
        }
        // 닫힘 콜백에서도 방 목록을 사용하므로 잠금 밖에서 닫는다.
        denied.forEach(this::sessionClose);
    }

    private void sendMembers(Long roomId) {
        broadcast(roomId, null);
    }

    @OnMessage
    public void handleMessage(String message, Session senderSession)
            throws IOException {

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

        try {
            dto = cService.insertMessage(dto);
        } catch (AccessDeniedException e) {
            sessionClose(senderSession);
            return;
        } catch (IllegalArgumentException e) {
            sendError(senderSession, input.getClientMessageKey(), e.getMessage());
            return;
        }

        publishMessage(dto, user);
    }

    // 트랜잭션이 끝난 메시지만 Controller 또는 WebSocket 콜백에서 전달한다.
    public void publishMessage(ChatMessageDTO dto, LoginUser user) {
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
        broadcast(dto.getChatRoomId(), payload);
        chatUpdates.publishRoom(dto.getCompanyId(), dto.getChatRoomId());
    }

    @OnClose
    public void handleClose(Session session) {
        Long roomId = (Long) session.getUserProperties().get("roomId");
        if (roomId == null) return;
        Set<Session> remaining = roomSessions.computeIfPresent(roomId, (id, sessions) -> {
            synchronized (sessions) {
                sessions.remove(session);
                return sessions.isEmpty() ? null : sessions;
            }
        });
        if (remaining != null) sendMembers(roomId);
    }

    @OnError
    public void handleError(Session session, Throwable throwable, @PathParam("roomId") String roomId) {
        System.err.println("에러 발생 - 방 ID: " + roomId + ", 세션 ID: " + session.getId());
        throwable.printStackTrace();
    }

}
