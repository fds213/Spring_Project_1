package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ieum.gw.code.MailType;
import com.ieum.gw.dao.MailDAO;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.MailResultDTO;

class MailServiceTest {

    /** 순서를 적어 두는 가짜 기록 */
    static class FakeDAO extends MailDAO {
        final List<String> events = new ArrayList<>();
        RuntimeException onInsert;
        RuntimeException onRecord;
        Boolean sent;
        String error;

        FakeDAO() {
            super(null);
        }

        @Override
        public int insertOutbox(MailMessageDTO dto) {
            if (onInsert != null) {
                throw onInsert;
            }
            events.add("기록");
            dto.setMailOutboxId(1000L);
            return 1;
        }

        @Override
        public int updateResult(Long mailOutboxId, boolean sent, String error) {
            if (onRecord != null) {
                throw onRecord;
            }
            events.add("결과");
            this.sent = sent;
            this.error = error;
            return 1;
        }

        @Override
        public int insertAttempt(Long mailOutboxId, boolean sent, String error) {
            events.add("시도");
            return 1;
        }
    }

    /** 보낸 메일을 적어 두거나 정해 준 예외를 던지는 가짜 메일 서버 */
    static class FakeSender implements JavaMailSender {
        final List<MimeMessage> sent = new ArrayList<>();
        FakeDAO dao;
        MailException fail;

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public void send(MimeMessage message) {
            if (dao != null) {
                dao.events.add("발송");
            }
            if (fail != null) {
                throw fail;
            }
            sent.add(message);
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            throw new UnsupportedOperationException();
        }
    }

    private static MailSettings settings(boolean enabled) {
        return new MailSettings() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public String getFrom() {
                return "team@ieum.test";
            }

            @Override
            public String getFromName() {
                return "이음";
            }
        };
    }

    private static MailMessageDTO mail(String to, String subject) {
        MailMessageDTO m = new MailMessageDTO();
        m.setMailType(MailType.MT_TRIAL_VERIFY);
        m.setRecipientEmail(to);
        m.setSubject(subject);
        m.setBody("본문");
        m.setDedupeKey("trial-verify:1");
        return m;
    }

    private final FakeDAO dao = new FakeDAO();
    private final FakeSender sender = new FakeSender();

    private MailService service(boolean enabled) {
        sender.dao = dao;
        return new MailService(dao, new MailRecordService(dao), settings(enabled), sender);
    }

    @Test
    @DisplayName("기록 → 발송 → 결과 기록 순서로 한 통을 보낸다")
    void recordsBeforeAndAfter() throws Exception {
        MailResultDTO result = service(true).send(mail("kim@gana.com", "제목"));

        assertTrue(result.isOk());
        assertEquals(List.of("기록", "발송", "결과", "시도"), dao.events);
        assertTrue(dao.sent);
        assertNull(dao.error);
        assertEquals("kim@gana.com", sender.sent.get(0).getAllRecipients()[0].toString());
        assertEquals("제목", sender.sent.get(0).getSubject());
    }

    @Test
    @DisplayName("트랜잭션 안에서 부르면 기록도 발송도 하지 않고 바로 예외를 던진다")
    void refusesInsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> service(true).send(mail("kim@gana.com", "제목")));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertTrue(dao.events.isEmpty());
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    @DisplayName("메일을 끄면 보내지 않고 실패로 남겨 '발송 완료'로 보이지 않게 한다")
    void offIsNotSent() {
        MailResultDTO result = service(false).send(mail("kim@gana.com", "제목"));

        assertFalse(result.isOk());
        assertEquals(MailService.REASON_OFF, result.getReason());
        assertEquals(List.of("기록", "결과", "시도"), dao.events);
        assertFalse(dao.sent);
    }

    @Test
    @DisplayName("실패 사유를 나눈다: 앱 비밀번호 오류, 연결 실패, 받는 주소 거부")
    void reasons() {
        MailService service = service(true);

        sender.fail = new MailAuthenticationException("인증 실패");
        assertEquals(MailService.REASON_AUTH, service.send(mail("kim@gana.com", "제목")).getReason());

        // Spring은 서버에 연결하지 못해도 MailSendException을 던진다. 실패 목록이 비어 있으면 주소 문제가 아니다
        sender.fail = new MailSendException("Mail server connection failed");
        assertEquals(MailService.REASON_CONNECT, service.send(mail("kim@gana.com", "제목")).getReason());

        sender.fail = new MailSendException(Map.of(new Object(), new SendFailedException("거부")));
        assertEquals(MailService.REASON_REJECTED, service.send(mail("kim@gana.com", "제목")).getReason());
        assertEquals(MailService.REASON_REJECTED, dao.error);
    }

    @Test
    @DisplayName("받는 주소 형식이 틀리면 메일 서버까지 가지 않고 실패로 남는다")
    void badAddress() {
        MailResultDTO result = service(true).send(mail("a b@gana.com", "제목"));

        assertEquals(MailService.REASON_ADDRESS, result.getReason());
        assertEquals(List.of("기록", "결과", "시도"), dao.events);
    }

    @Test
    @DisplayName("같은 키로 이미 기록한 메일은 보내지 않는다")
    void duplicateIsNotSent() {
        dao.onInsert = new DuplicateKeyException("UQ_72_01");

        assertEquals(MailService.REASON_DUPLICATE, service(true).send(mail("kim@gana.com", "제목")).getReason());
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    @DisplayName("기록을 남기지 못하면 보내지 않고, 예외도 내보내지 않는다")
    void noRecordNoSend() {
        dao.onInsert = new DataAccessResourceFailureException("DB 끊김");

        assertEquals(MailService.REASON_NO_RECORD, service(true).send(mail("kim@gana.com", "제목")).getReason());
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    @DisplayName("보낸 뒤 결과 기록이 실패해도 보낸 결과를 돌려준다")
    void sentEvenIfResultNotRecorded() {
        dao.onRecord = new DataAccessResourceFailureException("DB 끊김");

        assertTrue(service(true).send(mail("kim@gana.com", "제목")).isOk());
        assertEquals(1, sender.sent.size());
    }

    @Test
    @DisplayName("제목의 줄바꿈을 지우고 DB 칸 길이(200자)로 자른다")
    void cleansSubject() throws Exception {
        MailMessageDTO m = mail("kim@gana.com", "첫 줄\r\nBcc: x@evil.test" + "가".repeat(300));

        service(true).send(m);

        assertFalse(m.getSubject().contains("\n"));
        assertEquals(200, m.getSubject().length());
        assertFalse(sender.sent.get(0).getSubject().contains("\n"));
    }
}
