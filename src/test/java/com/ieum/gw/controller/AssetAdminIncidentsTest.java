package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.code.TicketStatus;
import com.ieum.gw.code.TicketType;
import com.ieum.gw.dao.AssetAllocationDAO;
import com.ieum.gw.dto.AssetAdminIncidentDTO;
import com.ieum.gw.dto.AssetAllocationDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.mapper.AssetAllocationMapper;
import com.ieum.gw.service.AssetAllocationService;

/** 실제 Controller·Service·DAO와 가짜 Mapper로 검증한다. DB·Security 필터는 사용하지 않는다. */
class AssetAdminIncidentsTest {
    private static final String URL = "/asset/admin/api/assets/13/incidents";
    private final List<String> reads = new ArrayList<>();
    private List<AssetAdminIncidentDTO> rows = List.of();
    private MockMvc mvc;

    /** 인증 업체·자산·티켓 종류와 호출 순서를 기록하는 Mapper를 연결한다. */
    @BeforeEach
    void setUp() {
        AssetAllocationMapper mapper = (AssetAllocationMapper) Proxy.newProxyInstance(
                AssetAllocationMapper.class.getClassLoader(), new Class<?>[] {AssetAllocationMapper.class},
                (proxy, method, args) -> {
                    reads.add(method.getName());
                    if ("detail".equals(method.getName())) {
                        if (!Long.valueOf(7L).equals(args[0]) || !Long.valueOf(13L).equals(args[1])) {
                            return null;
                        }
                        return new AssetAllocationDTO();
                    }
                    assertEquals("selectIncidents", method.getName());
                    assertArrayEquals(new Object[] {7L, 13L, TicketType.TK_INCIDENT.name()}, args);
                    return rows;
                });
        AssetAllocationService service = new AssetAllocationService(new AssetAllocationDAO(mapper), null, null);
        mvc = MockMvcBuilders.standaloneSetup(new AssetAdminController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        login(7L, "ROLE_IT");
    }

    /** 테스트 사이에 인증 정보가 남지 않도록 정리한다. */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** 서버 인증 정보로 사용할 업체와 역할을 설정한다. */
    private void login(Long companyId, String role) {
        AuthEmpDTO auth = new AuthEmpDTO();
        auth.setCompanyId(companyId);
        auth.setEmployeeId(9L);
        auth.setUserAccountId(11L);
        auth.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(auth), null, List.of()));
    }

    @Test
    @DisplayName("IT・관리자는 모든 상태의 장애 이력을 인증 업체와 공통 종류 코드로 조회한다")
    void returnsAllStatusesForBothRoles() throws Exception {
        List<AssetAdminIncidentDTO> prepared = new ArrayList<>();
        for (TicketStatus status : TicketStatus.values()) {
            AssetAdminIncidentDTO row = new AssetAdminIncidentDTO();
            row.setId(100L + prepared.size());
            row.setTitle("장애 접수");
            row.setStatus(status.name());
            row.setRequesterName("퇴사자도 포함하는 요청자 이름");
            row.setCreatedAt(LocalDateTime.of(2026, 11, 6, 9, 0));
            prepared.add(row);
        }
        rows = prepared;
        for (String role : List.of("ROLE_IT", "ROLE_ADMIN")) {
            login(7L, role);
            var result = mvc.perform(get(URL).param("companyId", "99").param("ticketType", "TK_QUESTION"))
                    .andReturn();
            assertEquals(200, result.getResponse().getStatus());
            JsonNode root = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
            assertTrue(root.path("success").asBoolean());
            assertEquals(prepared.size(), root.path("data").size());
            for (int i = 0; i < prepared.size(); i++) {
                JsonNode item = root.path("data").get(i);
                Set<String> fields = new HashSet<>();
                item.fieldNames().forEachRemaining(fields::add);
                assertEquals(Set.of("id", "title", "status", "requesterName", "assigneeName", "createdAt"), fields);
                assertEquals(prepared.get(i).getId().longValue(), item.path("id").asLong());
                assertEquals("장애 접수", item.path("title").asText());
                assertEquals(prepared.get(i).getStatus(), item.path("status").asText());
                assertEquals(prepared.get(i).getRequesterName(), item.path("requesterName").asText());
                assertTrue(item.path("assigneeName").isNull());
                assertEquals("2026-11-06T09:00:00", item.path("createdAt").asText());
            }
        }
        assertEquals(List.of("detail", "selectIncidents", "detail", "selectIncidents"), reads);
    }

    @Test
    @DisplayName("같은 업체 자산에 장애 이력이 없으면 빈 배열을 반환한다")
    void returnsEmptyArray() throws Exception {
        var result = mvc.perform(get(URL)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray()).path("data");
        assertTrue(data.isArray());
        assertEquals(0, data.size());
    }

    @Test
    @DisplayName("다른 업체와 존재하지 않는 자산은 이력 조회 없이 동일한 404를 반환한다")
    void rejectsOtherCompanyAndMissingAsset() throws Exception {
        login(99L, "ROLE_ADMIN");
        var other = mvc.perform(get(URL).param("companyId", "7")).andReturn();
        login(7L, "ROLE_IT");
        var missing = mvc.perform(get(URL.replace("/13/", "/999/"))).andReturn();
        assertEquals(404, other.getResponse().getStatus());
        assertEquals(404, missing.getResponse().getStatus());
        assertEquals(other.getResponse().getContentAsString(), missing.getResponse().getContentAsString());
        assertEquals(List.of("detail", "detail"), reads);
    }

    @Test
    @DisplayName("일반 직원・인사・운영자는 조회 전에 403으로 거절한다")
    void rejectsOtherRoles() throws Exception {
        for (String role : List.of("ROLE_EMP", "ROLE_HR", "ROLE_OPS")) {
            login(7L, role);
            assertEquals(403, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
        }
        assertTrue(reads.isEmpty());
    }

    @Test
    @DisplayName("비로그인은 401이고 잘못된 자산 번호는 400이며 조회를 수행하지 않는다")
    void rejectsInvalidRequests() throws Exception {
        SecurityContextHolder.clearContext();
        assertEquals(401, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
        login(7L, "ROLE_IT");
        for (String id : List.of("0", "-1", "abc")) {
            assertEquals(400, mvc.perform(get(URL.replace("/13/", "/" + id + "/")))
                    .andReturn().getResponse().getStatus());
        }
        assertTrue(reads.isEmpty());
    }

    @Test
    @DisplayName("장애 이력 조회는 POST를 허용하지 않는다")
    void rejectsPost() throws Exception {
        assertEquals(405, mvc.perform(post(URL)).andReturn().getResponse().getStatus());
        assertTrue(reads.isEmpty());
    }

    @Test
    @DisplayName("장애 이력 Service는 읽기 전용 트랜잭션을 선언한다")
    void declaresReadOnlyTransaction() {
        var method = java.util.Arrays.stream(AssetAllocationService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getReturnType() == List.class
                        && candidate.getGenericReturnType().getTypeName().contains("AssetAdminIncidentDTO"))
                .findFirst().orElseThrow();
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(transaction.readOnly());
    }
}
