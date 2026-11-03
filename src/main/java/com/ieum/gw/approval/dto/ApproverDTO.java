package com.ieum.gw.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class ApproverDTO {
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private String gradeName;
}
