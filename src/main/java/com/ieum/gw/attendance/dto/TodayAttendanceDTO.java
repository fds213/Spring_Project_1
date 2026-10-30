package com.ieum.gw.attendance.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

// 오늘 출퇴근(ATTENDANCE 오늘 줄). 헤더의 todayPunch(layout.tag)가 쓴다.
@Getter
@Setter
@ToString
public class TodayAttendanceDTO {
    private LocalDateTime checkInAt;  // check_in_at
    private LocalDateTime checkOutAt; // check_out_at (퇴근 전에는 null)
}
