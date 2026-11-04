package com.ieum.gw.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.code.MailStatus;
import com.ieum.gw.code.MailType;
import com.ieum.gw.dao.OpsDAO;
import com.ieum.gw.dto.MailMessageDTO;
import com.ieum.gw.dto.OpsGuestInquiryDTO;
import com.ieum.gw.dto.OpsGuestReplyDTO;
import com.ieum.gw.dto.OpsGuestReplySaveDTO;

import lombok.RequiredArgsConstructor;

/**
 * 답변 메일의 DB 트랜잭션들. 메일 발송은 트랜잭션 밖이어야 해서 순서는 OpsReplyService가 정하고 저장만 여기로 뗐다.
 * 둘 다 접수 행을 잠근다: 운영자 두 명이 같은 접수에 동시에 보내거나 두 번 눌러도 하나씩 처리되어 메일은 한 통이다.
 */
@Service
@RequiredArgsConstructor
public class OpsReplyRecordService {

    private final OpsDAO dao;
    private final MailService mailService;

    /**
     * 답변과 발송 기록을 함께 저장한다. 돌아오면 커밋이 끝난 뒤이고, 부른 쪽이 돌려받은 메일을 보낸다.
     *
     * @param lastReplyId 운영자가 화면에서 본 가장 최근 답변. 그사이 다른 답변이 생겼으면 거절한다(두 번 누른 요청도 여기서 걸린다)
     * @throws OpsReplyException 409 그사이 다른 답변이 있거나 보내는 중일 때
     * @throws OpsNotFoundException 없는 접수번호일 때
     */
    @Transactional
    public MailMessageDTO saveReply(Long no, String body, Long lastReplyId, Long operatorAccountId) {
        OpsGuestInquiryDTO inquiry = lock(no);
        OpsGuestReplyDTO latest = latestReply(no);
        if (!Objects.equals(latest == null ? null : latest.getId(), lastReplyId)) {
            throw new OpsReplyException(409, "그사이 다른 답변이 있습니다. 목록을 새로 받았으니 확인한 뒤 다시 보내 주세요.");
        }
        if (latest != null && MailStatus.MAIL_PENDING.name().equals(latest.getStatus()) && !latest.isStale()) {
            throw new OpsReplyException(409, "이 접수의 답변을 보내는 중입니다. 잠시 뒤 다시 시도해 주세요.");
        }

        OpsGuestReplySaveDTO reply = new OpsGuestReplySaveDTO();
        reply.setNo(no);
        reply.setOperatorAccountId(operatorAccountId);
        reply.setBody(body);
        dao.insertGuestReply(reply);

        MailMessageDTO mail = replyMail(inquiry, reply.getReplyId(), body);
        mail.setDedupeKey("inquiry-reply:" + reply.getReplyId());
        mailService.record(mail);
        return mail;
    }

    /**
     * 가장 최근 답변의 발송 기록을 다시 보낼 수 있게 되돌린다. 돌아오면 커밋이 끝난 뒤이고, 부른 쪽이 돌려받은 메일을 보낸다.
     * 가장 최근 것만 된다: 뒤에 보낸 답변이 있는데 앞선 답변이 뒤늦게 나가면 받는 사람이 순서를 헷갈린다.
     *
     * @throws OpsReplyException 409 가장 최근 답변이 아니거나, 이미 보냈거나 보내는 중일 때
     * @throws OpsNotFoundException 없는 접수번호일 때
     */
    @Transactional
    public MailMessageDTO claimResend(Long no, Long replyId) {
        OpsGuestInquiryDTO inquiry = lock(no);
        OpsGuestReplyDTO latest = latestReply(no);
        if (latest == null || !latest.getId().equals(replyId)) {
            throw new OpsReplyException(409, "가장 최근 답변만 다시 보낼 수 있습니다. 목록을 새로 받았으니 확인해 주세요.");
        }
        if (!mailService.claimRetry(latest.getMailOutboxId())) {
            throw new OpsReplyException(409, "이미 보냈거나 보내는 중인 답변입니다. 목록을 새로 받았으니 확인해 주세요.");
        }
        MailMessageDTO mail = replyMail(inquiry, latest.getId(), latest.getBody());
        mail.setMailOutboxId(latest.getMailOutboxId());
        return mail;
    }

    private OpsGuestInquiryDTO lock(Long no) {
        OpsGuestInquiryDTO inquiry = dao.lockGuestInquiry(no);
        if (inquiry == null) {
            throw new OpsNotFoundException("접수를 찾을 수 없습니다.");
        }
        return inquiry;
    }

    private OpsGuestReplyDTO latestReply(Long no) {
        List<OpsGuestReplyDTO> replies = dao.listGuestReplies(no, MailService.STALE_MINUTES);
        return replies.isEmpty() ? null : replies.get(replies.size() - 1);
    }

    private static MailMessageDTO replyMail(OpsGuestInquiryDTO inquiry, Long replyId, String body) {
        MailMessageDTO mail = new MailMessageDTO();
        mail.setMailType(MailType.MT_INQUIRY_REPLY);
        mail.setRecipientEmail(inquiry.getEmail());
        mail.setSubject("[이음] 문의 답변: " + orEmpty(inquiry.getTitle()));
        mail.setBody(replyBody(inquiry, body));
        mail.setPublicInquiryId(inquiry.getNo());
        mail.setPublicReplyId(replyId);
        return mail;
    }

    // 다시 보낼 때도 저장된 답변으로 같은 본문을 만든다(발송 기록에는 본문을 넣지 않는다)
    static String replyBody(OpsGuestInquiryDTO inquiry, String body) {
        return String.join("\n",
                "안녕하세요. 이음입니다. 보내 주신 문의에 답변드립니다.",
                "",
                body,
                "",
                "--------",
                "접수번호: " + inquiry.getNo(),
                "문의 제목: " + orEmpty(inquiry.getTitle()),
                "문의 내용:",
                inquiry.getBody());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
