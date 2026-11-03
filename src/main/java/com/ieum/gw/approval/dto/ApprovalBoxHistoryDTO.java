package com.ieum.gw.approval.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 결재함 표시용 처리 이력 한 줄
@Getter
@Setter
@ToString
public class ApprovalBoxHistoryDTO {
    private Long approvalDocumentId; // 문서별로 나눠 붙일 때만 쓴다
    private String createdAt;        // SQL에서 ISO 문자열로 만들어 받는다 (화면 whenFull이 new Date로 읽는다)
    private String employeeName;
    private String actionCode;
    private String opinion;
}
