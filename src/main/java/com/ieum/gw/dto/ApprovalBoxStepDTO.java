package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 결재함 표시용 결재선 한 단계
@Getter
@Setter
@ToString
public class ApprovalBoxStepDTO {
    private Long approvalDocumentId; // 문서별로 나눠 붙일 때만 쓴다
    private Long approverEmployeeId;
    private String employeeName;
    private String statusCode;
}
