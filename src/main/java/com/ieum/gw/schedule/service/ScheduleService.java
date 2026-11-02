package com.ieum.gw.schedule.service;

import com.ieum.gw.attendance.dto.LeaveDayDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 일정이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 달력의 휴일은 AttendanceService.getHolidays로 읽는다.
 */
@Service
public class ScheduleService {

    /**
     * 휴가 최종 승인 때 기안자의 개인 휴가 일정(CALENDAR_EVENT, SCOPE_PERSONAL)을 휴가 하루마다 하나씩 만든다.
     * 결재 최종 승인 트랜잭션 안에서 부른다. LEAVE_REQUEST_DAY_ID 고유 제약이 있어 같은 날짜 행으로 두 번 만들어지지 않는다.
     *
     * @param employeeId 휴가를 쓰는 사람(기안자, 일정 주인)
     * @param days       결재가 저장한 휴가 날짜 행(leaveRequestDayId가 채워져 있어야 한다)
     */
    public void addLeaveEvents(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
        // TODO(일정 담당): 구현. 구현 전에는 아무것도 하지 않는다(휴가 일정만 생기지 않는다).
    }
}
