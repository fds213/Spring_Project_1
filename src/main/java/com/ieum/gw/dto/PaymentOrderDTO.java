package com.ieum.gw.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** PAYMENT_ORDER 한 행 (결제 주문). 결제창을 열기 전에 서버가 인원·금액을 정해 저장한다 */
@Getter
@Setter
@ToString
public class PaymentOrderDTO {
    private Long paymentOrderId;      // PAYMENT_ORDER_ID     insert 전에는 null
    private Long companyId;           // COMPANY_ID
    private String externalOrderNo;   // EXTERNAL_ORDER_NO    토스에 보내는 주문 번호(orderId), 고유 제약
    private Long buyerAccountId;      // BUYER_ACCOUNT_ID     결제한 계정
    private Long subscriptionPlanId;  // SUBSCRIPTION_PLAN_ID
    private OrderPurpose purposeCode; // PURPOSE_CODE         첫 결제 또는 연장
    private OrderStatus statusCode;   // STATUS_CODE
    private Integer seatCount;        // SEAT_COUNT           구매 인원
    private Long monthlyUnitPrice;    // MONTHLY_UNIT_PRICE   주문 때의 1인 월 단가
    private BigDecimal discountRate;  // DISCOUNT_RATE        할인 비율 0~1
    private Long totalAmount;         // TOTAL_AMOUNT         서버가 계산한 결제 금액
    private LocalDateTime pricedAt;   // PRICED_AT            금액을 계산한 시각
    private Integer durationMonths;   // SUBSCRIPTION_PLAN.DURATION_MONTHS (조회 때만)
}
