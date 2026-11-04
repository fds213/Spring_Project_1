package com.ieum.gw.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dao.MailDAO;

import lombok.RequiredArgsConstructor;

/** 발송 결과를 DB에 남기는 트랜잭션. 메일 서버 호출은 트랜잭션 밖이어야 해서 순서는 MailService가 정하고 저장만 여기로 뗐다 */
@Service
@RequiredArgsConstructor
public class MailRecordService {

    private final MailDAO dao;

    /** 발송 결과와 이번 시도 이력을 함께 남긴다 */
    @Transactional
    public void recordResult(Long mailOutboxId, boolean sent, String error) {
        dao.updateResult(mailOutboxId, sent, error);
        dao.insertAttempt(mailOutboxId, sent, error);
    }
}
