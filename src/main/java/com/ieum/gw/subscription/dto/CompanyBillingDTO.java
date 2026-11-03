package com.ieum.gw.subscription.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 헤더의 이용 상태 띠와 읽기 전용 표시에 쓰는 값(layout.tag의 companyBilling) */
@Getter
@Setter
@ToString
public class CompanyBillingDTO {
    private String status;  // TRIAL(체험 중) / PAID(유료) / EXPIRED(만료)
    private boolean paid;   // 지금(만료면 마지막) 기간이 유료면 true. 만료의 체험·유료를 이것으로 가른다
    private String endDate; // 마지막 날 "2026.11.08"(다음 날 0시에 만료)
    private long daysLeft;  // 마지막 날까지 남은 일수. 0이면 마지막 날이거나 이미 지났다
}
