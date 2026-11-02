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
 * - 모든 직원이 15일. 이전 경력으로 바꾸지 않고, 입사기념일 자동 부여도 하지 않는다(10/29 회의).
 * - 입사일에 한 번 부여한다: 부여 행(leave_cycle)은 입사일부터 끝없이(9999-12-31) 하나다.
 * - 남은 일수 = 부여 일수 + 사용(-)·복원(+) 내역(leave_ledger) 합. 진실은 이 내역 하나다.
 * - 부여 행이 아직 없으면(가입 승인 전 직원 등) 남은 일수는 15일로 보고, 차감하는 순간에 그 행을 만든다.
 * - 승인 대기 일수와 신청 내역은 전자결재의 휴가 신청서에서 읽는다.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    /** 모든 직원의 연차 일수 */
    public static final BigDecimal GRANT_DAYS = BigDecimal.valueOf(15);
    /** 부여 행의 끝나는 날: 자동 부여가 없어 한 번 준 연차가 계속 이어진다 */
    static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    private static final Map<String, String> REQUEST_STATUS = Map.of("IN_PROGRESS", "REQUESTED", "APPROVED", "APPROVED", "REJECTED", "REJECTED");
    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");
    private static final int MAX_SPAN_DAYS = 366; // 한 번에 신청하는 휴가 기간의 상한

    private final LeaveDAO dao;
    private final AttendanceDAO attendanceDao;

    // ===== 연차 화면 =====

    /** leave.js 의 renderLeaveView 가 그리는 모양: { summary | null, reason?, items } */
    public Map<String, Object> leaves(Long companyId, Long employeeId) {
        Map<String, Object> res = new LinkedHashMap<>();
        EmployeeCareerDTO career = dao.selectEmployeeCareer(companyId, employeeId);
        if (career == null || career.getHireDate() == null) {
            res.put("summary", null);
            res.put("reason", career == null ? "NO_EMPLOYEE" : "NO_HIRE_DATE"); // 화면 안내 문구를 가른다
            res.put("items", List.of());
            return res;
        }
        Grant grant = grantOf(companyId, employeeId, LocalDate.parse(career.getHireDate()));

        BigDecimal used = grant.ledger.negate();
        BigDecimal pending = dao.selectPendingDays(companyId, employeeId, grant.start.toString(), OPEN_END.toString());
        BigDecimal left = grant.remaining();

        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("start", grant.start + "T00:00:00"); // 부여한 날(입사일)
        sum.put("grant", grant.days);
        sum.put("usedDays", used);
        sum.put("pendingDays", pending);
        sum.put("left", left);
        sum.put("available", left.subtract(pending));
        res.put("summary", sum);
        res.put("items", items(companyId, employeeId, grant.start));
        return res;
    }

    private List<Map<String, Object>> items(Long companyId, Long employeeId, LocalDate from) {
        List<LeaveRequestDTO> requests = dao.selectRequests(companyId, employeeId, from.toString(), OPEN_END.toString());
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

    /** 남은 연차 일수. 입사일이 없으면 0 */
    public BigDecimal getRemainingLeaveDays(Long companyId, Long employeeId) {
        EmployeeCareerDTO career = dao.selectEmployeeCareer(companyId, employeeId);
        if (career == null || career.getHireDate() == null) return BigDecimal.ZERO;
        return grantOf(companyId, employeeId, LocalDate.parse(career.getHireDate())).remaining();
    }

    /** 이미 승인된 휴가(leave_slot)와 날짜·시간대가 겹치는지. 종일 연차는 오전·오후를 모두 차지한다 */
    public boolean hasApprovedLeaveOverlap(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
        return !clashes(companyId, employeeId, days).isEmpty();
    }

    /**
     * 휴가 최종 승인 때 연차를 차감한다(결재 트랜잭션 안에서 불린다. 새 트랜잭션을 열지 않는다).
     * 부여 행을 잠그고(없으면 만들고) 남은 일수와 겹침을 다시 본 뒤, 시간대(leave_slot)와 사용 내역(LC_USE)을 저장한다.
     * 모자라거나 겹치거나 입사일 전 날짜면 IllegalStateException: 결재도 함께 취소된다.
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
        List<LeaveDayDTO> sorted = new ArrayList<>(days);
        sorted.sort(Comparator.comparing(LeaveDayDTO::getLeaveDate));
        if (sorted.get(0).getLeaveDate().isBefore(hire)) {
            throw new IllegalStateException("입사일(" + hire + ") 전 날짜는 연차로 쓸 수 없습니다");
        }

        // 부여 행 하나를 잠근다: 같은 직원의 두 승인이 동시에 와도 하나씩 남은 일수를 본다
        LeaveCycleDTO row = lockGrant(companyId, employeeId, hire);
        BigDecimal left = row.getGrantedDays().add(dao.selectLedgerSum(companyId, row.getLeaveCycleId()));
        BigDecimal need = sorted.stream().map(LeaveService::deductOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (left.compareTo(need) < 0) {
            throw new IllegalStateException("남은 연차가 모자랍니다 (남은 " + plain(left) + "일, 신청 " + plain(need) + "일)");
        }
        List<String> clash = clashes(companyId, employeeId, sorted);
        if (!clash.isEmpty()) {
            throw new IllegalStateException("이미 승인된 휴가와 겹칩니다: " + String.join(", ", clash));
        }
        try {
            for (LeaveDayDTO d : sorted) {
                for (String slot : slotsOf(d.getLeaveType())) {
                    LeaveSlotDTO s = new LeaveSlotDTO();
                    s.setCompanyId(companyId);
                    s.setEmployeeId(employeeId);
                    s.setLeaveDate(d.getLeaveDate());
                    s.setSlotCode(slot);
                    s.setLeaveRequestDayId(d.getLeaveRequestDayId());
                    dao.insertSlot(s);
                }
                dao.insertLedgerUse(companyId, row.getLeaveCycleId(), d.getLeaveRequestDayId(), deductOf(d).negate(), actorAccountId);
            }
        } catch (DuplicateKeyException ex) {
            // 위에서 확인한 뒤 다른 승인이 먼저 같은 시간대를 저장했거나, 이미 차감한 휴가 날짜 행이다
            throw new IllegalStateException("이미 승인된 휴가와 겹치거나 이미 차감한 날짜입니다", ex);
        }
    }

    /** 새 사원에게 연차 15일을 준다(입사일부터). 직원 가입 승인과 체험 신청이 부른다. 이미 있으면 그대로 둔다 */
    @Transactional
    public void grantInitialLeave(Long companyId, Long employeeId, LocalDate hireDate) {
        if (hireDate == null) throw new IllegalArgumentException("입사일이 없어 연차를 부여할 수 없습니다");
        if (dao.selectCycleOn(companyId, employeeId, hireDate.toString()) != null) return;
        try {
            dao.insertCycle(newGrantRow(companyId, employeeId, hireDate));
        } catch (DuplicateKeyException e) {
            // 같은 요청이 두 번 들어와 이미 만들어졌다
        }
    }

    // ===== 부여 행 =====

    /** 저장된 부여 행이 있으면 그 값과 사용 합, 없으면 15일(사용 합 0) */
    private Grant grantOf(Long companyId, Long employeeId, LocalDate hire) {
        LeaveCycleDTO row = dao.selectCycleOn(companyId, employeeId, hire.toString());
        if (row == null) return new Grant(hire, GRANT_DAYS, BigDecimal.ZERO);
        return new Grant(LocalDate.parse(row.getStartsOn()), row.getGrantedDays(), dao.selectLedgerSum(companyId, row.getLeaveCycleId()));
    }

    /** 업체 직원들의 부여 행(사용 합 포함)을 직원 번호로. 행이 없는 직원은 빠진다 */
    public Map<Long, LeaveCycleDTO> cyclesOn(Long companyId, LocalDate day) {
        Map<Long, LeaveCycleDTO> map = new HashMap<>();
        for (LeaveCycleDTO c : dao.selectCompanyCyclesOn(companyId, day.toString())) map.put(c.getEmployeeId(), c);
        return map;
    }

    /** 근태·연차 관리 화면: 미리 읽어 둔 부여 행(사용 합 포함, 없으면 null)으로 남은 일수. 입사일이 없으면 null */
    public static BigDecimal remainingOf(LeaveCycleDTO savedRow, String hireDate) {
        if (savedRow != null) {
            return savedRow.getGrantedDays().add(savedRow.getLedgerDays() == null ? BigDecimal.ZERO : savedRow.getLedgerDays());
        }
        return hireDate == null ? null : GRANT_DAYS;
    }

    /** 차감할 부여 행을 잠근다. 아직 없으면 만들고 다시 잠근다 */
    private LeaveCycleDTO lockGrant(Long companyId, Long employeeId, LocalDate hire) {
        LeaveCycleDTO row = dao.selectCycleOnForUpdate(companyId, employeeId, hire.toString());
        if (row != null) return row;
        try {
            dao.insertCycle(newGrantRow(companyId, employeeId, hire));
        } catch (DuplicateKeyException e) {
            // 동시에 다른 승인이 먼저 만들었다: 아래에서 그 행을 잠근다
        }
        row = dao.selectCycleOnForUpdate(companyId, employeeId, hire.toString());
        if (row == null) throw new IllegalStateException("연차 부여 행을 만들지 못했습니다");
        return row;
    }

    /** 입사일부터 끝없이 15일. 경력 칸(career_months)은 쓰지 않아 0 */
    static LeaveCycleDTO newGrantRow(Long companyId, Long employeeId, LocalDate hire) {
        LeaveCycleDTO row = new LeaveCycleDTO();
        row.setCompanyId(companyId);
        row.setEmployeeId(employeeId);
        row.setStartsOn(hire.toString());
        row.setEndsOn(OPEN_END.toString());
        row.setCareerMonths(0);
        row.setGrantedDays(GRANT_DAYS);
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

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** 부여: 부여한 날, 부여 일수, 사용(-)·복원(+) 합 */
    private static final class Grant {
        final LocalDate start;
        final BigDecimal days;
        final BigDecimal ledger;

        Grant(LocalDate start, BigDecimal days, BigDecimal ledger) {
            this.start = start;
            this.days = days;
            this.ledger = ledger;
        }

        BigDecimal remaining() {
            return days.add(ledger);
        }
    }
}
