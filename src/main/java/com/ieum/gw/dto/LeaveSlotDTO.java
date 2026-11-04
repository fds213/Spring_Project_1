package com.ieum.gw.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 승인된 휴가가 차지한 시간대 (leave_slot). slotCode = SLOT_AM | SLOT_PM, 종일 연차는 두 줄 */
@Getter
@Setter
public class LeaveSlotDTO {
    private Long companyId;
    private Long employeeId;
    private LocalDate leaveDate;
    private String slotCode;
    private Long leaveRequestDayId;
}
