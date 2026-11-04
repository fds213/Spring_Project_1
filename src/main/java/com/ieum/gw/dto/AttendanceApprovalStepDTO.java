package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 휴가 신청서의 결재선 한 칸 (approval_step + 결재자 이름). 연차 화면의 "1단계 OOO 결재 대기"·반려 사유에 쓴다 */
@Getter
@Setter
public class AttendanceApprovalStepDTO {
    private Long approvalDocumentId;
    private Integer stepNo;
    private String approverName;
    private String statusCode;  // WAITING, APPROVED, REJECTED
    private String opinion;
}
