package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;

/** 하루 근태 1건 (attendance). 날짜·시각은 DB가 서울 시각 문자열로 만들어 준다 */
@Getter
@Setter
public class AttendanceDTO {
    private Long attendanceId;
    private String workDate;  // YYYY-MM-DD
    private String checkIn;   // HH24:MI:SS
    private String checkOut;  // HH24:MI:SS, 퇴근 전이면 null
}
