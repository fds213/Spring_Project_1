package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.HolidayDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dao.ScheduleDAO;
import com.ieum.gw.dto.ScheduleDTO;
import com.ieum.gw.dto.ScheduleLeaveDTO;

/**
 * 업체 분리 (AGENTS.md '테스트'): 다른 업체의 일정 번호로 지우면 거절하고,
 * 달력 조회·일정 저장은 화면 값이 아니라 로그인한 사람의 업체로만 한다.
 * 가짜 DAO는 실제 SQL처럼 업체 ID가 맞을 때만 일정을 돌려준다.
 */
class ScheduleCompanyIsolationTest {

    private static final Long MY_COMPANY = 7L;
    private static final Long OTHER_COMPANY = 8L;
    private static final Long EVENT_ID = 300L; // 업체 7의 전사 일정

    /** 업체 7의 일정 한 건만 있는 가짜 DB. 넘어온 업체 ID와 바꾸는 요청을 적어 둔다 */
    static class FakeDAO extends ScheduleDAO {
        final List<Long> readCompanies = new ArrayList<>();
        final List<Long> insertCompanies = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        final List<Long> updated = new ArrayList<>();

        FakeDAO() {
            super(null);
        }

        @Override
        public List<ScheduleDTO> selectEvents(Long companyId, Long employeeId, LocalDateTime from, LocalDateTime to) {
            readCompanies.add(companyId);
            return new ArrayList<>();
        }

        @Override
        public List<ScheduleLeaveDTO> selectLeaveEvents(Long companyId, Long employeeId, LocalDateTime from, LocalDateTime to) {
            readCompanies.add(companyId);
            return new ArrayList<>();
        }

        @Override
        public ScheduleDTO selectEvent(Long companyId, Long eventId) {
            if (!MY_COMPANY.equals(companyId) || !EVENT_ID.equals(eventId)) return null;
            ScheduleDTO e = new ScheduleDTO();
            e.setId(eventId);
            e.setScope("COMPANY");
            e.setOwnerId(1L);
            return e;
        }

        @Override
        public int insertEvent(Long companyId, Long ownerId, String scopeCode, String title, String content,
                               LocalDateTime startsAt, LocalDateTime endsAt) {
            insertCompanies.add(companyId);
            return 1;
        }

        @Override
        public int deleteEvent(Long companyId, Long eventId) {
            deleted.add(eventId);
            return 1;
        }

        @Override
        public int updateEvent(Long companyId, Long eventId, String title, String content,
                               LocalDateTime startsAt, LocalDateTime endsAt) {
            updated.add(eventId);
            return 1;
        }

        @Override
        public String selectMyDeptName(Long companyId, Long employeeId) {
            readCompanies.add(companyId);
            return null;
        }
    }

    /** 휴일 목록만 쓰는 가짜 근태 서비스 (휴일은 없음, 물어본 업체를 적어 둔다) */
    static class FakeAttendance extends AttendanceService {
        final List<Long> holidayCompanies = new ArrayList<>();

        FakeAttendance() {
            super(null, null, null);
        }

        @Override
        public List<HolidayDTO> getHolidays(Long companyId, LocalDate fromDate, LocalDate toDate) {
            holidayCompanies.add(companyId);
            return new ArrayList<>();
        }
    }

    private static LoginUser admin(Long companyId) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole("ROLE_ADMIN");
        emp.setCompanyId(companyId);
        emp.setEmployeeId(1L); // 업체 7 일정의 작성자와 같은 번호여도 업체가 다르면 지울 수 없어야 한다
        return new LoginUser(emp);
    }

    @Test
    @DisplayName("다른 업체 관리자가 일정 번호로 지우면 404이고 지워지지 않는다 (사원 번호가 같아도)")
    void cannotDeleteOtherCompanyEvent() {
        FakeDAO dao = new FakeDAO();
        ScheduleService service = new ScheduleService(dao, null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.deleteSchedule(admin(OTHER_COMPANY), EVENT_ID));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals(List.of(), dao.deleted);
    }

    @Test
    @DisplayName("다른 업체 관리자가 일정 번호로 고치면 404이고 바뀌지 않는다")
    void cannotUpdateOtherCompanyEvent() {
        FakeDAO dao = new FakeDAO();
        ScheduleService service = new ScheduleService(dao, null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.updateSchedule(admin(OTHER_COMPANY), EVENT_ID, "2026-11-05", "바꿈", ""));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals(List.of(), dao.updated);
    }

    @Test
    @DisplayName("달력 조회는 로그인한 사람의 업체로만 일정·휴가·휴일을 읽는다")
    void monthReadsOnlyMyCompany() {
        FakeDAO dao = new FakeDAO();
        FakeAttendance attendance = new FakeAttendance();

        Map<String, Object> month = new ScheduleService(dao, attendance).getMonth(admin(OTHER_COMPANY), "2026-11");

        assertEquals("2026-11", month.get("month"));
        assertEquals(List.of(OTHER_COMPANY, OTHER_COMPANY, OTHER_COMPANY), dao.readCompanies); // 일정·내 부서·휴가
        assertEquals(List.of(OTHER_COMPANY), attendance.holidayCompanies);
    }

    @Test
    @DisplayName("일정 저장은 로그인한 사람의 업체로 들어간다")
    void addSavesToMyCompany() {
        FakeDAO dao = new FakeDAO();

        new ScheduleService(dao, null).addSchedule(admin(OTHER_COMPANY), "COMPANY", "2026-11-05", "전사 회의", "");

        assertEquals(List.of(OTHER_COMPANY), dao.insertCompanies);
    }
}
