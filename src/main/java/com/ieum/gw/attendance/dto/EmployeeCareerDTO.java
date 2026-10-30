package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 연차 계산에 쓰는 직원 정보: 현재 업체 입사일과 이전 경력 (employee) */
@Getter
@Setter
public class EmployeeCareerDTO {
    private String hireDate;           // YYYY-MM-DD, 없으면 null
    private Integer priorCareerMonths; // 이전 직장 경력 개월, 없으면 null
}
