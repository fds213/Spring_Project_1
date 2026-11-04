package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.service.AssetService;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.service.BranchService;
import com.ieum.gw.service.TicketReadService;

class TicketFormOptionsHttpTest {
    MockMvc mvc;
    @BeforeEach void setup() {
        AssetService assets=new AssetService(null) {
            @Override public List<AssetDTO> getMyAssets(LoginUser user) {
                assertEquals(7L,user.getCompanyId()); assertEquals(9L,user.getEmployeeId());
                return List.of();
            }
        };
        BranchService branches=new BranchService(null) {
            @Override public List<BranchDTO> getBranches(Long companyId) {
                assertEquals(7L,companyId);
                BranchDTO b=new BranchDTO(); b.setBranchId(10L); b.setBranchName("본사"); return List.of(b);
            }
        };
        EmployeeService employees=new EmployeeService() {
            @Override public Long getWorkBranchId(Long companyId, Long employeeId) {
                assertEquals(7L,companyId); assertEquals(9L,employeeId); return 10L;
            }
        };
        mvc=MockMvcBuilders.standaloneSetup(new TicketReadController(new TicketReadService(null,assets,branches,employees)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        SecurityContextHolder.clearContext();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test @DisplayName("접수 선택 API는 인증 업체·사원만 사용하고 숫자 지점 ID를 반환한다")
    void options() throws Exception {
        AuthEmpDTO a=new AuthEmpDTO(); a.setCompanyId(7L); a.setEmployeeId(9L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(a),"",List.of()));
        var response=mvc.perform(get("/ticket/api/form-options").param("companyId","999").param("employeeId","999"))
                .andReturn().getResponse();
        assertEquals(200,response.getStatus());
        var data=new ObjectMapper().readTree(response.getContentAsByteArray()).path("data");
        assertTrue(data.path("assets").isArray());
        assertTrue(data.path("defaultBranchId").isNumber());
        assertEquals(10L,data.path("defaultBranchId").asLong());
        assertEquals("본사",data.path("branches").get(0).path("name").asText());
    }
    @Test @DisplayName("접수 선택 API는 비로그인 요청에 401을 반환한다")
    void anonymous() throws Exception {
        assertEquals(401,mvc.perform(get("/ticket/api/form-options")).andReturn().getResponse().getStatus());
    }
}
