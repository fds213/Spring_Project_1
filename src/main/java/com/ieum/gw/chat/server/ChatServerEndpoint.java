package com.ieum.gw.chat.server;

import com.ieum.gw.chat.dto.ChatMessageDTO;
import com.ieum.gw.chat.service.ChatService;
import com.ieum.gw.common.dto.LoginUser;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/chat/room/{roomId}", configurator = SpringConfigurator.class)
public class ChatServerEndpoint {

    @Autowired
    private ChatService cService;

    public void sessionClose(Session session) {
        try {
            session.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Gson GSON = new Gson();

    private static final Map<Long, Set<Session>> roomSessions = new ConcurrentHashMap<>();

    @OnOpen
    public void handleOpen(Session session, @PathParam("roomId") Long roomId) {

        Long empNum = ((LoginUser) ((Authentication) session.getUserPrincipal()).getPrincipal()).getEmployeeId();


        if (!cService.isMember(roomId, empNum)) {
            sessionClose(session);
            System.out.println("개찐빠~!~");
            return;
        }

        String name = ((LoginUser) ((Authentication) session.getUserPrincipal()).getPrincipal()).getName();
        session.getUserProperties().put("roomId", roomId);
        session.getUserProperties().put("name", name);

        roomSessions.computeIfAbsent(roomId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
        System.out.println("새 유저 입장 방 ID" + roomId + ", 세션 ID " + session.getId());

        sendMembers(roomId);
    }

    // 방 전체에게 JSON 전송. 세션 하나가 실패해도 나머지에게는 보낸다.
    // 같은 세션에 동시 전송하면 예외가 나므로 방 단위로 직렬화한다.
    private void broadcast(Long roomId, Map<String, Object> payload) {
        Set<Session> sessionsInRoom = roomSessions.get(roomId);
        if (sessionsInRoom == null) return;
        String json = GSON.toJson(payload);
        synchronized (sessionsInRoom) {
            for (Session s : sessionsInRoom) {
                try {
                    if (s.isOpen()) s.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    System.err.println("전송 실패 세션 ID " + s.getId());
                }
            }
        }
    }

    // 지금 접속 중인 사람 이름 목록(같은 사람이 탭 여러 개여도 한 번만)
    private void sendMembers(Long roomId) {
        Set<Session> sessionsInRoom = roomSessions.get(roomId);
        if (sessionsInRoom == null) return;
        Set<String> names = new TreeSet<>();

        synchronized (sessionsInRoom) {
            for (Session s : sessionsInRoom) names.add((String) s.getUserProperties().get("name"));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MEMBERS");
        payload.put("members", names);
        broadcast(roomId, payload);
    }

    @OnMessage
    public void handleMessage(String message, Session senderSession) throws IOException {
        ChatMessageDTO dto = new ChatMessageDTO();

        Long roomId = (Long) senderSession.getUserProperties().get("roomId");
        if (roomId == null) return;

        Long senderEmployeeId = ((LoginUser) ((Authentication) senderSession.getUserPrincipal()).getPrincipal()).getEmployeeId();
        Long companyId = ((LoginUser) ((Authentication) senderSession.getUserPrincipal()).getPrincipal()).getCompanyId();

        // isMember 넣어서 검증

        // 메시지 DB 저장

        dto.setChatRoomId(roomId);
        dto.setCompanyId(companyId);
        dto.setSenderEmployeeId(senderEmployeeId);
        dto.setMessageText(message);
        dto.setMessageType("MSG_TEXT");
        dto.setClientMessageKey(UUID.randomUUID().toString()); // 임시로 랜덤값 넣어둔거라 중복 방지 안 됨.

        cService.insertMessage(dto);

        // 보낸 사람 이름은 클라이언트 값이 아니라 서버가 인증 정보에서 가져온 값을 쓴다
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MSG");
        payload.put("sender", senderSession.getUserProperties().get("name"));
        payload.put("content", message);
        broadcast(roomId, payload);
    }

    @OnClose
    public void handleClose(Session session) {
        Long roomId = (Long) session.getUserProperties().get("roomId");
        if (roomId == null) return;
        Set<Session> sessionsInRoom = roomSessions.get(roomId);
        if (sessionsInRoom != null) {
            sessionsInRoom.remove(session);
            // 방에 아무도 없으면 메모리 관리를 위해 방을 삭제합니다.
            if (sessionsInRoom.isEmpty()) {
                roomSessions.remove(roomId);
            } else {
                sendMembers(roomId);
            }
        }
        System.out.println("유저 퇴장 - 방 ID: " + roomId + ", 세션 ID: " + session.getId());
    }

    @OnError
    public void handleError(Session session, Throwable throwable, @PathParam("roomId") String roomId) {
        System.err.println("에러 발생 - 방 ID: " + roomId + ", 세션 ID: " + session.getId());
        throwable.printStackTrace();
    }

}
