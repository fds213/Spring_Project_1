package com.ieum.gw.subscription.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 결제 금액 미리 보기(GET /subscription/api/quote, billing.js). 실제 금액은 주문을 만들 때 서버가 다시 계산한다 */
@Getter
@Setter
@ToString
public class QuoteDTO {
    private String plan;    // MONTHLY
    private String mode;    // FIRST(첫 결제)
    private int users;
    private int months;
    private long amount;
    private String formula; // "3명 × 4,000원 × 1개월"
    private int pct;        // 할인율(%)
    private String why;     // 할인 설명
    private String from;    // 오늘 결제하면 시작하는 날
    private String to;      // 오늘 결제하면 마지막으로 쓰는 날
}
