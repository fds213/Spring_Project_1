package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 주문 만들기 요청(POST /subscription/api/orders) */
@Getter
@Setter
@ToString
public class OrderFormDTO {
    private String plan;   // MONTHLY / ANNUAL
    private Integer users; // 구매 인원. 연장은 지금 인원과 같아야 한다
    private Long amount;   // 화면이 사용자에게 보여 준 금액. 서버 계산과 다르면 결제하지 않고 다시 보여 준다
}
