package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
import com.ieum.gw.dao.TicketReadDAO;
import com.ieum.gw.dto.TicketCommentDTO;
import com.ieum.gw.dto.TicketHistoryDTO;
import com.ieum.gw.service.TicketReadService;

/** 실제 Service의 권한 검사를 거친다. DB와 전체 Security 필터는 이 테스트에 포함하지 않는다. */
class TicketReadControllerTest {
    static class FakeDAO extends TicketReadDAO {
        int ownershipReads;
        int contentReads;
        boolean empty;
        boolean fail;
        FakeDAO() { super(null); }
        @Override
        public int countMyTicket(Long companyId, Long employeeId, Long itTicketId) {
            ownershipReads++;
            if (fail) { throw new IllegalStateException("시험용 DB 오류 원인"); }
            return companyId == 7L && employeeId == 9L && itTicketId == 13L ? 1 : 0;
        }
        private void checkOwner(Long companyId, Long employeeId, Long itTicketId) {
            contentReads++;
            assertEquals(7L, companyId);
            assertEquals(9L, employeeId);
            assertEquals(13L, itTicketId);
        }
        @Override
        public List<TicketCommentDTO> selectMyComments(Long companyId, Long employeeId, Long itTicketId) {
            checkOwner(companyId, employeeId, itTicketId);
            if (empty) { return List.of(); }
            TicketCommentDTO dto = new TicketCommentDTO();
            dto.setTicketCommentId(15L);
            dto.setAuthorEmployeeId(17L);
            dto.setContent("장비를 확인했습니다.");
            dto.setCreatedAt(LocalDateTime.of(2026, 11, 2, 14, 3, 22));
            return List.of(dto);
        }
        @Override
        public List<TicketHistoryDTO> selectMyHistory(Long companyId, Long employeeId, Long itTicketId) {
            checkOwner(companyId, employeeId, itTicketId);
            if (empty) { return List.of(); }
            TicketHistoryDTO dto = new TicketHistoryDTO();
            dto.setTicketHistoryId(19L);
            dto.setActorAccountId(21L);
            dto.setBeforeStatus("ASSIGNED");
            dto.setAfterStatus("IN_PROGRESS");
            dto.setBeforeAssigneeId(17L);
            dto.setAfterAssigneeId(17L);
            dto.setReason("점검 시작");
            dto.setCreatedAt(LocalDateTime.of(2026, 11, 2, 14, 3, 22));
            return List.of(dto);
        }
    }

    private final FakeDAO dao = new FakeDAO();
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.standaloneSetup(new TicketReadController(new TicketReadService(dao)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        login(7L, 9L);
    }
    private void login(Long companyId, Long employeeId) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(companyId);
        emp.setEmployeeId(employeeId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(emp), null, List.of()));
    }
    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private List<String> urls(String id) {
        return List.of("/ticket/api/tickets/" + id + "/comments", "/ticket/api/tickets/" + id + "/history");
    }

    @Test
    @DisplayName("내 티켓·접수 화면 주소는 각각의 JSP를 연다")
    void opensTicketScreens() throws Exception {
        var mine = mvc.perform(get("/ticket/mine")).andReturn();
        assertEquals("ticket/mine", mine.getModelAndView().getViewName());
        var form = mvc.perform(get("/ticket/new")).andReturn();
        assertEquals("ticket/new", form.getModelAndView().getViewName());
        assertEquals(0, dao.ownershipReads);
    }

    @Test
    @DisplayName("내 티켓 댓글은 인증 정보로 조회하며 작성자 ID·내용·시각을 응답한다")
    void readsComments() throws Exception {
        var result = mvc.perform(get(urls("13").get(0)).param("companyId", "99")
                .param("employeeId", "88")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(body(result).get("success").asBoolean());
        var item = body(result).get("data").get(0);
        assertEquals(15L, item.get("ticketCommentId").asLong());
        assertEquals(17L, item.get("authorEmployeeId").asLong());
        assertEquals("장비를 확인했습니다.", item.get("content").asText());
        assertEquals("2026-11-02T14:03:22", item.get("createdAt").asText());
    }

    @Test
    @DisplayName("내 티켓 처리 이력은 이미 저장된 전후 상태와 담당자를 그대로 응답한다")
    void readsHistory() throws Exception {
        var result = mvc.perform(get(urls("13").get(1))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var item = body(result).get("data").get(0);
        assertEquals("ASSIGNED", item.get("beforeStatus").asText());
        assertEquals("IN_PROGRESS", item.get("afterStatus").asText());
        assertEquals("점검 시작", item.get("reason").asText());
        assertEquals(17L, item.get("afterAssigneeId").asLong());
    }

    @Test
    @DisplayName("내 티켓이 있지만 댓글·이력이 없으면 빈 배열로 응답한다")
    void returnsEmptyArrays() throws Exception {
        dao.empty = true;
        for (String url : urls("13")) {
            var result = mvc.perform(get(url)).andReturn();
            assertEquals(200, result.getResponse().getStatus());
            assertEquals(0, body(result).get("data").size());
        }
    }

    @Test
    @DisplayName("다른 업체나 같은 업체의 다른 사원은 티켓 댓글·이력을 읽지 못한다")
    void deniesOtherOwner() throws Exception {
        for (long[] owner : new long[][] {{99L, 9L}, {7L, 88L}}) {
            login(owner[0], owner[1]);
            for (String url : urls("13")) {
                assertEquals(404, mvc.perform(get(url).param("companyId", "7").param("employeeId", "9"))
                        .andReturn().getResponse().getStatus());
            }
        }
        assertEquals(0, dao.contentReads);
    }

    @Test
    @DisplayName("없는 티켓은 남의 티켓과 같은 404로 응답한다")
    void deniesMissingTicket() throws Exception {
        for (String url : urls("999")) {
            assertEquals(404, mvc.perform(get(url)).andReturn().getResponse().getStatus());
        }
        assertEquals(0, dao.contentReads);
    }

    @Test
    @DisplayName("비로그인이나 불완전한 인증 정보는 조회 전에 401로 거절한다")
    void requiresLogin() throws Exception {
        SecurityContextHolder.clearContext();
        for (String url : urls("13")) {
            assertEquals(401, mvc.perform(get(url)).andReturn().getResponse().getStatus());
        }
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            login(invalid, 9L);
            assertEquals(401, mvc.perform(get(urls("13").get(0))).andReturn().getResponse().getStatus());
            login(7L, invalid);
            assertEquals(401, mvc.perform(get(urls("13").get(1))).andReturn().getResponse().getStatus());
        }
        assertEquals(0, dao.ownershipReads);
    }

    @Test
    @DisplayName("잘못된 티켓 번호는 조회 전에 400으로 거절한다")
    void rejectsInvalidTicketId() throws Exception {
        for (String id : new String[] {"abc", "0", "-1", "99999999999999999999999"}) {
            for (String url : urls(id)) {
                assertEquals(400, mvc.perform(get(url)).andReturn().getResponse().getStatus());
            }
        }
        assertEquals(0, dao.ownershipReads);
    }

    @Test
    @DisplayName("조회 Controller는 POST 요청을 받지 않는다")
    void rejectsPost() throws Exception {
        for (String url : urls("13")) {
            assertEquals(405, mvc.perform(post(url)).andReturn().getResponse().getStatus());
        }
        assertEquals(0, dao.ownershipReads);
    }

    @Test
    @DisplayName("DB 오류 내용은 사용자 응답에서 숨긴다")
    void hidesDatabaseError() throws Exception {
        dao.fail = true;
        for (String url : urls("13")) {
            var result = mvc.perform(get(url)).andReturn();
            assertEquals(500, result.getResponse().getStatus());
            assertFalse(body(result).get("success").asBoolean());
            assertFalse(body(result).get("message").asText().contains("시험용 DB 오류 원인"));
        }
    }
}
