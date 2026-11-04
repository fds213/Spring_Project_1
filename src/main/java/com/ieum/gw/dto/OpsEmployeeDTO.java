package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 업체 관리 상세의 계정 한 줄 */
@Getter
@Setter
@ToString
public class OpsEmployeeDTO {
    private String name;
    private String empNo;
    private String dept;        // 부서가 없으면 Service가 '부서 없음'으로 바꾼다
    private String status;      // USER_ACCOUNT.STATUS_CODE
    private String statusLabel; // 공통 코드 이름. 공통 코드에 없으면 코드값 그대로
}
