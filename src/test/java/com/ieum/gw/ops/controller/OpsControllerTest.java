package com.ieum.gw.ops.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.ops.dto.OpsApplicationDTO;
import com.ieum.gw.ops.dto.OpsCompanyDTO;
import com.ieum.gw.ops.dto.OpsCompanyDetailDTO;
import com.ieum.gw.ops.dto.OpsStateDTO;
import com.ieum.gw.ops.service.OpsNotFoundException;
import com.ieum.gw.ops.service.OpsService;

/** 화면 JS가 받는 응답 모양을 확인한다. Security는 빼고 Controller만 띄운다. ROLE_OPS 검사는 임시 Tomcat에서 따로 본다 */
class OpsControllerTest {

    static class FakeService extends OpsService {
        RuntimeException error;

        FakeService() {
            super(null, null, null);
        }

        private void check() {
            if (error != null) {
                throw error;
            }
        }

        @Override
        public List<OpsCompanyDTO> listCompanies() {
            check();
            OpsCompanyDTO company = new OpsCompanyDTO();
            company.setCompanyCode("ieum0001");
            company.setState(new OpsStateDTO("TRIAL", "체험 중"));
            return List.of(company);
        }

        @Override
        public OpsCompanyDetailDTO getCompany(String code) {
            check();
            OpsCompanyDetailDTO detail = new OpsCompanyDetailDTO();
            detail.setCompanyCode(code);
            detail.setState(new OpsStateDTO("PAID", "유료"));
            detail.setUsers(12);
            detail.setTotal(48000);
            detail.setNo(7L);
            detail.setEmail("a@test.com");
            detail.setEmailVerified(true);
            detail.setEmployees(List.of());
            detail.setPayments(List.of());
            detail.setInquiries(List.of());
            return detail;
        }

        @Override
        public List<OpsApplicationDTO> listApplications() {
            check();
            OpsApplicationDTO application = new OpsApplicationDTO();
            application.setNewApplication(true);
            return List.of(application);
        }
    }

    private final FakeService service = new FakeService();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new OpsController(service)).build();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("화면 주소는 메뉴와 같고 ops 폴더의 JSP를 연다")
    void pages() throws Exception {
        assertEquals("ops/company", mvc.perform(get("/ops/company")).andReturn().getModelAndView().getViewName());
        assertEquals("ops/application", mvc.perform(get("/ops/application-status")).andReturn().getModelAndView().getViewName());
    }

    @Test
    @DisplayName("업체 목록은 success·data로 감싸고 상태는 status·label을 함께 준다")
    void companies() throws Exception {
        JsonNode body = body(mvc.perform(get("/ops/api/companies")).andReturn());
        assertTrue(body.get("success").asBoolean());
        assertEquals("ieum0001", body.get("data").get(0).get("companyCode").asText());
        assertEquals("TRIAL", body.get("data").get(0).get("state").get("status").asText());
        assertEquals("체험 중", body.get("data").get(0).get("state").get("label").asText());
    }

    @Test
    @DisplayName("업체 상세는 화면이 그대로 도는 목록 칸을 모두 준다")
    void companyDetail() throws Exception {
        JsonNode data = body(mvc.perform(get("/ops/api/companies/ieum0001")).andReturn()).get("data");
        assertEquals("ieum0001", data.get("companyCode").asText());
        assertEquals("유료", data.get("state").get("label").asText());
        assertEquals(12, data.get("users").asInt());
        assertEquals(48000, data.get("total").asInt());
        assertEquals(7, data.get("no").asInt());
        assertEquals("a@test.com", data.get("email").asText());
        assertTrue(data.get("emailVerified").asBoolean());
        assertTrue(data.get("employees").isArray());
        assertTrue(data.get("payments").isArray());
        assertTrue(data.get("inquiries").isArray());
    }

    @Test
    @DisplayName("없는 업체는 404와 안내 문구")
    void companyNotFound() throws Exception {
        service.error = new OpsNotFoundException("업체를 찾을 수 없습니다.");
        MvcResult result = mvc.perform(get("/ops/api/companies/ieum9999")).andReturn();
        assertEquals(404, result.getResponse().getStatus());
        assertEquals("업체를 찾을 수 없습니다.", body(result).get("message").asText());
    }

    @Test
    @DisplayName("신청 현황의 새 신청 칸 이름은 화면이 읽는 isNew다")
    void applicationsSendIsNew() throws Exception {
        JsonNode item = body(mvc.perform(get("/ops/api/applications")).andReturn()).get("data").get(0);
        assertTrue(item.get("isNew").asBoolean());
        assertFalse(item.has("new"));
        assertFalse(item.has("newApplication"));
    }

    @Test
    @DisplayName("그 밖의 오류는 원인을 숨기고 500과 같은 문구를 준다")
    void failureHidesCause() throws Exception {
        service.error = new IllegalStateException("ORA-00942: 비밀 내용");
        MvcResult result = mvc.perform(get("/ops/api/applications")).andReturn();
        assertEquals(500, result.getResponse().getStatus());
        assertFalse(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("ORA-"));
    }
}
