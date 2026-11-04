package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 업체 조회 한 행 (COMPANY + 업체 관리자). 화면에는 OpsCompanyDTO·OpsApplicationDTO로 바꿔 보낸다 */
@Getter
@Setter
@ToString
public class OpsCompanyRowDTO {
    private Long companyId;
    private String companyCode;
    private String companyName;
    private String email;                  // 체험 신청 이메일(COMPANY.TRIAL_EMAIL)
    private LocalDateTime requestedAt;     // 체험 신청 시각
    private LocalDateTime emailVerifiedAt; // 이메일 인증 시각. 인증 전이면 null
    private String adminName;              // 관리자가 빠진 자료면 null
    private String adminNo;
}
