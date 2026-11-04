package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

// 휴가 상세
@Getter
@Setter
@ToString
public class ApprovalLeaveDTO {
    private Long companyId;
    private Long approvalDocumentId;
    private String reason;
    private BigDecimal requestedDays;
}
