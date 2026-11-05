package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 부서·직급 관리 목록 한 줄 */
@Getter
@Setter
public class EmployeeOrgItemDTO {
    private Long id;
    private String name;
    private int inUse;        // 사용 중 계정 수
    private int retired;      // 비활성 계정 수
    private int requests;     // 대기 중인 가입 요청 수
    private String type;      // 부서 형태 (부서만, 없으면 null = 일반)
    private String headName;  // 부서장 이름 (부서만)
}