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
    private Long lastId;    // 그 안 읽은 알림 중 가장 큰 알림 번호. 화면에 보인 것까지만 읽음으로 바꿀 때 쓴다
}
