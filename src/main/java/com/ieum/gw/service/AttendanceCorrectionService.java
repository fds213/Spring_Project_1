package com.ieum.gw.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dao.AttendanceCorrectionDAO;
import com.ieum.gw.dao.AttendanceDAO;
import com.ieum.gw.dto.AttendanceCorrectionDTO;
import com.ieum.gw.dto.AttendanceDTO;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 근태 정정 요청 (attendance_correction). 승인은 전자결재가 아니라 근태 안에서 한다: 직원 요청 → 처리자 승인·반려.
 * - 처리자: 요청자 부서의 부서장. 부서장이 없거나(퇴사·휴직 포함) 요청자 본인이 부서장이면 업체 관리자. 본인 요청은 본인이 처리할 수 없다.
 * - 두 가지를 받는다(팀장님 11/6 결정): '퇴근 누락'(출근만 있는 날 → 퇴근 시각), '결근'(기록이 없는 근무일 → 출근·퇴근 시각).
 *   결근 정정은 근무일만: 입사일 전·주말·휴일·승인된 종일 연차인 날은 받지 않는다.
 * - 기한: 근무일로부터 7일까지 요청할 수 있다(오늘이 11/9 면 11/2 까지). 처리(승인·반려)에는 기한이 없다.
 * - 승인하면 퇴근 누락은 그날 근태의 퇴근 시각(attendance.check_out_at)을 채우고, 결근은 그날 근태를 새로 만든다.
 *   지각·조퇴는 근태 화면·집계가 이 시각으로 다시 판단한다.
 *   반려·취소면 근태는 그대로다: 퇴근 누락은 '퇴근 누락'으로 남는다(팀장님 11/3 결정: 조퇴로 바꾸지 않는다).
 * - PR #54 검토의 서버 몫: 원래 출퇴근은 근태 행에서 복사, 남의 근태·다른 날 근태는 직원+날짜로 찾아 막고, 본인 처리·처리된 건 다시 처리를 막는다.
 * 거절은 AttendanceException(상태, 문구): 400 값, 403 권한, 404 없는 요청·다른 업체, 409 상태가 맞지 않음.
 */
@Service
@RequiredArgsConstructor
public class AttendanceCorrectionService {

    static final String REQUESTED = "ACR_REQUESTED";
    static final String APPROVED = "ACR_APPROVED";
    static final String REJECTED = "ACR_REJECTED";
    static final String CANCELED = "ACR_CANCELED";

    /** 화면 값: 상태 코드 → 짧은 이름 */
    private static final Map<String, String> STATUS = Map.of(REQUESTED, "REQUESTED", APPROVED, "APPROVED",
            REJECTED, "REJECTED", CANCELED, "CANCELED");
    private static final Pattern HH_MM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    /** 사유 길이 (attendance_correction.reason·reject_reason VARCHAR2(500 CHAR)) */
    private static final int TEXT_MAX = 500;
    /** 정정 요청 기한: 근무일로부터 이 일수까지 (팀장님 11/6 결정) */
    static final int REQUEST_DAYS = 7;

    private final AttendanceCorrectionDAO dao;
    private final AttendanceDAO attendanceDao;

    // ===== 직원 =====

    /**
     * 정정 요청 { date: YYYY-MM-DD, inTime: HH:mm(결근만), outTime: HH:mm, reason } → 만든 요청.
     * 그날 출근 기록이 있으면 퇴근 누락(퇴근 시각만), 없으면 결근(출근·퇴근 시각)으로 받는다.
     */
    @Transactional
    public Map<String, Object> request(LoginUser user, String date, String inTime, String outTime, String reason) {
        Long companyId = user.getCompanyId();
        Long employeeId = requireEmployee(user);
        if (!"ST_SERVING".equals(attendanceDao.selectEmploymentStatus(companyId, employeeId))) {
            throw new AttendanceException(403, "재직 중인 직원만 근태 정정을 요청할 수 있습니다");
        }
        LocalDate day = parseDate(date);
        LocalDate today = LocalDate.parse(attendanceDao.selectToday());
        if (!day.isBefore(today)) {
            throw new AttendanceException(409, "오늘 출퇴근은 단말기로 찍어 주세요. 지난 날만 정정할 수 있습니다");
        }
        if (day.isBefore(today.minusDays(REQUEST_DAYS))) {
            throw new AttendanceException(409, "근무일로부터 " + REQUEST_DAYS + "일이 지나 정정을 요청할 수 없습니다");
        }
        String out = time(outTime, "퇴근");
        String in = inTime == null || inTime.isBlank() ? null : time(inTime, "출근");
        String text = requireText(reason, "정정 사유를 넣어 주세요", "정정 사유는 ");

        AttendanceCorrectionDTO dto = new AttendanceCorrectionDTO();
        dto.setCompanyId(companyId);
        dto.setEmployeeId(employeeId);
        dto.setWorkDate(day.toString());
        dto.setReqCheckOut(out);
        dto.setReason(text);

        AttendanceDTO att = dao.selectAttendanceOnForUpdate(companyId, employeeId, day.toString());
        if (att != null) {
            // 퇴근 누락: 출근은 단말기 기록이 원본이라 바꾸지 않는다
            if (att.getCheckOut() != null) throw new AttendanceException(409, "그날은 출퇴근 기록이 모두 있어 정정할 것이 없습니다");
            if (in != null) throw new AttendanceException(400, "출근 기록이 있는 날은 퇴근 시각만 정정합니다");
            if ((out + ":00").compareTo(att.getCheckIn()) < 0) {
                throw new AttendanceException(400, "퇴근 시각은 출근 시각(" + att.getCheckIn().substring(0, 5) + ")보다 늦어야 합니다");
            }
            dto.setAttendanceId(att.getAttendanceId());
        } else {
            // 결근: 근무일이어야 하고, 출근·퇴근 시각을 둘 다 받는다
            requireWorkday(companyId, employeeId, day);
            if (in == null) throw new AttendanceException(400, "출퇴근 기록이 없는 날은 출근 시각도 넣어 주세요");
            if (out.compareTo(in) < 0) throw new AttendanceException(400, "퇴근 시각은 출근 시각보다 늦어야 합니다");
            dto.setReqCheckIn(in);
        }
        try {
            int saved = att != null ? dao.insertCorrection(dto) : dao.insertAbsenceCorrection(dto);
            if (saved == 0) throw new AttendanceException(409, "그날 근태가 바뀌어 요청하지 못했습니다");
        } catch (DuplicateKeyException e) {
            throw new AttendanceException(409, "그날 처리를 기다리는 정정 요청이 이미 있습니다");
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", dto.getAttendanceCorrectionId());
        res.put("date", day.toString());
        res.put("kind", att != null ? "NO_OUT" : "ABSENT");
        res.put("inTime", in == null ? "" : in);
        res.put("outTime", out);
        res.put("status", "REQUESTED");
        return res;
    }

    /** 결근 정정은 근무일만: 입사일 전·주말·휴일·승인된 종일 연차(오전·오후 반차가 둘 다 있는 날 포함)는 409 */
    private void requireWorkday(Long companyId, Long employeeId, LocalDate day) {
        String hire = dao.selectHireDate(companyId, employeeId);
        if (hire != null && day.isBefore(LocalDate.parse(hire))) {
            throw new AttendanceException(409, "입사일(" + hire + ") 전 날은 정정할 수 없습니다");
        }
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new AttendanceException(409, "주말은 근무일이 아니라 정정할 수 없습니다");
        }
        if (!attendanceDao.selectHolidays(companyId, day.toString(), day.toString()).isEmpty()) {
            throw new AttendanceException(409, "휴일은 근무일이 아니라 정정할 수 없습니다");
        }
        Set<String> leave = new HashSet<>();
        for (LeaveDayDTO l : attendanceDao.selectMonthLeaveDays(companyId, employeeId, day.toString().substring(0, 7))) {
            if (day.equals(l.getLeaveDate())) leave.add(l.getLeaveType());
        }
        if (leave.contains("LV_FULL") || (leave.contains("LV_AM") && leave.contains("LV_PM"))) {
            throw new AttendanceException(409, "그날은 승인된 종일 연차라 정정할 수 없습니다");
        }
    }

    /** HH:mm 시각. 아니면 400 ("퇴근 시각을 00:00 형식으로…") */
    private static String time(String v, String what) {
        String t = v == null ? "" : v.trim();
        if (!HH_MM.matcher(t).matches()) throw new AttendanceException(400, what + " 시각을 00:00 형식으로 넣어 주세요");
        return t;
    }

    /** 내 그 달 정정 요청 (month = YYYY-MM, 최근 순). 근태 화면이 날짜별 '정정 대기·승인·반려'를 붙인다 */
    public Map<String, Object> myCorrections(LoginUser user, String month) {
        if (month == null || !MONTH.matcher(month).matches()) throw new AttendanceException(400, "달을 YYYY-MM 으로 넣어 주세요");
        List<Map<String, Object>> items = new ArrayList<>();
        for (AttendanceCorrectionDTO c : dao.selectMyCorrections(user.getCompanyId(), requireEmployee(user), month)) {
            items.add(view(c, false));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("month", month);
        res.put("items", items);
        return res;
    }

    /** 처리 전인 내 요청을 취소한다 */
    @Transactional
    public Map<String, Object> cancel(LoginUser user, Long id) {
        Long employeeId = requireEmployee(user);
        AttendanceCorrectionDTO c = lockOrThrow(user.getCompanyId(), id);
        if (!employeeId.equals(c.getEmployeeId())) throw new AttendanceException(404, "정정 요청을 찾을 수 없습니다");
        if (!REQUESTED.equals(c.getStatusCode()) || dao.cancelCorrection(user.getCompanyId(), id, employeeId) == 0) {
            throw new AttendanceException(409, "이미 처리됐거나 취소된 요청입니다");
        }
        return Map.of("id", id, "status", "CANCELED");
    }

    // ===== 처리자 (부서장·업체 관리자) =====

    /** 내가 처리할 대기 요청 (오래된 순). 부서장도 업체 관리자도 아니면 빈 목록 */
    public Map<String, Object> inbox(LoginUser user) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (user.getEmployeeId() != null) {
            for (AttendanceCorrectionDTO c : dao.selectInbox(user.getCompanyId(), user.getEmployeeId(), isAdmin(user))) {
                items.add(view(c, true));
            }
        }
        return Map.of("items", items);
    }

    /** 승인: 퇴근 누락은 그날 근태에 퇴근 시각을 넣고, 결근은 그날 근태를 만든 뒤 승인으로 바꾼다 (한 트랜잭션) */
    @Transactional
    public Map<String, Object> approve(LoginUser user, Long id) {
        AttendanceCorrectionDTO c = lockForDecision(user, id);
        if (c.getAttendanceId() != null) {
            if (dao.applyCheckOut(user.getCompanyId(), id) == 0) {
                throw new AttendanceException(409, "그날 퇴근 기록이 이미 있어 반영하지 못했습니다. 반려해 주세요");
            }
        } else {
            try {
                if (dao.insertAttendanceFromCorrection(user.getCompanyId(), id) == 0) {
                    throw new AttendanceException(409, "요청한 출근 시각이 없어 반영하지 못했습니다");
                }
            } catch (DuplicateKeyException e) {
                // UQ_36_01: 요청 뒤 그날 근태가 생겼다
                throw new AttendanceException(409, "그날 출근 기록이 이미 있어 반영하지 못했습니다. 반려해 주세요");
            }
        }
        decide(user, c, APPROVED, null);
        return Map.of("id", id, "status", "APPROVED");
    }

    /** 반려: 사유와 함께 돌려보낸다. 근태는 그대로(퇴근 누락은 '퇴근 누락', 결근은 기록 없음) */
    @Transactional
    public Map<String, Object> reject(LoginUser user, Long id, String reason) {
        String text = requireText(reason, "반려 사유를 넣어 주세요", "반려 사유는 ");
        AttendanceCorrectionDTO c = lockForDecision(user, id);
        decide(user, c, REJECTED, text);
        return Map.of("id", id, "status", "REJECTED");
    }

    // ===== 도구 =====

    /** 처리 전 확인: 요청을 잠그고, 대기 중인지·내가 처리자인지 본다 */
    private AttendanceCorrectionDTO lockForDecision(LoginUser user, Long id) {
        Long me = requireEmployee(user);
        AttendanceCorrectionDTO c = lockOrThrow(user.getCompanyId(), id);
        if (me.equals(c.getEmployeeId())) throw new AttendanceException(403, "본인 요청은 처리할 수 없습니다");
        if (!canDecide(user, c)) {
            throw new AttendanceException(403, "이 요청은 요청자 부서장이나 업체 관리자가 처리합니다");
        }
        if (!REQUESTED.equals(c.getStatusCode())) throw new AttendanceException(409, "이미 처리됐거나 취소된 요청입니다");
        return c;
    }

    /** 처리자인가: 요청자 부서장, 또는 (부서장이 없거나 요청자가 부서장이면) 업체 관리자 */
    static boolean canDecide(LoginUser user, AttendanceCorrectionDTO c) {
        Long head = c.getHeadEmployeeId();
        boolean headDecides = head != null && !head.equals(c.getEmployeeId());
        return headDecides ? head.equals(user.getEmployeeId()) : isAdmin(user);
    }

    /** 대기 중인 요청을 승인·반려로 바꾼다. 그새 다른 처리가 먼저 끝났으면 409 */
    private void decide(LoginUser user, AttendanceCorrectionDTO c, String status, String rejectReason) {
        if (dao.decideCorrection(user.getCompanyId(), c.getAttendanceCorrectionId(), status, user.getEmployeeId(), rejectReason) == 0) {
            throw new AttendanceException(409, "이미 처리됐거나 취소된 요청입니다");
        }
    }

    /** 같은 업체의 정정 요청을 잠그며 읽는다. 없으면 404 */
    private AttendanceCorrectionDTO lockOrThrow(Long companyId, Long id) {
        AttendanceCorrectionDTO c = id == null ? null : dao.selectCorrectionForUpdate(companyId, id);
        if (c == null) throw new AttendanceException(404, "정정 요청을 찾을 수 없습니다");
        return c;
    }

    /** 업체 관리자인가 */
    private static boolean isAdmin(LoginUser user) {
        return "ROLE_ADMIN".equals(user.getRole());
    }

    /** 사원이 없는 계정(운영자 등)은 근태 정정을 쓸 수 없다 */
    private static Long requireEmployee(LoginUser user) {
        if (user.getEmployeeId() == null) throw new AttendanceException(403, "사원 정보가 있는 계정만 쓸 수 있습니다");
        return user.getEmployeeId();
    }

    /** YYYY-MM-DD 날짜. 아니면 400 */
    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date == null ? "" : date.trim());
        } catch (DateTimeParseException e) {
            throw new AttendanceException(400, "날짜를 YYYY-MM-DD 로 넣어 주세요");
        }
    }

    /** 필수 글자(앞뒤 공백 제거, 500자까지) */
    private static String requireText(String v, String emptyMessage, String tooLongPrefix) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) throw new AttendanceException(400, emptyMessage);
        if (t.length() > TEXT_MAX) throw new AttendanceException(400, tooLongPrefix + TEXT_MAX + "자까지 넣을 수 있습니다");
        return t;
    }

    /** 화면 값. 처리자 목록이면 요청자 이름·사번·부서를 같이 */
    private static Map<String, Object> view(AttendanceCorrectionDTO c, boolean withRequester) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getAttendanceCorrectionId());
        if (withRequester) {
            m.put("name", c.getEmployeeName());
            m.put("employeeNo", c.getEmployeeNo());
            m.put("dept", c.getDepartmentName() == null ? "" : c.getDepartmentName());
        }
        m.put("date", c.getWorkDate());
        m.put("kind", c.getAttendanceId() != null ? "NO_OUT" : "ABSENT"); // 퇴근 누락·결근
        m.put("in", c.getOriginCheckIn() == null ? "" : c.getOriginCheckIn().substring(0, 5)); // 원래 출근 (결근이면 빈칸)
        m.put("inTime", c.getReqCheckIn() == null ? "" : c.getReqCheckIn());                 // 요청한 출근 (결근만)
        m.put("outTime", c.getReqCheckOut());
        m.put("reason", c.getReason());
        m.put("status", STATUS.getOrDefault(c.getStatusCode(), c.getStatusCode()));
        m.put("at", c.getCreatedAt());
        m.put("decidedBy", c.getApproverName() == null ? "" : c.getApproverName());
        m.put("decidedAt", c.getDecidedAt() == null ? "" : c.getDecidedAt());
        m.put("rejectReason", c.getRejectReason() == null ? "" : c.getRejectReason());
        return m;
    }
}
