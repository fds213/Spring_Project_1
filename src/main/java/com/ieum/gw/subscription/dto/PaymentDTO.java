package com.ieum.gw.subscription.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** PAYMENT 한 행 (토스 승인 결과). 주문 하나에 한 행이다 */
@Getter
@Setter
@ToString
public class PaymentDTO {
    private Long paymentId;           // PAYMENT_ID       insert 전에는 null
    private Long companyId;           // COMPANY_ID
    private Long paymentOrderId;      // PAYMENT_ORDER_ID
    private String paymentKey;        // PAYMENT_KEY      토스가 준 결제 키. 실패 행은 비운다
    private String idempotencyKey;    // IDEMPOTENCY_KEY  승인 요청에 쓴 멱등키(주문 번호)
    private Long approvedAmount;      // APPROVED_AMOUNT
    private LocalDateTime approvedAt; // APPROVED_AT      토스 승인 시각(한국 시간)
    private String failureCode;       // FAILURE_CODE
    private String failureMessage;    // FAILURE_MESSAGE
}
