package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 최종 승인된 휴가 하루 (leave_request_day). leaveType = LV_FULL, LV_AM, LV_PM */
@Getter
@Setter
public class LeaveDayDTO {
    private String leaveDate; // YYYY-MM-DD
    private String leaveType;
}
