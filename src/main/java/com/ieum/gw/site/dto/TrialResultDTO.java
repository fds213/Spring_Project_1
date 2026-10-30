package com.ieum.gw.site.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 무료 체험 신청 결과(신청 완료 화면에 보여 줄 값). 업체 코드·사번을 모르면 로그인할 수 없다(ACC-01 세부)
@Getter
@Setter
@ToString
public class TrialResultDTO {
    private String companyName;    // 업체명
    private String companyCode;    // 업체 코드 (예: ieum4827)
    private String employeeNo;     // 관리자 사번 (예: 20260001)
    private String email;          // 정리한 이메일(소문자)
    private int seats;             // 신청 인원
    private String trialStartDate; // 체험 첫날 "2026-10-30" (신청일)
    private String trialLastDate;  // 체험 마지막 날 "2026-11-12" (다음 날 0시에 만료)
}
