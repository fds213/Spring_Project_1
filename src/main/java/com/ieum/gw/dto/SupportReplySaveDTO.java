package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

// 저장할 답변 1행(SUPPORT_REPLY)
@Getter
@Setter
public class SupportReplySaveDTO {
    private Long replyId;         // 저장할 때 시퀀스에서 뽑아 채운다
    private Long companyId;       // 문의한 업체. 운영자의 업체가 아니다
    private Long no;              // 접수번호(SUPPORT_INQUIRY_ID)
    private Long authorAccountId; // 로그인한 운영자
    private String body;
}
