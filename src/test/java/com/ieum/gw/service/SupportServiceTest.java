package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.code.SupportInquiryStatus;
import com.ieum.gw.dao.SupportDAO;
import com.ieum.gw.dto.SupportInquiryDTO;
import com.ieum.gw.dto.SupportInquiryFormDTO;
import com.ieum.gw.dto.SupportInquirySaveDTO;
import com.ieum.gw.dto.SupportReplyDTO;
import com.ieum.gw.dto.SupportReplyFormDTO;
import com.ieum.gw.dto.SupportReplySaveDTO;

/** DB 없이 입력 규칙과 업체 값의 출처를 확인한다. SQL은 db-check와 임시 Oracle에서 따로 본다 */
class SupportServiceTest {

    private static final Long CUSTOMER_COMPANY = 7L;
    private static final Long OPERATOR_ACCOUNT = 1L;

    static class FakeDAO extends SupportDAO {
        SupportInquiryDTO inquiry = inquiry(100L, CUSTOMER_COMPANY);
        final List<SupportInquirySaveDTO> savedInquiries = new ArrayList<>();
        final List<SupportReplySaveDTO> savedReplies = new ArrayList<>();
        final List<SupportInquiryDTO> inquiries = new ArrayList<>();
        final List<SupportReplyDTO> replies = new ArrayList<>();
        final List<String> statusChanges = new ArrayList<>();
        final List<Long> askedScope = new ArrayList<>();
        final List<String> editedReplies = new ArrayList<>();
        int editedRows = 1;

        FakeDAO() {
            super(null);
        }

        @Override
        public int insertInquiry(SupportInquirySaveDTO dto) {
            dto.setNo(900L);
            savedInquiries.add(dto);
            return 1;
        }

        @Override
        public List<SupportInquiryDTO> listMyInquiries(Long companyId, Long employeeId) {
            askedScope.add(companyId);
            askedScope.add(employeeId);
            return inquiries;
        }

        @Override
        public List<SupportReplyDTO> listMyReplies(Long companyId, Long employeeId) {
            askedScope.add(companyId);
            askedScope.add(employeeId);
            return replies;
        }

        @Override
        public List<SupportInquiryDTO> listCompanyInquiries(Long companyId) {
            askedScope.add(companyId);
            return inquiries;
        }

        @Override
        public List<SupportReplyDTO> listCompanyReplies(Long companyId) {
            askedScope.add(companyId);
            return replies;
        }

        @Override
        public int updateReply(Long no, Long replyId, String body) {
            editedReplies.add(no + ":" + replyId + ":" + body);
            return editedRows;
        }

        @Override
        public List<SupportInquiryDTO> listAllInquiries() {
            return inquiries;
        }

        @Override
        public List<SupportReplyDTO> listAllReplies() {
            return replies;
        }

        @Override
        public SupportInquiryDTO getInquiry(Long no) {
            return inquiry;
        }

        @Override
        public List<SupportReplyDTO> listReplies(Long no) {
            return replies;
        }

        @Override
        public int insertReply(SupportReplySaveDTO dto) {
            savedReplies.add(dto);
            return 1;
        }

        @Override
        public int changeInquiryStatus(Long no, Long operatorAccountId, SupportInquiryStatus from, SupportInquiryStatus to) {
            statusChanges.add(no + ":" + operatorAccountId + ":" + from + ">" + to);
            return 1;
        }
    }

    private final FakeDAO dao = new FakeDAO();
    private final SupportService service = new SupportService(dao);

    private static SupportInquiryDTO inquiry(Long no, Long companyId) {
        SupportInquiryDTO inquiry = new SupportInquiryDTO();
        inquiry.setNo(no);
        inquiry.setCompanyId(companyId);
        return inquiry;
    }

    private static SupportReplyDTO replyOf(Long inquiryNo, Long id) {
        SupportReplyDTO reply = new SupportReplyDTO();
        reply.setId(id);
        reply.setInquiryNo(inquiryNo);
        return reply;
    }

    private static SupportInquiryFormDTO form(String title, String body) {
        SupportInquiryFormDTO form = new SupportInquiryFormDTO();
        form.setTitle(title);
        form.setBody(body);
        return form;
    }

    private static SupportReplyFormDTO replyForm(String body) {
        SupportReplyFormDTO form = new SupportReplyFormDTO();
        form.setBody(body);
        return form;
    }

    @Test
    @DisplayName("문의는 넘겨받은 로그인 업체·직원으로 저장하고 제목·내용의 앞뒤 공백을 뗀다")
    void createUsesLoginValues() {
        Long no = service.createInquiry(3L, 44L, form("  화면 오류  ", "\n결재함이 안 열립니다\n"));

        assertEquals(900L, no);
        SupportInquirySaveDTO saved = dao.savedInquiries.get(0);
        assertEquals(3L, saved.getCompanyId());
        assertEquals(44L, saved.getEmployeeId());
        assertEquals("화면 오류", saved.getTitle());
        assertEquals("결재함이 안 열립니다", saved.getBody());
    }

    @Test
    @DisplayName("제목이나 내용이 비었으면 저장하지 않고 거절한다")
    void createRejectsBlank() {
        assertEquals("제목을 입력해 주세요.",
                assertThrows(SupportFormException.class, () -> service.createInquiry(3L, 44L, form("   ", "내용"))).getMessage());
        assertEquals("내용을 입력해 주세요.",
                assertThrows(SupportFormException.class, () -> service.createInquiry(3L, 44L, form("제목", null))).getMessage());
        assertTrue(dao.savedInquiries.isEmpty());
    }

    @Test
    @DisplayName("제목 100자·내용 4000자까지 받고 한 글자라도 넘으면 거절한다")
    void createChecksLength() {
        service.createInquiry(3L, 44L, form("가".repeat(SupportService.MAX_TITLE), "나".repeat(SupportService.MAX_BODY)));
        assertEquals(1, dao.savedInquiries.size());

        assertThrows(SupportFormException.class,
                () -> service.createInquiry(3L, 44L, form("가".repeat(SupportService.MAX_TITLE + 1), "내용")));
        assertThrows(SupportFormException.class,
                () -> service.createInquiry(3L, 44L, form("제목", "나".repeat(SupportService.MAX_BODY + 1))));
        assertEquals(1, dao.savedInquiries.size());
    }

    @Test
    @DisplayName("내가 보낸 문의는 로그인 업체·직원으로만 찾고 답변을 문의별로 붙인다")
    void listMineScopedAndGrouped() {
        dao.inquiries.add(inquiry(100L, 3L));
        dao.inquiries.add(inquiry(101L, 3L));
        dao.replies.add(replyOf(100L, 1L));
        dao.replies.add(replyOf(100L, 2L));

        List<SupportInquiryDTO> mine = service.listMine(3L, 44L);

        assertEquals(List.of(3L, 44L, 3L, 44L), dao.askedScope);
        assertEquals(2, mine.get(0).getReplies().size());
        assertEquals(1L, mine.get(0).getReplies().get(0).getId());
        assertTrue(mine.get(1).getReplies().isEmpty());
    }

    @Test
    @DisplayName("업체 관리자 목록은 로그인 업체로만 찾고 답변을 문의별로 붙인다")
    void listCompanyScopedAndGrouped() {
        dao.inquiries.add(inquiry(100L, 3L));
        dao.replies.add(replyOf(100L, 1L));

        List<SupportInquiryDTO> company = service.listCompany(3L);

        assertEquals(List.of(3L, 3L), dao.askedScope);
        assertEquals(1, company.get(0).getReplies().size());
    }

    @Test
    @DisplayName("답변을 고치면 앞뒤 공백을 뗀 내용으로 그 문의의 그 답변만 바꾸고 상태는 건드리지 않는다")
    void editReplyUpdatesOnlyThatReply() {
        dao.replies.add(replyOf(100L, 5L));

        SupportInquiryDTO updated = service.editReply(100L, 5L, replyForm("  고친 답변  "));

        assertEquals(List.of("100:5:고친 답변"), dao.editedReplies);
        assertTrue(dao.statusChanges.isEmpty());
        assertEquals(1, updated.getReplies().size());
    }

    @Test
    @DisplayName("그 문의에 없는 답변을 고치면 404 예외다")
    void editMissingReply() {
        dao.editedRows = 0;

        assertEquals("답변을 찾을 수 없습니다.",
                assertThrows(SupportNotFoundException.class, () -> service.editReply(100L, 999L, replyForm("답변"))).getMessage());
    }

    @Test
    @DisplayName("고치는 답변이 비었거나 4000자를 넘으면 거절하고 고치지 않는다")
    void editChecksBody() {
        assertThrows(SupportFormException.class, () -> service.editReply(100L, 5L, replyForm("   ")));
        assertThrows(SupportFormException.class,
                () -> service.editReply(100L, 5L, replyForm("가".repeat(SupportService.MAX_BODY + 1))));

        assertTrue(dao.editedReplies.isEmpty());
    }

    @Test
    @DisplayName("운영자 답변은 운영자의 업체가 아니라 문의한 업체로 저장하고 접수 상태만 지원 중으로 바꾼다")
    void replySavesToInquiryCompany() {
        dao.replies.add(replyOf(100L, 5L));

        SupportInquiryDTO updated = service.reply(100L, replyForm("  확인 중입니다  "), OPERATOR_ACCOUNT);

        SupportReplySaveDTO saved = dao.savedReplies.get(0);
        assertEquals(CUSTOMER_COMPANY, saved.getCompanyId());
        assertEquals(100L, saved.getNo());
        assertEquals(OPERATOR_ACCOUNT, saved.getAuthorAccountId());
        assertEquals("확인 중입니다", saved.getBody());
        assertEquals(List.of("100:1:SUP_RECEIVED>SUP_PROCESSING"), dao.statusChanges);
        assertEquals(1, updated.getReplies().size());
    }

    @Test
    @DisplayName("없는 접수번호에 답변하면 404 예외이고 저장하지 않는다")
    void replyToMissingInquiry() {
        dao.inquiry = null;

        assertThrows(SupportNotFoundException.class, () -> service.reply(404L, replyForm("답변"), OPERATOR_ACCOUNT));

        assertTrue(dao.savedReplies.isEmpty());
        assertTrue(dao.statusChanges.isEmpty());
    }

    @Test
    @DisplayName("답변이 비었거나 4000자를 넘으면 거절한다")
    void replyChecksBody() {
        assertEquals("답변을 입력해 주세요.",
                assertThrows(SupportFormException.class, () -> service.reply(100L, replyForm("  "), OPERATOR_ACCOUNT)).getMessage());
        assertThrows(SupportFormException.class,
                () -> service.reply(100L, replyForm("가".repeat(SupportService.MAX_BODY + 1)), OPERATOR_ACCOUNT));
        assertTrue(dao.savedReplies.isEmpty());
    }
}
