package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 업체별 결제 집계 한 행 */
@Getter
@Setter
@ToString
public class OpsPaymentStatDTO {
    private Long companyId;
    private LocalDateTime paidAt; // 첫 승인 시각. 승인 건이 없으면 null
    private int failedPayments;
}
