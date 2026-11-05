package com.ieum.gw.server;

import com.ieum.gw.controller.ChatController;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServerEndpointTest {
    @Test
    @DisplayName("이미지 전송 중 닫힌 연결은 건너뛰고 다른 참여자에게 전달한다")
    void skipsClosedRecipientDuringImageSend() throws Exception {
        long roomId = 902L;
        ChatService service = new ChatService() {
            @Override
            public boolean canAccessRoom(Long companyId, long id, Long employeeId) {
                return true;
            }

            @Override
            public MessageSaveResult sendImage(long id, LoginUser user, String key, MultipartFile file) {
                ChatMessageDTO message = new ChatMessageDTO();
                message.setChatRoomId(id);
                message.setCompanyId(user.getCompanyId());
                message.setClientMessageKey(key);
                message.setChatMessageId(42L);
                message.setAttachmentId(43L);
                return new MessageSaveResult(message, true);
            }
        };
        ChatServerEndpoint endpoint = new ChatServerEndpoint();
        ChatUpdateEndpoint updates = new ChatUpdateEndpoint();
        ReflectionTestUtils.setField(endpoint, "cService", service);
        ReflectionTestUtils.setField(endpoint, "chatUpdates", updates);
        ChatController controller = new ChatController(service, null, null, endpoint, updates);
        SocketClient closingDuringSelection = new SocketClient(11L);
        SocketClient closingDuringEnqueue = new SocketClient(12L);
        SocketClient recipient = new SocketClient(13L);
        try {
            endpoint.handleOpen(closingDuringSelection.session, roomId);
            endpoint.handleOpen(closingDuringEnqueue.session, roomId);
            endpoint.handleOpen(recipient.session, roomId);
            recipient.finishWrites();
            closingDuringSelection.propertyReadsUntilClose = 0;
            closingDuringEnqueue.propertyReadsUntilClose = 1;

            MockMultipartFile file = new MockMultipartFile("image", "chat.png", "image/png", new byte[] {1});
            ChatMessageDTO response = controller.sendImage(roomId, "image-key", List.of(file), recipient.user);

            assertEquals(42L, response.getChatMessageId());
            assertEquals(1, recipient.messages().size());
            assertTrue(recipient.messages().get(0).contains("\"attachmentId\":\"43\""));
        } finally {
            closingDuringSelection.propertyReadsUntilClose = -1;
            closingDuringEnqueue.propertyReadsUntilClose = -1;
            endpoint.handleClose(closingDuringSelection.session);
            endpoint.handleClose(closingDuringEnqueue.session);
            endpoint.handleClose(recipient.session);
        }
    }

    @Test
    @DisplayName("느린 연결이 있어도 다른 참여자에게 전송하며 같은 키 재전송은 보낸 연결에만 응답한다")
    void isolatesSlowRecipientAndRepliesOnlyToSenderOnReplay() {
        long roomId = 901L;
        ChatServerEndpoint endpoint = new ChatServerEndpoint();
        ReflectionTestUtils.setField(endpoint, "cService", new ChatService() {
            private ChatMessageDTO saved;

            @Override
            public boolean canAccessRoom(Long companyId, long roomId, Long employeeId) {
                return true;
            }

            @Override
            public MessageSaveResult insertMessage(ChatMessageDTO message) {
                if (saved != null) return new MessageSaveResult(saved, false);
                saved = message;
                saved.setChatMessageId(42L);
                return new MessageSaveResult(saved, true);
            }
        });
        ReflectionTestUtils.setField(endpoint, "chatUpdates", new ChatUpdateEndpoint());
        SocketClient slow = new SocketClient(11L);
        SocketClient sender = new SocketClient(12L);
        try {
            endpoint.handleOpen(slow.session, roomId);
            endpoint.handleOpen(sender.session, roomId);
            sender.finishWrites();

            String input = "{\"clientMessageKey\":\"text-key\",\"messageText\":\"테스트\"}";
            endpoint.handleMessage(input, sender.session);
            assertEquals(1, sender.messages().size());
            assertTrue(slow.messages().isEmpty());

            endpoint.handleMessage(input, sender.session);
            assertEquals(1, sender.messages().size());
            sender.finishWrites();
            assertEquals(2, sender.messages().size());

            slow.finishWrites();
            assertEquals(1, slow.messages().size());
            assertEquals(sender.messages().get(0), slow.messages().get(0));
        } finally {
            endpoint.handleClose(sender.session);
            endpoint.handleClose(slow.session);
        }
    }

    private static class SocketClient {
        private final Queue<SendHandler> callbacks = new ArrayDeque<>();
        private final List<String> frames = new ArrayList<>();
        private final Session session;
        private final LoginUser user;
        private int propertyReadsUntilClose = -1;

        SocketClient(long employeeId) {
            AuthEmpDTO account = new AuthEmpDTO();
            account.setCompanyId(7L);
            account.setEmployeeId(employeeId);
            account.setUserAccountId(employeeId);
            account.setName("테스트 " + employeeId);
            account.setStatus("ACTIVE");
            account.setRole("ROLE_EMP");
            user = new LoginUser(account);
            Map<String, Object> properties = new HashMap<>();

            RemoteEndpoint.Async remote = (RemoteEndpoint.Async) Proxy.newProxyInstance(
                    RemoteEndpoint.Async.class.getClassLoader(), new Class<?>[] {RemoteEndpoint.Async.class},
                    (proxy, method, args) -> {
                        if ("sendText".equals(method.getName())) {
                            frames.add((String) args[0]);
                            callbacks.add((SendHandler) args[1]);
                        }
                        return null;
                    });
            session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[] {Session.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getUserPrincipal":
                                return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                            case "getUserProperties":
                                if (propertyReadsUntilClose == 0) throw new IllegalStateException("닫힌 연결");
                                if (propertyReadsUntilClose > 0) propertyReadsUntilClose--;
                                return properties;
                            case "getAsyncRemote":
                                return remote;
                            case "isOpen":
                                return true;
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                throw new AssertionError(method.getName());
                        }
                    });
        }

        void finishWrites() {
            while (!callbacks.isEmpty()) callbacks.remove().onResult(new SendResult());
        }

        List<String> messages() {
            return frames.stream().filter(frame -> frame.contains("\"type\":\"MSG\""))
                    .collect(java.util.stream.Collectors.toList());
        }
    }
}
