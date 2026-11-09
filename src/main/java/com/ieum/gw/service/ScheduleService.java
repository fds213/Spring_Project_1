package com.ieum.gw.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.HolidayDTO;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dao.ScheduleDAO;
import com.ieum.gw.dto.ScheduleDTO;

import lombok.RequiredArgsConstructor;

/**
 * 일정 규칙을 맡는 Service.
 * - 다른 모듈에 주는 메서드(연동 계약, 10/30): addLeaveEvents. 메인 화면 건수: countTodaySchedules. 이름·인자·반환값을 바꿀 때는 부르는 담당(결재)과 먼저 맞춘다.
 * - 달력의 휴일은 AttendanceService.getHolidays로 읽는다.
 * 업체·작성자는 로그인 정보에서만 꺼낸다 (AGENTS.md)
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final int TITLE_MAX = 40;  // 제목 글자 수 (calendar.js와 같게)
    private static final int MEMO_MAX = 200;  // 메모 글자 수 (calendar.js와 같게)

    // 화면 구분 → DB 공개 범위 코드 (PERSONAL → SCOPE_PERSONAL, DEPT → SCOPE_DEPARTMENT, COMPANY → SCOPE_COMPANY)
    private static final String PERSONAL = "PERSONAL";
    private static final String DEPT = "DEPT";
    private static final String COMPANY = "COMPANY";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul"); // '오늘'을 정하는 기준 (DB 기본값과 같은 서울 시각)

    private final ScheduleDAO dao;
    private final AttendanceService attendanceService; // 휴일 목록 (근태 담당)

    // ---------- 다른 모듈에 주는 메서드 ----------

    /**
     * 휴가 최종 승인 때 기안자의 개인 휴가 일정(CALENDAR_EVENT, SCOPE_PERSONAL)을 휴가 하루마다 하나씩 만든다.
     * 결재 최종 승인 트랜잭션 안에서 부른다. LEAVE_REQUEST_DAY_ID 고유 제약이 있어 같은 날짜 행으로 두 번 만들어지지 않는다.
     * 같은 휴가 일자로 다시 불려도 이미 만든 일정은 건너뛴다 (두 번 승인 요청이 와도 일정은 하나).
     *
     * @param employeeId 휴가를 쓰는 사람(기안자, 일정 주인)
     * @param days       결재가 저장한 휴가 날짜 행(leaveRequestDayId가 채워져 있어야 한다)
     */
    @Transactional
    public void addLeaveEvents(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
        if (companyId == null || employeeId == null || days == null) {
            throw new IllegalArgumentException("휴가 일정을 만들 값이 비어 있습니다");
        }
        for (LeaveDayDTO day : days) {
            if (day.getLeaveRequestDayId() == null || day.getLeaveDate() == null) {
                throw new IllegalArgumentException("휴가 일자 ID와 날짜가 채워져 있어야 합니다");
            }
            LocalDate date = day.getLeaveDate();
            String type = day.getLeaveType();

            // 종일 연차는 그날 하루 종일, 오전 반차 09~13시, 오후 반차 14~18시
            String title;
            LocalDateTime startsAt;
            LocalDateTime endsAt;
            String allDayYn;
            if ("LV_AM".equals(type)) {
                title = "오전 반차";
                startsAt = date.atTime(9, 0);
                endsAt = date.atTime(13, 0);
                allDayYn = "N";
            } else if ("LV_PM".equals(type)) {
                title = "오후 반차";
                startsAt = date.atTime(14, 0);
                endsAt = date.atTime(18, 0);
                allDayYn = "N";
            } else if ("LV_FULL".equals(type)) {
                title = "연차";
                startsAt = date.atStartOfDay();
                endsAt = date.plusDays(1).atStartOfDay();
                allDayYn = "Y";
            } else {
                throw new IllegalArgumentException("알 수 없는 휴가 종류입니다: " + type);
            }

            dao.insertLeaveEvent(companyId, employeeId, day.getLeaveRequestDayId(), title, startsAt, endsAt, allDayYn);
        }
    }

    /**
     * 메인 화면 '오늘 일정' 카드의 건수 (홈 담당이 부른다).
     * 달력의 오늘 칸에 보이는 것과 같은 조건: 내 개인 일정 + 우리 부서 일정 + 전사 일정 + 내 휴가 일정.
     * '오늘'은 서울 시각 기준 0시부터 다음 날 0시 전까지.
     */
    public int countTodaySchedules(Long companyId, Long employeeId) {
        return countSchedulesOn(companyId, employeeId, LocalDate.now(SEOUL));
    }

    // 정한 날짜 하루의 일정 수 (테스트에서 날짜를 정해 부를 수 있게 따로 둔다)
    int countSchedulesOn(Long companyId, Long employeeId, LocalDate day) {
        if (companyId == null || employeeId == null || day == null) {
            throw new IllegalArgumentException("일정 수를 셀 값이 비어 있습니다");
        }
        return dao.countEvents(companyId, employeeId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    // ---------- 달력 화면 ----------

    // 한 달 치 달력 (calendar.js renderSchedule이 읽는 모양)
    // { month, items, holidays, leaves, scopes, dept }
    public Map<String, Object> getMonth(LoginUser user, String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month); // 예: 2026-11
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "달 값이 잘못되었습니다");
        }
        Long companyId = user.getCompanyId();
        LocalDateTime from = ym.atDay(1).atStartOfDay();          // 그달 1일 0시
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay(); // 다음 달 1일 0시 (이 시각은 빼고)

        // 1. 일정 + 각 일정의 수정·삭제 버튼 여부 (고칠 수 있는 사람과 지울 수 있는 사람은 같다)
        List<ScheduleDTO> items = dao.selectEvents(companyId, user.getEmployeeId(), from, to);
        for (ScheduleDTO s : items) {
            boolean can = canChange(user, s);
            s.setCanEdit(can);
            s.setCanDelete(can);
        }
        String myDept = dao.selectMyDeptName(companyId, user.getEmployeeId());

        // 2. 휴일 (근태 담당이 준다)
        List<Map<String, String>> holidays = new ArrayList<>();
        for (HolidayDTO h : attendanceService.getHolidays(companyId, ym.atDay(1), ym.atEndOfMonth())) {
            Map<String, String> m = new HashMap<>();
            m.put("date", h.getHolidayDate().toString());
            m.put("name", h.getHolidayName());
            holidays.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("month", ym.toString());
        result.put("items", items);
        result.put("holidays", holidays);
        result.put("leaves", dao.selectLeaveEvents(companyId, user.getEmployeeId(), from, to)); // 3. 내 연차
        result.put("scopes", writableScopes(user, myDept)); // 4. 이 사람이 쓸 수 있는 구분
        result.put("dept", myDept);                         // 내 부서 이름 (부서가 없으면 null → 부서 보기·선택지를 숨긴다)
        return result;
    }

    // 일정 추가. 성공하면 { date } → 화면이 그 날짜로 옮겨 보여 준다
    @Transactional
    public Map<String, Object> addSchedule(LoginUser user, String scope, String date, String title, String memo) {
        // 1. 구분: 이 사람이 쓸 수 있는 것인지 (개인은 누구나, 부서는 부서가 있는 사람, 전사는 업체 관리자·인사 담당)
        String myDept = dao.selectMyDeptName(user.getCompanyId(), user.getEmployeeId());
        if (scope == null || !writableScopes(user, myDept).contains(scope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 구분의 일정은 쓸 수 있는 권한이 없습니다");
        }

        // 2. 값 검사 (화면도 검사하지만 서버가 다시 본다)
        LocalDate day = checkDate(date);
        title = cleanTitle(title);
        memo = cleanMemo(memo);

        // 3. 저장: 하루짜리 종일 일정
        String scopeCode = COMPANY.equals(scope) ? "SCOPE_COMPANY" : DEPT.equals(scope) ? "SCOPE_DEPARTMENT" : "SCOPE_PERSONAL";
        dao.insertEvent(user.getCompanyId(), user.getEmployeeId(), scopeCode, title, memo.isEmpty() ? null : memo,
                day.atStartOfDay(), day.plusDays(1).atTime(LocalTime.MIDNIGHT));

        Map<String, Object> result = new HashMap<>();
        result.put("date", day.toString());
        return result;
    }

    // 일정 고치기: 날짜·제목·메모만 바꾼다 (구분은 그대로). 고칠 수 있는 사람은 지울 수 있는 사람과 같다.
    // 성공하면 { date } → 화면이 그 날짜로 옮겨 보여 준다
    @Transactional
    public Map<String, Object> updateSchedule(LoginUser user, Long id, String date, String title, String memo) {
        ScheduleDTO s = (id == null) ? null : dao.selectEvent(user.getCompanyId(), id);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 일정입니다");
        }
        if ("EV_LEAVE".equals(s.getEventType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "휴가 일정은 고칠 수 없습니다");
        }
        if (!canChange(user, s)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 일정만 고칠 수 있습니다");
        }

        LocalDate day = checkDate(date);
        title = cleanTitle(title);
        memo = cleanMemo(memo);
        int changed = dao.updateEvent(user.getCompanyId(), id, title, memo.isEmpty() ? null : memo,
                day.atStartOfDay(), day.plusDays(1).atTime(LocalTime.MIDNIGHT));
        if (changed == 0) { // 확인한 뒤 고치기 전에 다른 창·다른 사람이 지웠다
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 일정입니다");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", day.toString());
        return result;
    }

    // 날짜 값 (예: 2026-11-02)
    private LocalDate checkDate(String date) {
        LocalDate day;
        try {
            day = LocalDate.parse(date == null ? "" : date);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "날짜를 입력해 주세요");
        }
        return day;
    }

    // 제목: 앞뒤 빈칸을 빼고 1~40자
    private String cleanTitle(String title) {
        title = (title == null) ? "" : title.trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요");
        }
        if (title.length() > TITLE_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 " + TITLE_MAX + "자까지입니다");
        }
        return title;
    }

    // 메모: 앞뒤 빈칸을 빼고 200자까지 (비어도 된다)
    private String cleanMemo(String memo) {
        memo = (memo == null) ? "" : memo.trim();
        if (memo.length() > MEMO_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메모는 " + MEMO_MAX + "자까지입니다");
        }
        return memo;
    }

    // 일정 삭제: 개인·부서 일정은 쓴 본인만, 전사 일정은 쓴 본인 또는 업체 관리자. 결재에서 온 휴가 일정은 지울 수 없다
    @Transactional
    public Map<String, Object> deleteSchedule(LoginUser user, Long id) {
        ScheduleDTO s = (id == null) ? null : dao.selectEvent(user.getCompanyId(), id);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 일정입니다");
        }
        if ("EV_LEAVE".equals(s.getEventType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "휴가 일정은 지울 수 없습니다");
        }
        if (!canChange(user, s)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 일정만 삭제할 수 있습니다");
        }
        dao.deleteEvent(user.getCompanyId(), id);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    // ---------- 같이 쓰는 도구 ----------

    // 이 사람이 쓸 수 있는 구분: 개인은 누구나, 부서는 부서가 있는 사람 누구나(자기 부서), 전사는 업체 관리자·인사 담당
    private List<String> writableScopes(LoginUser user, String myDept) {
        List<String> scopes = new ArrayList<>();
        scopes.add(PERSONAL);
        if (myDept != null) {
            scopes.add(DEPT);
        }
        if (hasRole(user, "ROLE_ADMIN") || hasRole(user, "ROLE_HR")) {
            scopes.add(COMPANY);
        }
        return scopes;
    }

    // 수정·삭제 버튼 (휴가 일정은 아니어야 함)
    // - 개인 일정: 쓴 본인만. 관리자도 남의 개인 일정은 화면에 안 보이므로 번호만으로 고치거나 지우지 못하게 막는다
    // - 부서 일정: 쓴 본인만. 업체 관리자도 다른 부서 일정은 화면에 안 보이므로 같은 이유로 막는다
    // - 전사 일정: 쓴 본인 또는 업체 관리자
    private boolean canChange(LoginUser user, ScheduleDTO s) {
        if ("EV_LEAVE".equals(s.getEventType())) return false;
        boolean mine = user.getEmployeeId().equals(s.getOwnerId());
        if (COMPANY.equals(s.getScope())) return mine || hasRole(user, "ROLE_ADMIN");
        return mine;
    }

    private boolean hasRole(LoginUser user, String role) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }
}
