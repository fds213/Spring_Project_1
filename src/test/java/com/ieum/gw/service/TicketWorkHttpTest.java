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
