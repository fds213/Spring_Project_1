package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 연차 계산에 쓰는 직원 입사일 (employee.hire_date). 행이 없으면 없는 직원, hireDate 가 비면 입사일 미입력 */
@Getter
@Setter
public class EmployeeHireDTO {
    private Long employeeId;           // 있는 직원이면 항상 값이 있다 (입사일이 빈 직원을 '없는 직원'과 구분한다)
    private String hireDate;           // YYYY-MM-DD, 없으면 null
    private String employmentStatus;   // ST_SERVING 등 (연차 추가 부여는 재직 중인 직원에게만)
}
