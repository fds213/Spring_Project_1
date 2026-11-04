package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

// 휴일(HOLIDAY 한 줄)
@Getter
@Setter
@ToString
public class HolidayDTO {
    private Long holidayId;        // holiday_id (휴일 관리 화면에서 지울 때)
    private LocalDate holidayDate; // holiday_date
    private String holidayName;    // holiday_name
}
