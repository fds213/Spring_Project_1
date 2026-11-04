package com.ieum.gw.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 토스 Payment 객체에서 검증·저장에 쓰는 값만 받는다 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentDTO {
    private String paymentKey;
    private String orderId;
    private String status;      // DONE이어야 승인 완료다
    private String currency;    // KRW
    private Long totalAmount;
    private String approvedAt;  // "2026-11-02T14:03:22+09:00"
}
