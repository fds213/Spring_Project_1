package com.ieum.gw.attendance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ieum.gw.attendance.dao.AttendanceDAO;
import com.ieum.gw.attendance.dao.LeaveDAO;
import com.ieum.gw.attendance.dto.ApprovalStepDTO;
import com.ieum.gw.attendance.dto.EmployeeCareerDTO;
import com.ieum.gw.attendance.dto.LeaveCycleDTO;
import com.ieum.gw.attendance.dto.LeaveRequestDTO;
import com.ieum.gw.attendance.dto.LeaveUsageDTO;

import lombok.RequiredArgsConstructor;

/**
 * 연차 화면 (요구분석서 3.9): 본인의 이번 연차 주기, 사용·승인 대기 일수, 휴가 신청 내역.
 * - 부여 = 15일 + 내림(총 완료 경력 연수 ÷ 3). 총 경력 = 이전 경력 + 현재 업체 근속(주기 시작일 기준).
 * - 주기는 입사기념일부터 다음 입사기념일 전날까지. 남은 연차는 이월하지 않는다.
 * - leave_cycle 에 오늘의 주기가 있으면(입사기념일 부여 작업이 만든 것) 그 값을 쓰고, 없으면 같은 계산식으로 구한다.
 * - 사용·대기·내역은 전자결재의 휴가 신청서에서 읽는다(최종 승인 = 사용, 결재 중 = 승인 대기).
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final Map<String, String> REQUEST_STATUS = Map.of("IN_PROGRESS", "REQUESTED", "APPROVED", "APPROVED", "REJECTED", "REJECTED");

    private final LeaveDAO dao;
    private final AttendanceDAO attendanceDao;

    /** leave.js 의 renderLeaveView 가 그리는 모양: { summary | null, items } */
    public Map<String, Object> leaves(Long companyId, Long employeeId) {
        Map<String, Object> res = new LinkedHashMap<>();
        LocalDate today = LocalDate.parse(attendanceDao.selectToday());
        EmployeeCareerDTO career = dao.selectEmployeeCareer(companyId, employeeId);
        if (career == null || career.getHireDate() == null) {
            res.put("summary", null);
            res.put("reason", career == null ? "NO_EMPLOYEE" : "NO_HIRE_DATE"); // 화면 안내 문구를 가른다
            res.put("items", List.of());
            return res;
        }
        LocalDate hire = LocalDate.parse(career.getHireDate());
        int prior = career.getPriorCareerMonths() == null ? 0 : career.getPriorCareerMonths();

        Cycle cycle;
        LeaveCycleDTO saved = dao.selectCurrentCycle(companyId, employeeId, today.toString());
        if (saved != null) {
            cycle = new Cycle(LocalDate.parse(saved.getStartsOn()), LocalDate.parse(saved.getEndsOn()),
                    saved.getCareerMonths(), saved.getGrantedDays());
        } else {
            cycle = cycleFor(hire, prior, today);
        }

        LeaveUsageDTO usage = dao.selectUsage(companyId, employeeId, cycle.start.toString(), cycle.end.toString());
        BigDecimal used = usage == null || usage.getUsedDays() == null ? BigDecimal.ZERO : usage.getUsedDays();
        BigDecimal pending = usage == null || usage.getPendingDays() == null ? BigDecimal.ZERO : usage.getPendingDays();
        BigDecimal left = cycle.grant.subtract(used);

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

    /** 오늘이 들어 있는 연차 주기를 계산식으로 구한다 (입사기념일 기준, 2월 29일 입사는 평년에 2월 28일) */
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

    /** 연차 주기: [start, end), 부여 때 총 경력 개월과 부여 일수 */
    public static final class Cycle {
        public final LocalDate start;
        public final LocalDate end;
        public final int careerMonths;
        public final BigDecimal grant;

        Cycle(LocalDate start, LocalDate end, int careerMonths, BigDecimal grant) {
            this.start = start;
            this.end = end;
            this.careerMonths = careerMonths;
            this.grant = grant;
        }
    }
}
