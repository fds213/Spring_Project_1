package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.code.MailType;
import com.ieum.gw.dao.OpsDAO;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.MailResultDTO;
import com.ieum.gw.dto.OpsGuestInquiryDTO;
import com.ieum.gw.dto.OpsGuestReplyDTO;
import com.ieum.gw.dto.OpsGuestReplySaveDTO;
import com.ieum.gw.dto.OpsReplyFormDTO;
import com.ieum.gw.dto.OpsReplyResultDTO;

/** DB·메일 서버 없이 순서와 거절 규칙을 확인한다. 동시 요청과 SQL은 임시 Oracle·가짜 메일 서버에서 따로 본다 */
class OpsReplyServiceTest {

    private final List<String> events = new ArrayList<>();

    class FakeDAO extends OpsDAO {
        OpsGuestInquiryDTO inquiry = inquiry();
        final List<OpsGuestReplyDTO> replies = new ArrayList<>();
        final List<OpsGuestReplySaveDTO> saved = new ArrayList<>();
        RuntimeException onMark;

        FakeDAO() {
            super(null);
        }

        @Override
        public OpsGuestInquiryDTO lockGuestInquiry(Long no) {
            events.add("잠금");
            return inquiry;
        }

        @Override
        public OpsGuestInquiryDTO getGuestInquiry(Long no) {
            return inquiry;
        }

        @Override
        public List<OpsGuestReplyDTO> listGuestReplies(Long no, int staleMinutes) {
            return replies;
        }

        @Override
        public int insertGuestReply(OpsGuestReplySaveDTO dto) {
            events.add("답변 저장");
            dto.setReplyId(500L);
            saved.add(dto);
            return 1;
        }

        @Override
        public int markGuestInquiryAnswered(Long no) {
            if (onMark != null) {
                throw onMark;
            }
            events.add("답변 완료");
            return 1;
        }
    }

    class FakeMail extends MailService {
        MailResultDTO answer = MailResultDTO.sent();
        boolean claim = true;
        Long claimedOutboxId;
        MailMessageDTO recorded;
        MailMessageDTO sent;

        FakeMail() {
            super(null, null, null, null);
        }

        @Override
        public void record(MailMessageDTO mail) {
            events.add("발송 기록");
            mail.setMailOutboxId(900L);
            recorded = mail;
        }

        @Override
        public boolean claimRetry(Long mailOutboxId) {
            events.add("되돌림");
            claimedOutboxId = mailOutboxId;
            return claim;
        }

        @Override
        public MailResultDTO sendRecorded(MailMessageDTO mail) {
            events.add("발송");
            sent = mail;
            return answer;
        }
    }

    private final FakeDAO dao = new FakeDAO();
    private final FakeMail mail = new FakeMail();
    private final OpsReplyService service = new OpsReplyService(new OpsReplyRecordService(dao, mail), mail,
            new OpsService(dao, null, null), dao);

    private static OpsGuestInquiryDTO inquiry() {
        OpsGuestInquiryDTO q = new OpsGuestInquiryDTO();
        q.setNo(1000L);
        q.setEmail("kim@gana.com");
        q.setTitle("도입 비용 문의");
        q.setBody("50명 기준 비용이 궁금합니다.");
        return q;
    }

    private static OpsReplyFormDTO form(String body, Long lastReplyId) {
        OpsReplyFormDTO form = new OpsReplyFormDTO();
        form.setBody(body);
        form.setLastReplyId(lastReplyId);
        return form;
    }

    private OpsGuestReplyDTO reply(long id, String status, boolean stale) {
        OpsGuestReplyDTO reply = new OpsGuestReplyDTO();
        reply.setId(id);
        reply.setStatus(status);
        reply.setStale(stale);
        reply.setBody("저장된 답변");
        reply.setMailOutboxId(id + 800);
        dao.replies.add(reply);
        return reply;
    }

    private void assertRejected(int status, Runnable call) {
        assertEquals(status, assertThrows(OpsReplyException.class, call::run).getStatus());
    }

    @Test
    @DisplayName("답변: 접수를 잠그고 답변과 발송 기록을 저장한 뒤 메일을 보내고 답변 완료로 바꾼다")
    void sendOrder() {
        OpsReplyResultDTO result = service.sendReply(1000L, form("  답변입니다  ", null), 9L);

        assertEquals(List.of("잠금", "답변 저장", "발송 기록", "발송", "답변 완료"), events);
        assertTrue(result.isSent());
        assertNull(result.getReason());
        assertEquals(1000L, result.getInquiry().getNo());
        assertEquals("답변입니다", dao.saved.get(0).getBody());
        assertEquals(9L, dao.saved.get(0).getOperatorAccountId());
        assertEquals(1000L, dao.saved.get(0).getNo());
    }

    @Test
    @DisplayName("답변 메일: 문의한 주소로, 답변 하나에 키 하나로, 문의 내용을 함께 담아 보낸다")
    void mailContent() {
        service.sendReply(1000L, form("답변입니다", null), 9L);

        assertEquals(MailType.MT_INQUIRY_REPLY, mail.sent.getMailType());
        assertEquals("kim@gana.com", mail.sent.getRecipientEmail());
        assertEquals("[이음] 문의 답변: 도입 비용 문의", mail.sent.getSubject());
        assertEquals("inquiry-reply:500", mail.sent.getDedupeKey());
        assertEquals(1000L, mail.sent.getPublicInquiryId());
        assertEquals(500L, mail.sent.getPublicReplyId());
        assertTrue(mail.sent.getBody().contains("답변입니다"));
        assertTrue(mail.sent.getBody().contains("50명 기준 비용이 궁금합니다."));
    }

    @Test
    @DisplayName("메일이 실패하면 답변은 남고 사유를 돌려주며 답변 대기로 둔다")
    void mailFailed() {
        mail.answer = MailResultDTO.failed("메일 서버 연결 실패");

        OpsReplyResultDTO result = service.sendReply(1000L, form("답변", null), 9L);

        assertFalse(result.isSent());
        assertEquals("메일 서버 연결 실패", result.getReason());
        assertEquals(1, dao.saved.size());
        assertFalse(events.contains("답변 완료"));
    }

    @Test
    @DisplayName("메일이 나간 뒤 답변 완료 표시가 실패해도 보냈다고 답한다: 실패로 답하면 운영자가 또 보낸다")
    void sentEvenIfMarkFails() {
        dao.onMark = new IllegalStateException("DB 끊김");

        assertTrue(service.sendReply(1000L, form("답변", null), 9L).isSent());
    }

    @Test
    @DisplayName("빈 답변과 4,000자를 넘는 답변은 저장하지 않고 400이다")
    void invalidBody() {
        assertRejected(400, () -> service.sendReply(1000L, form("   ", null), 9L));
        assertRejected(400, () -> service.sendReply(1000L, form(null, null), 9L));
        assertRejected(400, () -> service.sendReply(1000L, null, 9L));
        assertRejected(400, () -> service.sendReply(1000L, form("가".repeat(OpsReplyService.MAX_BODY + 1), null), 9L));

        assertTrue(events.isEmpty());
        assertTrue(service.sendReply(1000L, form("가".repeat(OpsReplyService.MAX_BODY), null), 9L).isSent());
    }

    @Test
    @DisplayName("없는 접수는 404이고 메일을 보내지 않는다")
    void noInquiry() {
        dao.inquiry = null;

        assertThrows(OpsNotFoundException.class, () -> service.sendReply(1000L, form("답변", null), 9L));
        assertThrows(OpsNotFoundException.class, () -> service.resend(1000L, 1L));
        assertFalse(events.contains("발송"));
    }

    @Test
    @DisplayName("두 번 누른 요청·다른 운영자의 답변: 화면에서 본 마지막 답변과 다르면 409이고 메일을 보내지 않는다")
    void staleScreen() {
        reply(1L, "MAIL_SENT", false);

        assertRejected(409, () -> service.sendReply(1000L, form("답변", null), 9L));
        assertRejected(409, () -> service.sendReply(1000L, form("답변", 2L), 9L));
        assertTrue(dao.saved.isEmpty());
        assertFalse(events.contains("발송"));

        assertTrue(service.sendReply(1000L, form("추가 답변", 1L), 9L).isSent());
    }

    @Test
    @DisplayName("마지막 답변을 보내는 중이면 새 답변은 409다. 결과 없이 멈춘 발송은 막지 않는다")
    void inFlight() {
        reply(1L, "MAIL_PENDING", false);
        assertRejected(409, () -> service.sendReply(1000L, form("답변", 1L), 9L));
        assertTrue(dao.saved.isEmpty());

        dao.replies.get(0).setStale(true);
        assertTrue(service.sendReply(1000L, form("답변", 1L), 9L).isSent());
    }

    @Test
    @DisplayName("다시 보내기: 같은 발송 기록으로 저장된 답변을 보내고, 새 답변을 만들지 않으며 답변 완료로 바꾼다")
    void resend() {
        reply(1L, "MAIL_FAILED", false);

        OpsReplyResultDTO result = service.resend(1000L, 1L);

        assertEquals(List.of("잠금", "되돌림", "발송", "답변 완료"), events);
        assertTrue(result.isSent());
        assertEquals(801L, mail.claimedOutboxId);
        assertEquals(801L, mail.sent.getMailOutboxId());
        assertEquals(1L, mail.sent.getPublicReplyId());
        assertTrue(mail.sent.getBody().contains("저장된 답변"));
        assertTrue(dao.saved.isEmpty());
    }

    @Test
    @DisplayName("다시 보내기: 조건부 UPDATE에 진 요청은 409이고 메일을 보내지 않는다(동시에 눌러도 한 통)")
    void resendLost() {
        reply(1L, "MAIL_SENT", false);
        mail.claim = false;

        assertRejected(409, () -> service.resend(1000L, 1L));
        assertFalse(events.contains("발송"));
    }

    @Test
    @DisplayName("다시 보내기는 가장 최근 답변만 된다: 앞선 답변이 뒤늦게 나가지 않게")
    void resendLatestOnly() {
        reply(1L, "MAIL_FAILED", false);
        reply(2L, "MAIL_SENT", false);

        assertRejected(409, () -> service.resend(1000L, 1L));
        assertFalse(events.contains("되돌림"));
        assertFalse(events.contains("발송"));
    }

    @Test
    @DisplayName("다시 보내기: 답변이 없거나 다른 번호면 409다")
    void resendNothing() {
        assertRejected(409, () -> service.resend(1000L, 1L));

        reply(1L, "MAIL_FAILED", false);
        assertRejected(409, () -> service.resend(1000L, 7L));
    }
}
