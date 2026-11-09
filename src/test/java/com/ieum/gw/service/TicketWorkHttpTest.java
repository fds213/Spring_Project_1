package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.controller.TicketWorkController;

/** 실제 Service를 통과하는 HTTP 바인딩 검사. 전체 Security·CSRF 필터 검증은 별도다. */
class TicketWorkHttpTest {
    TicketWorkServiceTest fixture;
    MockMvc mvc;
    @BeforeEach void setup() {
        fixture=new TicketWorkServiceTest(); fixture.setup();
        mvc=MockMvcBuilders.standaloneSetup(new TicketWorkController(fixture.service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    void login(String role, long employee) {
        LoginUser u=TicketWorkServiceTest.user(1L,employee,role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,"",u.getAuthorities()));
    }

    @Test
    @DisplayName("관리자 목록은 기존 응답을 유지하고 검색 요청에는 페이지 정보를 추가한다")
    void adminListSupportsLegacyAndSearchRequests() throws Exception {
        login("ROLE_IT", 3L);
        String url = "/ticket/admin/api/tickets";
        ObjectMapper json = new ObjectMapper();

        var legacy = mvc.perform(get(url).param("filter", "ALL")).andReturn().getResponse();
        assertEquals(200, legacy.getStatus());
        JsonNode legacyData = json.readTree(legacy.getContentAsByteArray()).path("data");
        assertEquals(2, legacyData.size());
        assertEquals(1, legacyData.path("items").size());
        assertEquals(1, legacyData.path("counts").path("ALL").asInt());
        assertNull(fixture.rows.countArguments);

        fixture.rows.adminCounts.setAllCount(21L);
        var paged = mvc.perform(get(url).param("filter", "ALL").param("page", "2")
                .param("keyword", "장애").param("companyId", "999").param("employeeId", "999"))
                .andReturn().getResponse();
        assertEquals(200, paged.getStatus());
        JsonNode data = json.readTree(paged.getContentAsByteArray()).path("data");
        assertEquals(2, data.path("page").asInt());
        assertEquals(2, data.path("pages").asInt());
        assertEquals(21, data.path("total").asInt());
        assertEquals(21, data.path("counts").path("ALL").asInt());
        assertEquals(legacyData.path("items"), data.path("items"));
        assertEquals(java.util.List.of(1L, 3L, "장애", "ALL", 20L, 20), fixture.rows.pageArguments);
    }

    @Test
    @DisplayName("관리자 검색은 일반 직원과 잘못된 탭·페이지 요청을 DB 조회 전에 거절한다")
    void adminSearchRejectsUnauthorizedAndInvalidRequests() throws Exception {
        String url = "/ticket/admin/api/tickets";
        login("ROLE_EMP", 2L);
        assertEquals(403, mvc.perform(get(url).param("page", "1")).andReturn().getResponse().getStatus());

        login("ROLE_ADMIN", 3L);
        assertEquals(400, mvc.perform(get(url).param("page", "abc")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(get(url).param("filter", "UNKNOWN").param("page", "1"))
                .andReturn().getResponse().getStatus());
        assertNull(fixture.rows.countArguments);
        assertNull(fixture.rows.pageArguments);
    }

    @Test @DisplayName("접수 HTTP 응답에는 숫자 id가 있고 위조 업체·작성자 대신 인증 정보를 저장한다")
    void create() throws Exception {
        login("ROLE_EMP",2L);
        var response=mvc.perform(post("/ticket/api/tickets").contentType("application/json")
                .content("{\"type\":\"TK_QUESTION\",\"title\":\"문의\",\"body\":\"내용\",\"branchId\":8,\"assetId\":null,\"companyId\":999,\"requesterId\":999}"))
                .andReturn().getResponse();
        assertEquals(200,response.getStatus());
        JsonNode json=new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertTrue(json.path("data").path("id").isNumber());
        assertEquals(2L,fixture.rows.row.getRequesterId()); assertEquals(1L,fixture.rows.row.getCompanyId());
    }
    @Test @DisplayName("내 티켓 HTTP 목록은 data 배열과 댓글·이력·version을 제공한다")
    void mine() throws Exception {
        login("ROLE_EMP",2L);
        var response=mvc.perform(get("/ticket/api/tickets")).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        JsonNode item=new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data").get(0);
        assertTrue(item.path("comments").isArray()); assertTrue(item.path("history").isArray());
        assertEquals(0,item.path("version").asInt()); assertFalse(item.path("canClose").asBoolean());
        assertTrue(item.path("canCancel").isBoolean());
        assertTrue(item.path("canCancel").asBoolean());
    }

    @Test
    @DisplayName("내 티켓 HTTP 목록은 종료된 문의의 canCancel을 false로 반환한다")
    void closedQuestionCannotBeCancelled() throws Exception {
        login("ROLE_EMP", 2L);
        fixture.rows.row.setType("TK_QUESTION");
        fixture.rows.row.setStatus("CLOSED");
        var response = mvc.perform(get("/ticket/api/tickets")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        JsonNode item = new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8))
                .path("data").get(0);
        assertTrue(item.path("canCancel").isBoolean());
        assertFalse(item.path("canCancel").asBoolean());
    }
    @Test @DisplayName("비로그인·잘못된 번호·깨진 JSON은 401·400으로 응답한다")
    void malformed() throws Exception {
        assertEquals(401,mvc.perform(get("/ticket/api/tickets")).andReturn().getResponse().getStatus());
        login("ROLE_EMP",2L);
        assertEquals(400,mvc.perform(post("/ticket/api/tickets/abc/close").contentType("application/json").content("{\"version\":0}"))
                .andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(post("/ticket/api/tickets").contentType("application/json").content("{"))
                .andReturn().getResponse().getStatus());
    }
    @Test
    @DisplayName("취소 POST는 입력 업체·작성자 대신 인증 사용자를 쓰고 성공 응답을 반환한다")
    void cancel() throws Exception {
        login("ROLE_EMP", 2L);
        var response = mvc.perform(post("/ticket/api/tickets/10/cancel").contentType("application/json")
                .content("{\"version\":0,\"companyId\":999,\"requesterId\":999}"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        JsonNode json = new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, json.size());
        assertTrue(json.path("success").asBoolean());
        assertEquals("CANCELED", fixture.rows.lastChange.get("afterStatus"));
        assertEquals(1L, fixture.rows.lastChange.get("companyId"));
        assertEquals(102L, fixture.rows.lastChange.get("actorAccountId"));
        assertEquals(1, fixture.rows.histories);
    }

    @Test
    @DisplayName("취소 GET은 405로 거절하고 비로그인 POST는 401로 거절한다")
    void cancelRequiresLoginAndPost() throws Exception {
        assertEquals(401, mvc.perform(post("/ticket/api/tickets/10/cancel")
                .contentType("application/json").content("{\"version\":0}"))
                .andReturn().getResponse().getStatus());
        login("ROLE_EMP", 2L);
        assertEquals(405, mvc.perform(get("/ticket/api/tickets/10/cancel"))
                .andReturn().getResponse().getStatus());
        assertEquals(0, fixture.rows.updates);
    }

    @Test
    @DisplayName("취소 POST의 타인 요청·오래된 버전·빈 버전은 404·409·400으로 응답한다")
    void cancelRejectsInvalidRequests() throws Exception {
        login("ROLE_EMP", 3L);
        assertEquals(404, mvc.perform(post("/ticket/api/tickets/10/cancel")
                .contentType("application/json").content("{\"version\":0}"))
                .andReturn().getResponse().getStatus());
        login("ROLE_EMP", 2L);
        fixture.rows.row.setVersion(1L);
        assertEquals(409, mvc.perform(post("/ticket/api/tickets/10/cancel")
                .contentType("application/json").content("{\"version\":0}"))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post("/ticket/api/tickets/10/cancel")
                .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus());
        assertEquals(0, fixture.rows.updates);
    }

    @Test @DisplayName("일반 직원의 관리자 쓰기는 403이고 중복 버전은 409로 응답한다")
    void mutations() throws Exception {
        login("ROLE_EMP",2L);
        assertEquals(403,mvc.perform(post("/ticket/admin/api/tickets/10/assign").contentType("application/json").content("{\"version\":0}"))
                .andReturn().getResponse().getStatus());
        login("ROLE_IT",3L); fixture.rows.row.setVersion(2L);
        assertEquals(409,mvc.perform(post("/ticket/admin/api/tickets/10/assign").contentType("application/json").content("{\"version\":0}"))
                .andReturn().getResponse().getStatus());
    }
}
