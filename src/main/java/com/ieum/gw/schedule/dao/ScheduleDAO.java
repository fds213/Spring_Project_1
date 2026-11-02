package com.ieum.gw.schedule.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.schedule.dto.ScheduleDTO;
import com.ieum.gw.schedule.dto.ScheduleLeaveDTO;
import com.ieum.gw.schedule.mapper.ScheduleMapper;

import lombok.RequiredArgsConstructor;

// 일정 DAO: ScheduleMapper를 감싸서 Service에 건네준다.
// Service는 Mapper를 직접 부르지 않고 이 DAO만 부른다 (AGENTS.md)
@Repository
@RequiredArgsConstructor
public class ScheduleDAO {

    private final ScheduleMapper mapper;

    // 기간 안의 일정 (내 개인 + 전사)
    public List<ScheduleDTO> selectEvents(Long companyId, Long employeeId, LocalDateTime from, LocalDateTime to) {
        return mapper.selectEvents(companyId, employeeId, from, to);
    }

    // 기간 안의 내 휴가 일정
    public List<ScheduleLeaveDTO> selectLeaveEvents(Long companyId, Long employeeId, LocalDateTime from, LocalDateTime to) {
        return mapper.selectLeaveEvents(companyId, employeeId, from, to);
    }

    // 일정 한 건 (없으면 null)
    public ScheduleDTO selectEvent(Long companyId, Long eventId) {
        return mapper.selectEvent(companyId, eventId);
    }

    // 일정 저장
    public int insertEvent(Long companyId, Long ownerId, String scopeCode, String title, String content,
                           LocalDateTime startsAt, LocalDateTime endsAt) {
        return mapper.insertEvent(companyId, ownerId, scopeCode, title, content, startsAt, endsAt);
    }

    // 일정 지우기 (성공하면 1)
    public int deleteEvent(Long companyId, Long eventId) {
        return mapper.deleteEvent(companyId, eventId);
    }

    // 휴가 일정 저장 (이미 있으면 0)
    public int insertLeaveEvent(Long companyId, Long ownerId, Long leaveRequestDayId, String title,
                                LocalDateTime startsAt, LocalDateTime endsAt, String allDayYn) {
        return mapper.insertLeaveEvent(companyId, ownerId, leaveRequestDayId, title, startsAt, endsAt, allDayYn);
    }
}
