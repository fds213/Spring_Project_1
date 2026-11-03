package com.ieum.gw.location.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.location.dto.BranchDTO;
import com.ieum.gw.location.service.BranchService;

/** 실제 Security 체인 없이 Controller의 인증 정보 사용과 JSON 응답을 확인한다. */
class BranchControllerTest {
    static class FakeService extends BranchService {
        Long companyId;
        RuntimeException error;
        List<BranchDTO> branches = List.of();
        FakeService() { super(null); }
        @Override
        public List<BranchDTO> getBranches(Long id) {
            companyId = id;
            if (error != null) { throw error; }
            return branches;
        }
    }

    private final FakeService service = new FakeService();
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.standaloneSetup(new BranchController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        login(7L);
    }

    private void login(Long companyId) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(companyId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(emp), null, List.of()));
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test
    @DisplayName("다른 업체 번호를 요청에 실어도 로그인한 업체의 지점만 조회한다")
    void ignoresSubmittedCompany() throws Exception {
        BranchDTO branch = new BranchDTO();
        branch.setBranchId(13L);
        branch.setBranchName("본사");
        service.branches = List.of(branch);
        var result = mvc.perform(get("/location/api/branches").param("companyId", "99")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(7L, service.companyId);
        var body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(body.get("success").asBoolean());
        assertEquals(13L, body.get("data").get(0).get("branchId").asLong());
        assertEquals("본사", body.get("data").get(0).get("branchName").asText());
    }

    @Test
    @DisplayName("지점이 없는 업체의 응답은 빈 배열이다")
    void returnsEmptyList() throws Exception {
        var result = mvc.perform(get("/location/api/branches")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, json.readTree(result.getResponse().getContentAsString()).get("data").size());
    }

    @Test
    @DisplayName("로그인이 없으면 401이며 지점을 조회하지 않는다")
    void rejectsMissingLogin() throws Exception {
        SecurityContextHolder.clearContext();
        var result = mvc.perform(get("/location/api/branches")).andReturn();
        assertEquals(401, result.getResponse().getStatus());
        assertNull(service.companyId);
    }

    @Test
    @DisplayName("인증 정보에 업체가 없거나 잘못되면 조회하지 않는다")
    void rejectsInvalidCompany() throws Exception {
        for (Long id : new Long[] {null, 0L, -1L}) {
            login(id);
            assertEquals(401, mvc.perform(get("/location/api/branches")).andReturn().getResponse().getStatus());
            assertNull(service.companyId);
        }
    }

    @Test
    @DisplayName("조회 API에 POST를 보내면 405이며 쓰기 처리를 하지 않는다")
    void rejectsPost() throws Exception {
        assertEquals(405, mvc.perform(post("/location/api/branches")).andReturn().getResponse().getStatus());
        assertNull(service.companyId);
    }

    @Test
    @DisplayName("DB 오류가 발생해도 응답에 내부 오류 내용을 노출하지 않는다")
    void hidesDatabaseFailure() throws Exception {
        service.error = new IllegalStateException("시험용 DB 오류 원인");
        var result = mvc.perform(get("/location/api/branches")).andReturn();
        assertEquals(500, result.getResponse().getStatus());
        var body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertFalse(body.get("success").asBoolean());
        assertFalse(body.get("message").asText().contains("시험용 DB 오류 원인"));
    }
}
