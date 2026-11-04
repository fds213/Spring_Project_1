package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 이용 결제 화면에서 고를 수 있는 요금제 한 개(BillingDTO.plans) */
@Getter
@Setter
@ToString
public class BillingPlanDTO {

    private String plan;        // MONTHLY / ANNUAL
    private int months;         // 한 번에 사는 개월 수
    private int pct;            // 할인율(%)
    private long amountPerUser; // 1인 금액(개월 수·할인 포함)
}
