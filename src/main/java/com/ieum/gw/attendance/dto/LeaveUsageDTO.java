package com.ieum.gw.attendance.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 한 연차 주기 안의 휴가 일수: 최종 승인(사용)과 결재 중(승인 대기) */
@Getter
@Setter
public class LeaveUsageDTO {
    private BigDecimal usedDays;
    private BigDecimal pendingDays;
}
