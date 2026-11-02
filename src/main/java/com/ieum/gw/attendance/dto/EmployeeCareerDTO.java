package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 연차 계산에 쓰는 직원 정보: 현재 업체 입사일 (employee). 연차는 모두 15일이라 이전 경력은 읽지 않는다 */
@Getter
@Setter
public class EmployeeCareerDTO {
    private String hireDate;           // YYYY-MM-DD, 없으면 null
}
