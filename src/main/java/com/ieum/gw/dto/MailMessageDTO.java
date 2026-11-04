package com.ieum.gw.dto;

import com.ieum.gw.code.MailType;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 보낼 메일 한 통. 본문은 DB에 넣지 않는다: 인증 링크처럼 그 자체가 비밀인 값이 들어갈 수 있다
@Getter
@Setter
@ToString
public class MailMessageDTO {
    private Long mailOutboxId;        // 기록하면 채워진다
    private MailType mailType;
    private String recipientEmail;
    private String subject;
    private String dedupeKey;         // 같은 메일을 두 번 기록하지 않게 하는 키(종류:대상 번호)

    @ToString.Exclude
    private String body;              // 일반 텍스트

    // 어떤 자료의 메일인지. 해당하는 칸만 채운다
    private Long companyId;
    private Long userAccountId;
    private Long emailVerificationId;
    private Long publicInquiryId;
    private Long publicReplyId;
}
