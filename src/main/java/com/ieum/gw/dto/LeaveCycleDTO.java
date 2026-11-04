package com.ieum.gw.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 연차 부여 행 (leave_cycle). 직원마다 입사일에 15일 하나, 끝나는 날 없음 */
@Getter
@Setter
public class LeaveCycleDTO {
    private Long leaveCycleId;
    private Long companyId;
    private Long employeeId;
    private String startsOn;        // YYYY-MM-DD
    private String endsOn;          // YYYY-MM-DD (9999-12-31: 자동 부여가 없어 끝나지 않는다)
    private BigDecimal grantedDays;
    private BigDecimal ledgerDays;  // 사용(-)·복원(+) 내역 합 (조회에 따라 채운다)
}
