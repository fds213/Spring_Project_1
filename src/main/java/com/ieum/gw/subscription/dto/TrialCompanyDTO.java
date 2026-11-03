package com.ieum.gw.subscription.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 체험 신청으로 만든 업체와 체험 기간(SubscriptionService.createTrialCompany의 결과)
@Getter
@Setter
@ToString
public class TrialCompanyDTO {
    private Long companyId;         // company_id
    private String companyCode;     // company_code (신청 완료 화면에 보여 준다)
    private LocalDateTime startsAt; // 체험 시작 = 신청 시각
    private LocalDateTime endsAt;   // 체험 만료 = 신청일 + 14일 0시(마지막 날은 그 전날)
}
