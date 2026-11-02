package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 연차 계산에 쓰는 직원 입사일 (employee.hire_date). 행이 없으면 없는 직원, hireDate 가 비면 입사일 미입력 */
@Getter
@Setter
public class EmployeeHireDTO {
    private String hireDate;           // YYYY-MM-DD, 없으면 null
}
