package com.ieum.gw.attendance.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.attendance.dao.AttendanceDAO;
import com.ieum.gw.attendance.dao.LeaveDAO;
import com.ieum.gw.attendance.dto.ApprovalStepDTO;
import com.ieum.gw.attendance.dto.EmployeeCareerDTO;
import com.ieum.gw.attendance.dto.HolidayDTO;
import com.ieum.gw.attendance.dto.LeaveCycleDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.dto.LeaveRequestDTO;
import com.ieum.gw.attendance.dto.LeaveSlotDTO;

import lombok.RequiredArgsConstructor;

/**
 * 연차 (요구분석서 3.9). 연차 화면과, 결재가 부르는 연동 메서드(AttendanceService 맨 아래)가 같은 규칙을 쓴다.
 * - 부여 = 15일 + 내림(총 완료 경력 연수 ÷ 3). 총 경력 = 이전 경력 + 현재 업체 근속(주기 시작일 기준).
 * - 주기는 입사기념일부터 다음 입사기념일 전날까지. 남은 연차는 이월하지 않는다.
 * - 남은 일수 = 부여 일수 + 사용(-)·복원(+) 내역(leave_ledger) 합. 진실은 이 내역 하나다.
 * - 그 날의 주기 행(leave_cycle)이 아직 없으면 같은 계산식으로 구한다(내역이 없으니 남은 일수 = 부여 일수).
 *   차감하는 순간에 그 행을 만든다. 그래서 입사기념일 부여 작업이 없어도 해가 바뀌면 새 주기로 넘어간다.
 * - 승인 대기 일수와 신청 내역은 전자결재의 휴가 신청서에서 읽는다.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final Map<String, String> REQUEST_STATUS = Map.of("IN_PROGRESS", "REQUESTED", "APPROVED", "APPROVED", "REJECTED", "REJECTED");
    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");
    private static final int MAX_SPAN_DAYS = 366; // 한 번에 신청하는 휴가 기간의 상한

    private final LeaveDAO dao;
    private final AttendanceDAO attendanceDao;

    // ===== 연차 화면 =====

    /** leave.js 의 renderLeaveView 가 그리는 모양: { summary | null, reason?, items } */
    public Map<String, Object> leaves(Long companyId, Long employeeId) {
        Map<String, Object> res = new LinkedHashMap<>();
        LocalDate today = today();
        EmployeeCareerDTO career = dao.selectEmployeeCareer(companyId, employeeId);
        if (career == null || career.getHireDate() == null) {
            res.put("summary", null);
            res.put("reason", career == null ? "NO_EMPLOYEE" : "NO_HIRE_DATE"); // 화면 안내 문구를 가른다
            res.put("items", List.of());
            return res;
        }
        LocalDate hire = LocalDate.parse(career.getHireDate());
        int prior = priorOf(career);
        Cycle cycle = cycleOn(companyId, employeeId, hire, prior, today);

        BigDecimal used = cycle.ledger.negate();
        BigDecimal pending = dao.selectPendingDays(companyId, employeeId, cycle.start.toString(), cycle.end.toString());
        BigDecimal left = cycle.remaining();

        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("start", cycle.start + "T00:00:00");
        sum.put("end", cycle.end + "T00:00:00");
        sum.put("years", Math.max(0, cycle.start.getYear() - hire.getYear()));
        sum.put("prev", prior / 12);
        sum.put("career", cycle.careerMonths / 12);
        sum.put("grant", cycle.grant);
        sum.put("usedDays", used);
        sum.put("pendingDays", pending);
        sum.put("left", left);
        sum.put("available", left.subtract(pending));
        res.put("summary", sum);
        res.put("items", items(companyId, employeeId, cycle));
        return res;
    }

    private List<Map<String, Object>> items(Long companyId, Long employeeId, Cycle cycle) {
        List<LeaveRequestDTO> requests = dao.selectRequests(companyId, employeeId, cycle.start.toString(), cycle.end.toString());
        if (requests.isEmpty()) return List.of();
        Map<Long, List<ApprovalStepDTO>> steps = dao.selectSteps(companyId,
                requests.stream().map(LeaveRequestDTO::getApprovalDocumentId).collect(Collectors.toList()))
                .stream().collect(Collectors.groupingBy(ApprovalStepDTO::getApprovalDocumentId));
        List<Map<String, Object>> items = new ArrayList<>();
        for (LeaveRequestDTO r : requests) {
            List<ApprovalStepDTO> line = steps.getOrDefault(r.getApprovalDocumentId(), List.of());
            List<Map<String, Object>> names = new ArrayList<>();
            int step = 0;
            String comment = "";
            for (ApprovalStepDTO s : line) {
                names.add(Map.of("name", s.getApproverName()));
                if ("APPROVED".equals(s.getStatusCode())) step++;
                if ("REJECTED".equals(s.getStatusCode()) && s.getOpinion() != null) comment = s.getOpinion();
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getApprovalDocumentId());
            m.put("kind", AttendanceService.LEAVE_KIND.getOrDefault(r.getLeaveType(), "FULL"));
            m.put("from", r.getFromDate());
            m.put("to", r.getToDate());
            m.put("days", r.getDays());
            m.put("reason", r.getReason());
            m.put("at", r.getSubmittedAt());
            m.put("status", REQUEST_STATUS.getOrDefault(r.getStatusCode(), r.getStatusCode()));
            m.put("step", step);
            m.put("line", names);
            m.put("comment", comment);
            items.add(m);
        }
        return items;
    }

    // ===== 결재가 부르는 연차 규칙 (AttendanceService 의 연동 메서드가 여기로 넘긴다) =====

    /** 휴가 기간을 하루씩 나눈다. 주말·휴일은 빼고, 휴일 자료가 없는 해가 끼어 있거나 남는 날이 없으면 IllegalArgumentException */
    public List<LeaveDayDTO> calculateLeaveDays(Long companyId, String leaveType, LocalDate fromDate, LocalDate toDate) {
        List<String> slots = slotsOf(leaveType);
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("휴가 기간이 올바르지 않습니다");
        }
        if (slots.size() == 1 && !fromDate.equals(toDate)) {
            throw new IllegalArgumentException("반차는 하루만 신청할 수 있습니다");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_SPAN_DAYS) {
            throw new IllegalArgumentException("휴가 기간은 1년을 넘을 수 없습니다");
        }
        Set<Integer> loaded = new HashSet<>(attendanceDao.selectHolidayYears(companyId, fromDate.getYear(), toDate.getYear()));
        for (int y = fromDate.getYear(); y <= toDate.getYear(); y++) {
            if (!loaded.contains(y)) {
                throw new IllegalArgumentException(y + "년 휴일 자료가 없어 휴가 날짜를 계산할 수 없습니다. 휴일 관리에서 먼저 넣어 주세요");
            }
        }
        Set<LocalDate> holidays = attendanceDao.selectHolidays(companyId, fromDate.toString(), toDate.toString())
                .stream().map(HolidayDTO::getHolidayDate).collect(Collectors.toSet());
        BigDecimal deduct = slots.size() == 2 ? BigDecimal.ONE : HALF_DAY;

        List<LeaveDayDTO> days = new ArrayList<>();
        for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
            if (isWeekend(d) || holidays.contains(d)) continue;
            LeaveDayDTO day = new LeaveDayDTO();
            day.setLeaveDate(d);
            day.setLeaveType(leaveType);
            day.setDeductDays(deduct);
            days.add(day);
        }
        if (days.isEmpty()) {
            throw new IllegalArgumentException("고른 기간이 모두 주말·휴일이라 쓸 휴가가 없습니다");
        }
        return days;
    }

    /** 오늘이 속한 주기의 남은 일수. 입사일이 없으면 0 */
    public BigDecimal getRemainingLeaveDays(Long companyId, Long employeeId) {
        EmployeeCareerDTO career = dao.selectEmployeeCareer(companyId, employeeId);
        if (career == null || career.getHireDate() == null) return BigDecimal.ZERO;
        return cycleOn(companyId, employeeId, LocalDate.parse(career.getHireDate()), priorOf(career), today()).remaining();
    }

    /** 이미 승인된 휴가(leave_slot)와 날짜·시간대가 겹치는지. 종일 연차는 오전·오후를 모두 차지한다 */
    public boolean hasApprovedLeaveOverlap(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
        return !clashes(companyId, employeeId, days).isEmpty();
    }

    /**
     * 휴가 최종 승인 때 연차를 차감한다(결재 트랜잭션 안에서 불린다. 새 트랜잭션을 열지 않는다).
     * 날짜가 든 주기 행을 잠그고(없으면 만들고) 남은 일수와 겹침을 다시 본 뒤, 시간대(leave_slot)와 사용 내역(LC_USE)을 저장한다.
     * 모자라거나 겹치면 IllegalStateException: 결재도 함께 취소된다.
     */
    @Transactional
    public void useLeave(Long companyId, Long employeeId, Long actorAccountId, List<LeaveDayDTO> days) {
        if (days == null || days.isEmpty()) throw new IllegalArgumentException("차감할 휴가 날짜가 없습니다");
        if (actorAccountId == null) throw new IllegalArgumentException("승인한 사람의 계정이 없습니다");
        for (LeaveDayDTO d : days) {
            if (d.getLeaveRequestDayId() == null || d.getLeaveDate() == null) {
                throw new IllegalArgumentException("휴가 날짜 행 ID가 없습니다. 결재가 저장한 뒤 넘겨 주세요");
            }
            slotsOf(d.getLeaveType());
        }
        EmployeeCareerDTO career = dao.selectEmployeeCareer(companyId, employeeId);
        if (career == null || career.getHireDate() == null) {
            throw new IllegalStateException("입사일이 없어 연차를 차감할 수 없습니다");
        }
        LocalDate hire = LocalDate.parse(career.getHireDate());
        int prior = priorOf(career);

        // 날짜순으로 주기를 잠근다 (같은 순서로 잠가야 두 승인이 서로 기다리다 멈추지 않는다)
        List<LeaveDayDTO> sorted = new ArrayList<>(days);
        sorted.sort(Comparator.comparing(LeaveDayDTO::getLeaveDate));
        Map<Long, LeaveCycleDTO> cycles = new LinkedHashMap<>();
        Map<Long, List<LeaveDayDTO>> byCycle = new HashMap<>();
        for (LeaveDayDTO d : sorted) {
            LeaveCycleDTO row = lockCycle(companyId, employeeId, hire, prior, d.getLeaveDate());
            cycles.putIfAbsent(row.getLeaveCycleId(), row);
            byCycle.computeIfAbsent(row.getLeaveCycleId(), k -> new ArrayList<>()).add(d);
        }
        for (Map.Entry<Long, LeaveCycleDTO> e : cycles.entrySet()) {
            LeaveCycleDTO row = e.getValue();
            BigDecimal left = row.getGrantedDays().add(dao.selectLedgerSum(companyId, row.getLeaveCycleId()));
            BigDecimal need = byCycle.get(e.getKey()).stream().map(LeaveService::deductOf).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (left.compareTo(need) < 0) {
                throw new IllegalStateException("남은 연차가 모자랍니다 (남은 " + plain(left) + "일, 신청 " + plain(need) + "일)");
            }
        }
        List<String> clash = clashes(companyId, employeeId, sorted);
        if (!clash.isEmpty()) {
            throw new IllegalStateException("이미 승인된 휴가와 겹칩니다: " + String.join(", ", clash));
        }
        try {
            for (Map.Entry<Long, List<LeaveDayDTO>> e : byCycle.entrySet()) {
                for (LeaveDayDTO d : e.getValue()) {
                    for (String slot : slotsOf(d.getLeaveType())) {
                        LeaveSlotDTO s = new LeaveSlotDTO();
                        s.setCompanyId(companyId);
                        s.setEmployeeId(employeeId);
                        s.setLeaveDate(d.getLeaveDate());
                        s.setSlotCode(slot);
                        s.setLeaveRequestDayId(d.getLeaveRequestDayId());
                        dao.insertSlot(s);
                    }
                    dao.insertLedgerUse(companyId, e.getKey(), d.getLeaveRequestDayId(), deductOf(d).negate(), actorAccountId);
                }
            }
        } catch (DuplicateKeyException ex) {
            // 위에서 확인한 뒤 다른 승인이 먼저 같은 시간대를 저장했거나, 이미 차감한 휴가 날짜 행이다
            throw new IllegalStateException("이미 승인된 휴가와 겹치거나 이미 차감한 날짜입니다", ex);
        }
    }

    /** 새 사원의 첫 연차 주기(입사일부터). 이미 있으면 그대로 둔다. 부여 일수는 연차 화면과 같은 계산식(이전 경력 포함) */
    @Transactional
    public void grantInitialLeave(Long companyId, Long employeeId, LocalDate hireDate) {
        if (hireDate == null) throw new IllegalArgumentException("입사일이 없어 연차를 부여할 수 없습니다");
        if (dao.selectCycleOn(companyId, employeeId, hireDate.toString()) != null) return;
        EmployeeCareerDTO career = dao.selectEmployeeCareer(companyId, employeeId);
        Cycle first = cycleFor(hireDate, priorOf(career), hireDate);
        try {
            dao.insertCycle(toRow(companyId, employeeId, first));
        } catch (DuplicateKeyException e) {
            // 같은 요청이 두 번 들어와 이미 만들어졌다
        }
    }

    // ===== 주기 =====

    /** 그 날의 주기: 저장된 행이 있으면 그 값과 사용 합, 없으면 계산식(사용 합 0) */
    Cycle cycleOn(Long companyId, Long employeeId, LocalDate hire, int prior, LocalDate day) {
        LeaveCycleDTO row = dao.selectCycleOn(companyId, employeeId, day.toString());
        if (row == null) return cycleFor(hire, prior, day);
        return fromRow(row, dao.selectLedgerSum(companyId, row.getLeaveCycleId()));
    }

    /** 업체 직원들의 그 날 주기 행(사용 합 포함)을 직원 번호로. 행이 없는 직원은 빠진다 */
    public Map<Long, LeaveCycleDTO> cyclesOn(Long companyId, LocalDate day) {
        Map<Long, LeaveCycleDTO> map = new HashMap<>();
        for (LeaveCycleDTO c : dao.selectCompanyCyclesOn(companyId, day.toString())) map.put(c.getEmployeeId(), c);
        return map;
    }

    /** 근태·연차 관리 화면: 미리 읽어 둔 주기 행(사용 합 포함, 없으면 null)으로 남은 일수. 입사일이 없으면 null */
    public static BigDecimal remainingOf(LeaveCycleDTO savedRow, String hireDate, Integer priorCareerMonths, LocalDate day) {
        if (savedRow != null) {
            return fromRow(savedRow, savedRow.getLedgerDays() == null ? BigDecimal.ZERO : savedRow.getLedgerDays()).remaining();
        }
        if (hireDate == null) return null;
        return cycleFor(LocalDate.parse(hireDate), priorCareerMonths == null ? 0 : priorCareerMonths, day).remaining();
    }

    /** 차감할 주기 행을 잠근다. 아직 없으면 계산식으로 만들고 다시 잠근다 */
    private LeaveCycleDTO lockCycle(Long companyId, Long employeeId, LocalDate hire, int prior, LocalDate day) {
        LeaveCycleDTO row = dao.selectCycleOnForUpdate(companyId, employeeId, day.toString());
        if (row != null) return row;
        try {
            dao.insertCycle(toRow(companyId, employeeId, cycleFor(hire, prior, day)));
        } catch (DuplicateKeyException e) {
            // 동시에 다른 승인이 먼저 만들었다: 아래에서 그 행을 잠근다
        }
        row = dao.selectCycleOnForUpdate(companyId, employeeId, day.toString());
        if (row == null) throw new IllegalStateException("연차 주기를 만들지 못했습니다");
        return row;
    }

    /** 그 날이 들어 있는 연차 주기를 계산식으로 구한다 (입사기념일 기준, 2월 29일 입사는 평년에 2월 28일) */
    public static Cycle cycleFor(LocalDate hire, int priorCareerMonths, LocalDate today) {
        LocalDate start;
        if (today.isBefore(hire)) {
            start = hire;
        } else {
            start = anniversary(hire, today.getYear());
            if (start.isAfter(today)) start = anniversary(hire, today.getYear() - 1);
        }
        LocalDate end = anniversary(hire, start.getYear() + 1);
        // 시작일은 입사기념일이라 해 수로 센다. MONTHS.between 은 2월 29일 입사자의 평년 기념일(28일)을 한 달 모자라게 본다
        int careerMonths = priorCareerMonths + 12 * (start.getYear() - hire.getYear());
        return new Cycle(start, end, careerMonths, grantFor(careerMonths));
    }

    /** 부여 일수 = 15 + 내림(총 완료 경력 연수 ÷ 3) */
    public static BigDecimal grantFor(int careerMonths) {
        return BigDecimal.valueOf(15 + (careerMonths / 12) / 3);
    }

    private static LocalDate anniversary(LocalDate hire, int year) {
        return hire.withYear(year); // 2월 29일 → 평년이면 2월 28일 (LocalDate 가 맞춰 준다)
    }

    private static Cycle fromRow(LeaveCycleDTO row, BigDecimal ledger) {
        return new Cycle(LocalDate.parse(row.getStartsOn()), LocalDate.parse(row.getEndsOn()),
                row.getCareerMonths(), row.getGrantedDays(), ledger);
    }

    private static LeaveCycleDTO toRow(Long companyId, Long employeeId, Cycle c) {
        LeaveCycleDTO row = new LeaveCycleDTO();
        row.setCompanyId(companyId);
        row.setEmployeeId(employeeId);
        row.setStartsOn(c.start.toString());
        row.setEndsOn(c.end.toString());
        row.setCareerMonths(c.careerMonths);
        row.setGrantedDays(c.grant);
        return row;
    }

    // ===== 도구 =====

    /** 휴가 구분이 차지하는 시간대. 종일 = 오전·오후 */
    static List<String> slotsOf(String leaveType) {
        if ("LV_FULL".equals(leaveType)) return List.of("SLOT_AM", "SLOT_PM");
        if ("LV_AM".equals(leaveType)) return List.of("SLOT_AM");
        if ("LV_PM".equals(leaveType)) return List.of("SLOT_PM");
        throw new IllegalArgumentException("휴가 구분은 LV_FULL·LV_AM·LV_PM 중 하나입니다");
    }

    /** 겹치는 날짜와 시간대 ("2026-11-03 오전"). 없으면 빈 목록 */
    private List<String> clashes(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
        if (days == null || days.isEmpty()) return List.of();
        List<String> dates = days.stream().map(d -> d.getLeaveDate().toString()).distinct().collect(Collectors.toList());
        Map<LocalDate, Set<String>> taken = new HashMap<>();
        for (LeaveSlotDTO s : dao.selectTakenSlots(companyId, employeeId, dates)) {
            taken.computeIfAbsent(s.getLeaveDate(), k -> new HashSet<>()).add(s.getSlotCode());
        }
        List<String> clash = new ArrayList<>();
        for (LeaveDayDTO d : days) {
            Set<String> used = taken.getOrDefault(d.getLeaveDate(), Set.of());
            for (String slot : slotsOf(d.getLeaveType())) {
                if (used.contains(slot)) clash.add(d.getLeaveDate() + ("SLOT_AM".equals(slot) ? " 오전" : " 오후"));
            }
        }
        return clash;
    }

    private static BigDecimal deductOf(LeaveDayDTO d) {
        if (d.getDeductDays() != null) return d.getDeductDays();
        return "LV_FULL".equals(d.getLeaveType()) ? BigDecimal.ONE : HALF_DAY;
    }

    private static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static int priorOf(EmployeeCareerDTO career) {
        return career == null || career.getPriorCareerMonths() == null ? 0 : career.getPriorCareerMonths();
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private LocalDate today() {
        return LocalDate.parse(attendanceDao.selectToday());
    }

    /** 연차 주기: [start, end), 부여 때 총 경력 개월과 부여 일수, 사용(-)·복원(+) 합 */
    public static final class Cycle {
        public final LocalDate start;
        public final LocalDate end;
        public final int careerMonths;
        public final BigDecimal grant;
        public final BigDecimal ledger;

        Cycle(LocalDate start, LocalDate end, int careerMonths, BigDecimal grant) {
            this(start, end, careerMonths, grant, BigDecimal.ZERO);
        }

        Cycle(LocalDate start, LocalDate end, int careerMonths, BigDecimal grant, BigDecimal ledger) {
            this.start = start;
            this.end = end;
            this.careerMonths = careerMonths;
            this.grant = grant;
            this.ledger = ledger;
        }

        public BigDecimal remaining() {
            return grant.add(ledger);
        }
    }
}
