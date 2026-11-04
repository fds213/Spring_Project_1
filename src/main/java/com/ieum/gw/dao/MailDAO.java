package com.ieum.gw.dao;

import org.springframework.stereotype.Repository;

import com.ieum.gw.code.MailStatus;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.mapper.MailMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MailDAO {

    private final MailMapper mapper;

    /** 발송 전 기록 1행(발송 대기). 번호를 dto.mailOutboxId에 채운다. 같은 키면 DuplicateKeyException */
    public int insertOutbox(MailMessageDTO dto) {
        return mapper.insertOutbox(dto);
    }

    /** 발송 결과. 시도 횟수를 1 올린다 */
    public int updateResult(Long mailOutboxId, boolean sent, String error) {
        return mapper.updateResult(mailOutboxId, (sent ? MailStatus.MAIL_SENT : MailStatus.MAIL_FAILED).name(), sent ? "Y" : "N", error);
    }

    /** 이번 시도 이력. updateResult 다음에 부른다(순번이 그 시도 횟수다) */
    public int insertAttempt(Long mailOutboxId, boolean sent, String error) {
        return mapper.insertAttempt(mailOutboxId, sent ? "Y" : "N", error);
    }
}
