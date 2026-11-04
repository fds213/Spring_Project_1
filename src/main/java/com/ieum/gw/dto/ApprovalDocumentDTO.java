package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

//문서 저장용
@Getter
@Setter
@ToString
public class ApprovalDocumentDTO {
    private Long approvalDocumentId;
    private Long companyId;
    private Long authorEmployeeId;
    private String documentType;
    private String title;
    private String content;
    private String statusCode;
}
