package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 화면 공통 값 (layout.tag의 loginInfo). 헤더의 업체명·부서·직급, 사이드바의 부서장 여부 */
@Getter
@Setter
public class EmployeeLoginInfoDTO {
    private String companyName;
    private String deptName;       // 부서가 없으면 null
    private String rankName;       // 직급이 없으면 null
    private String headDeptName;   // 내가 부서장인 부서 이름. 부서장이 아니면 null
}