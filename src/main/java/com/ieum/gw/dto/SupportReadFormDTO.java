package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 직원이 문의를 펼쳤을 때 보내는 읽음 요청. upTo는 목록에서 받은 unreadUpTo(그때 보인 마지막 답변 알림 번호)다 */
@Getter
@Setter
public class SupportReadFormDTO {
    private Long upTo;
}
