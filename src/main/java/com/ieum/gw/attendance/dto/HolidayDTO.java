package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

// 휴일(HOLIDAY 한 줄)
@Getter
@Setter
@ToString
public class HolidayDTO {
    private LocalDate holidayDate; // holiday_date
    private String holidayName;    // holiday_name
}
