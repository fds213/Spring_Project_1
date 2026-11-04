package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.NotificationDTO;
import com.ieum.gw.dto.NotificationPageDTO;
import com.ieum.gw.service.NotificationNotFoundException;
import com.ieum.gw.service.NotificationService;

/** 화면 JS가 받는 응답 모양(docs/front-screen-api.md 2장)을 확인한다. Security(CSRF)는 빼고 Controller만 띄운다 */
class NotificationControllerTest {

    /** 받은 값을 기억하고 정해 둔 예외를 던지는 가짜 */
    static class FakeService extends NotificationService {
        RuntimeException error;
        Long companyId;
        Long accountId;
        Integer page;
        Long notificationId;

        FakeService() {
            super(null);
        }

        @Override
        public NotificationPageDTO list(Long c, Long a, int p) {
            companyId = c;
            accountId = a;
            page = p;
            if (error != null) {
                throw error;
            }
            NotificationDTO unread = new NotificationDTO();
            unread.setNotificationId(5L);
            unread.setNotificationType("NT_APPROVAL_REQUEST");
            unread.setTitle("결재 요청");
            unread.setMessage("'휴가 신청서' 문서의 결재를 요청받았습니다.");
            unread.setApprovalDocumentId(7L);
            unread.setCreatedAt("2026-11-02T14:03:22");
            NotificationPageDTO result = new NotificationPageDTO();
            result.setPage(p);
            result.setPages(3);
            result.setTotal(41);
            result.setItems(List.of(unread));
            return result;
        }

        @Override
        public void markRead(Long c, Long a, Long n) {
            companyId = c;
            accountId = a;
            notificationId = n;
            if (error != null) {
                throw error;
            }
        }
    }

    private final FakeService service = new FakeService();
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(1L);
        emp.setUserAccountId(10L);
        LoginUser login = new LoginUser(emp);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("알림 목록 화면 주소는 notification/list 화면을 연다")
    void listScreen() throws Exception {
        MvcResult result = mvc.perform(get("/notification/list")).andReturn();

        assertEquals("notification/list", result.getModelAndView().getViewName());
    }

    @Test
    @DisplayName("목록은 로그인 정보의 업체·계정으로 읽고 success·data로 감싸 보낸다")
    void listJson() throws Exception {
        MvcResult result = mvc.perform(get("/notification/api/list").param("page", "2")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(1L, service.companyId);
        assertEquals(10L, service.accountId);
        assertEquals(2, service.page);
        JsonNode root = body(result);
        assertTrue(root.get("success").asBoolean());
        JsonNode data = root.get("data");
        assertEquals(2, data.get("page").asInt());
        assertEquals(3, data.get("pages").asInt());
        assertEquals(41, data.get("total").asInt());
        JsonNode item = data.get("items").get(0);
        assertEquals(5, item.get("notificationId").asLong());
        assertEquals("NT_APPROVAL_REQUEST", item.get("notificationType").asText());
        assertEquals("결재 요청", item.get("title").asText());
        assertEquals(7, item.get("approvalDocumentId").asLong());
        assertTrue(item.get("itTicketId").isNull());
        assertTrue(item.get("readAt").isNull());
        assertEquals("2026-11-02T14:03:22", item.get("createdAt").asText());
    }

    @Test
    @DisplayName("요청에 다른 업체·계정 값을 실어 보내도 로그인 정보만 쓴다")
    void requestValuesIgnored() throws Exception {
        mvc.perform(get("/notification/api/list")
                .param("companyId", "2").param("userAccountId", "20").param("receiverAccountId", "20")).andReturn();

        assertEquals(1L, service.companyId);
        assertEquals(10L, service.accountId);
    }

    @Test
    @DisplayName("페이지를 안 보내면 1페이지다")
    void defaultPage() throws Exception {
        mvc.perform(get("/notification/api/list")).andReturn();

        assertEquals(1, service.page);
    }

    @Test
    @DisplayName("페이지에 숫자가 아닌 값이 오면 400과 안내 문구다")
    void badPage() throws Exception {
        MvcResult result = mvc.perform(get("/notification/api/list").param("page", "abc")).andReturn();

        assertEquals(400, result.getResponse().getStatus());
        assertFalse(body(result).get("message").asText().isEmpty());
        assertNull(service.page);
    }

    @Test
    @DisplayName("읽음 처리는 POST이고 로그인 정보와 주소의 번호로 부른다")
    void read() throws Exception {
        MvcResult result = mvc.perform(post("/notification/api/5/read")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(body(result).get("success").asBoolean());
        assertEquals(1L, service.companyId);
        assertEquals(10L, service.accountId);
        assertEquals(5L, service.notificationId);
    }

    @Test
    @DisplayName("GET으로는 읽음 처리를 하지 않는다")
    void readGetNotAllowed() throws Exception {
        MvcResult result = mvc.perform(get("/notification/api/5/read")).andReturn();

        assertEquals(405, result.getResponse().getStatus());
        assertNull(service.notificationId);
    }

    @Test
    @DisplayName("없거나 남의 알림이면 404와 안내 문구다")
    void readNotFound() throws Exception {
        service.error = new NotificationNotFoundException();

        MvcResult result = mvc.perform(post("/notification/api/5/read")).andReturn();

        assertEquals(404, result.getResponse().getStatus());
        assertEquals("알림을 찾을 수 없습니다.", body(result).get("message").asText());
    }

    @Test
    @DisplayName("알림 번호에 숫자가 아닌 값이 오면 400이다")
    void readBadId() throws Exception {
        MvcResult result = mvc.perform(post("/notification/api/abc/read")).andReturn();

        assertEquals(400, result.getResponse().getStatus());
    }

    @Test
    @DisplayName("그 밖의 오류는 500과 같은 문구만 보이고 원인을 드러내지 않는다")
    void failedHidesCause() throws Exception {
        service.error = new IllegalStateException("ORA-00942 NOTIFICATION");

        MvcResult result = mvc.perform(get("/notification/api/list")).andReturn();

        assertEquals(500, result.getResponse().getStatus());
        assertFalse(body(result).get("message").asText().contains("ORA"));
    }
}
