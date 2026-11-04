package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

// 답변 입력(POST /ops/api/guest-inquiries/{no}/replies의 JSON 본문)
@Getter
@Setter
public class OpsReplyFormDTO {
    private String body;
    private Long lastReplyId; // 운영자가 화면에서 본 가장 최근 답변 번호. 아직 답변이 없으면 null
}
