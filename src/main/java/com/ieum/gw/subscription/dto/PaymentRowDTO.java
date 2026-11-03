package com.ieum.gw.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 결제 이력 조회 한 행 (PAYMENT + 주문 + 요금제 + 이용 기간). 화면에는 BillingPaymentDTO로 바꿔 보낸다 */
@Getter
@Setter
@ToString
public class PaymentRowDTO {
    private String orderNo;         // 주문 번호
    private String kind;            // FIRST(첫 결제) / EXTEND(연장)
    private String plan;            // MONTHLY / ANNUAL
    private Integer durationMonths;
    private Integer seatCount;
    private Long totalAmount;
    private BigDecimal discountRate; // 0~1
    private boolean approved;       // 승인 성공이면 true
    private LocalDateTime paidAt;   // 승인 시각. 실패 건은 기록한 시각
    private LocalDateTime startsAt; // 반영한 유료 기간. 반영 전이면 null
    private LocalDateTime endsAt;
    private String reason;          // 실패 사유(토스가 준 문구). 승인 건은 null. 운영자 화면에만 보인다
}
