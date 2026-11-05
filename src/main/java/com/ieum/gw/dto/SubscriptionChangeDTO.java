package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** SUBSCRIPTION_CHANGE 한 행 (결제로 이용 기간·인원이 어떻게 바뀌었는지). 승인·주문마다 한 행이다(고유 제약) */
@Getter
@Setter
@ToString
public class SubscriptionChangeDTO {
    private Long companyId;            // COMPANY_ID
    private Long paymentId;            // PAYMENT_ID
    private Long paymentOrderId;       // PAYMENT_ORDER_ID
    private Long servicePeriodId;      // SERVICE_PERIOD_ID  새로 만든 유료 기간
    private OrderPurpose changeType;   // CHANGE_TYPE        주문의 결제 목적
    private Integer beforeSeats;       // BEFORE_SEATS       이전 한도
    private Integer afterSeats;        // AFTER_SEATS        구매 인원
    private LocalDateTime beforeEndsAt; // BEFORE_ENDS_AT    이전 기간이 끝나는 시각
    private LocalDateTime afterEndsAt;  // AFTER_ENDS_AT
    private LocalDateTime appliedAt;    // APPLIED_AT
}
