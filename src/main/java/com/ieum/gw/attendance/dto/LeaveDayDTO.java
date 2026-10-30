package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

// 휴가 하루(LEAVE_REQUEST_DAY 한 줄). 근태가 계산해 결재에 주고, 결재가 상신 때 저장한 뒤
// 행 ID를 채워 최종 승인 때 근태(useLeave)·일정(addLeaveEvents)에 넘긴다.
@Getter
@Setter
@ToString
public class LeaveDayDTO {
    private Long leaveRequestDayId; // leave_request_day_id (결재가 저장하기 전에는 null)
    private LocalDate leaveDate;    // leave_date
    private String leaveType;       // leave_type: LV_FULL(종일)·LV_AM(오전 반차)·LV_PM(오후 반차)
    private BigDecimal deductDays;  // deduct_days: LV_FULL 1, 반차 0.5
}
