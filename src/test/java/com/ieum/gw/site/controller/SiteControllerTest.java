package com.ieum.gw.site.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.site.dto.TrialApplyDTO;
import com.ieum.gw.site.dto.TrialResultDTO;
import com.ieum.gw.site.service.SiteService;
import com.ieum.gw.site.service.TrialFormException;
import com.ieum.gw.subscription.service.CompanyCodeExhaustedException;
import com.ieum.gw.subscription.service.TrialEmailUsedException;

/** 화면 JS가 받는 응답 모양(docs/front-screen-api.md 2장)을 확인한다. Security(CSRF)는 빼고 Controller만 띄운다 */
class SiteControllerTest {

    private static final String BODY = "{\"companyName\":\"가나상사\",\"seats\":5,\"email\":\"kim@gana.com\","
            + "\"adminName\":\"김가나\",\"password\":\"password1\"}";

    /** applyTrial이 정해 둔 결과를 돌려주거나 정해 둔 예외를 던지는 가짜 */
    static class FakeSiteService extends SiteService {
        private final Supplier<TrialResultDTO> answer;
        TrialApplyDTO received; // 마지막으로 받은 입력

        FakeSiteService(Supplier<TrialResultDTO> answer) {
            super(null, null);
            this.answer = answer;
        }

        @Override
        public TrialResultDTO applyTrial(TrialApplyDTO form) {
            received = form;
            return answer.get();
        }
    }

    private static MockMvc mvc(Supplier<TrialResultDTO> answer) {
        return mvc(new FakeSiteService(answer));
    }

    private static MockMvc mvc(FakeSiteService service) {
        return MockMvcBuilders.standaloneSetup(new SiteController(service)).build();
    }

    @Test
    @DisplayName("인원 10.9는 10으로 잘리지 않고 그대로 Service에 가서 거절된다")
    void seatsKeepFraction() throws Exception {
        FakeSiteService service = new FakeSiteService(TrialResultDTO::new);
        apply(mvc(service), "{\"seats\":10.9}");

        assertEquals(new BigDecimal("10.9"), service.received.getSeats());
    }

    private static MvcResult apply(MockMvc mvc, String body) throws Exception {
        return mvc.perform(post("/trial/api/apply").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("소개·체험 신청 화면 주소가 site JSP를 연다")
    void pages() throws Exception {
        MockMvc mvc = mvc(TrialResultDTO::new);
        // view().name()·jsonPath()는 pom에 없는 Hamcrest·json-path가 있어야 해서 결과에서 직접 꺼낸다
        assertEquals("site/intro", mvc.perform(get("/")).andReturn().getModelAndView().getViewName());
        assertEquals("site/trial", mvc.perform(get("/trial")).andReturn().getModelAndView().getViewName());
    }

    @Test
    @DisplayName("신청 성공: 200, success·message·data(업체 코드·사번·마지막 날)")
    void success() throws Exception {
        TrialResultDTO done = new TrialResultDTO();
        done.setCompanyCode("ieum4827");
        done.setEmployeeNo("20260001");
        done.setTrialLastDate("2026-11-12");

        MvcResult result = apply(mvc(() -> done), BODY);

        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertTrue(body.get("success").asBoolean());
        assertFalse(body.get("message").asText().isEmpty());
        assertEquals("ieum4827", body.get("data").get("companyCode").asText());
        assertEquals("20260001", body.get("data").get("employeeNo").asText());
        assertEquals("2026-11-12", body.get("data").get("trialLastDate").asText());
        assertFalse(body.get("data").has("password"));
    }

    @Test
    @DisplayName("입력 오류는 400과 안내 문구·틀린 칸, 깨진 JSON도 400")
    void badRequest() throws Exception {
        MvcResult result = apply(mvc(() -> {
            throw new TrialFormException("seats", "이용 인원은 관리자를 포함해 1~10명입니다.");
        }), BODY);
        assertEquals(400, result.getResponse().getStatus());
        assertEquals("seats", json(result).get("field").asText());
        assertEquals("이용 인원은 관리자를 포함해 1~10명입니다.", json(result).get("message").asText());

        MvcResult broken = apply(mvc(TrialResultDTO::new), "{\"seats\": \"열\"}");
        assertEquals(400, broken.getResponse().getStatus());
        assertTrue(json(broken).has("message"));
    }

    @Test
    @DisplayName("이미 쓴 이메일은 409, 업체 코드 소진은 503")
    void conflictAndExhausted() throws Exception {
        MvcResult used = apply(mvc(() -> {
            throw new TrialEmailUsedException();
        }), BODY);
        assertEquals(409, used.getResponse().getStatus());
        assertEquals("email", json(used).get("field").asText());

        MvcResult exhausted = apply(mvc(() -> {
            throw new CompanyCodeExhaustedException();
        }), BODY);
        assertEquals(503, exhausted.getResponse().getStatus());
        assertTrue(json(exhausted).has("message"));
    }

    @Test
    @DisplayName("그 밖의 오류는 500과 같은 문구만 보이고 원인 메시지는 내보내지 않는다")
    void unexpected() throws Exception {
        MvcResult result = apply(mvc(() -> {
            throw new UnsupportedOperationException("구현 전: 김상준");
        }), BODY);

        assertEquals(500, result.getResponse().getStatus());
        assertFalse(json(result).get("message").asText().contains("김상준"));
    }
}
