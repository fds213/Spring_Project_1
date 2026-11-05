package com.ieum.gw.server;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.ChatService;
import com.ieum.gw.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ServerEndpoint(value = "/chat/updates", configurator = SpringConfigurator.class)
public class ChatUpdateEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(ChatUpdateEndpoint.class);
    private static final String UPDATE = "{\"type\":\"UNREAD_CHANGED\"}";
    private final ConcurrentMap<List<Long>, Set<Session>> sessions = new ConcurrentHashMap<>();

    @Autowired
    private ChatService chatService;

    @Autowired
    private EmployeeService employeeService;

    /** 로그인한 활성 직원의 배지 갱신 연결을 등록한다 */
    @OnOpen
    public void open(Session session) throws IOException {
        if (!(session.getUserPrincipal() instanceof Authentication)) {
            deny(session);
            return;
        }
        Authentication auth = (Authentication) session.getUserPrincipal();
        if (!auth.isAuthenticated() || !(auth.getPrincipal() instanceof LoginUser)) {
            deny(session);
            return;
        }
        LoginUser user = (LoginUser) auth.getPrincipal();
        List<Long> key = List.of(user.getCompanyId(), user.getEmployeeId());
        if (!employeeService.isActiveEmployee(key.get(0), key.get(1))) {
            deny(session);
            return;
        }
        session.getUserProperties().put("updateUser", user);
        sessions.compute(key, (id, connections) -> {
            if (connections == null) connections = ConcurrentHashMap.newKeySet();
            connections.add(session);
            return connections;
        });
    }

    /** 닫힌 배지 갱신 연결을 제거한다 */
    @OnClose
    public void close(Session session) {
        LoginUser user = (LoginUser) session.getUserProperties().get("updateUser");
        if (user == null) return;
        sessions.computeIfPresent(List.of(user.getCompanyId(), user.getEmployeeId()), (key, connections) -> {
            connections.remove(session);
            return connections.isEmpty() ? null : connections;
        });
    }

    /** 방 참여자에게 안 읽은 수 갱신을 알린다 */
    public void publishRoom(Long companyId, Long roomId) {
        if (sessions.isEmpty()) return;
        try {
            for (Long employeeId : chatService.getRoomMemberIds(companyId, roomId)) {
                publishUser(companyId, employeeId);
            }
        } catch (RuntimeException e) {
            LOG.warn("채팅방 안 읽은 수 갱신 신호를 보내지 못했습니다.", e);
        }
    }

    /** 해당 사원의 열린 연결에 안 읽은 수 갱신을 알린다 */
    public void publishUser(Long companyId, Long employeeId) {
        Set<Session> connections = sessions.get(List.of(companyId, employeeId));
        if (connections == null) return;
        try {
            boolean active = employeeService.isActiveEmployee(companyId, employeeId);
            for (Session session : connections) {
                if (!session.isOpen()) continue;
                try {
                    if (!active) {
                        deny(session);
                        continue;
                    }
                    synchronized (session) {
                        if (session.isOpen()) session.getBasicRemote().sendText(UPDATE);
                    }
                } catch (IOException | IllegalStateException e) {
                    LOG.debug("채팅 알림 연결 처리 실패", e);
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("채팅 안 읽은 수 갱신 신호를 보내지 못했습니다.", e);
        }
    }

    /** 권한이 없는 배지 갱신 연결을 닫는다 */
    private void deny(Session session) throws IOException {
        session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "채팅 알림 수신 권한이 없습니다."));
    }
}
