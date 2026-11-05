package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 인증 메일을 받을 업체와 그 관리자(메일에 업체 코드·사번을 적는다, ACC-01 세부)
@Getter
@Setter
@ToString
public class TrialVerifyTargetDTO {
    private Long companyId;
    private String companyCode;
    private String email;                // 신청 이메일(COMPANY.TRIAL_EMAIL)
    private LocalDateTime emailVerifiedAt;
    private Long userAccountId;          // 업체 관리자 계정
    private String employeeNo;           // 관리자 사번
}
