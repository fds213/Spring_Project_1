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

    // 임시저장 탭에서만 쓴다
    private String savedAt;              // 마지막으로 저장한 시각 (ISO 문자열)
    private List<String> line;           // 고른 결재자 번호 (아직 결재선을 만들기 전이라 입력값 그대로)
    private Long originalDocumentId;     // 재기안을 임시저장했을 때 원본 문서 번호
    @JsonIgnore
    private String content;              // 임시저장 입력값 JSON. 서버에서 풀어 위 칸에 담고 화면에는 보내지 않는다

    // 탭 분류용: 내 결재 차례인가 (SQL이 채운다)
    private boolean myTurn;


}
