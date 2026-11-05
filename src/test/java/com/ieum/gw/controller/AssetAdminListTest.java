package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.lang.reflect.Proxy;
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
import com.ieum.gw.code.AssetStatus;
import com.ieum.gw.code.AssetType;
import com.ieum.gw.dao.AssetAllocationDAO;
import com.ieum.gw.dto.AssetListDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.mapper.AssetAllocationMapper;
import com.ieum.gw.service.AssetAllocationService;

/** 실제 Controller·Service·DAO를 연결한다. Mapper는 가짜이고 DB·Security 필터는 포함하지 않는다. */
class AssetAdminListTest {
    private static final String URL = "/asset/admin/api/assets";
    private final ObjectMapper json = new ObjectMapper();
    private final List<Long> queriedCompanies = new ArrayList<>();
    private List<AssetListDTO> rows = List.of();
    private MockMvc mvc;

    /** 목록 조회 인자를 기록하는 Mapper와 실제 업무 계층을 연결한다. */
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        AssetAllocationMapper mapper = (AssetAllocationMapper) Proxy.newProxyInstance(
                AssetAllocationMapper.class.getClassLoader(), new Class<?>[] {AssetAllocationMapper.class},
                (proxy, method, args) -> {
                    assertEquals("selectList", method.getName());
                    queriedCompanies.add((Long) args[0]);
                    return rows;
                });
        AssetAllocationService service = new AssetAllocationService(new AssetAllocationDAO(mapper), null, null);
        mvc = MockMvcBuilders.standaloneSetup(new AssetAdminController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        login(7L, 9L, 11L, "ROLE_IT");
    }

    /** 테스트 간 인증 정보가 섞이지 않도록 정리한다. */
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    /** 서버 인증 정보에 해당하는 시험용 사용자를 설정한다. */
    private void login(Long companyId, Long employeeId, Long accountId, String role) {
        AuthEmpDTO auth = new AuthEmpDTO();
        auth.setCompanyId(companyId);
        auth.setEmployeeId(employeeId);
        auth.setUserAccountId(accountId);
        auth.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(auth), null, List.of()));
    }

    /** 자산번호와 제조 일련번호를 구분한 목록 행을 만든다. */
    private AssetListDTO asset(long id, AssetStatus status) {
        AssetListDTO row = new AssetListDTO();
        row.setId(id);
        row.setAssetNo("A-" + id);
        row.setName("업무용 노트북");
        row.setSerial(id == 1L ? null : "SN-" + id);
        row.setKind(AssetType.AT_LAPTOP.name());
        row.setStatus(status.name());
        row.setBranchId(10L);
        row.setVersion(0L);
        return row;
    }

    /** HTTP 응답을 JSON 트리로 읽는다. */
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    @DisplayName("관리자 목록은 네 상태 자산과 약속한 필드·숫자 ID·null 일련번호를 반환한다")
    void returnsAllStatusesAndResponseContract() throws Exception {
        rows = List.of(asset(1L, AssetStatus.AS_AVAILABLE), asset(2L, AssetStatus.AS_IN_USE),
                asset(3L, AssetStatus.AS_INACTIVE), asset(4L, AssetStatus.AS_DISPOSED));
        MvcResult result = mvc.perform(get(URL)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode root = body(result);
        assertTrue(root.path("success").asBoolean());
        assertEquals(4, root.path("data").size());
        JsonNode first = root.path("data").get(0);
        assertEquals(8, first.size());
        assertTrue(first.path("id").isIntegralNumber());
        assertEquals(1L, first.path("id").asLong());
        assertEquals("A-1", first.path("assetNo").asText());
        assertEquals("업무용 노트북", first.path("name").asText());
        assertTrue(first.path("serial").isNull());
        assertEquals(AssetType.AT_LAPTOP.name(), first.path("kind").asText());
        assertTrue(first.path("branchId").isIntegralNumber());
        assertEquals(10L, first.path("branchId").asLong());
        assertTrue(first.path("version").isIntegralNumber());
        assertEquals(0L, first.path("version").asLong());
        assertEquals("SN-2", root.path("data").get(1).path("serial").asText());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(rows.get(i).getStatus(), root.path("data").get(i).path("status").asText());
        }
    }

    @Test
    @DisplayName("IT 담당자와 관리자는 요청 업체 ID를 무시하고 인증 업체 ID만 Mapper에 전달한다")
    void usesAuthenticatedCompanyForBothRoles() throws Exception {
        for (String role : List.of("ROLE_IT", "ROLE_ADMIN")) {
            login(7L, 9L, 11L, role);
            var result = mvc.perform(get(URL).param("companyId", "999").param("employeeId", "888"))
                    .andReturn();
            assertEquals(200, result.getResponse().getStatus());
        }
        login(8L, 12L, 14L, "ROLE_ADMIN");
        assertEquals(200, mvc.perform(get(URL).param("companyId", "7"))
                .andReturn().getResponse().getStatus());
        assertEquals(List.of(7L, 7L, 8L), queriedCompanies);
    }

    @Test
    @DisplayName("업체에 자산이 없으면 성공 응답의 data는 빈 배열이다")
    void returnsEmptyArray() throws Exception {
        var result = mvc.perform(get(URL)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(body(result).path("success").asBoolean());
        assertTrue(body(result).path("data").isArray());
        assertEquals(0, body(result).path("data").size());
    }

    @Test
    @DisplayName("일반 직원·인사·운영자 역할은 업체 자산 목록 조회 전에 403으로 거절한다")
    void deniesUnauthorizedRolesBeforeRead() throws Exception {
        for (String role : List.of("ROLE_EMP", "ROLE_HR", "ROLE_OPS")) {
            login(7L, 9L, 11L, role);
            assertEquals(403, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
        }
        assertTrue(queriedCompanies.isEmpty());
    }

    @Test
    @DisplayName("비로그인과 잘못된 업체·사원·계정 ID는 Mapper 호출 전에 401로 거절한다")
    void deniesIncompleteAuthenticationBeforeRead() throws Exception {
        SecurityContextHolder.clearContext();
        assertEquals(401, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            for (Long[] ids : new Long[][] {{invalid, 9L, 11L}, {7L, invalid, 11L}, {7L, 9L, invalid}}) {
                login(ids[0], ids[1], ids[2], "ROLE_IT");
                assertEquals(401, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
            }
        }
        assertTrue(queriedCompanies.isEmpty());
    }

    @Test
    @DisplayName("조회 전용 목록 주소는 POST를 받지 않고 Mapper도 호출하지 않는다")
    void rejectsPost() throws Exception {
        assertEquals(405, mvc.perform(post(URL)).andReturn().getResponse().getStatus());
        assertTrue(queriedCompanies.isEmpty());
    }
}
