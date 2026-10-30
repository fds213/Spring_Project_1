package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 업체 휴일 (holiday) */
@Getter
@Setter
public class HolidayDTO {
    private String holidayDate; // YYYY-MM-DD
    private String holidayName;
}
