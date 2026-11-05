package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

// 저장할 답변 1행(PUBLIC_REPLY)
@Getter
@Setter
public class OpsGuestReplySaveDTO {
    private Long replyId;           // 저장할 때 시퀀스에서 뽑아 채운다
    private Long no;                // 접수번호(PUBLIC_INQUIRY_ID)
    private Long operatorAccountId; // 로그인한 운영자
    private String body;
}
