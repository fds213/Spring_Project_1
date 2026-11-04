package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import com.ieum.gw.dto.InquiryResultDTO;
import com.ieum.gw.dto.InquirySubmitDTO;
import com.ieum.gw.service.InquiryFormException;
import com.ieum.gw.service.InquiryService;

/** 화면 JS(site/inquiry.js)가 받는 응답 모양을 확인한다. Security(CSRF)는 빼고 Controller만 띄운다 */
class InquiryControllerTest {

    private static final String BODY = "{\"companyName\":\"가나상사\",\"name\":\"김가나\",\"email\":\"kim@gana.com\","
            + "\"phone\":\"\",\"title\":\"제목\",\"content\":\"내용\",\"website\":\"\"}";

    /** submit이 정해 둔 결과를 돌려주거나 정해 둔 예외를 던지는 가짜 */
    static class FakeService extends InquiryService {
        private final Supplier<InquiryResultDTO> answer;
        InquirySubmitDTO received; // 마지막으로 받은 입력

        FakeService(Supplier<InquiryResultDTO> answer) {
            super(null, null);
            this.answer = answer;
        }

        @Override
        public InquiryResultDTO submit(InquirySubmitDTO form) {
            received = form;
            return answer.get();
        }
    }

    private static MvcResult submit(FakeService service, String body) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InquiryController(service)).build();
        return mvc.perform(post("/inquiry/api/submit").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private static MvcResult submit(Supplier<InquiryResultDTO> answer) throws Exception {
        return submit(new FakeService(answer), BODY);
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("접수 성공: 200, success·message·data(접수번호·이메일)")
    void success() throws Exception {
        InquiryResultDTO done = new InquiryResultDTO();
        done.setNo(1000L);
        done.setEmail("kim@gana.com");

        MvcResult result = submit(() -> done);

        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertTrue(body.get("success").asBoolean());
        assertFalse(body.get("message").asText().isEmpty());
        assertEquals(1000L, body.get("data").get("no").asLong());
        assertEquals("kim@gana.com", body.get("data").get("email").asText());
    }

    @Test
    @DisplayName("봇 막기 칸에 걸린 요청도 200이고 접수번호만 비어 있다")
    void honeypot() throws Exception {
        MvcResult result = submit(InquiryResultDTO::new);

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(json(result).get("success").asBoolean());
        assertTrue(json(result).get("data").get("no").isNull());
    }

    @Test
    @DisplayName("화면 JS가 보내는 값 이름이 DTO로 들어온다. 모르는 값이 더 있어도 거절하지 않는다")
    void bindsFields() throws Exception {
        FakeService service = new FakeService(InquiryResultDTO::new);

        submit(service, "{\"companyName\":\"가나상사\",\"name\":\"김가나\",\"email\":\"kim@gana.com\",\"phone\":\"010-1234-5678\","
                + "\"title\":\"제목\",\"content\":\"내용\",\"website\":\"w\",\"agree\":true}");

        assertEquals("가나상사", service.received.getCompanyName());
        assertEquals("김가나", service.received.getName());
        assertEquals("kim@gana.com", service.received.getEmail());
        assertEquals("010-1234-5678", service.received.getPhone());
        assertEquals("제목", service.received.getTitle());
        assertEquals("내용", service.received.getContent());
        assertEquals("w", service.received.getWebsite());
    }

    @Test
    @DisplayName("입력 오류는 400과 안내 문구·틀린 칸, 깨진 JSON은 400과 안내 문구")
    void badRequest() throws Exception {
        MvcResult result = submit(() -> {
            throw new InquiryFormException("phone", "휴대전화는 010-0000-0000 형식으로 입력해 주세요.");
        });
        assertEquals(400, result.getResponse().getStatus());
        assertEquals("phone", json(result).get("field").asText());
        assertEquals("휴대전화는 010-0000-0000 형식으로 입력해 주세요.", json(result).get("message").asText());

        MvcResult broken = submit(new FakeService(InquiryResultDTO::new), "{\"content\": {\"a\": 1}}");
        assertEquals(400, broken.getResponse().getStatus());
        assertTrue(json(broken).has("message"));
    }

    @Test
    @DisplayName("그 밖의 오류는 500과 같은 문구만 보이고 원인 메시지는 내보내지 않는다")
    void unexpected() throws Exception {
        MvcResult result = submit(() -> {
            throw new IllegalStateException("ORA-00001: 비밀 내용");
        });

        assertEquals(500, result.getResponse().getStatus());
        assertFalse(json(result).get("message").asText().contains("ORA"));
    }
}
