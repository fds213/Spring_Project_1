package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.List;

// 결재함 문서 한 건 (화면 approval.js가 읽는 이름 그대로)
@Getter
@Setter
@ToString
public class ApprovalBoxDTO {
    private Long approvalDocumentId;
    private String documentType;
    private String title;
    private String statusCode;
    private String submittedAt;      // SQL에서 ISO 문자열로 만들어 받는다
    private String employeeName;
    private String departmentName;
    private BigDecimal requestedDays;
    private String branchName;
    private List<ApprovalBoxStepDTO> steps;
    private List<ApprovalBoxHistoryDTO> history;
    private ApprovalFieldsDTO fields;
    private String assetTypeName;
    private boolean canDecide;
    private boolean canRedraft;
    private boolean redrafted;
    private String rejectOpinion;
    @JsonIgnore
    private Long authorEmployeeId;
    private Long ticketId;           // 6단계 전까지 null
    private List<ApprovalFileDTO> files; // 첨부 목록
    
    // 탭 분류용: 내 결재 차례인가 (SQL이 채운다)
    private boolean myTurn;
    
    
}
