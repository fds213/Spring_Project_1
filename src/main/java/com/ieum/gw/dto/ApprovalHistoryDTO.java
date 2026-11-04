package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 이력 저장용
@Getter
@Setter
@ToString
public class ApprovalHistoryDTO {
    private Long approvalDocumentId;
    private Long actorAccountId;
    private Long companyId;
    private String actionCode;
    private String beforeStatus;
    private String afterStatus;
    private String opinion;
}
