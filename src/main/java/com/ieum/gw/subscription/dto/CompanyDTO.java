package com.ieum.gw.subscription.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** COMPANY 한 행 (체험 신청 때 만든다) */
@Getter
@Setter
@ToString
public class CompanyDTO {
    private Long companyId;                 // COMPANY_ID         insert 전에는 null
    private String companyCode;             // COMPANY_CODE       ieum + 4자리, 고유 제약
    private String companyName;             // COMPANY_NAME       입력값 그대로
    private String trialEmail;              // TRIAL_EMAIL        소문자·앞뒤 공백 없음(DB 검사 제약)
    private Integer requestedSeats;         // REQUESTED_SEATS    체험 신청 인원 1~10
    private LocalDateTime trialRequestedAt; // TRIAL_REQUESTED_AT 체험 기간 시작 시각과 같다
}
