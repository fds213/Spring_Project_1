package com.ieum.gw.schedule.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.schedule.dto.ScheduleDTO;
import com.ieum.gw.schedule.dto.ScheduleLeaveDTO;

// 일정 SQL을 부르는 창구. 메서드 이름 = ScheduleMapper.xml 의 id
@Mapper
public interface ScheduleMapper {

    // 기간 안의 일정: 내 개인 일정 + 우리 업체 전사 일정 (휴가 일정은 빼고)
    List<ScheduleDTO> selectEvents(@Param("companyId") Long companyId,
                                   @Param("employeeId") Long employeeId,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    // 기간 안의 내 휴가 일정 (연차·반차 구분은 휴가 신청 일자에서 가져온다)
    List<ScheduleLeaveDTO> selectLeaveEvents(@Param("companyId") Long companyId,
                                             @Param("employeeId") Long employeeId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    // 기간 안의 일정 수: 달력에 보이는 것과 같은 조건 (내 개인 일정 + 전사 일정 + 내 휴가 일정)
    int countEvents(@Param("companyId") Long companyId,
                    @Param("employeeId") Long employeeId,
                    @Param("from") LocalDateTime from,
                    @Param("to") LocalDateTime to);

    // 일정 한 건 (삭제 권한 확인용. 다른 업체 일정이면 null)
    ScheduleDTO selectEvent(@Param("companyId") Long companyId,
                            @Param("eventId") Long eventId);

    // 일정 저장
    int insertEvent(@Param("companyId") Long companyId,
                    @Param("ownerId") Long ownerId,
                    @Param("scopeCode") String scopeCode,
                    @Param("title") String title,
                    @Param("content") String content,
                    @Param("startsAt") LocalDateTime startsAt,
                    @Param("endsAt") LocalDateTime endsAt);

    // 일정 지우기 (휴가 일정은 지워지지 않는다)
    int deleteEvent(@Param("companyId") Long companyId,
                    @Param("eventId") Long eventId);

    // 휴가 일정 저장: 같은 휴가 일자(leaveRequestDayId)로 이미 만든 일정이 있으면 아무것도 안 한다 (0을 돌려준다)
    int insertLeaveEvent(@Param("companyId") Long companyId,
                         @Param("ownerId") Long ownerId,
                         @Param("leaveRequestDayId") Long leaveRequestDayId,
                         @Param("title") String title,
                         @Param("startsAt") LocalDateTime startsAt,
                         @Param("endsAt") LocalDateTime endsAt,
                         @Param("allDayYn") String allDayYn);
}
