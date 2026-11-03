package com.ieum.gw.approval.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ApprovalFieldsDTO {
    private String leaveType;
    private String fromDate;
    private String toDate;
    private String reason;
}
