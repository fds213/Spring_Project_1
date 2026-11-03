package com.ieum.gw.approval.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ApprovalDecisionResultDTO {
    private String employeeName;
    private String documentType;
    private String statusCode;
    private String ticketId;
}
