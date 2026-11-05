package com.ieum.gw.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ieum.gw.dao.OpsDAO;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.MailResultDTO;
import com.ieum.gw.dto.OpsReplyFormDTO;
import com.ieum.gw.dto.OpsReplyResultDTO;

import lombok.RequiredArgsConstructor;

/**
 * 비회원 접수 답변 메일(WEB-03)의 순서. /ops/**는 security-context.xml이 ROLE_OPS만 통과시킨다.
 * 트랜잭션이 없다: 저장(OpsReplyRecordService)이 커밋된 뒤에 메일을 보낸다. 메일이 실패해도 답변은 남고 다시 보낼 수 있다.
 */
@Service
@RequiredArgsConstructor
public class OpsReplyService {

    static final int MAX_BODY = 4000; // 화면(ops/inquiry.js)과 같은 값. 내용은 CLOB이라 여기서 막아야 길이가 정해진다

    private static final Logger logger = LoggerFactory.getLogger(OpsReplyService.class);

    private final OpsReplyRecordService recordService;
    private final MailService mailService;
    private final OpsService opsService;
    private final OpsDAO opsDAO;

    /**
     * 답변을 저장하고 메일로 보낸다. 메일이 실패해도 예외가 아니라 결과의 sent·reason으로 알린다.
     *
     * @throws OpsReplyException 400 입력 오류, 409 그사이 다른 답변이 있거나 보내는 중일 때
     * @throws OpsNotFoundException 없는 접수번호일 때
     */
    public OpsReplyResultDTO sendReply(Long no, OpsReplyFormDTO form, Long operatorAccountId) {
        String body = form == null || form.getBody() == null ? "" : form.getBody().trim();
        if (body.isEmpty()) {
            throw new OpsReplyException(400, "답변 내용을 입력해 주세요.");
        }
        if (body.length() > MAX_BODY) {
            throw new OpsReplyException(400, "답변은 " + MAX_BODY + "자까지 쓸 수 있습니다.");
        }
        return deliver(no, recordService.saveReply(no, body, form.getLastReplyId(), operatorAccountId));
    }

    /**
     * 실패했거나 결과를 알 수 없는 가장 최근 답변을 같은 발송 기록으로 다시 보낸다.
     *
     * @throws OpsReplyException 409 가장 최근 답변이 아니거나, 이미 보냈거나 보내는 중일 때
     * @throws OpsNotFoundException 없는 접수번호일 때
     */
    public OpsReplyResultDTO resend(Long no, Long replyId) {
        return deliver(no, recordService.claimResend(no, replyId));
    }

    private OpsReplyResultDTO deliver(Long no, MailMessageDTO mail) {
        MailResultDTO result = mailService.sendRecorded(mail);
        if (result.isOk()) {
            try {
                opsDAO.markGuestInquiryAnswered(no);
            } catch (RuntimeException e) {
                // 메일은 이미 나갔다. 실패로 답하면 운영자가 또 보내게 되니 로그만 남긴다. 다음 답변이 나갈 때 다시 바뀐다
                logger.error("답변 완료 표시 실패: no={}", no, e);
            }
        }
        return new OpsReplyResultDTO(opsService.getGuestInquiry(no), result.isOk(), result.getReason());
    }
}
