package com.ieum.gw.schedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.service.AttendanceService;
import com.ieum.gw.schedule.dao.ScheduleDAO;

/**
 * DB 없이 확인한다: DAO를 가짜로 바꿔 휴가 일정이 어떤 값으로 저장되는지 본다.
 * 같은 휴가 일자로 두 번 만들어지지 않는 것은 SQL(NOT EXISTS)과 고유 제약 UQ_49_01이 막으므로 팀 DB에서 확인한다.
 */
class ScheduleServiceTest {

    /** 휴가 일정 저장 요청을 받아 적어 두기만 하는 가짜 DAO */
    static class FakeDAO extends ScheduleDAO {
        final List<Object[]> leaveInserts = new ArrayList<>();

        FakeDAO() {
            super(null);
        }

        @Override
        public int insertLeaveEvent(Long companyId, Long ownerId, Long leaveRequestDayId, String title,
                                    LocalDateTime startsAt, LocalDateTime endsAt, String allDayYn) {
            leaveInserts.add(new Object[] { companyId, ownerId, leaveRequestDayId, title, startsAt, endsAt, allDayYn });
            return 1;
        }
    }

    private static LeaveDayDTO day(long id, String date, String type) {
        LeaveDayDTO d = new LeaveDayDTO();
        d.setLeaveRequestDayId(id);
        d.setLeaveDate(LocalDate.parse(date));
        d.setLeaveType(type);
        return d;
    }

    @Test
    @DisplayName("휴가 최종 승인: 하루마다 일정 하나, 종일 연차는 그날 0시~다음 날 0시, 반차는 오전 9~13시·오후 14~18시")
    void addLeaveEventsMakesOnePerDay() {
        FakeDAO dao = new FakeDAO();
        ScheduleService service = new ScheduleService(dao, new AttendanceService());

        service.addLeaveEvents(1L, 10L, List.of(
                day(100L, "2026-11-05", "LV_FULL"),
                day(101L, "2026-11-06", "LV_AM"),
                day(102L, "2026-11-09", "LV_PM")));

        assertEquals(3, dao.leaveInserts.size());

        Object[] full = dao.leaveInserts.get(0);
        assertEquals(100L, full[2]);
        assertEquals("연차", full[3]);
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), full[4]);
        assertEquals(LocalDateTime.of(2026, 11, 6, 0, 0), full[5]);
        assertEquals("Y", full[6]);

        Object[] am = dao.leaveInserts.get(1);
        assertEquals("오전 반차", am[3]);
        assertEquals(LocalDateTime.of(2026, 11, 6, 9, 0), am[4]);
        assertEquals(LocalDateTime.of(2026, 11, 6, 13, 0), am[5]);
        assertEquals("N", am[6]);

        Object[] pm = dao.leaveInserts.get(2);
        assertEquals("오후 반차", pm[3]);
        assertEquals(LocalDateTime.of(2026, 11, 9, 14, 0), pm[4]);
        assertEquals(LocalDateTime.of(2026, 11, 9, 18, 0), pm[5]);
    }

    @Test
    @DisplayName("휴가 일자 ID가 비어 있으면 거절한다 (결재가 저장한 뒤 넘겨야 한다)")
    void rejectsDayWithoutId() {
        FakeDAO dao = new FakeDAO();
        ScheduleService service = new ScheduleService(dao, new AttendanceService());
        LeaveDayDTO noId = day(1L, "2026-11-05", "LV_FULL");
        noId.setLeaveRequestDayId(null);

        assertThrows(IllegalArgumentException.class, () -> service.addLeaveEvents(1L, 10L, List.of(noId)));
        assertEquals(0, dao.leaveInserts.size());
    }

    @Test
    @DisplayName("모르는 휴가 종류면 거절한다")
    void rejectsUnknownLeaveType() {
        ScheduleService service = new ScheduleService(new FakeDAO(), new AttendanceService());
        assertThrows(IllegalArgumentException.class,
                () -> service.addLeaveEvents(1L, 10L, List.of(day(1L, "2026-11-05", "LV_HOUR"))));
    }
}
