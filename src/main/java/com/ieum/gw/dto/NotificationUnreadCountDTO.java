package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 대상(이음 장애 문의 등)별 안 읽은 알림 수 한 줄 */
@Getter
@Setter
@ToString
public class NotificationUnreadCountDTO {
    private Long targetId;  // 대상 번호(이음 문의 답변 알림이면 SUPPORT_INQUIRY_ID)
    private int unread;
}
