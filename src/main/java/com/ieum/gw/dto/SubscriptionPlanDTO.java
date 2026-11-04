package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** SUBSCRIPTION_PLAN 한 행 (요금제) */
@Getter
@Setter
@ToString
public class SubscriptionPlanDTO {
    private Long subscriptionPlanId; // SUBSCRIPTION_PLAN_ID
    private Integer durationMonths;  // DURATION_MONTHS     한 번에 사는 개월 수
    private Long monthlyUnitPrice;   // MONTHLY_UNIT_PRICE  1인 월 단가
}
