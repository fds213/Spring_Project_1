package com.ieum.gw.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.OpsApplicationDTO;
import com.ieum.gw.dto.OpsCompanyDTO;
import com.ieum.gw.dto.OpsCompanyDetailDTO;
import com.ieum.gw.dto.OpsGuestInquiryDTO;
import com.ieum.gw.dto.OpsGuestReplyDTO;
import com.ieum.gw.dto.OpsReplyFormDTO;
import com.ieum.gw.dto.OpsReplyResultDTO;
import com.ieum.gw.dto.OpsStateDTO;
import com.ieum.gw.service.OpsNotFoundException;
import com.ieum.gw.service.OpsReplyException;
import com.ieum.gw.service.OpsReplyService;
import com.ieum.gw.service.OpsService;

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

        @Override
        public List<OpsGuestInquiryDTO> listGuestInquiries() {
            check();
            OpsGuestInquiryDTO inquiry = new OpsGuestInquiryDTO();
            inquiry.setNo(1000L);
            inquiry.setAt("2026-11-03T10:00:00");
            inquiry.setCompany("가나다");
            inquiry.setName("홍길동");
            inquiry.setEmail("a@test.com");
            inquiry.setTitle("도입 문의");
            inquiry.setBody("내용");
            inquiry.setState("IQ_RECEIVED");
            OpsGuestReplyDTO reply = new OpsGuestReplyDTO();
            reply.setId(5L);
            reply.setAt("2026-11-03T11:00:00");
            reply.setBody("답변");
            reply.setStatus("MAIL_FAILED");
            reply.setReason("메일 서버 연결 실패");
            reply.setInquiryNo(1000L);
            reply.setMailOutboxId(77L);
            inquiry.setReplies(List.of(reply));
            return List.of(inquiry);
        }
    }

    static class FakeReplies extends OpsReplyService {
        RuntimeException error;
        Long no;
        Long replyId;
        OpsReplyFormDTO form;
        Long operator;

        FakeReplies() {
            super(null, null, null, null);
        }

        private OpsReplyResultDTO result() {
            if (error != null) {
                throw error;
            }
            OpsGuestInquiryDTO inquiry = new OpsGuestInquiryDTO();
            inquiry.setNo(no);
            return new OpsReplyResultDTO(inquiry, false, "메일 서버 연결 실패");
        }

        @Override
        public OpsReplyResultDTO sendReply(Long no, OpsReplyFormDTO form, Long operatorAccountId) {
            this.no = no;
            this.form = form;
            this.operator = operatorAccountId;
            return result();
        }

        @Override
        public OpsReplyResultDTO resend(Long no, Long replyId) {
            this.no = no;
            this.replyId = replyId;
            return result();
        }
    }

    private final FakeService service = new FakeService();
    private final FakeReplies replies = new FakeReplies();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new OpsController(service, replies))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void login() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setUserAccountId(9L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(emp), null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private MvcResult postReply(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("화면 주소는 메뉴와 같고 ops 폴더의 JSP를 연다")
    void pages() throws Exception {
        assertEquals("ops/company", mvc.perform(get("/ops/company")).andReturn().getModelAndView().getViewName());
        assertEquals("ops/application", mvc.perform(get("/ops/application-status")).andReturn().getModelAndView().getViewName());
        assertEquals("ops/inquiry-guest", mvc.perform(get("/ops/inquiry-guest")).andReturn().getModelAndView().getViewName());
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
    @DisplayName("비회원 접수는 화면이 읽는 칸 이름으로 주고 비운 휴대전화는 null이다")
    void guestInquiries() throws Exception {
        JsonNode body = body(mvc.perform(get("/ops/api/guest-inquiries")).andReturn());
        assertTrue(body.get("success").asBoolean());
        JsonNode item = body.get("data").get(0);
        assertEquals(1000, item.get("no").asInt());
        assertEquals("2026-11-03T10:00:00", item.get("at").asText());
        assertEquals("가나다", item.get("company").asText());
        assertEquals("홍길동", item.get("name").asText());
        assertEquals("a@test.com", item.get("email").asText());
        assertTrue(item.get("phone").isNull());
        assertEquals("도입 문의", item.get("title").asText());
        assertEquals("내용", item.get("body").asText());
        assertEquals("IQ_RECEIVED", item.get("state").asText());
    }

    @Test
    @DisplayName("비회원 접수의 답변은 화면이 읽는 칸만 주고 안쪽 번호는 내보내지 않는다")
    void guestReplies() throws Exception {
        JsonNode reply = body(mvc.perform(get("/ops/api/guest-inquiries")).andReturn()).get("data").get(0).get("replies").get(0);
        assertEquals(5, reply.get("id").asInt());
        assertEquals("2026-11-03T11:00:00", reply.get("at").asText());
        assertEquals("답변", reply.get("body").asText());
        assertEquals("MAIL_FAILED", reply.get("status").asText());
        assertEquals("메일 서버 연결 실패", reply.get("reason").asText());
        assertFalse(reply.get("stale").asBoolean());
        assertFalse(reply.has("mailOutboxId"));
        assertFalse(reply.has("inquiryNo"));
    }

    @Test
    @DisplayName("답변: 운영자는 로그인 정보에서 꺼내고, 메일이 실패해도 200에 sent·reason으로 알린다")
    void reply() throws Exception {
        MvcResult result = postReply("/ops/api/guest-inquiries/1000/replies",
                "{\"body\":\"답변\",\"lastReplyId\":5,\"operatorAccountId\":1}");

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(1000L, replies.no);
        assertEquals(9L, replies.operator);
        assertEquals("답변", replies.form.getBody());
        assertEquals(5L, replies.form.getLastReplyId());
        JsonNode data = body(result).get("data");
        assertFalse(data.get("sent").asBoolean());
        assertEquals("메일 서버 연결 실패", data.get("reason").asText());
        assertEquals(1000, data.get("inquiry").get("no").asInt());
    }

    @Test
    @DisplayName("다시 보내기: 접수번호와 답변 번호를 주소에서 읽는다")
    void resend() throws Exception {
        MvcResult result = postReply("/ops/api/guest-inquiries/1000/replies/5/resend", "");

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(1000L, replies.no);
        assertEquals(5L, replies.replyId);
    }

    @Test
    @DisplayName("겹친 답변은 서비스가 정한 상태와 문구로, 없는 접수는 404로 답한다")
    void replyRejected() throws Exception {
        replies.error = new OpsReplyException(409, "그사이 다른 답변이 있습니다.");
        MvcResult conflict = postReply("/ops/api/guest-inquiries/1000/replies", "{\"body\":\"답변\"}");
        assertEquals(409, conflict.getResponse().getStatus());
        assertEquals("그사이 다른 답변이 있습니다.", body(conflict).get("message").asText());

        replies.error = new OpsNotFoundException("접수를 찾을 수 없습니다.");
        assertEquals(404, postReply("/ops/api/guest-inquiries/1/replies/5/resend", "").getResponse().getStatus());
    }

    @Test
    @DisplayName("JSON이 깨졌거나 번호 칸에 글자가 오면 500이 아니라 400이다")
    void replyUnreadable() throws Exception {
        assertEquals(400, postReply("/ops/api/guest-inquiries/1000/replies", "{깨진").getResponse().getStatus());
        assertEquals(400, postReply("/ops/api/guest-inquiries/abc/replies", "{\"body\":\"답변\"}").getResponse().getStatus());
        assertEquals(400, postReply("/ops/api/guest-inquiries/1000/replies/abc/resend", "").getResponse().getStatus());
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
