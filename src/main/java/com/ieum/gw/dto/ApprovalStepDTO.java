package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 결재선 저장용
@Getter
@Setter
@ToString
public class ApprovalStepDTO {
    private Long approvalDocumentId;
    private Long approverEmployeeId;
    private Long companyId;
    private Integer stepNo;
}
