package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 부서장 지정·해제 대상 (사번으로 찾은 같은 업체 사원 1명) */
@Getter
@Setter
public class EmployeeHeadTargetDTO {
    private Long employeeId;
    private String employeeName;
    private Long departmentId;     // 부서가 없으면 null
    private String departmentName;
    private String statusCode;     // 계정 상태 (ACTIVE만 지정 가능)
    private boolean head;          // 지금 그 부서의 부서장인지
}