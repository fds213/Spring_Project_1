package com.ieum.gw.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 휴가 신청서 1건 (approval_document + leave_request + leave_request_day 묶음) */
@Getter
@Setter
public class LeaveRequestDTO {
    private Long approvalDocumentId;
    private String leaveType;   // LV_FULL, LV_AM, LV_PM (여러 날이 섞이면 LV_FULL)
    private String fromDate;    // YYYY-MM-DD
    private String toDate;
    private BigDecimal days;
    private String reason;
    private String submittedAt; // YYYY-MM-DDTHH24:MI:SS
    private String statusCode;  // IN_PROGRESS, APPROVED, REJECTED
}
