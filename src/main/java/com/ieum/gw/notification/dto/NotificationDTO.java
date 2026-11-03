package com.ieum.gw.notification.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 알림 목록 한 건(화면 JS). 날짜는 문자열 "2026-11-02T14:03:22"로 보낸다 */
@Getter
@Setter
@ToString
public class NotificationDTO {
    private Long notificationId;
    private String notificationType;   // NotificationType 이름
    private String title;              // 종류 이름(결재 요청 등)
    private String message;            // 문서·티켓 제목이 든 문장. 사용자가 쓴 제목이 들어 있으니 화면에서 이스케이프한다
    private Long approvalDocumentId;   // 결재 알림만
    private Long itTicketId;           // 티켓 알림만
    private String readAt;             // 안 읽었으면 null
    private String createdAt;
}
