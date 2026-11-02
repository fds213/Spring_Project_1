package com.ieum.gw.subscription.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

// 업체 이용 상태(SubscriptionService.getCompanyUsage의 결과). trial·expired 두 값으로 네 상태를 나눈다:
// 체험 중(trial, !expired), 유료(!trial, !expired), 체험 만료(trial, expired), 유료 만료(!trial, expired)
@Getter
@Setter
@ToString
public class CompanyUsageDTO {
    private boolean trial;         // 지금(만료면 마지막) 이용 기간이 체험(PD_TRIAL)이면 true, 유료(PD_PAID)면 false
    private boolean expired;       // 마지막 이용 기간이 끝났으면 true
    private LocalDateTime endsAt;  // 지금(만료면 마지막) 이용 기간이 끝나는 시각
    private boolean emailVerified; // 업체 이메일 인증을 마쳤는지(COMPANY.EMAIL_VERIFIED_AT)
}
