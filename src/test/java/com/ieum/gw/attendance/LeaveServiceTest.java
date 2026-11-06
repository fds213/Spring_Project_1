package com.ieum.gw.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dao.AttendanceDAO;
import com.ieum.gw.dao.LeaveDAO;
import com.ieum.gw.dto.AttendanceApprovalStepDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.EmployeeHireDTO;
import com.ieum.gw.dto.LeaveCycleDTO;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.dto.LeaveGrantDTO;
import com.ieum.gw.dto.LeaveRequestDTO;
import com.ieum.gw.dto.LeaveSlotDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.mapper.LeaveMapper;
import com.ieum.gw.service.AttendanceException;
import com.ieum.gw.service.HolidayService;
import com.ieum.gw.service.LeaveService;

/** 연차 연동 규칙 (결재가 부르는 날짜 나누기·남은 일수·겹침·차감·최초 부여, 요구분석서 3.9). 모든 직원 15일, 자동 부여 없음 */
class LeaveServiceTest {

    // 업체 10의 사원 102: 2024-03-04 입사, 연차 15일 (이전 경력은 연차에 반영하지 않는다)
    private static final long CO = 10L;
    private static final long EMP = 102L;
    private static final long ACTOR = 900L;

    private FakeLeaveMapper leaveMapper;
    private AttendanceServiceTest.FakeMapper attMapper;
    private LeaveService leave;
    private HolidayService holiday;

    @BeforeEach
    void setUp() {
        leaveMapper = new FakeLeaveMapper();
        attMapper = new AttendanceServiceTest.FakeMapper();
        AttendanceDAO attDao = new AttendanceDAO(attMapper);
        leave = new LeaveService(new LeaveDAO(leaveMapper), attDao);
        holiday = new HolidayService(attDao);
    }

    private void load2026Holidays() {
        holiday.addPreset(CO, 2026);
    }

    private static LeaveDayDTO day(String date, String type, long dayId) {
        LeaveDayDTO d = new LeaveDayDTO();
        d.setLeaveDate(LocalDate.parse(date));
        d.setLeaveType(type);
        d.setDeductDays("LV_FULL".equals(type) ? BigDecimal.ONE : new BigDecimal("0.5"));
        d.setLeaveRequestDayId(dayId);
        return d;
    }

    @Test
    @DisplayName("휴가 날짜 나누기: 주말·휴일은 빼고, 휴일 자료가 없는 해가 끼면 거절한다")
    void calculateLeaveDays() {
        IllegalArgumentException noData = assertThrows(IllegalArgumentException.class,
                () -> leave.calculateLeaveDays(CO, "LV_FULL", LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 28)));
        assertTrue(noData.getMessage().contains("2026년 휴일 자료"));

        load2026Holidays();
        // 9/23(수) · 9/24~26 추석 · 9/27(일) · 9/28(월) → 23일, 28일 이틀
        List<LeaveDayDTO> days = leave.calculateLeaveDays(CO, "LV_FULL", LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 28));
        assertEquals(List.of(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 28)),
                days.stream().map(LeaveDayDTO::getLeaveDate).collect(Collectors.toList()));
        assertEquals(BigDecimal.ONE, days.get(0).getDeductDays());

        List<LeaveDayDTO> half = leave.calculateLeaveDays(CO, "LV_AM", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));
        assertEquals(new BigDecimal("0.5"), half.get(0).getDeductDays());

        assertThrows(IllegalArgumentException.class, // 반차는 하루만
                () -> leave.calculateLeaveDays(CO, "LV_PM", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4)));
        assertThrows(IllegalArgumentException.class, // 주말만
                () -> leave.calculateLeaveDays(CO, "LV_FULL", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)));
        assertThrows(IllegalArgumentException.class, // 모르는 구분
                () -> leave.calculateLeaveDays(CO, "LV_X", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)));
    }

    @Test
    @DisplayName("부여 행이 없으면 15일이 남은 일수다 (가입 승인 전에 들어온 직원도 휴가를 낼 수 있다)")
    void remainingWithoutCycleRow() {
        assertEquals(BigDecimal.valueOf(15), leave.getRemainingLeaveDays(CO, EMP));
        assertEquals(BigDecimal.ZERO, leave.getRemainingLeaveDays(CO, 999L)); // 입사일 모름
    }

    @Test
    @DisplayName("차감: 부여 행을 만들고 시간대·사용 내역을 남긴다. 남은 일수와 연차 화면이 같은 값을 본다")
    void useLeave() {
        leave.useLeave(CO, EMP, ACTOR, List.of(day("2026-11-03", "LV_FULL", 1), day("2026-11-04", "LV_AM", 2)));

        assertEquals(1, leaveMapper.cycles.size());
        assertEquals(3, leaveMapper.slots.size()); // 종일 2 + 오전 1
        assertEquals(new BigDecimal("13.5"), leave.getRemainingLeaveDays(CO, EMP));

        @SuppressWarnings("unchecked")
        Map<String, Object> sum = (Map<String, Object>) leave.leaves(CO, EMP).get("summary");
        assertEquals(new BigDecimal("1.5"), sum.get("usedDays"));
        assertEquals(new BigDecimal("13.5"), sum.get("left"));
        assertEquals(BigDecimal.valueOf(15), sum.get("grant"));
    }

    @Test
    @DisplayName("겹침: 승인된 오전 반차가 있는 날에 종일·오전은 겹치고 오후는 안 겹친다. 겹치면 차감하지 않는다")
    void overlap() {
        leave.useLeave(CO, EMP, ACTOR, List.of(day("2026-11-05", "LV_AM", 10)));
        assertTrue(leave.hasApprovedLeaveOverlap(CO, EMP, List.of(day("2026-11-05", "LV_FULL", 11))));
        assertTrue(leave.hasApprovedLeaveOverlap(CO, EMP, List.of(day("2026-11-05", "LV_AM", 11))));
        assertFalse(leave.hasApprovedLeaveOverlap(CO, EMP, List.of(day("2026-11-05", "LV_PM", 11))));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> leave.useLeave(CO, EMP, ACTOR, List.of(day("2026-11-05", "LV_FULL", 12))));
        assertTrue(e.getMessage().contains("2026-11-05 오전"));
        assertEquals(1, leaveMapper.ledger.size());

        leave.useLeave(CO, EMP, ACTOR, List.of(day("2026-11-05", "LV_PM", 13))); // 오후는 된다
        assertEquals(2, leaveMapper.ledger.size());
    }

    @Test
    @DisplayName("남은 일수보다 많이 차감하려 하면 IllegalStateException, 아무것도 남기지 않는다")
    void shortage() {
        List<LeaveDayDTO> many = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 11, 2);
        for (int i = 0; i < 18; i++, d = d.plusDays(1)) many.add(day(d.toString(), "LV_FULL", 100 + i));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> leave.useLeave(CO, EMP, ACTOR, many));
        assertTrue(e.getMessage().contains("남은 15일, 신청 18일"));
        assertEquals(0, leaveMapper.ledger.size());
        assertEquals(0, leaveMapper.slots.size());
    }

    @Test
    @DisplayName("입사기념일이 지나도 새로 부여하지 않는다: 기념일 앞뒤 휴가도 같은 15일에서 차감한다")
    void noAnniversaryGrant() {
        leave.useLeave(CO, EMP, ACTOR, List.of(day("2027-03-03", "LV_FULL", 1), day("2027-03-04", "LV_FULL", 2), day("2027-03-05", "LV_FULL", 3)));
        assertEquals(1, leaveMapper.cycles.size());
        LeaveCycleDTO row = leaveMapper.cycles.get(0);
        assertEquals("2024-03-04", row.getStartsOn());
        assertEquals("9999-12-31", row.getEndsOn());
        assertEquals(BigDecimal.valueOf(15), row.getGrantedDays());
        assertEquals(BigDecimal.valueOf(12), leave.getRemainingLeaveDays(CO, EMP));
    }

    @Test
    @DisplayName("입사일 전 날짜는 차감하지 않는다")
    void beforeHire() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> leave.useLeave(CO, EMP, ACTOR, List.of(day("2024-03-01", "LV_FULL", 1))));
        assertTrue(e.getMessage().contains("입사일"));
        assertEquals(0, leaveMapper.ledger.size());
    }

    @Test
    @DisplayName("근태·연차 관리의 남은 연차: 저장된 부여 행은 부여+사용 합, 없으면 15일, 입사일이 없으면 비운다")
    void remainingOf() {
        LeaveCycleDTO saved = new LeaveCycleDTO();
        saved.setStartsOn("2024-03-04");
        saved.setEndsOn("9999-12-31");
        saved.setGrantedDays(BigDecimal.valueOf(15));
        saved.setLedgerDays(new BigDecimal("-2.5"));
        assertEquals(new BigDecimal("12.5"), LeaveService.remainingOf(saved, "2024-03-04"));
        assertEquals(BigDecimal.valueOf(15), LeaveService.remainingOf(null, "2024-03-04"));
        assertEquals(null, LeaveService.remainingOf(null, null));
    }

    @Test
    @DisplayName("관리 표의 남은 연차는 연차 화면과 같은 부여 행(입사일이 들어 있는 행)을 쓴다: 기간이 끝난 예전 행이어도 기본 15일로 보이지 않는다")
    void grantsAtHireMatchesLeaveScreen() {
        // 예전 방식으로 만든 행: 입사일부터 1년, 오늘은 이미 지났다. 하루 썼다
        LeaveCycleDTO old = new LeaveCycleDTO();
        old.setCompanyId(CO);
        old.setEmployeeId(EMP);
        old.setStartsOn("2024-03-04");
        old.setEndsOn("2025-03-04");
        old.setGrantedDays(BigDecimal.valueOf(15));
        leaveMapper.insertCycle(old);
        leaveMapper.insertLedgerUse(CO, old.getLeaveCycleId(), 1L, new BigDecimal("-1"), ACTOR);

        LeaveCycleDTO row = leave.grantsAtHire(CO).get(EMP);
        assertEquals(old.getLeaveCycleId(), row.getLeaveCycleId());
        assertEquals(0, new BigDecimal("14").compareTo(LeaveService.remainingOf(row, "2024-03-04")));
        assertEquals(0, leave.getRemainingLeaveDays(CO, EMP).compareTo(LeaveService.remainingOf(row, "2024-03-04")));
    }

    @Test
    @DisplayName("결재가 저장하기 전의 날짜(행 ID 없음)는 차감하지 않는다")
    void needsSavedDays() {
        LeaveDayDTO unsaved = day("2026-11-03", "LV_FULL", 1);
        unsaved.setLeaveRequestDayId(null);
        assertThrows(IllegalArgumentException.class, () -> leave.useLeave(CO, EMP, ACTOR, List.of(unsaved)));
    }

    @Test
    @DisplayName("최초 부여: 입사일부터 15일 하나(끝나는 날 없음). 두 번 불러도, 나중에 차감해도 하나만")
    void grantInitialLeave() {
        leaveMapper.hires.put(200L, hire("2026-11-02"));
        leave.grantInitialLeave(CO, 200L, LocalDate.of(2026, 11, 2));
        leave.grantInitialLeave(CO, 200L, LocalDate.of(2026, 11, 2));
        leave.useLeave(CO, 200L, ACTOR, List.of(day("2027-05-03", "LV_FULL", 1)));
        List<LeaveCycleDTO> rows = leaveMapper.cycles.stream().filter(c -> c.getEmployeeId() == 200L).collect(Collectors.toList());
        assertEquals(1, rows.size());
        assertEquals("2026-11-02", rows.get(0).getStartsOn());
        assertEquals("9999-12-31", rows.get(0).getEndsOn());
        assertEquals(BigDecimal.valueOf(15), rows.get(0).getGrantedDays());
        assertEquals(BigDecimal.valueOf(14), leave.getRemainingLeaveDays(CO, 200L));
    }

    @Test
    @DisplayName("상신 잠금: 최종 승인(useLeave)과 같은 부여 행을 잠근다. 행이 이미 있으면 새로 만들지 않는다")
    void lockLeaveForSubmitSameRow() {
        leave.grantInitialLeave(CO, EMP, LocalDate.of(2024, 3, 4));
        leave.lockLeaveForSubmit(CO, EMP);
        leave.useLeave(CO, EMP, ACTOR, List.of(day("2026-11-03", "LV_FULL", 1)));
        assertEquals(1, leaveMapper.cycles.size());
        Long row = leaveMapper.cycles.get(0).getLeaveCycleId();
        assertEquals(List.of(row, row), leaveMapper.locked); // 상신 한 번, 차감 한 번 모두 그 행
    }

    @Test
    @DisplayName("상신 잠금: 부여 행이 없으면 15일·사용 0으로 만든 뒤 잠근다. 남은 일수는 그대로 15일")
    void lockLeaveForSubmitCreatesRow() {
        assertEquals(BigDecimal.valueOf(15), leave.getRemainingLeaveDays(CO, EMP));
        leave.lockLeaveForSubmit(CO, EMP);
        assertEquals(1, leaveMapper.cycles.size());
        assertEquals("2024-03-04", leaveMapper.cycles.get(0).getStartsOn());
        assertEquals(List.of(leaveMapper.cycles.get(0).getLeaveCycleId()), leaveMapper.locked);
        assertEquals(BigDecimal.valueOf(15), leave.getRemainingLeaveDays(CO, EMP));

        leave.lockLeaveForSubmit(CO, EMP); // 두 번째 상신: 행을 더 만들지 않고 같은 행을 잠근다
        assertEquals(1, leaveMapper.cycles.size());
        assertEquals(2, leaveMapper.locked.size());
    }

    @Test
    @DisplayName("상신 잠금: 입사일이 비었거나 사원이 없으면 잠그지도 만들지도 않는다 (남은 연차 0이라 상신이 거절된다)")
    void lockLeaveForSubmitWithoutHireDate() {
        leaveMapper.hires.put(103L, hire(null));
        leave.lockLeaveForSubmit(CO, 103L);
        leave.lockLeaveForSubmit(CO, 999L);
        assertTrue(leaveMapper.cycles.isEmpty());
        assertTrue(leaveMapper.locked.isEmpty());
    }

    @Test
    @DisplayName("상신 잠금은 호출한 트랜잭션 안에서만 쓴다(MANDATORY): 밖에서 부르면 잠금이 바로 풀려 한 줄로 서지 않는다")
    void lockLeaveForSubmitIsMandatory() throws NoSuchMethodException {
        Transactional tx = LeaveService.class.getMethod("lockLeaveForSubmit", Long.class, Long.class).getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }

    @Test
    @DisplayName("휴일 관리: 같은 날은 409, 공휴일 한 번에 넣기는 이미 있는 날을 건너뛴다")
    void holidays() {
        holiday.add(CO, "2026-01-01", "신정");
        assertEquals(409, assertThrows(com.ieum.gw.service.AttendanceException.class,
                () -> holiday.add(CO, "2026-01-01", "또 신정")).getStatus());
        Map<String, Object> r = holiday.addPreset(CO, 2026);
        assertEquals(1, r.get("skipped"));
        assertEquals(19, r.get("added"));
        assertEquals(404, assertThrows(com.ieum.gw.service.AttendanceException.class,
                () -> holiday.addPreset(CO, 2031)).getStatus());
    }

    @Test
    @DisplayName("휴일 넣기: 휴일 관리 화면에서 볼 수 있는 2000~2100년만 받는다 (범위 밖은 넣으면 화면에서 못 지운다)")
    void holidayYearRange() {
        for (String outside : List.of("1999-12-31", "2101-01-01")) {
            assertEquals(400, assertThrows(com.ieum.gw.service.AttendanceException.class,
                    () -> holiday.add(CO, outside, "범위 밖")).getStatus());
        }
        holiday.add(CO, "2000-01-01", "처음 해");
        holiday.add(CO, "2100-12-31", "마지막 해");
        assertEquals(1, ((List<?>) holiday.list(CO, 2000).get("items")).size());
        assertEquals(1, ((List<?>) holiday.list(CO, 2100).get("items")).size());
    }

    @Test
    @DisplayName("연차 화면: 입사일이 빈 직원과 없는 직원의 안내를 가른다")
    void leavesWithoutHireDate() {
        EmployeeHireDTO hireless = hire(null);
        hireless.setEmployeeId(103L); // 실제 매퍼처럼 employee_id 만 차 있다 (LeaveMapper.xml selectEmployeeHire)
        leaveMapper.hires.put(103L, hireless);
        Map<String, Object> noHire = leave.leaves(CO, 103L);
        assertNull(noHire.get("summary"));
        assertEquals("NO_HIRE_DATE", noHire.get("reason"));
        assertEquals("NO_EMPLOYEE", leave.leaves(CO, 999L).get("reason"));
    }

    // ===== 연차 추가 부여 (기능명세서 '연차 세부', 팀장 11/3 결정) =====

    /** 같은 업체(CO)의 로그인 사용자. 계정 번호는 사원 번호 + 1000 */
    private static LoginUser user(String role, long employeeId) {
        AuthEmpDTO a = new AuthEmpDTO();
        a.setCompanyId(CO);
        a.setEmployeeId(employeeId);
        a.setUserAccountId(employeeId + 1000);
        a.setRole(role);
        return new LoginUser(a);
    }

    private static final LoginUser HR = user("ROLE_HR", 500L);

    /** 연차 화면의 요약 (보유·승인 완료·남은 일수) */
    @SuppressWarnings("unchecked")
    private Map<String, Object> summary(long employeeId) {
        return (Map<String, Object>) leave.leaves(CO, employeeId).get("summary");
    }

    /** 거절된 요청의 상태 코드 */
    private static int status(Runnable r) {
        return assertThrows(AttendanceException.class, r::run).getStatus();
    }

    @Test
    @DisplayName("추가 부여: 보유·승인 완료·남은 일수를 나눠 센다 (+5 → 20·0·20, 3일 사용 → 20·3·17, -2 되돌리기 → 18·3·15)")
    void grantSplitsTotals() {
        leave.grantExtraLeave(HR, EMP, new BigDecimal("5"), "프로젝트 보상");
        Map<String, Object> s = summary(EMP);
        assertEquals(0, new BigDecimal("20").compareTo((BigDecimal) s.get("grant")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) s.get("usedDays")));
        assertEquals(0, new BigDecimal("20").compareTo((BigDecimal) s.get("left")));
        assertEquals(0, new BigDecimal("5").compareTo((BigDecimal) s.get("extraDays")));

        leave.useLeave(CO, EMP, ACTOR, List.of(day("2026-11-02", "LV_FULL", 1), day("2026-11-03", "LV_FULL", 2), day("2026-11-04", "LV_FULL", 3)));
        s = summary(EMP);
        assertEquals(0, new BigDecimal("20").compareTo((BigDecimal) s.get("grant")));
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) s.get("usedDays")));
        assertEquals(0, new BigDecimal("17").compareTo((BigDecimal) s.get("left")));

        leave.grantExtraLeave(HR, EMP, new BigDecimal("-2"), "잘못 준 2일 되돌림");
        s = summary(EMP);
        assertEquals(0, new BigDecimal("18").compareTo((BigDecimal) s.get("grant")));
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) s.get("usedDays")));
        assertEquals(0, new BigDecimal("15").compareTo((BigDecimal) s.get("left")));
        assertEquals(0, new BigDecimal("15").compareTo(leave.getRemainingLeaveDays(CO, EMP)));
    }

    @Test
    @DisplayName("추가 부여: 부여 행이 없던 직원은 행을 만들어 잠근 뒤 쓰고, 휴가 승인과 같은 행을 잠근다")
    void grantCreatesAndLocksRow() {
        Map<String, Object> r = leave.grantExtraLeave(HR, EMP, new BigDecimal("1.5"), "포상");
        assertEquals(1, leaveMapper.cycles.size());
        assertEquals(List.of(leaveMapper.cycles.get(0).getLeaveCycleId()), leaveMapper.locked);
        assertEquals(0, new BigDecimal("16.5").compareTo((BigDecimal) r.get("left")));
        assertEquals("포상", leaveMapper.grants.get(0).getNote());
        assertEquals(Long.valueOf(1500L), leaveMapper.grantActor.get(0)); // 처리한 사람의 계정 번호
    }

    @Test
    @DisplayName("추가 부여 권한: 업체 관리자·인사 담당만, 본인은 안 된다 (403)")
    void grantPermission() {
        assertEquals(403, status(() -> leave.grantExtraLeave(user("ROLE_EMP", 501L), EMP, BigDecimal.ONE, "사유")));
        assertEquals(403, status(() -> leave.grantExtraLeave(user("ROLE_IT", 502L), EMP, BigDecimal.ONE, "사유")));
        assertEquals(403, status(() -> leave.grantExtraLeave(user("ROLE_ADMIN", EMP), EMP, BigDecimal.ONE, "본인")));
        assertEquals(403, status(() -> leave.grantExtraLeave(user("ROLE_HR", EMP), EMP, new BigDecimal("-1"), "본인 되돌리기")));
        leave.grantExtraLeave(user("ROLE_ADMIN", 503L), EMP, BigDecimal.ONE, "관리자");
        leave.grantExtraLeave(HR, EMP, BigDecimal.ONE, "인사 담당");
        assertEquals(2, leaveMapper.grants.size());
    }

    @Test
    @DisplayName("추가 부여 값: 0일·0.5일 단위 밖·30일 넘음·사유 없음·500자 넘음은 400")
    void grantValues() {
        assertEquals(400, status(() -> leave.grantExtraLeave(HR, EMP, null, "사유")));
        assertEquals(400, status(() -> leave.grantExtraLeave(HR, EMP, BigDecimal.ZERO, "사유")));
        assertEquals(400, status(() -> leave.grantExtraLeave(HR, EMP, new BigDecimal("0.3"), "사유")));
        assertEquals(400, status(() -> leave.grantExtraLeave(HR, EMP, new BigDecimal("30.5"), "사유")));
        assertEquals(400, status(() -> leave.grantExtraLeave(HR, EMP, BigDecimal.ONE, "  ")));
        assertEquals(400, status(() -> leave.grantExtraLeave(HR, EMP, BigDecimal.ONE, "가".repeat(501))));
        assertEquals(400, status(() -> leave.grantExtraLeave(HR, null, BigDecimal.ONE, "사유")));
        assertTrue(leaveMapper.grants.isEmpty());
        leave.grantExtraLeave(HR, EMP, new BigDecimal("-0.5"), "가".repeat(500));
        assertEquals(1, leaveMapper.grants.size());
    }

    @Test
    @DisplayName("추가 부여 대상: 없는 직원 404, 퇴사자·입사일 없음 409, 되돌려 남은 연차가 0 아래로 가면 409")
    void grantTarget() {
        assertEquals(404, status(() -> leave.grantExtraLeave(HR, 999L, BigDecimal.ONE, "사유")));
        EmployeeHireDTO retired = hire("2020-01-01");
        retired.setEmploymentStatus("ST_RETIRED");
        leaveMapper.hires.put(104L, retired);
        assertEquals(409, status(() -> leave.grantExtraLeave(HR, 104L, BigDecimal.ONE, "사유")));
        leaveMapper.hires.put(103L, hire(null));
        assertEquals(409, status(() -> leave.grantExtraLeave(HR, 103L, BigDecimal.ONE, "사유")));

        AttendanceException tooMuch = assertThrows(AttendanceException.class,
                () -> leave.grantExtraLeave(HR, EMP, new BigDecimal("-15.5"), "사유"));
        assertEquals(409, tooMuch.getStatus());
        assertTrue(tooMuch.getMessage().contains("남은 15일"));
        leave.grantExtraLeave(HR, EMP, new BigDecimal("-15"), "모두 되돌림"); // 딱 0일까지는 된다
        assertEquals(0, BigDecimal.ZERO.compareTo(leave.getRemainingLeaveDays(CO, EMP)));
    }

    @Test
    @DisplayName("추가 부여 내역: 최근 순으로 일수·사유·처리자, 보유·남은 일수와 함께. 다른 업체·없는 직원은 404")
    void grantHistory() {
        leave.grantExtraLeave(HR, EMP, new BigDecimal("2"), "첫 부여");
        leave.grantExtraLeave(HR, EMP, new BigDecimal("-1"), "하루 되돌림");
        Map<String, Object> h = leave.grantHistory(CO, EMP);
        assertEquals(0, new BigDecimal("16").compareTo((BigDecimal) h.get("grant")));
        assertEquals(0, new BigDecimal("16").compareTo((BigDecimal) h.get("left")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) h.get("items");
        assertEquals(List.of("하루 되돌림", "첫 부여"), items.stream().map(m -> m.get("note")).collect(Collectors.toList()));
        assertEquals(0, new BigDecimal("-1").compareTo((BigDecimal) items.get(0).get("days")));
        assertEquals(404, status(() -> leave.grantHistory(CO, 999L)));
    }

    private static EmployeeHireDTO hire(String date) {
        EmployeeHireDTO e = new EmployeeHireDTO();
        e.setHireDate(date);
        e.setEmploymentStatus("ST_SERVING");
        return e;
    }

    /** 연차 테이블 메모리판: leave_cycle(UQ 사원+시작일), leave_slot(PK 사원+날짜+시간대), leave_ledger(UQ 날짜 행+종류) */
    static class FakeLeaveMapper implements LeaveMapper {
        final Map<Long, EmployeeHireDTO> hires = new HashMap<>(Map.of(EMP, hire("2024-03-04")));
        final List<LeaveCycleDTO> cycles = new ArrayList<>();
        final List<LeaveSlotDTO> slots = new ArrayList<>();
        final Map<Long, BigDecimal> ledger = new HashMap<>(); // leave_request_day_id → delta (사용 내역 한 줄)
        final Map<Long, Long> ledgerCycle = new HashMap<>();  // leave_request_day_id → leave_cycle_id
        long seq = 1;

        @Override public EmployeeHireDTO selectEmployeeHire(Long companyId, Long employeeId) { return hires.get(employeeId); }

        @Override public LeaveCycleDTO selectCycleOn(Long companyId, Long employeeId, String day) {
            LocalDate d = LocalDate.parse(day);
            return cycles.stream().filter(c -> c.getEmployeeId().equals(employeeId)
                    && !LocalDate.parse(c.getStartsOn()).isAfter(d) && LocalDate.parse(c.getEndsOn()).isAfter(d)).findFirst().orElse(null);
        }

        final List<Long> locked = new ArrayList<>(); // FOR UPDATE 로 잠근 부여 행 (잠글 때마다 한 줄)

        @Override public LeaveCycleDTO selectCycleOnForUpdate(Long companyId, Long employeeId, String day) {
            LeaveCycleDTO c = selectCycleOn(companyId, employeeId, day);
            if (c != null) locked.add(c.getLeaveCycleId());
            return c;
        }

        @Override public int insertCycle(LeaveCycleDTO c) {
            if (cycles.stream().anyMatch(x -> x.getEmployeeId().equals(c.getEmployeeId()) && x.getStartsOn().equals(c.getStartsOn()))) {
                throw new DuplicateKeyException("UQ_32_01");
            }
            c.setLeaveCycleId(seq++);
            cycles.add(c);
            return 1;
        }

        @Override public BigDecimal selectLedgerSum(Long companyId, Long leaveCycleId) {
            BigDecimal used = ledger.entrySet().stream().filter(e -> leaveCycleId.equals(ledgerCycle.get(e.getKey())))
                    .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            return used.add(selectGrantSum(companyId, leaveCycleId));
        }

        // 추가 부여(LC_GRANT): 날짜 행 없이 여러 줄. CK_33_01 처럼 0일·사유 없음은 거절
        final List<LeaveGrantDTO> grants = new ArrayList<>();
        final List<Long> grantCycle = new ArrayList<>();
        final List<Long> grantActor = new ArrayList<>();

        @Override public BigDecimal selectGrantSum(Long companyId, Long leaveCycleId) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < grants.size(); i++) {
                if (leaveCycleId.equals(grantCycle.get(i))) sum = sum.add(grants.get(i).getDeltaDays());
            }
            return sum;
        }

        @Override public int insertLedgerGrant(Long companyId, Long leaveCycleId, BigDecimal deltaDays, Long actorAccountId, String note) {
            if (deltaDays.signum() == 0 || note == null) throw new IllegalStateException("CK_33_01");
            LeaveGrantDTO g = new LeaveGrantDTO();
            g.setLeaveLedgerId(seq++);
            g.setDeltaDays(deltaDays);
            g.setNote(note);
            g.setCreatedAt("2026-11-06T10:00:0" + grants.size());
            g.setActorName("인사 담당");
            grants.add(g);
            grantCycle.add(leaveCycleId);
            grantActor.add(actorAccountId);
            return 1;
        }

        @Override public List<LeaveGrantDTO> selectGrantHistory(Long companyId, Long employeeId) {
            List<LeaveGrantDTO> mine = new ArrayList<>();
            for (int i = grants.size() - 1; i >= 0; i--) { // 최근 순
                Long cycle = grantCycle.get(i);
                if (cycles.stream().anyMatch(c -> c.getLeaveCycleId().equals(cycle) && c.getEmployeeId().equals(employeeId))) {
                    mine.add(grants.get(i));
                }
            }
            return mine;
        }

        @Override public List<LeaveCycleDTO> selectCompanyCyclesAtHire(Long companyId) {
            List<LeaveCycleDTO> rows = new ArrayList<>();
            hires.forEach((emp, h) -> {
                if (h.getHireDate() == null) return;
                LeaveCycleDTO c = selectCycleOn(companyId, emp, h.getHireDate());
                if (c == null) return;
                c.setLedgerDays(selectLedgerSum(companyId, c.getLeaveCycleId()));
                rows.add(c);
            });
            return rows;
        }

        @Override public List<LeaveSlotDTO> selectTakenSlots(Long companyId, Long employeeId, List<String> days) {
            return slots.stream().filter(s -> s.getEmployeeId().equals(employeeId) && days.contains(s.getLeaveDate().toString()))
                    .collect(Collectors.toList());
        }

        @Override public int insertSlot(LeaveSlotDTO slot) {
            if (slots.stream().anyMatch(s -> s.getEmployeeId().equals(slot.getEmployeeId()) && s.getLeaveDate().equals(slot.getLeaveDate())
                    && s.getSlotCode().equals(slot.getSlotCode()))) {
                throw new DuplicateKeyException("PK_LEAVE_SLOT");
            }
            slots.add(slot);
            return 1;
        }

        @Override public int insertLedgerUse(Long companyId, Long leaveCycleId, Long leaveRequestDayId, BigDecimal deltaDays, Long actorAccountId) {
            if (ledger.containsKey(leaveRequestDayId)) throw new DuplicateKeyException("UQ_33_01");
            ledger.put(leaveRequestDayId, deltaDays);
            ledgerCycle.put(leaveRequestDayId, leaveCycleId);
            return 1;
        }

        @Override public BigDecimal selectPendingDays(Long companyId, Long employeeId, String startsOn, String endsOn) { return BigDecimal.ZERO; }

        @Override public List<LeaveRequestDTO> selectRequests(Long companyId, Long employeeId, String startsOn, String endsOn) { return List.of(); }

        @Override public List<AttendanceApprovalStepDTO> selectSteps(Long companyId, List<Long> documentIds) { return List.of(); }
    }
}
