package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 이용 결제 화면의 결제 이력 한 건과 승인 확인 결과(billing.js). 날짜는 문자열 "2026-11-02T14:03:22"로 보낸다 */
@Getter
@Setter
@ToString
public class BillingPaymentDTO {
    private String id;      // 주문 번호
    private String kind;    // FIRST(첫 결제)
    private String plan;    // MONTHLY
    private Integer months;
    private Integer users;  // 구매 인원
    private Long amount;
    private Integer pct;    // 할인율(%)
    private String at;      // 승인 시각
    private String from;    // 유료 기간 첫날. 반영 전이면 null
    private String to;      // 유료 기간 마지막 날(다음 날 0시에 만료)
    private String result;  // OK 또는 FAILED
}
