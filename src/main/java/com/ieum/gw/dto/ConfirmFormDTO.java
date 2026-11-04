package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 결제 승인 확인 요청(POST /subscription/api/orders/confirm). 토스가 successUrl에 붙여 준 값 그대로다 */
@Getter
@Setter
@ToString
public class ConfirmFormDTO {
    private String orderId;
    private String paymentKey;
    private Long amount;
}
