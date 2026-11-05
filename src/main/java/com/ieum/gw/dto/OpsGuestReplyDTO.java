package com.ieum.gw.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 비회원 접수에 보낸 답변 한 건과 그 발송 결과 (PUBLIC_REPLY + MAIL_OUTBOX). 칸 이름은 화면 ops/inquiry.js가 읽는 그대로다 */
@Getter
@Setter
@ToString
public class OpsGuestReplyDTO {
    private Long id;          // 답변 번호(PUBLIC_REPLY_ID)
    private String at;        // 마지막 발송 시도 시각
    private String body;
    private String status;    // 메일 발송 상태 MAIL_PENDING·MAIL_SENT·MAIL_FAILED
    private String reason;    // 실패 사유. 없으면 null
    private boolean stale;    // 대기인데 결과를 남기지 못한 채 멈췄다. 메일이 나갔는지 알 수 없다

    @JsonIgnore
    private Long inquiryNo;
    @JsonIgnore
    private Long mailOutboxId;
}
