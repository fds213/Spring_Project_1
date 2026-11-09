package com.ieum.gw.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 이음 장애 문의에 단 답변 한 건 (SUPPORT_REPLY). 칸 이름은 화면 JS가 읽는 그대로다 */
@Getter
@Setter
@ToString
public class SupportReplyDTO {
    private Long id;        // 답변 번호(SUPPORT_REPLY_ID)
    private String at;      // 쓴 시각
    private String editedAt; // 고친 시각. 고친 적이 없으면 null
    private String body;

    @JsonIgnore
    private Long inquiryNo;
}
