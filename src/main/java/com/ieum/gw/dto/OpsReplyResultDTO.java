package com.ieum.gw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 답변 발송 결과. 화면은 inquiry로 목록의 그 줄을 바꾼다
@Getter
@AllArgsConstructor
public class OpsReplyResultDTO {
    private final OpsGuestInquiryDTO inquiry;
    private final boolean sent;
    private final String reason; // 실패 사유. 성공이면 null
}
