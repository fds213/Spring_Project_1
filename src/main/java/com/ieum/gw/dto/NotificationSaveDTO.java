package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 알림 1행 저장. 결재 알림이면 approvalDocumentId, 티켓 알림이면 itTicketId,
 * 근태 정정 알림이면 attendanceCorrectionId만 채운다(CK_69_01)
 */
@Getter
@Setter
@ToString
public class NotificationSaveDTO {
    private Long companyId;
    private Long receiverAccountId;
    private String notificationType;
    private String title;
    private String message;
    private Long approvalDocumentId;
    private Long itTicketId;
    private Long attendanceCorrectionId;
    private String dedupeKey; // 받는 계정 안에서 고유(UQ_69_01)
}
