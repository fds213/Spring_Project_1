package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 사원 관리에서 사번으로 찾은 같은 업체 사원 1명 (부서장 지정·계정 켜기/끄기) */
@Getter
@Setter
public class EmployeeHeadTargetDTO {
    private Long employeeId;
    private String employeeName;
    private Long departmentId;     // 부서가 없으면 null
    private String departmentName;
    private String statusCode;     // 계정 상태 (ACTIVE·RETIRED 등)
    private String roleCode;       // 계정 역할 (업체 관리자는 끌 수 없다)
    private String employmentStatus; // 재직 상태 (퇴사자는 다시 사용할 수 없다)
    private boolean head;          // 지금 그 부서의 부서장인지
}