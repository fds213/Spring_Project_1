package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.ieum.gw.dao.AssetAllocationDAO;
import com.ieum.gw.dto.AssetAdminAssignmentDTO;
import com.ieum.gw.dto.AssetAllocationDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.mapper.AssetAllocationMapper;
import com.ieum.gw.service.AssetAllocationService;

/** 실제 Controller·Service·DAO와 가짜 Mapper를 연결한다. DB와 Security 필터는 사용하지 않는다. */
class AssetAdminAssignmentsTest {
    private static final String BASE = "/asset/admin/api/assets/";
    private final ObjectMapper json = new ObjectMapper();
    private final List<List<Long>> assetReads = new ArrayList<>();
    private final List<List<Long>> assignmentReads = new ArrayList<>();
    private List<AssetAdminAssignmentDTO> rows = List.of();
    private MockMvc mvc;

    /** 자산 존재 확인과 이력 조회의 입력을 기록하는 Mapper를 연결한다. */
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        AssetAllocationMapper mapper = (AssetAllocationMapper) Proxy.newProxyInstance(
                AssetAllocationMapper.class.getClassLoader(), new Class<?>[] {AssetAllocationMapper.class},
                (proxy, method, args) -> {
                    Long companyId = (Long) args[0];
                    Long assetId = (Long) args[1];
                    if ("detail".equals(method.getName())) {
                        assetReads.add(List.of(companyId, assetId));
                        if (companyId != 7L || assetId != 13L) {
                            return null;
                        }
                        AssetAllocationDTO asset = new AssetAllocationDTO();
                        asset.setAssetId(assetId);
                        return asset;
                    }
                    assertEquals("selectAssignments", method.getName());
                    assertEquals(List.of(7L, 13L), List.of(companyId, assetId));
                    assignmentReads.add(List.of(companyId, assetId));
                    return rows;
                });
        AssetAllocationService service = new AssetAllocationService(new AssetAllocationDAO(mapper), null, null);
        mvc = MockMvcBuilders.standaloneSetup(new AssetAdminController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        login(7L, 9L, 11L, "ROLE_IT");
    }

    /** 다음 테스트로 인증 정보가 넘어가지 않도록 정리한다. */
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    /** 인증 정보에서 제공할 업체·사원·계정·역할을 설정한다. */
    private void login(Long companyId, Long employeeId, Long accountId, String role) {
        AuthEmpDTO auth = new AuthEmpDTO();
        auth.setCompanyId(companyId);
        auth.setEmployeeId(employeeId);
        auth.setUserAccountId(accountId);
        auth.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(auth), null, List.of()));
    }

    /** 현재 지급 건 또는 회수된 과거 지급 건의 조회 결과를 만든다. */
    private AssetAdminAssignmentDTO assignment(long id, boolean returned) {
        AssetAdminAssignmentDTO row = new AssetAdminAssignmentDTO();
        row.setAssetAssignmentId(id);
        row.setAssetId(13L);
        row.setEmployeeId(returned ? 21L : 22L);
        row.setEmployeeName(returned ? "이전 수령인" : "현재 수령인");
        row.setItTicketId(returned ? 31L : null);
        row.setIssuedBy(41L);
        row.setIssuedByName("지급 담당자");
        row.setIssuedAt(LocalDateTime.of(2026, 11, returned ? 2 : 5, 9, 0));
        if (returned) {
            row.setReturnedBy(42L);
            row.setReturnedByName("회수 담당자");
            row.setReturnedAt(LocalDateTime.of(2026, 11, 4, 18, 0));
        }
        return row;
    }

    /** 자산 번호로 이력 조회 주소를 구성한다. */
    private String url(String id) {
        return BASE + id + "/assignments";
    }

    /** HTTP 응답을 JSON 트리로 읽는다. */
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    @DisplayName("IT·관리자는 인증 업체로 현재·과거 지급 건의 직원 이름·계정 ID·시각을 조회한다")
    void returnsHistoryForBothRoles() throws Exception {
        rows = List.of(assignment(102L, false), assignment(101L, true));
        for (String role : List.of("ROLE_IT", "ROLE_ADMIN")) {
            login(7L, 9L, 11L, role);
            var result = mvc.perform(get(url("13")).param("companyId", "99").param("employeeId", "88"))
                    .andReturn();
            assertEquals(200, result.getResponse().getStatus());
            JsonNode root = body(result);
            assertTrue(root.path("success").asBoolean());
            JsonNode data = root.path("data");
            assertEquals(2, data.size());
            JsonNode current = data.get(0);
            assertEquals(11, current.size());
            assertEquals(102L, current.path("assetAssignmentId").asLong());
            assertEquals(13L, current.path("assetId").asLong());
            assertEquals(22L, current.path("employeeId").asLong());
            assertEquals("현재 수령인", current.path("employeeName").asText());
            assertTrue(current.path("itTicketId").isNull());
            assertEquals(41L, current.path("issuedBy").asLong());
            assertEquals("지급 담당자", current.path("issuedByName").asText());
            assertEquals("2026-11-05T09:00:00", current.path("issuedAt").asText());
            assertTrue(current.path("returnedBy").isNull());
            assertTrue(current.path("returnedByName").isNull());
            assertTrue(current.path("returnedAt").isNull());
            JsonNode past = data.get(1);
            assertEquals(21L, past.path("employeeId").asLong());
            assertEquals("이전 수령인", past.path("employeeName").asText());
            assertEquals(31L, past.path("itTicketId").asLong());
            assertEquals("2026-11-02T09:00:00", past.path("issuedAt").asText());
            assertEquals(42L, past.path("returnedBy").asLong());
            assertEquals("회수 담당자", past.path("returnedByName").asText());
            assertEquals("2026-11-04T18:00:00", past.path("returnedAt").asText());
        }
        assertEquals(List.of(List.of(7L, 13L), List.of(7L, 13L)), assetReads);
        assertEquals(assetReads, assignmentReads);
    }

    @Test
    @DisplayName("같은 업체 자산에 지급 이력이 없으면 성공 응답과 빈 배열을 반환한다")
    void returnsEmptyHistoryForExistingAsset() throws Exception {
        var result = mvc.perform(get(url("13"))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(body(result).path("success").asBoolean());
        assertTrue(body(result).path("data").isArray());
        assertEquals(0, body(result).path("data").size());
        assertEquals(List.of(List.of(7L, 13L)), assignmentReads);
    }

    @Test
    @DisplayName("다른 업체 자산과 없는 자산은 같은 404로 응답하고 이력 조회를 진행하지 않는다")
    void deniesOtherCompanyAndMissingAsset() throws Exception {
        login(99L, 9L, 11L, "ROLE_ADMIN");
        var other = mvc.perform(get(url("13")).param("companyId", "7")).andReturn();
        login(7L, 9L, 11L, "ROLE_IT");
        var missing = mvc.perform(get(url("999"))).andReturn();
        assertEquals(404, other.getResponse().getStatus());
        assertEquals(404, missing.getResponse().getStatus());
        assertEquals(body(other), body(missing));
        assertEquals(List.of(List.of(99L, 13L), List.of(7L, 999L)), assetReads);
        assertTrue(assignmentReads.isEmpty());
    }

    @Test
    @DisplayName("일반 직원·인사·운영자는 자산 존재 확인 전에 403으로 거절한다")
    void deniesOtherRolesBeforeRead() throws Exception {
        for (String role : List.of("ROLE_EMP", "ROLE_HR", "ROLE_OPS")) {
            login(7L, 9L, 11L, role);
            assertEquals(403, mvc.perform(get(url("13"))).andReturn().getResponse().getStatus());
        }
        assertTrue(assetReads.isEmpty());
        assertTrue(assignmentReads.isEmpty());
    }

    @Test
    @DisplayName("비로그인과 불완전한 인증 정보는 DB 조회 전에 401로 거절한다")
    void requiresValidAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        assertEquals(401, mvc.perform(get(url("13"))).andReturn().getResponse().getStatus());
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            for (Long[] ids : new Long[][] {{invalid, 9L, 11L}, {7L, invalid, 11L}, {7L, 9L, invalid}}) {
                login(ids[0], ids[1], ids[2], "ROLE_IT");
                assertEquals(401, mvc.perform(get(url("13"))).andReturn().getResponse().getStatus());
            }
        }
        assertTrue(assetReads.isEmpty());
        assertTrue(assignmentReads.isEmpty());
    }

    @Test
    @DisplayName("잘못된 자산 번호는 DB 조회 전에 400으로 거절한다")
    void rejectsInvalidAssetId() throws Exception {
        for (String id : List.of("0", "-1", "abc", "99999999999999999999999")) {
            assertEquals(400, mvc.perform(get(url(id))).andReturn().getResponse().getStatus());
        }
        assertTrue(assetReads.isEmpty());
        assertTrue(assignmentReads.isEmpty());
    }

    @Test
    @DisplayName("조회 전용 이력 주소는 POST를 받지 않는다")
    void rejectsPost() throws Exception {
        assertEquals(405, mvc.perform(post(url("13"))).andReturn().getResponse().getStatus());
        assertTrue(assetReads.isEmpty());
        assertTrue(assignmentReads.isEmpty());
    }

    @Test
    @DisplayName("이름을 찾지 못한 지급 건도 ID·시각을 유지하고 이름은 null로 반환한다")
    void keepsHistoryWithNullNames() throws Exception {
        AssetAdminAssignmentDTO row = assignment(101L, true);
        row.setEmployeeName(null);
        row.setIssuedByName(null);
        row.setReturnedByName(null);
        rows = List.of(row);
        var result = mvc.perform(get(url("13"))).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode item = body(result).path("data").get(0);
        assertEquals(21L, item.path("employeeId").asLong());
        assertEquals(41L, item.path("issuedBy").asLong());
        assertEquals(42L, item.path("returnedBy").asLong());
        assertTrue(item.path("employeeName").isNull());
        assertTrue(item.path("issuedByName").isNull());
        assertTrue(item.path("returnedByName").isNull());
        assertEquals("2026-11-04T18:00:00", item.path("returnedAt").asText());
    }
}
