package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 만든 주문. 화면 JS가 이 값으로 토스 결제창을 연다 */
@Getter
@Setter
@ToString
public class OrderResultDTO {
    private String orderNo;   // 토스 orderId
    private String orderName; // 결제창에 보이는 이름
    private long amount;      // 서버가 계산한 금액
    private String clientKey; // 토스 클라이언트 키(브라우저에 보여도 되는 값)
}
