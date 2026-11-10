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
import com.ieum.gw.dto.SupportInquiryDTO;
import com.ieum.gw.dto.SupportInquiryFormDTO;
import com.ieum.gw.dto.SupportReplyDTO;
import com.ieum.gw.dto.SupportReplyFormDTO;
import com.ieum.gw.service.SupportFormException;
import com.ieum.gw.service.SupportNotFoundException;
import com.ieum.gw.service.SupportService;

/** 화면 JS가 받는 응답 모양과 로그인 값 전달을 확인한다. Security는 빼고 Controller만 띄운다. 역할 검사는 임시 Tomcat에서 따로 본다 */
class SupportControllerTest {

    static class FakeService extends SupportService {
        RuntimeException error;
        Long companyId;
        Long employeeId;
        Long operatorAccountId;
        Long userAccountId;
        String editedTarget;
        String readTarget;

        FakeService() {
            super(null, null);
        }

        private void check() {
            if (error != null) {
                throw error;
            }
        }

        @Override
        public Long createInquiry(Long companyId, Long employeeId, SupportInquiryFormDTO form) {
            check();
            this.companyId = companyId;
            this.employeeId = employeeId;
            return 900L;
        }

        @Override
        public List<SupportInquiryDTO> listMine(Long companyId, Long employeeId, Long userAccountId) {
            check();
            this.companyId = companyId;
            this.employeeId = employeeId;
            this.userAccountId = userAccountId;
            SupportInquiryDTO mine = inquiry();
            mine.setUnread(1);
            return List.of(mine);
        }

        @Override
        public int markRepliesRead(Long companyId, Long userAccountId, Long no) {
            check();
            this.readTarget = companyId + ":" + userAccountId + ":" + no;
            return 1;
        }

        @Override
        public List<SupportInquiryDTO> listCompany(Long companyId) {
            check();
            this.companyId = companyId;
            return List.of(inquiry());
        }

        @Override
        public SupportInquiryDTO editReply(Long no, Long replyId, SupportReplyFormDTO form) {
            check();
            this.editedTarget = no + ":" + replyId + ":" + form.getBody();
            return inquiry();
        }

        @Override
        public List<SupportInquiryDTO> listAll() {
            check();
            return List.of(inquiry());
        }

        @Override
        public SupportInquiryDTO reply(Long no, SupportReplyFormDTO form, Long operatorAccountId) {
            check();
            this.operatorAccountId = operatorAccountId;
            return inquiry();
        }
    }

    private static SupportInquiryDTO inquiry() {
        SupportReplyDTO reply = new SupportReplyDTO();
        reply.setId(5L);
        reply.setBody("확인했습니다");
        reply.setInquiryNo(100L);
        SupportInquiryDTO inquiry = new SupportInquiryDTO();
        inquiry.setNo(100L);
        inquiry.setCompanyId(7L);
        inquiry.setRequesterEmployeeId(55L);
        inquiry.setCompany("강아지상사");
        inquiry.setTitle("제목");
        inquiry.setState("SUP_RECEIVED");
        inquiry.setReplies(List.of(reply));
        return inquiry;
    }

    private final FakeService service = new FakeService();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SupportController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void login() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setUserAccountId(9L);
        emp.setCompanyId(3L);
        emp.setEmployeeId(44L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(emp), null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private MvcResult postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("직원 화면은 support 폴더, 회원 접수 화면은 ops 폴더의 JSP를 연다")
    void pages() throws Exception {
        assertEquals("support/inquiry", mvc.perform(get("/support/inquiry")).andReturn().getModelAndView().getViewName());
        assertEquals("ops/inquiry-member", mvc.perform(get("/ops/inquiry-member")).andReturn().getModelAndView().getViewName());
    }

    @Test
    @DisplayName("문의를 보내면 요청에 다른 업체 값이 있어도 로그인 업체·직원으로 처리하고 접수번호를 준다")
    void createUsesLoginUser() throws Exception {
        JsonNode body = body(postJson("/support/api/inquiries",
                "{\"title\":\"제목\",\"body\":\"내용\",\"companyId\":999,\"employeeId\":999}"));

        assertTrue(body.get("success").asBoolean());
        assertEquals(900L, body.get("data").get("no").asLong());
        assertEquals(3L, service.companyId);
        assertEquals(44L, service.employeeId);
    }

    @Test
    @DisplayName("내가 보낸 문의는 로그인 사용자로 찾고 업체 번호는 응답에 넣지 않는다")
    void myInquiries() throws Exception {
        JsonNode data = body(mvc.perform(get("/support/api/inquiries")).andReturn()).get("data");

        assertEquals(3L, service.companyId);
        assertEquals(44L, service.employeeId);
        assertEquals(9L, service.userAccountId);
        assertEquals(100L, data.get(0).get("no").asLong());
        assertEquals(1, data.get(0).get("unread").asInt());
        assertEquals("확인했습니다", data.get(0).get("replies").get(0).get("body").asText());
        assertFalse(data.get(0).has("companyId"));
        assertFalse(data.get(0).has("requesterEmployeeId"));
        assertFalse(data.get(0).get("replies").get(0).has("inquiryNo"));
    }

    @Test
    @DisplayName("문의 읽음은 요청 값이 아니라 로그인 업체·계정과 주소의 접수번호로 처리한다")
    void readRepliesUsesLoginUser() throws Exception {
        JsonNode body = body(postJson("/support/api/inquiries/100/read", "{\"companyId\":999,\"userAccountId\":999}"));

        assertTrue(body.get("success").asBoolean());
        assertEquals(1, body.get("data").get("read").asInt());
        assertEquals("3:9:100", service.readTarget);
    }

    @Test
    @DisplayName("회원 접수 목록은 화면이 읽는 칸을 준다")
    void memberInquiries() throws Exception {
        JsonNode first = body(mvc.perform(get("/ops/api/member-inquiries")).andReturn()).get("data").get(0);

        assertEquals("강아지상사", first.get("company").asText());
        assertEquals("SUP_RECEIVED", first.get("state").asText());
        assertFalse(first.has("unread"));
        assertFalse(first.has("requesterEmployeeId"));
    }

    @Test
    @DisplayName("운영자 답변은 로그인한 운영자 계정으로 처리하고 반영된 문의를 돌려준다")
    void replyUsesOperator() throws Exception {
        JsonNode data = body(postJson("/ops/api/member-inquiries/100/replies", "{\"body\":\"답변\"}")).get("data");

        assertEquals(9L, service.operatorAccountId);
        assertEquals(100L, data.get("no").asLong());
    }

    @Test
    @DisplayName("업체 관리자 목록은 로그인 업체로 찾는다")
    void companyInquiries() throws Exception {
        JsonNode data = body(mvc.perform(get("/support/admin/api/inquiries")).andReturn()).get("data");

        assertEquals(3L, service.companyId);
        assertEquals(100L, data.get(0).get("no").asLong());
    }

    @Test
    @DisplayName("답변 수정은 주소의 문의·답변 번호로 처리하고 반영된 문의를 돌려준다")
    void editReply() throws Exception {
        JsonNode data = body(postJson("/ops/api/member-inquiries/100/replies/5", "{\"body\":\"고친 답변\"}")).get("data");

        assertEquals("100:5:고친 답변", service.editedTarget);
        assertEquals(100L, data.get("no").asLong());
    }

    @Test
    @DisplayName("입력 오류는 400, 없는 문의는 404로 문구와 함께 준다")
    void rejected() throws Exception {
        service.error = new SupportFormException("제목을 입력해 주세요.");
        MvcResult bad = postJson("/support/api/inquiries", "{}");
        assertEquals(400, bad.getResponse().getStatus());
        assertEquals("제목을 입력해 주세요.", body(bad).get("message").asText());

        service.error = new SupportNotFoundException("문의를 찾을 수 없습니다.");
        MvcResult missing = postJson("/ops/api/member-inquiries/404/replies", "{\"body\":\"답변\"}");
        assertEquals(404, missing.getResponse().getStatus());
        assertEquals("문의를 찾을 수 없습니다.", body(missing).get("message").asText());
    }

    @Test
    @DisplayName("번호 칸에 글자가 오거나 JSON이 깨지면 400이다")
    void unreadable() throws Exception {
        assertEquals(400, postJson("/ops/api/member-inquiries/abc/replies", "{\"body\":\"답변\"}").getResponse().getStatus());
        assertEquals(400, postJson("/support/api/inquiries", "{깨진").getResponse().getStatus());
    }

    @Test
    @DisplayName("예상 못 한 오류는 500이고 원인을 응답에 넣지 않는다")
    void failedHidesCause() throws Exception {
        service.error = new IllegalStateException("ORA-00942");
        MvcResult failed = mvc.perform(get("/support/api/inquiries")).andReturn();

        assertEquals(500, failed.getResponse().getStatus());
        assertFalse(failed.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("ORA-00942"));
    }
}
