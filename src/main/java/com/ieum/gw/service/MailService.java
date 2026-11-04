package com.ieum.gw.service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;

import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ieum.gw.code.MailType;
import com.ieum.gw.dao.MailDAO;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.MailResultDTO;

/**
 * 메일을 보내는 곳은 이 클래스 하나다(기능명세서 1.2): 발송 전에 기록하고, 트랜잭션 밖에서 보낸 뒤, 결과를 기록한다.
 * 실패해도 예외를 던지지 않고 결과만 돌려준다. 메일 때문에 체험 신청이나 문의 저장이 취소되면 안 된다.
 * 로그에 본문을 남기지 않는다(인증 링크가 들어 있다).
 */
@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);
    private static final int MAX_SUBJECT = 200;  // MAIL_OUTBOX.SUBJECT
    /** 결과를 남기지 못하고 멈춘 발송으로 보는 시간. 메일 서버와 주고받는 단계마다 5초 제한이라 정상 발송은 이 안에 끝난다 */
    public static final int STALE_MINUTES = 2;
    static final String REASON_OFF = "메일 발송이 꺼져 있음(서버 설정)";
    static final String REASON_AUTH = "메일 서버 인증 실패(서버 설정 확인 필요)";
    static final String REASON_REJECTED = "받는 주소를 메일 서버가 거부함";
    static final String REASON_CONNECT = "메일 서버 연결 실패";
    static final String REASON_ADDRESS = "받는 주소 형식 오류";
    static final String REASON_OTHER = "메일을 보내지 못함";
    static final String REASON_DUPLICATE = "이미 기록된 메일이라 보내지 않음";
    static final String REASON_NO_RECORD = "발송 기록을 남기지 못해 보내지 않음";

    private final MailDAO dao;
    private final MailRecordService recordService;
    private final MailSettings settings;
    private final JavaMailSender sender;

    @Autowired
    public MailService(MailDAO dao, MailRecordService recordService, MailSettings settings) {
        this(dao, recordService, settings, settings.newSender());
    }

    MailService(MailDAO dao, MailRecordService recordService, MailSettings settings, JavaMailSender sender) {
        this.dao = dao;
        this.recordService = recordService;
        this.settings = settings;
        this.sender = sender;
    }

    /**
     * 한 통을 보낸다. 트랜잭션이 끝난 뒤에 부른다. 부르는 메서드에 @Transactional이 있으면 메일을 기다리는 동안 DB 연결을 붙잡고,
     * 나중에 롤백돼도 메일은 이미 나간 뒤이므로 바로 예외를 던져 개발 중에 알게 한다.
     *
     * @throws IllegalStateException 트랜잭션 안에서 불렀을 때
     */
    public MailResultDTO send(MailMessageDTO mail) {
        requireNoTransaction();
        cleanSubject(mail);
        try {
            dao.insertOutbox(mail);
        } catch (DuplicateKeyException e) {
            return MailResultDTO.failed(REASON_DUPLICATE);
        } catch (RuntimeException e) {
            logger.error("메일 발송 기록 실패: 종류={}", mail.getMailType(), e);
            return MailResultDTO.failed(REASON_NO_RECORD);
        }
        return deliverAndRecord(mail);
    }

    /**
     * 발송 기록만 남기고 보내지는 않는다. 문의 답변처럼 업무 자료와 기록이 함께 커밋돼야 하는 메일은 그 트랜잭션에서 이것을 부르고,
     * 커밋이 끝난 뒤 sendRecorded로 보낸다. 기록에 실패하면 업무 자료도 함께 취소되도록 예외를 그대로 던진다.
     */
    public void record(MailMessageDTO mail) {
        cleanSubject(mail);
        dao.insertOutbox(mail);
    }

    /**
     * record로 남겼거나 claimRetry로 되돌린 기록의 메일을 보내고 결과를 그 기록에 남긴다.
     *
     * @throws IllegalStateException 트랜잭션 안에서 불렀을 때
     */
    public MailResultDTO sendRecorded(MailMessageDTO mail) {
        requireNoTransaction();
        cleanSubject(mail);
        return deliverAndRecord(mail);
    }

    /**
     * 실패했거나 결과를 남기지 못하고 멈춘 기록을 다시 보낼 수 있게 대기로 되돌린다. 조건부 UPDATE라 같은 기록을 동시에 되돌려도
     * 한 요청만 true를 받는다. true를 받은 요청만 커밋 뒤 sendRecorded를 부른다.
     *
     * @return 되돌렸으면 true, 이미 보냈거나 다른 요청이 보내는 중이면 false
     */
    public boolean claimRetry(Long mailOutboxId) {
        return dao.claimRetry(mailOutboxId, STALE_MINUTES) == 1;
    }

    /** 그날 이 주소로 기록된 이 종류의 메일 수. 보내지 못한 것도 센다. 공개 폼의 횟수 제한에 쓴다 */
    public int countForRecipient(MailType mailType, String recipientEmail, LocalDate day) {
        return dao.countForRecipient(mailType, recipientEmail, day);
    }

    private void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("메일은 트랜잭션 밖에서 보냅니다. 커밋이 끝난 뒤에 부르세요.");
        }
    }

    // 제목 줄바꿈은 메일 머리글을 끼워 넣는 데 쓰일 수 있다
    private static void cleanSubject(MailMessageDTO mail) {
        String subject = mail.getSubject().replaceAll("[\\r\\n]+", " ");
        mail.setSubject(subject.length() > MAX_SUBJECT ? subject.substring(0, MAX_SUBJECT) : subject);
    }

    private MailResultDTO deliverAndRecord(MailMessageDTO mail) {
        MailResultDTO result = deliver(mail);
        try {
            recordService.recordResult(mail.getMailOutboxId(), result.isOk(), result.getReason());
        } catch (RuntimeException e) {
            // 메일은 이미 나갔거나 실패가 정해졌다. 기록이 대기로 남을 뿐이라 결과는 그대로 돌려준다
            logger.error("메일 결과 기록 실패: mailOutboxId={}", mail.getMailOutboxId(), e);
        }
        return result;
    }

    // 연결 실패도 MailSendException이다. 받는 주소를 서버가 거부했을 때만 실패 목록에 SendFailedException이 들어 있다
    private MailResultDTO deliver(MailMessageDTO mail) {
        if (!settings.isEnabled()) {
            logger.info("메일 발송 꺼짐: 종류={}", mail.getMailType());
            return MailResultDTO.failed(REASON_OFF);
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(settings.getFrom(), settings.getFromName());
            helper.setTo(mail.getRecipientEmail());
            helper.setSubject(mail.getSubject());
            helper.setText(mail.getBody(), false);
            sender.send(message);
            return MailResultDTO.sent();
        } catch (MailAuthenticationException e) {
            logger.error("메일 계정 인증 실패(config/mail.properties 확인)", e);
            return MailResultDTO.failed(REASON_AUTH);
        } catch (MailSendException e) {
            boolean rejected = e.getFailedMessages().values().stream().anyMatch(SendFailedException.class::isInstance);
            logger.warn("메일 발송 실패: 종류={}, 받는 사람={}", mail.getMailType(), mail.getRecipientEmail(), e);
            return MailResultDTO.failed(rejected ? REASON_REJECTED : REASON_CONNECT);
        } catch (AddressException e) {
            logger.warn("메일 주소 오류: 종류={}, 받는 사람={}", mail.getMailType(), mail.getRecipientEmail(), e);
            return MailResultDTO.failed(REASON_ADDRESS);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            logger.error("메일 발송 오류: 종류={}", mail.getMailType(), e);
            return MailResultDTO.failed(REASON_OTHER);
        }
    }
}
