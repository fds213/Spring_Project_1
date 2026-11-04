package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 결제 실패 알림(POST /subscription/api/orders/fail). 토스가 failUrl에 붙여 준 값 그대로다 */
@Getter
@Setter
@ToString
public class FailFormDTO {
    private String orderId; // 구매자가 취소하면 토스가 주지 않는다
    private String code;
    private String message;
}
