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
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dao.AssetDAO;
import com.ieum.gw.dto.AssetAssignmentDTO;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.service.AssetService;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;

/** Controller와 실제 Service를 연결한다. DB는 가짜이며 Security 필터 체인은 포함하지 않는다. */
class AssetControllerTest {
    static class FakeDAO extends AssetDAO {
        int reads;
        boolean fail;
        FakeDAO() { super(null); }
        private void read() {
            reads++;
            if (fail) { throw new IllegalStateException("시험용 DB 오류 원인"); }
        }
        @Override
        public List<AssetDTO> selectMyAssets(Long companyId, Long employeeId) {
            read();
            if (companyId != 7L || employeeId != 9L) { return List.of(); }
            AssetDTO dto = new AssetDTO();
            dto.setAssetId(13L);
            dto.setAssetNo("LT-001");
            dto.setAssetName("업무용 노트북");
            dto.setBranchId(15L);
            dto.setItTicketId(17L);
            dto.setIssuedAt(LocalDateTime.of(2026, 11, 2, 14, 3, 22));
            return List.of(dto);
        }
        @Override
        public List<AssetAssignmentDTO> selectMyAssignments(Long companyId, Long employeeId, Long assetId) {
            read();
            if (companyId != 7L || employeeId != 9L || assetId != 13L) { return List.of(); }
            AssetAssignmentDTO dto = new AssetAssignmentDTO();
            dto.setAssetAssignmentId(19L);
            dto.setAssetId(13L);
            dto.setIssuedAt(LocalDateTime.of(2026, 10, 30, 9, 0));
            dto.setReturnedAt(LocalDateTime.of(2026, 11, 1, 18, 0));
            return List.of(dto);
        }
    }

    private final FakeDAO dao = new FakeDAO();
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        InternalResourceViewResolver views = new InternalResourceViewResolver();
        views.setPrefix("/WEB-INF/views/");
        views.setSuffix(".jsp");
        mvc = MockMvcBuilders.standaloneSetup(new AssetController(new AssetService(dao)))
                .setViewResolvers(views)
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

    @Test
    @DisplayName("내 장비 화면 주소는 asset/mine JSP를 연다")
    void opensMyAssetsScreen() throws Exception {
        var result = mvc.perform(get("/asset/mine")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("asset/mine", result.getModelAndView().getViewName());
        assertEquals(0, dao.reads);
    }

    @Test
    @DisplayName("자산 관리 화면 주소는 관리자 목록 JSP를 선택하고 DB를 조회하지 않는다")
    void opensAdminAssetsScreen() throws Exception {
        var result = mvc.perform(get("/asset/admin/list")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertNotNull(result.getModelAndView());
        String viewName = result.getModelAndView().getViewName();
        assertNotNull(viewName);
        assertEquals("asset/admin/list", viewName.replaceFirst("^/", ""));
        assertEquals("/WEB-INF/views/asset/admin/list.jsp", result.getResponse().getForwardedUrl()
                .replaceAll("/{2,}", "/"));
        assertEquals(0, dao.reads);
    }

    @Test
    @DisplayName("현재 지급 자산은 로그인 정보로 조회하고 날짜와 빈 제조 일련번호를 JSON으로 보낸다")
    void assignedAssets() throws Exception {
        var result = mvc.perform(get("/asset/api/assets/assigned").param("companyId", "99")
                .param("employeeId", "88")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(body(result).get("success").asBoolean());
        var item = body(result).get("data").get(0);
        assertEquals("업무용 노트북", item.get("assetName").asText());
        assertEquals("LT-001", item.get("assetNo").asText());
        assertTrue(item.get("serialNo").isNull());
        assertEquals("2026-11-02T14:03:22", item.get("issuedAt").asText());
    }

    @Test
    @DisplayName("지급 자산이 없는 직원은 빈 배열을 받는다")
    void emptyAssets() throws Exception {
        login(7L, 88L);
        var result = mvc.perform(get("/asset/api/assets/assigned")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, body(result).get("data").size());
    }

    @Test
    @DisplayName("회수된 장비의 본인 지급 이력도 조회할 수 있다")
    void returnedAssignment() throws Exception {
        var result = mvc.perform(get("/asset/api/assets/13/assignments")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var item = body(result).get("data").get(0);
        assertEquals(19L, item.get("assetAssignmentId").asLong());
        assertEquals("2026-11-01T18:00:00", item.get("returnedAt").asText());
        assertNull(item.get("employeeId"));
    }

    @Test
    @DisplayName("다른 업체나 다른 사원으로 로그인하면 같은 자산 번호의 지급 이력을 볼 수 없다")
    void deniesOtherOwner() throws Exception {
        for (long[] owner : new long[][] {{99L, 9L}, {7L, 88L}}) {
            login(owner[0], owner[1]);
            assertEquals(404, mvc.perform(get("/asset/api/assets/13/assignments")
                    .param("companyId", "7").param("employeeId", "9")).andReturn().getResponse().getStatus());
        }
    }

    @Test
    @DisplayName("지급한 이력이 없는 자산은 404로 응답한다")
    void missingAssetHistory() throws Exception {
        assertEquals(404, mvc.perform(get("/asset/api/assets/999/assignments")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("비로그인과 불완전한 인증 정보는 DB 조회 전에 401로 거절한다")
    void requiresLogin() throws Exception {
        SecurityContextHolder.clearContext();
        assertEquals(401, mvc.perform(get("/asset/api/assets/assigned")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/asset/api/assets/13/assignments")).andReturn().getResponse().getStatus());
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            login(invalid, 9L);
            assertEquals(401, mvc.perform(get("/asset/api/assets/assigned")).andReturn().getResponse().getStatus());
            login(7L, invalid);
            assertEquals(401, mvc.perform(get("/asset/api/assets/13/assignments")).andReturn().getResponse().getStatus());
        }
        assertEquals(0, dao.reads);
    }

    @Test
    @DisplayName("자산 번호가 숫자가 아니거나 양수가 아니면 400으로 거절한다")
    void invalidAssetId() throws Exception {
        for (String value : new String[] {"abc", "0", "-1", "99999999999999999999999"}) {
            var result = mvc.perform(get("/asset/api/assets/" + value + "/assignments")).andReturn();
            assertEquals(400, result.getResponse().getStatus());
        }
        assertEquals(0, dao.reads);
    }

    @Test
    @DisplayName("조회 API는 POST 요청을 받지 않는다")
    void rejectsPost() throws Exception {
        assertEquals(405, mvc.perform(post("/asset/api/assets/assigned")).andReturn().getResponse().getStatus());
        assertEquals(405, mvc.perform(post("/asset/api/assets/13/assignments")).andReturn().getResponse().getStatus());
        assertEquals(0, dao.reads);
    }

    @Test
    @DisplayName("DB 오류의 원인은 응답에 노출하지 않는다")
    void hidesDatabaseError() throws Exception {
        dao.fail = true;
        var result = mvc.perform(get("/asset/api/assets/assigned")).andReturn();
        assertEquals(500, result.getResponse().getStatus());
        assertFalse(body(result).get("success").asBoolean());
        assertFalse(body(result).get("message").asText().contains("시험용 DB 오류 원인"));
    }
}
