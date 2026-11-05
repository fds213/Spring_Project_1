package com.ieum.gw.service;

import com.ieum.gw.code.ChatRoomType;
import com.ieum.gw.dao.ChatDAO;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.server.ChatServerEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ChatAccessTest {
    private static final long COMPANY = 7L;
    private static final long ROOM = 100L;
    private static final long EMPLOYEE = 11L;
    private static final long MESSAGE = 500L;
    private static final long ATTACHMENT = 55L;

    private final ChatService service = new ChatService();
    private final CompanyUsageDTO usage = new CompanyUsageDTO();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dao", new ChatDAO(null) {
            @Override
            public int isMember(Long companyId, long roomId, Long employeeId) {
                return companyId == COMPANY && roomId == ROOM && employeeId == EMPLOYEE ? 1 : 0;
            }

            @Override
            public Long lockRoom(Long companyId, Long roomId) {
                return companyId == COMPANY && roomId == ROOM ? ROOM : null;
            }

            @Override
            public List<ChatMessageDTO> getMessages(Long companyId, Long roomId, Long beforeMessageId) {
                assertEquals(COMPANY, companyId);
                assertEquals(ROOM, roomId);
                return List.of(message(companyId));
            }

            @Override
            public Long findImageAttachmentId(Long companyId, Long roomId, Long messageId) {
                assertEquals(COMPANY, companyId);
                assertEquals(ROOM, roomId);
                return messageId == MESSAGE ? ATTACHMENT : null;
            }

            @Override
            public ChatMessageDTO findByClientKey(ChatMessageDTO message) {
                fail("거절된 전송은 저장 단계에 들어갈 수 없습니다.");
                return null;
            }

            @Override
            public int markRead(Long companyId, Long roomId, Long employeeId, Long messageId) {
                fail("거절된 읽음 처리는 저장될 수 없습니다.");
                return 0;
            }
        });
        ReflectionTestUtils.setField(service, "eService", new EmployeeService() {
            @Override
            public boolean isActiveEmployee(Long companyId, Long employeeId) {
                return true;
            }

            @Override
            public Map<Long, String> getEmployeeNames(Long companyId, List<Long> employeeIds) {
                return Map.of(EMPLOYEE, "테스트 참여자");
            }
        });
        ReflectionTestUtils.setField(service, "sService", new SubscriptionService(null, null) {
            @Override
            public CompanyUsageDTO getCompanyUsage(Long companyId) {
                return usage;
            }
        });
        ReflectionTestUtils.setField(service, "attachmentService", new AttachmentService(null, null) {
            @Override
            public AttachmentDTO get(Long attachmentId, Long companyId) {
                assertEquals(COMPANY, companyId);
                assertEquals(ATTACHMENT, attachmentId);
                AttachmentDTO attachment = new AttachmentDTO();
                attachment.setAttachmentId(attachmentId);
                return attachment;
            }
        });
    }

    @Test
    @DisplayName("다른 업체 계정은 방 번호와 이미지 메시지 번호를 알아도 접근할 수 없다")
    void deniesOtherCompany() {
        long otherCompany = 8L;
        LoginUser outsider = user(otherCompany, EMPLOYEE);
        assertAll(
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.getMessages(otherCompany, ROOM, EMPLOYEE, null)),
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.getMessagesAfter(otherCompany, ROOM, EMPLOYEE, 0L)),
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.getRoomMemberNames(otherCompany, ROOM, EMPLOYEE)),
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.getImage(ROOM, MESSAGE, outsider)),
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.insertMessage(message(otherCompany)))
        );
    }

    @Test
    @DisplayName("같은 업체라도 방에 참여하지 않으면 대화와 이미지를 볼 수 없다")
    void deniesNonMember() {
        long outsider = 12L;
        assertThrows(AccessDeniedException.class,
                () -> service.getMessages(COMPANY, ROOM, outsider, null));
        assertThrows(AccessDeniedException.class,
                () -> service.getImage(ROOM, MESSAGE, user(COMPANY, outsider)));
    }

    @Test
    @DisplayName("만료 업체의 참여자는 기존 대화와 이미지를 조회할 수 있다")
    void permitsReadingAfterExpiry() {
        usage.setExpired(true);
        assertEquals(MESSAGE, service.getMessages(COMPANY, ROOM, EMPLOYEE, null).get(0).getChatMessageId());
        assertEquals(ATTACHMENT, service.getImage(ROOM, MESSAGE, user(COMPANY, EMPLOYEE)).getAttachmentId());
    }

    @Test
    @DisplayName("만료 업체는 이미지 전송과 읽음 저장을 거절한다")
    void deniesExpiredWrites() {
        usage.setExpired(true);
        MockMultipartFile file = new MockMultipartFile("image", "test.png", "image/png", new byte[] {1});
        assertThrows(AccessDeniedException.class,
                () -> service.sendImage(ROOM, user(COMPANY, EMPLOYEE), "image-key", file));
        assertThrows(AccessDeniedException.class,
                () -> service.markRead(COMPANY, ROOM, EMPLOYEE, MESSAGE));
    }

    @Test
    @DisplayName("그룹방 생성 시 본인과 중복 선택을 빼고 상대가 한 명뿐이면 거절한다")
    void rejectsGroupWithOnlyOneOtherEmployee() {
        assertThrows(IllegalArgumentException.class, () -> service.createRoom(
                COMPANY, EMPLOYEE, ChatRoomType.ROOM_GROUP, "그룹", List.of(EMPLOYEE, 12L, 12L)));
    }

    @Test
    @DisplayName("짝이 없는 서로게이트가 포함된 메시지는 저장하지 않는다")
    void rejectsUnpairedSurrogate() {
        ChatMessageDTO dto = message(COMPANY);
        dto.setMessageText("x" + Character.MIN_HIGH_SURROGATE);
        assertThrows(IllegalArgumentException.class, () -> service.insertMessage(dto));
    }

    @Test
    @DisplayName("열린 웹소켓에서도 업체가 만료되면 메시지 저장을 거절하고 연결을 닫는다")
    void rejectsSendOnOpenSocketAfterExpiry() throws Exception {
        ChatServerEndpoint endpoint = new ChatServerEndpoint();
        ReflectionTestUtils.setField(endpoint, "cService", service);
        Map<String, Object> properties = Map.of("roomId", ROOM, "loginUser", user(COMPANY, EMPLOYEE));
        AtomicReference<CloseReason> closed = new AtomicReference<>();
        Session session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
                new Class<?>[] {Session.class}, (proxy, method, args) -> {
                    if ("getUserProperties".equals(method.getName())) return properties;
                    if ("close".equals(method.getName())) {
                        closed.set((CloseReason) args[0]);
                        return null;
                    }
                    throw new AssertionError("만료 시 호출되면 안 되는 웹소켓 메서드: " + method.getName());
                });

        assertFalse(usage.isExpired());
        usage.setExpired(true);
        endpoint.handleMessage("{\"clientMessageKey\":\"text-key\",\"messageText\":\"테스트\"}", session);

        assertNotNull(closed.get());
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, closed.get().getCloseCode());
    }

    private static LoginUser user(long companyId, long employeeId) {
        AuthEmpDTO account = new AuthEmpDTO();
        account.setCompanyId(companyId);
        account.setEmployeeId(employeeId);
        account.setUserAccountId(employeeId);
        account.setStatus("ACTIVE");
        account.setRole("ROLE_EMP");
        return new LoginUser(account);
    }

    private static ChatMessageDTO message(long companyId) {
        ChatMessageDTO message = new ChatMessageDTO();
        message.setCompanyId(companyId);
        message.setChatRoomId(ROOM);
        message.setSenderEmployeeId(EMPLOYEE);
        message.setChatMessageId(MESSAGE);
        message.setMessageText("테스트");
        message.setClientMessageKey("text-key");
        return message;
    }
}
