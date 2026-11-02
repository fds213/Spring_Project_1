package com.ieum.gw.attendance.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 연차 주기 (leave_cycle). 입사기념일마다 새로 부여한다 */
@Getter
@Setter
public class LeaveCycleDTO {
    private Long leaveCycleId;
    private Long companyId;
    private Long employeeId;
    private String startsOn;        // YYYY-MM-DD
    private String endsOn;          // YYYY-MM-DD (다음 부여일, 이 날은 포함하지 않는다)
    private Integer careerMonths;   // 부여 때 총 경력 개월
    private BigDecimal grantedDays;
    private BigDecimal ledgerDays;  // 사용(-)·복원(+) 내역 합 (조회에 따라 채운다)
}
