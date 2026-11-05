package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
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
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.dto.EmpSummaryDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.AssetAllocationService;
import com.ieum.gw.service.BranchService;
import com.ieum.gw.service.EmployeeService;

/** 실제 자산 Service와 가짜 직원·지점 Service로 HTTP 계약을 검증한다. DB·Security 필터는 포함하지 않는다. */
class AssetAdminOptionsTest {
    private static final String URL = "/asset/admin/api/assets/form-options";
    private final ObjectMapper json = new ObjectMapper();
    private final List<Long> employeeQueries = new ArrayList<>();
    private final List<Long> branchQueries = new ArrayList<>();
    private List<EmpSummaryDTO> employees = List.of();
    private List<BranchDetailDTO> branches = List.of();
    private MockMvc mvc;

    /** 공개 조회 메서드의 인자를 기록하고 실제 자산 Controller·Service에 연결한다. */
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        EmployeeService people = new EmployeeService() {
            /** 제외할 직원 없이 조회하는지 확인하고 준비한 직원 목록을 반환한다. */
            @Override
            public List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId) {
                assertNull(excludeEmpId);
                employeeQueries.add(companyId);
                return employees;
            }
        };
        BranchService places = new BranchService(null) {
            /** 사용 중지 지점을 포함하는 표시용 조회가 호출됐는지 확인한다. */
            @Override
            public List<BranchDetailDTO> getBranchDetails(Long companyId) {
                branchQueries.add(companyId);
                return branches;
            }
        };
        AssetAllocationService service = new AssetAllocationService(null, places, people);
        mvc = MockMvcBuilders.standaloneSetup(new AssetAdminController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        login(7L, 9L, 11L, "ROLE_IT");
    }

    /** 테스트 간 인증 정보가 섞이지 않도록 정리한다. */
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    /** 인증 정보로 사용할 업체·사원·계정·역할을 설정한다. */
    private void login(Long companyId, Long employeeId, Long accountId, String role) {
        AuthEmpDTO auth = new AuthEmpDTO();
        auth.setCompanyId(companyId);
        auth.setEmployeeId(employeeId);
        auth.setUserAccountId(accountId);
        auth.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(auth), null, List.of()));
    }

    /** JSON 응답을 읽는다. */
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    /** 부서·직급이 없는 경우도 표현할 수 있는 지급 대상 직원을 만든다. */
    private EmpSummaryDTO employee(long id, String name, String department, String grade) {
        EmpSummaryDTO row = new EmpSummaryDTO();
        row.setCompanyId(7L);
        row.setEmployeeId(id);
        row.setEmployeeName(name);
        row.setDepartmentName(department);
        row.setGradeName(grade);
        return row;
    }

    /** 기존 자산의 지점명 표시용 지점 정보를 만든다. */
    private BranchDetailDTO branch(long id, String name, String useYn) {
        BranchDetailDTO row = new BranchDetailDTO();
        row.setBranchId(id);
        row.setBranchName(name);
        row.setUseYn(useYn);
        return row;
    }

    @Test
    @DisplayName("IT·관리자의 선택 목록은 본인 포함 직원과 사용 중지 지점을 employees·branches로 반환한다")
    void returnsPeopleAndAllDisplayBranches() throws Exception {
        employees = List.of(employee(9L, "로그인한 직원", null, null),
                employee(12L, "다른 직원", "개발팀", "대리"));
        branches = List.of(branch(10L, "본사", "Y"), branch(20L, "사용 중지 지점", "N"));
        for (String role : List.of("ROLE_IT", "ROLE_ADMIN")) {
            login(7L, 9L, 11L, role);
            var result = mvc.perform(get(URL)).andReturn();
            assertEquals(200, result.getResponse().getStatus());
            JsonNode root = body(result);
            assertTrue(root.path("success").asBoolean());
            JsonNode data = root.path("data");
            assertEquals(2, data.size());
            assertFalse(data.has("employee"));
            assertEquals(2, data.path("employees").size());
            JsonNode self = data.path("employees").get(0);
            assertEquals(9L, self.path("employeeId").asLong());
            assertEquals("로그인한 직원", self.path("employeeName").asText());
            assertTrue(self.path("departmentName").isNull());
            assertTrue(self.path("gradeName").isNull());
            assertEquals("개발팀", data.path("employees").get(1).path("departmentName").asText());
            assertEquals("대리", data.path("employees").get(1).path("gradeName").asText());
            assertEquals(2, data.path("branches").size());
            assertEquals(20L, data.path("branches").get(1).path("branchId").asLong());
            assertEquals("사용 중지 지점", data.path("branches").get(1).path("branchName").asText());
            assertEquals("N", data.path("branches").get(1).path("useYn").asText());
        }
        assertEquals(List.of(7L, 7L), employeeQueries);
        assertEquals(employeeQueries, branchQueries);
    }

    @Test
    @DisplayName("위조한 업체·사원 파라미터는 무시하고 두 Service에 인증 업체 ID만 전달한다")
    void usesAuthenticatedCompany() throws Exception {
        for (long companyId : new long[] {7L, 8L}) {
            login(companyId, 9L, 11L, "ROLE_ADMIN");
            assertEquals(200, mvc.perform(get(URL).param("companyId", "999").param("employeeId", "888"))
                    .andReturn().getResponse().getStatus());
        }
        assertEquals(List.of(7L, 8L), employeeQueries);
        assertEquals(employeeQueries, branchQueries);
    }

    @Test
    @DisplayName("직원과 지점이 없으면 null 대신 두 빈 배열을 반환한다")
    void returnsEmptyArrays() throws Exception {
        var result = mvc.perform(get(URL)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode data = body(result).path("data");
        assertTrue(data.path("employees").isArray());
        assertEquals(0, data.path("employees").size());
        assertTrue(data.path("branches").isArray());
        assertEquals(0, data.path("branches").size());
    }

    @Test
    @DisplayName("일반 직원·인사·운영자는 직원·지점 조회 전에 403으로 거절한다")
    void deniesOtherRolesBeforeRead() throws Exception {
        for (String role : List.of("ROLE_EMP", "ROLE_HR", "ROLE_OPS")) {
            login(7L, 9L, 11L, role);
            assertEquals(403, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
        }
        assertTrue(employeeQueries.isEmpty());
        assertTrue(branchQueries.isEmpty());
    }

    @Test
    @DisplayName("비로그인과 잘못된 인증 ID는 두 Service를 호출하기 전에 401로 거절한다")
    void requiresValidAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        assertEquals(401, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            for (Long[] ids : new Long[][] {{invalid, 9L, 11L}, {7L, invalid, 11L}, {7L, 9L, invalid}}) {
                login(ids[0], ids[1], ids[2], "ROLE_IT");
                assertEquals(401, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
            }
        }
        assertTrue(employeeQueries.isEmpty());
        assertTrue(branchQueries.isEmpty());
    }

    @Test
    @DisplayName("선택 목록 GET 주소는 POST를 받지 않는다")
    void rejectsPost() throws Exception {
        assertEquals(405, mvc.perform(post(URL)).andReturn().getResponse().getStatus());
        assertTrue(employeeQueries.isEmpty());
        assertTrue(branchQueries.isEmpty());
    }

    @Test
    @DisplayName("재사용 SQL은 업체·재직·ACTIVE 조건을 적용하고 본인과 사용 중지 지점을 제외하지 않는다")
    void verifiesReusedQueryContracts() throws Exception {
        Configuration config = new Configuration();
        for (String resource : List.of("/mapper/EmployeeMapper.xml", "/mapper/BranchMapper.xml")) {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                assertNotNull(in);
                new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
            }
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("companyId", 7L);
        parameters.put("excludeEmpId", null);
        var people = config.getMappedStatement("com.ieum.gw.mapper.EmployeeMapper.listActiveEmp")
                .getBoundSql(parameters);
        String peopleSql = people.getSql().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        assertTrue(peopleSql.contains("where e.company_id = ?"));
        assertTrue(peopleSql.contains("e.employment_status = 'st_serving'"));
        assertTrue(peopleSql.contains("a.status_code = 'active'"));
        assertFalse(peopleSql.contains("e.employee_id <>"));
        assertEquals(1, people.getParameterMappings().size());
        assertEquals("companyId", people.getParameterMappings().get(0).getProperty());
        var places = config.getMappedStatement("com.ieum.gw.mapper.BranchMapper.selectBranchDetails")
                .getBoundSql(parameters);
        String placesSql = places.getSql().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        assertTrue(placesSql.contains("where company_id = ?"));
        assertFalse(placesSql.contains("use_yn ="));
        assertEquals(1, places.getParameterMappings().size());
        assertEquals("companyId", places.getParameterMappings().get(0).getProperty());
    }
}
