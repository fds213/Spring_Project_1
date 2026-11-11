package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dao.BranchDAO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.service.BranchService;

/** 실제 Security 체인 없이 Controller의 인증 정보 사용과 JSON 응답을 확인한다. */
class BranchControllerTest {
    static class FakeService extends BranchService {
        Long companyId;
        RuntimeException error;
        List<BranchDTO> branches = List.of();
        FakeService(BranchDAO dao) { super(dao); }
        @Override
        public List<BranchDTO> getBranches(Long id) {
            companyId = id;
            if (error != null) { throw error; }
            return branches;
        }
    }

    private Long adminCompanyId;
    private Long createdCompanyId;
    private String createdBranchName;
    private String createdAddress;
    private String createdDetailAddress;
    private boolean duplicateBranchName;
    private List<Object> updatedBranch;
    private final List<String> disableSteps = new ArrayList<>();
    private String branchUseYn = "Y";
    private int pendingEquipmentTickets;
    private final FakeService service = new FakeService(new BranchDAO(null) {
        @Override
        public List<BranchDetailDTO> selectBranchDetails(Long companyId) {
            adminCompanyId = companyId;
            if (Long.valueOf(7L).equals(companyId)) {
                return List.of(branchDetail(13L, "Y"), branchDetail(14L, "N"));
            }
            return List.of(branchDetail(99L, "Y"));
        }

        @Override
        public int insertBranch(Long companyId, String branchName, String address, String detailAddress) {
            if (duplicateBranchName) {
                throw new DuplicateKeyException("시험용 지점명 중복");
            }
            createdCompanyId = companyId;
            createdBranchName = branchName;
            createdAddress = address;
            createdDetailAddress = detailAddress;
            return 1;
        }

        @Override
        public int updateBranch(Long companyId, Long branchId, String branchName, String address, String detailAddress) {
            updatedBranch = Arrays.asList(companyId, branchId, branchName, address, detailAddress);
            if (!Long.valueOf(7L).equals(companyId) || !Long.valueOf(13L).equals(branchId)) {
                return 0;
            }
            if (duplicateBranchName) {
                throw new DuplicateKeyException("시험용 지점명 중복");
            }
            return 1;
        }

        @Override
        public Long lockBranch(Long companyId, Long branchId) {
            disableSteps.add("lock");
            return Long.valueOf(7L).equals(companyId) && Long.valueOf(13L).equals(branchId)
                    ? branchId : null;
        }

        @Override
        public String selectBranchUseYn(Long companyId, Long branchId) {
            disableSteps.add("status");
            return branchUseYn;
        }

        @Override
        public int countPendingEquipmentTickets(Long companyId, Long branchId) {
            disableSteps.add("count");
            assertEquals(7L, companyId);
            assertEquals(13L, branchId);
            return pendingEquipmentTickets;
        }

        @Override
        public int disableBranch(Long companyId, Long branchId) {
            disableSteps.add("disable");
            assertEquals(7L, companyId);
            assertEquals(13L, branchId);
            branchUseYn = "N";
            return 1;
        }
    });
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
        login(companyId, "ROLE_EMP");
    }

    private void login(Long companyId, String role) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(companyId);
        emp.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(emp), null, List.of()));
    }

    private static BranchDetailDTO branchDetail(long branchId, String useYn) {
        BranchDetailDTO branch = new BranchDetailDTO();
        branch.setBranchId(branchId);
        branch.setUseYn(useYn);
        return branch;
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
    @DisplayName("지점 관리 화면 주소는 관리 화면 JSP를 고르고 지점을 조회하지 않는다")
    void opensAdminBranchScreen() throws Exception {
        InternalResourceViewResolver views = new InternalResourceViewResolver();
        views.setPrefix("/WEB-INF/views/");
        views.setSuffix(".jsp");
        MockMvc page = MockMvcBuilders.standaloneSetup(new BranchController(service))
                .setViewResolvers(views).build();

        var response = page.perform(get("/location/admin/branch")).andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("/WEB-INF/views/location/admin/branch.jsp", response.getForwardedUrl());
        assertNull(adminCompanyId);
    }

    @Test
    @DisplayName("업체 관리자는 요청의 업체 번호와 관계없이 소속 업체의 사용 중·사용 중지 지점을 조회한다")
    void adminListIncludesBothStatusesForAuthenticatedCompany() throws Exception {
        login(7L, "ROLE_ADMIN");
        var response = mvc.perform(get("/location/admin/api/branches").param("companyId", "99"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(7L, adminCompanyId);
        var body = json.readTree(response.getContentAsByteArray());
        assertTrue(body.path("success").asBoolean());
        var rows = body.path("data");
        assertEquals(2, rows.size());
        assertEquals(13L, rows.get(0).path("branchId").asLong());
        assertEquals("Y", rows.get(0).path("useYn").asText());
        assertEquals(14L, rows.get(1).path("branchId").asLong());
        assertEquals("N", rows.get(1).path("useYn").asText());
    }

    @Test
    @DisplayName("지점 관리 목록은 미인증과 관리자 이외의 요청을 DAO 호출 전에 거절한다")
    void adminListRejectsUnauthorizedRequests() throws Exception {
        String url = "/location/admin/api/branches";
        SecurityContextHolder.clearContext();
        assertEquals(401, mvc.perform(get(url)).andReturn().getResponse().getStatus());
        login(0L, "ROLE_ADMIN");
        assertEquals(401, mvc.perform(get(url)).andReturn().getResponse().getStatus());
        for (String role : List.of("ROLE_IT", "ROLE_EMP")) {
            login(7L, role);
            assertEquals(403, mvc.perform(get(url)).andReturn().getResponse().getStatus());
        }
        assertNull(adminCompanyId);
    }

    @Test
    @DisplayName("지점 등록은 branchName을 받아 로그인 업체에 저장하고 빈 상세 주소를 null로 전달한다")
    void createsBranchForAuthenticatedCompany() throws Exception {
        login(7L, "ROLE_ADMIN");
        var response = mvc.perform(post("/location/admin/api/branches").contentType("application/json")
                .content("{\"companyId\":99,\"branchName\":\" 판교지점 \",\"address\":\" 판교역로 \",\"detailAddress\":\" \"}"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertTrue(json.readTree(response.getContentAsByteArray()).path("success").asBoolean());
        assertEquals(7L, createdCompanyId);
        assertEquals("판교지점", createdBranchName);
        assertEquals("판교역로", createdAddress);
        assertNull(createdDetailAddress);
    }

    @Test
    @DisplayName("지점 등록은 권한 오류와 빈 필수값을 거절하고 같은 지점명은 409로 응답한다")
    void rejectsInvalidBranchCreation() throws Exception {
        String url = "/location/admin/api/branches";
        String valid = "{\"branchName\":\"판교지점\",\"address\":\"판교역로\"}";
        login(7L, "ROLE_EMP");
        assertEquals(403, mvc.perform(post(url).contentType("application/json").content(valid))
                .andReturn().getResponse().getStatus());
        login(7L, "ROLE_ADMIN");
        assertEquals(400, mvc.perform(post(url).contentType("application/json").content("{"))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post(url).contentType("application/json")
                .content("{\"branchName\":\"판교지점\",\"address\":\" \"}"))
                .andReturn().getResponse().getStatus());
        assertNull(createdCompanyId);

        duplicateBranchName = true;
        assertEquals(409, mvc.perform(post(url).contentType("application/json").content(valid))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("지점 수정은 로그인 업체와 주소의 지점 번호를 사용하고 다른 업체 지점은 404로 응답한다")
    void updatesOnlyBranchInAuthenticatedCompany() throws Exception {
        String url = "/location/admin/api/branches/{branchId}/update";
        String form = "{\"companyId\":99,\"branchId\":99,\"branchName\":\" 새 지점 \",\"address\":\" 새 주소 \"}";
        login(7L, "ROLE_ADMIN");

        var response = mvc.perform(post(url, 13).contentType("application/json").content(form))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertTrue(json.readTree(response.getContentAsByteArray()).path("success").asBoolean());
        assertEquals(Arrays.asList(7L, 13L, "새 지점", "새 주소", null), updatedBranch);

        assertEquals(404, mvc.perform(post(url, 99).contentType("application/json").content(form))
                .andReturn().getResponse().getStatus());
        assertEquals(7L, updatedBranch.get(0));
        assertEquals(99L, updatedBranch.get(1));
    }

    @Test
    @DisplayName("지점 수정은 권한·입력 오류를 먼저 거절하고 중복 지점명은 409로 응답한다")
    void rejectsInvalidBranchUpdate() throws Exception {
        String url = "/location/admin/api/branches/{branchId}/update";
        String form = "{\"branchName\":\"판교지점\",\"address\":\"판교역로\"}";
        login(7L, "ROLE_IT");
        assertEquals(403, mvc.perform(post(url, 13).contentType("application/json").content(form))
                .andReturn().getResponse().getStatus());

        login(7L, "ROLE_ADMIN");
        assertEquals(400, mvc.perform(post(url, "abc").contentType("application/json").content(form))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post(url, 0).contentType("application/json").content(form))
                .andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post(url, 13).contentType("application/json")
                .content("{\"branchName\":\"판교지점\"}")).andReturn().getResponse().getStatus());
        assertNull(updatedBranch);

        duplicateBranchName = true;
        assertEquals(409, mvc.perform(post(url, 13).contentType("application/json").content(form))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("미지급 건이 있으면 중지를 막고 모두 지급한 뒤에는 상태를 바꾸며 재요청도 성공한다")
    void blocksDisableUntilEquipmentIsIssued() throws Exception {
        String url = "/location/admin/api/branches/{branchId}/disable";
        login(7L, "ROLE_ADMIN");
        pendingEquipmentTickets = 2;

        var conflict = mvc.perform(post(url, 13).param("companyId", "99")).andReturn().getResponse();
        assertEquals(409, conflict.getStatus());
        assertTrue(json.readTree(conflict.getContentAsByteArray()).path("message").asText().contains("2건"));
        assertEquals(List.of("lock", "status", "count"), disableSteps);
        assertEquals("Y", branchUseYn);

        pendingEquipmentTickets = 0;
        disableSteps.clear();
        assertEquals(200, mvc.perform(post(url, 13)).andReturn().getResponse().getStatus());
        assertEquals(List.of("lock", "status", "count", "disable"), disableSteps);
        assertEquals("N", branchUseYn);

        disableSteps.clear();
        assertEquals(200, mvc.perform(post(url, 13)).andReturn().getResponse().getStatus());
        assertEquals(List.of("lock", "status"), disableSteps);
    }

    @Test
    @DisplayName("다른 업체 지점은 404로 응답하고 IT 담당 요청은 잠금을 얻기 전에 거절한다")
    void rejectsDisableOutsideAdminCompany() throws Exception {
        String url = "/location/admin/api/branches/{branchId}/disable";
        login(7L, "ROLE_IT");
        assertEquals(403, mvc.perform(post(url, 13)).andReturn().getResponse().getStatus());
        assertTrue(disableSteps.isEmpty());

        login(99L, "ROLE_ADMIN");
        assertEquals(404, mvc.perform(post(url, 13).param("companyId", "7"))
                .andReturn().getResponse().getStatus());
        assertEquals(List.of("lock"), disableSteps);
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
