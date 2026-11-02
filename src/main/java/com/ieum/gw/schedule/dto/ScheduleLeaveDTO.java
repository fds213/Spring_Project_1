package com.ieum.gw.schedule.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 달력의 "내 연차" 하나. calendar.js가 읽는 모양: { from, to, kind }
// 휴가 일정은 하루마다 한 줄이라 from과 to는 같은 날이다
@Getter
@Setter
@ToString
public class ScheduleLeaveDTO {

    private String from;  // 시작 날짜 (예: 2026-11-05)
    private String to;    // 끝 날짜
    private String kind;  // FULL(연차)·AM(오전 반차)·PM(오후 반차)
}
