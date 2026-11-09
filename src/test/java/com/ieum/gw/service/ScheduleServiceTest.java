package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dao.ScheduleDAO;
import com.ieum.gw.dto.ScheduleDTO;

/**
 * DB 없이 확인한다: DAO를 가짜로 바꿔 휴가 일정이 어떤 값으로 저장되는지, 누가 일정을 지울 수 있는지 본다.
 * 근태(AttendanceService)는 이 테스트에서 쓰지 않아 null로 넘긴다 (근태 쪽 생성자가 바뀌어도 이 테스트는 그대로 돈다).
 * 같은 휴가 일자로 두 번 만들어지지 않는 것은 SQL(NOT EXISTS)과 고유 제약 UQ_49_01이 막으므로 팀 DB에서 확인한다.
 */
class ScheduleServiceTest {

    /** 저장·삭제 요청을 받아 적어 두기만 하는 가짜 DAO */
    static class FakeDAO extends ScheduleDAO {
        final List<Object[]> leaveInserts = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        final List<Object[]> updates = new ArrayList<>();
        final List<String> insertScopes = new ArrayList<>();
        int updateResult = 1; // updateEvent가 돌려줄 바뀐 줄 수 (0이면 그사이 지워진 일정)
        String myDept = "개발팀"; // selectMyDeptName이 돌려줄 내 부서 (null이면 부서 없음)
        ScheduleDTO event; // selectEvent가 돌려줄 일정
        LocalDateTime countFrom, countTo; // countEvents에 넘어온 기간

        FakeDAO() {
            super(null);
        }

        @Override
        public int insertLeaveEvent(Long companyId, Long ownerId, Long leaveRequestDayId, String title,
                                    LocalDateTime startsAt, LocalDateTime endsAt, String allDayYn) {
            leaveInserts.add(new Object[] { companyId, ownerId, leaveRequestDayId, title, startsAt, endsAt, allDayYn });
            return 1;
        }

        @Override
        public int countEvents(Long companyId, Long employeeId, LocalDateTime from, LocalDateTime to) {
            countFrom = from;
            countTo = to;
            return 2;
        }

        @Override
        public ScheduleDTO selectEvent(Long companyId, Long eventId) {
            return event;
        }

        @Override
        public int deleteEvent(Long companyId, Long eventId) {
            deleted.add(eventId);
            return 1;
        }

        @Override
        public int updateEvent(Long companyId, Long eventId, String title, String content,
                               LocalDateTime startsAt, LocalDateTime endsAt) {
            updates.add(new Object[] { companyId, eventId, title, content, startsAt, endsAt });
            return updateResult;
        }

        @Override
        public int insertEvent(Long companyId, Long ownerId, String scopeCode, String title, String content,
                               LocalDateTime startsAt, LocalDateTime endsAt) {
            insertScopes.add(scopeCode);
            return 1;
        }

        @Override
        public String selectMyDeptName(Long companyId, Long employeeId) {
            return myDept;
        }
    }

    // 로그인한 사람 (업체 1)
    private static LoginUser user(long employeeId, String role) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(1L);
        emp.setEmployeeId(employeeId);
        emp.setRole(role);
        return new LoginUser(emp);
    }

    // 일정 한 건 (scope: PERSONAL·DEPT·COMPANY)
    private static ScheduleDTO event(long id, String scope, long ownerId) {
        ScheduleDTO e = new ScheduleDTO();
        e.setId(id);
        e.setScope(scope);
        e.setOwnerId(ownerId);
        return e;
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
        ScheduleService service = new ScheduleService(dao, null);

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
        ScheduleService service = new ScheduleService(dao, null);
        LeaveDayDTO noId = day(1L, "2026-11-05", "LV_FULL");
        noId.setLeaveRequestDayId(null);

        assertThrows(IllegalArgumentException.class, () -> service.addLeaveEvents(1L, 10L, List.of(noId)));
        assertEquals(0, dao.leaveInserts.size());
    }

    @Test
    @DisplayName("모르는 휴가 종류면 거절한다")
    void rejectsUnknownLeaveType() {
        ScheduleService service = new ScheduleService(new FakeDAO(), null);
        assertThrows(IllegalArgumentException.class,
                () -> service.addLeaveEvents(1L, 10L, List.of(day(1L, "2026-11-05", "LV_HOUR"))));
    }

    @Test
    @DisplayName("휴가 날짜 목록이 비어 있으면 아무것도 만들지 않는다")
    void emptyDaysMakesNothing() {
        FakeDAO dao = new FakeDAO();
        new ScheduleService(dao, null).addLeaveEvents(1L, 10L, List.of());
        assertEquals(0, dao.leaveInserts.size());
    }

    @Test
    @DisplayName("개인 일정은 쓴 본인만 지운다: 업체 관리자도 남의 개인 일정은 403")
    void personalEventOnlyOwnerDeletes() {
        FakeDAO dao = new FakeDAO();
        dao.event = event(5L, "PERSONAL", 10L);
        ScheduleService service = new ScheduleService(dao, null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.deleteSchedule(user(20L, "ROLE_ADMIN"), 5L));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals(0, dao.deleted.size());

        service.deleteSchedule(user(10L, "ROLE_EMP"), 5L); // 본인은 지울 수 있다
        assertEquals(List.of(5L), dao.deleted);
    }

    @Test
    @DisplayName("전사 일정은 쓴 본인 또는 업체 관리자가 지운다. 다른 직원은 403")
    void companyEventOwnerOrAdminDeletes() {
        FakeDAO dao = new FakeDAO();
        dao.event = event(7L, "COMPANY", 10L);
        ScheduleService service = new ScheduleService(dao, null);

        assertThrows(ResponseStatusException.class, () -> service.deleteSchedule(user(30L, "ROLE_HR"), 7L));
        service.deleteSchedule(user(20L, "ROLE_ADMIN"), 7L);
        assertTrue(dao.deleted.contains(7L));
    }

    @Test
    @DisplayName("부서 일정은 쓴 본인만 고치고 지운다: 업체 관리자도 남의 부서 일정은 403")
    void deptEventOnlyOwnerChanges() {
        FakeDAO dao = new FakeDAO();
        dao.event = event(8L, "DEPT", 10L);
        ScheduleService service = new ScheduleService(dao, null);

        assertThrows(ResponseStatusException.class, () -> service.deleteSchedule(user(20L, "ROLE_ADMIN"), 8L));
        assertThrows(ResponseStatusException.class,
                () -> service.updateSchedule(user(20L, "ROLE_ADMIN"), 8L, "2026-11-20", "회식", ""));
        assertEquals(0, dao.deleted.size());
        assertEquals(0, dao.updates.size());

        service.updateSchedule(user(10L, "ROLE_EMP"), 8L, "2026-11-20", "회식", "");
        assertEquals(1, dao.updates.size());
    }

    @Test
    @DisplayName("일정 수정: 날짜·제목·메모를 바꾸고, 그날 0시~다음 날 0시 종일 일정으로 저장한다")
    void updateSavesNewDayAndText() {
        FakeDAO dao = new FakeDAO();
        dao.event = event(5L, "PERSONAL", 10L);

        Map<String, Object> r = new ScheduleService(dao, null)
                .updateSchedule(user(10L, "ROLE_EMP"), 5L, "2026-11-30", "  병원  ", "  ");

        assertEquals("2026-11-30", r.get("date"));
        Object[] u = dao.updates.get(0);
        assertEquals(5L, u[1]);
        assertEquals("병원", u[2]);
        assertNull(u[3]); // 빈 메모는 비워 둔다
        assertEquals(LocalDateTime.of(2026, 11, 30, 0, 0), u[4]);
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), u[5]);
    }

    @Test
    @DisplayName("일정 수정: 없는 일정 404, 휴가 일정 403, 빈 제목 400")
    void updateRejects() {
        FakeDAO dao = new FakeDAO();
        ScheduleService service = new ScheduleService(dao, null);

        ResponseStatusException none = assertThrows(ResponseStatusException.class,
                () -> service.updateSchedule(user(10L, "ROLE_EMP"), 99L, "2026-11-30", "a", ""));
        assertEquals(HttpStatus.NOT_FOUND, none.getStatus());

        dao.event = event(6L, "PERSONAL", 10L);
        dao.event.setEventType("EV_LEAVE");
        ResponseStatusException leave = assertThrows(ResponseStatusException.class,
                () -> service.updateSchedule(user(10L, "ROLE_EMP"), 6L, "2026-11-30", "a", ""));
        assertEquals(HttpStatus.FORBIDDEN, leave.getStatus());

        dao.event = event(7L, "PERSONAL", 10L);
        ResponseStatusException blank = assertThrows(ResponseStatusException.class,
                () -> service.updateSchedule(user(10L, "ROLE_EMP"), 7L, "2026-11-30", "   ", ""));
        assertEquals(HttpStatus.BAD_REQUEST, blank.getStatus());
        assertEquals(0, dao.updates.size());
    }

    @Test
    @DisplayName("일정 수정: 확인한 뒤 고치기 전에 지워졌으면(바뀐 줄 0) 404")
    void updateDeletedMeanwhileIs404() {
        FakeDAO dao = new FakeDAO();
        dao.event = event(5L, "PERSONAL", 10L);
        dao.updateResult = 0;

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new ScheduleService(dao, null).updateSchedule(user(10L, "ROLE_EMP"), 5L, "2026-11-30", "병원", ""));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @DisplayName("부서 일정 추가: 부서가 있는 직원은 누구나 쓰고, 부서가 없으면 403")
    void deptAddNeedsDept() {
        FakeDAO dao = new FakeDAO();
        ScheduleService service = new ScheduleService(dao, null);

        service.addSchedule(user(10L, "ROLE_EMP"), "DEPT", "2026-11-20", "팀 회식", "");
        assertEquals(List.of("SCOPE_DEPARTMENT"), dao.insertScopes);

        dao.myDept = null;
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.addSchedule(user(10L, "ROLE_EMP"), "DEPT", "2026-11-20", "팀 회식", ""));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals(1, dao.insertScopes.size());
    }

    @Test
    @DisplayName("오늘 일정 수: 그날 0시부터 다음 날 0시 전까지를 센다")
    void countsOneDay() {
        FakeDAO dao = new FakeDAO();
        int n = new ScheduleService(dao, null).countSchedulesOn(1L, 10L, LocalDate.parse("2026-11-30"));

        assertEquals(2, n);
        assertEquals(LocalDateTime.of(2026, 11, 30, 0, 0), dao.countFrom);
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), dao.countTo); // 달이 넘어가도 다음 날 0시
    }
}
