package com.ieum.gw.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dao.AttendanceDAO;
import com.ieum.gw.dto.AttendanceDTO;
import com.ieum.gw.dto.AttendanceDeviceDTO;
import com.ieum.gw.dto.AttendanceSummaryDTO;
import com.ieum.gw.dto.DevicePersonDTO;
import com.ieum.gw.dto.HolidayDTO;
import com.ieum.gw.dto.LeaveCycleDTO;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.dto.PunchDTO;
import com.ieum.gw.dto.TodayAttendanceDTO;

import lombok.RequiredArgsConstructor;

/**
 * 근태: 지문 단말기(시뮬레이터)의 출퇴근 수신과, 로그인한 본인의 근태 조회 (요구분석서 3.4·3.10, 기능명세서 1.9).
 * - 출퇴근 시각은 이음이 받은 서울 시각이다. 단말기가 보낸 시각은 받지 않는다.
 * - 같은 날 출근·퇴근은 각 1건. 중복 출근, 출근 없는 퇴근, 두 번째 퇴근은 거절한다.
 * - 단말기는 키(해시로 저장)로 업체를 찾는다. 다른 업체의 같은 사용자 번호와 섞이지 않는다.
 * - 이용 기간이 끝난 업체(읽기 전용)의 체크는 받지 않는다.
 *
 * 맨 아래 "다른 모듈에 주는 메서드"는 연동 계약(10/30)이다. 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 결재가 부르는 메서드는 결재 트랜잭션 안에서 불린다. 따로 트랜잭션을 열지 않고(REQUIRES_NEW 금지),
 * 안 되면 예외를 던져 결재까지 함께 취소되게 한다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    // 기본 근무 09:00~18:00 (요구분석서 3.4). 출근은 10분 유예: 09:10:00 까지 찍으면 정상, 그 뒤면 지각
    // 근태·연차 관리의 그 달 지각·조퇴 수(SQL)도 이 두 값으로 센다
    private static final String LATE_AFTER = "09:10:00";
    private static final String WORK_END = "18:00:00";

    // 휴가 구분 공통 코드 → 화면 값 (LeaveService 도 같이 쓴다)
    static final Map<String, String> LEAVE_KIND = Map.of("LV_FULL", "FULL", "LV_AM", "AM", "LV_PM", "PM");

    private final AttendanceDAO dao;
    private final LeaveService leaveService;
    private final HolidayService holidayService;

    // ===== 단말기 =====

    /** 단말기 키로 쓰는 중인 단말기를 찾는다. 없으면 401 */
    public AttendanceDeviceDTO requireDevice(String deviceKey) {
        if (deviceKey == null || deviceKey.isBlank()) {
            throw new AttendanceException(401, "단말기 키가 없습니다");
        }
        AttendanceDeviceDTO device = dao.selectDeviceByKeyHash(sha256(deviceKey.trim()));
        if (device == null) {
            throw new AttendanceException(401, "단말기 키가 맞지 않습니다");
        }
        return device;
    }

    /** 시뮬레이터 화면: 단말기와 등록된 사람 목록 */
    public Map<String, Object> deviceInfo(String deviceKey) {
        AttendanceDeviceDTO device = requireDevice(deviceKey);
        List<Map<String, Object>> people = new ArrayList<>();
        for (DevicePersonDTO p : dao.selectDevicePeople(device.getAttendanceDeviceId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userNo", p.getDeviceUserNo());
            m.put("name", p.getEmployeeName());
            m.put("dept", p.getDepartmentName() == null ? "" : p.getDepartmentName());
            m.put("serving", "ST_SERVING".equals(p.getEmploymentStatus()));
            people.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("companyName", device.getCompanyName());
        res.put("deviceName", device.getDeviceName());
        res.put("people", people);
        return res;
    }

    /** 단말기의 출근(IN)·퇴근(OUT) 체크 */
    @Transactional
    public Map<String, Object> punch(String deviceKey, String deviceUserNo, String type) {
        AttendanceDeviceDTO device = requireDevice(deviceKey);
        if (!"IN".equals(type) && !"OUT".equals(type)) {
            throw new AttendanceException(400, "출근 또는 퇴근을 골라 주세요");
        }
        if (deviceUserNo == null || deviceUserNo.isBlank()) {
            throw new AttendanceException(400, "단말기 사용자 번호가 없습니다");
        }
        if (dao.isCompanyExpired(device.getCompanyId())) {
            throw new AttendanceException(403, "이용 기간이 끝난 업체라 출퇴근을 받지 않습니다");
        }
        DevicePersonDTO person = dao.selectDevicePerson(device.getAttendanceDeviceId(), deviceUserNo.trim());
        if (person == null) {
            throw new AttendanceException(404, "단말기에 등록되지 않은 사용자 번호입니다");
        }
        if (!"ST_SERVING".equals(person.getEmploymentStatus())) {
            throw new AttendanceException(403, "재직 중인 직원이 아니라 출퇴근을 받지 않습니다");
        }

        PunchDTO dto = new PunchDTO();
        dto.setCompanyId(device.getCompanyId());
        dto.setEmployeeId(person.getEmployeeId());
        dto.setAttendanceDeviceId(device.getAttendanceDeviceId());
        dto.setEventKey(UUID.randomUUID().toString().replace("-", ""));

        if ("IN".equals(type)) {
            try {
                dao.insertCheckIn(dto);
            } catch (DuplicateKeyException e) {
                throw new AttendanceException(409, "오늘 이미 출근했습니다");
            }
            dto.setEventType("ATT_IN");
        } else {
            AttendanceDTO today = dao.selectTodayAttendance(device.getCompanyId(), person.getEmployeeId());
            if (today == null) {
                throw new AttendanceException(409, "오늘 출근 기록이 없어 퇴근할 수 없습니다");
            }
            dto.setAttendanceId(today.getAttendanceId());
            if (today.getCheckOut() != null || dao.updateCheckOut(dto) == 0) {
                throw new AttendanceException(409, "오늘 이미 퇴근했습니다");
            }
            dto.setEventType("ATT_OUT");
        }
        dao.insertEvent(dto);

        AttendanceDTO saved = dao.selectTodayAttendance(device.getCompanyId(), person.getEmployeeId());
        String time = "IN".equals(type) ? saved.getCheckIn() : saved.getCheckOut();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("name", person.getEmployeeName());
        res.put("userNo", person.getDeviceUserNo());
        res.put("type", type);
        res.put("date", saved.getWorkDate());
        res.put("time", time);
        res.put("status", "IN".equals(type) ? inStatus(time) : outStatus(time));
        return res;
    }

    // ===== 그룹웨어: 본인 근태 =====

    /** 한 달 출퇴근 기록 (month = YYYY-MM). attendance.js 의 renderAttRecords 가 그리는 모양 */
    public Map<String, Object> records(Long companyId, Long employeeId, String month) {
        requireMonth(month);
        String deviceNo = dao.selectMyDeviceUserNoInCompany(companyId, employeeId);
        String today = dao.selectToday();

        Map<String, AttendanceDTO> byDate = new HashMap<>();
        for (AttendanceDTO a : dao.selectMonthAttendance(companyId, employeeId, month)) {
            byDate.put(a.getWorkDate(), a);
        }
        Map<String, String> holidays = new HashMap<>();
        LocalDate first = LocalDate.parse(month + "-01");
        for (HolidayDTO h : dao.selectHolidays(companyId, first.toString(), first.withDayOfMonth(first.lengthOfMonth()).toString())) {
            holidays.put(h.getHolidayDate().toString(), h.getHolidayName());
        }
        // 최종 승인된 휴가: 오전·오후 반차가 같은 날 둘 다 있으면 종일로 본다
        Map<String, String> leaves = new HashMap<>();
        for (LeaveDayDTO l : dao.selectMonthLeaveDays(companyId, employeeId, month)) {
            String kind = LEAVE_KIND.getOrDefault(l.getLeaveType(), "FULL");
            String day = l.getLeaveDate().toString();
            String before = leaves.get(day);
            leaves.put(day, before == null || before.equals(kind) ? kind : "FULL");
        }

        // 카드의 "이번 달"은 보고 있는 달이 아니라 오늘이 속한 달이다. 보통은 이번 달을 보므로 같은 조회를 다시 하지 않는다
        String thisMonth = today.substring(0, 7);
        boolean viewingThisMonth = thisMonth.equals(month);
        List<AttendanceDTO> thisMonthRows = viewingThisMonth ? new ArrayList<>(byDate.values())
                : dao.selectMonthAttendance(companyId, employeeId, thisMonth);
        int late = 0;
        int early = 0;
        for (AttendanceDTO a : thisMonthRows) {
            if ("LATE".equals(inStatus(a.getCheckIn()))) late++;
            if (a.getCheckOut() != null && "EARLY_LEAVE".equals(outStatus(a.getCheckOut()))) early++;
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate d = first; d.getMonthValue() == first.getMonthValue(); d = d.plusDays(1)) {
            String date = d.toString();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("weekday", d.getDayOfWeek().getValue() % 7); // 0 = 일요일
            day.put("holiday", holidays.getOrDefault(date, ""));
            Map<String, String> punch = punchView(byDate.get(date));
            // 퇴근 누락: 오늘보다 앞선 날인데 출근만 있고 퇴근이 없다(지난 날 퇴근은 나중에 찍을 수 없다). 오늘은 아직 퇴근할 수 있어 비워 둔다
            if (punch != null && punch.get("out").isEmpty() && date.compareTo(today) < 0) punch.put("outStatus", "NO_OUT");
            day.put("punch", punch);
            day.put("leave", leaves.get(date));
            days.add(day);
        }

        Map<String, Object> todayView = new LinkedHashMap<>();
        todayView.put("date", today);
        AttendanceDTO todayRow = viewingThisMonth ? byDate.get(today) : dao.selectTodayAttendance(companyId, employeeId);
        todayView.put("punch", punchView(todayRow));
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("late", late);
        counts.put("early", early);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("month", month);
        res.put("deviceId", deviceNo == null ? "" : deviceNo);
        res.put("today", todayView);
        res.put("thisMonth", counts);
        res.put("days", days);
        return res;
    }

    /** 헤더의 "오늘 출근 08:52" (layout.tag 의 todayPunch). 기록이 없으면 null */
    public Map<String, String> todayPunchForHeader(Long companyId, Long employeeId) {
        TodayAttendanceDTO t = getTodayAttendance(companyId, employeeId);
        if (t == null || t.getCheckInAt() == null) return null;
        Map<String, String> m = new HashMap<>();
        m.put("inTime", hhmm(t.getCheckInAt().toLocalTime().toString()));
        m.put("outTime", t.getCheckOutAt() == null ? "" : hhmm(t.getCheckOutAt().toLocalTime().toString()));
        return m;
    }

    /** 본인을 업체 단말기의 사용자 번호와 연결한다 (번호는 숫자 4자리) */
    @Transactional
    public Map<String, Object> linkDevice(Long companyId, Long employeeId, String deviceUserNo) {
        if (deviceUserNo == null || !deviceUserNo.trim().matches("\\d{4}")) {
            throw new AttendanceException(400, "단말기 사용자 번호는 숫자 4자리입니다");
        }
        String no = deviceUserNo.trim();
        AttendanceDeviceDTO device = dao.selectCompanyDevice(companyId);
        if (device == null) {
            throw new AttendanceException(409, "업체에 등록된 근태 단말기가 없습니다");
        }
        if (dao.isCompanyExpired(companyId)) {
            throw new AttendanceException(403, "이용 기간이 끝나 읽기 전용입니다");
        }
        if (dao.countDeviceUserNoOfOthers(device.getAttendanceDeviceId(), no, employeeId) > 0) {
            throw new AttendanceException(409, "같은 업체의 다른 직원이 쓰는 번호입니다");
        }
        dao.deleteMyDeviceLink(device.getAttendanceDeviceId(), employeeId);
        try {
            dao.insertDeviceLink(companyId, device.getAttendanceDeviceId(), no, employeeId);
        } catch (DuplicateKeyException e) {
            throw new AttendanceException(409, "같은 업체의 다른 직원이 쓰는 번호입니다");
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deviceId", no);
        return res;
    }

    /** 본인의 단말기 사용자 번호 연결을 끊는다 (출퇴근 기록은 남는다) */
    @Transactional
    public Map<String, Object> unlinkDevice(Long companyId, Long employeeId) {
        AttendanceDeviceDTO device = dao.selectCompanyDevice(companyId);
        if (device != null) {
            if (dao.isCompanyExpired(companyId)) {
                throw new AttendanceException(403, "이용 기간이 끝나 읽기 전용입니다");
            }
            dao.deleteMyDeviceLink(device.getAttendanceDeviceId(), employeeId);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deviceId", "");
        return res;
    }

    // ===== 근무 > 근태 단말기 (업체 관리자·인사 담당) =====

    private static final String DEFAULT_DEVICE_NAME = "정문 지문 단말기";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 근태 단말기 화면: 업체 단말기, 번호가 연결된 사람, 연결할 수 있는 재직 직원 */
    public Map<String, Object> deviceAdmin(Long companyId) {
        AttendanceDeviceDTO device = dao.selectCompanyDevice(companyId);
        List<Map<String, Object>> people = new ArrayList<>();
        Map<Long, String> linked = new HashMap<>();
        if (device != null) {
            for (DevicePersonDTO p : dao.selectDevicePeople(device.getAttendanceDeviceId())) {
                linked.put(p.getEmployeeId(), p.getDeviceUserNo());
                people.add(personView(p, p.getDeviceUserNo()));
            }
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (DevicePersonDTO e : dao.selectCompanyEmployees(companyId)) {
            if ("ST_SERVING".equals(e.getEmploymentStatus()) && !linked.containsKey(e.getEmployeeId())) {
                candidates.add(personView(e, null));
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        if (device == null) {
            res.put("device", null);
        } else {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", device.getDeviceName());
            d.put("companyName", device.getCompanyName());
            res.put("device", d);
        }
        res.put("readOnly", dao.isCompanyExpired(companyId));
        res.put("people", people);
        res.put("employees", candidates);
        return res;
    }

    /**
     * 단말기 키 발급. 업체에 단말기가 없으면 함께 등록하고, 있으면 키만 바꾼다(예전 키는 바로 막힌다).
     * 키 원문은 이 응답에서 한 번만 돌려주고 DB에는 해시만 남긴다.
     */
    @Transactional
    public Map<String, Object> issueDeviceKey(Long companyId) {
        if (dao.isCompanyExpired(companyId)) {
            throw new AttendanceException(403, "이용 기간이 끝나 읽기 전용입니다");
        }
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AttendanceDeviceDTO device = dao.selectCompanyDevice(companyId);
        if (device == null) {
            dao.insertDevice(companyId, DEFAULT_DEVICE_NAME, sha256(key));
        } else {
            dao.updateDeviceKey(device.getAttendanceDeviceId(), companyId, sha256(key));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("key", key);
        res.put("deviceName", device == null ? DEFAULT_DEVICE_NAME : device.getDeviceName());
        res.put("created", device == null);
        return res;
    }

    /** 관리자가 직원을 단말기 번호에 연결한다 (같은 업체의 재직 직원만) */
    @Transactional
    public Map<String, Object> adminLinkDevice(Long companyId, Long employeeId, String deviceUserNo) {
        requireServingEmployee(companyId, employeeId);
        return linkDevice(companyId, employeeId, deviceUserNo);
    }

    /** 관리자가 직원의 단말기 번호 연결을 끊는다 (출퇴근 기록은 남는다) */
    @Transactional
    public Map<String, Object> adminUnlinkDevice(Long companyId, Long employeeId) {
        if (employeeId == null || dao.selectEmploymentStatus(companyId, employeeId) == null) {
            throw new AttendanceException(404, "같은 업체의 직원이 아닙니다");
        }
        return unlinkDevice(companyId, employeeId);
    }

    /**
     * 근태·연차 관리: 업체 직원별 오늘 출퇴근, 그 달 출근일·지각·조퇴, 오늘 기준 남은 연차.
     * 직원 수만큼 따로 묻지 않는다: 근태 요약 한 번, 연차 부여 행·사용 합 한 번.
     */
    public Map<String, Object> overview(Long companyId, String month) {
        requireMonth(month);
        LocalDate today = LocalDate.parse(dao.selectToday());
        Map<Long, LeaveCycleDTO> grants = leaveService.grantsOn(companyId, today);
        List<Map<String, Object>> items = new ArrayList<>();
        int serving = 0;
        int punched = 0;
        int lateToday = 0;
        for (AttendanceSummaryDTO a : dao.selectMonthSummary(companyId, month, LATE_AFTER, WORK_END)) {
            boolean isServing = "ST_SERVING".equals(a.getEmploymentStatus());
            if (isServing) serving++;
            boolean late = a.getTodayIn() != null && "LATE".equals(inStatus(a.getTodayIn()));
            if (a.getTodayIn() != null) punched++;
            if (late) lateToday++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", a.getEmployeeId());
            m.put("employeeNo", a.getEmployeeNo());
            m.put("name", a.getEmployeeName());
            m.put("dept", a.getDepartmentName() == null ? "" : a.getDepartmentName());
            m.put("serving", isServing);
            m.put("deviceNo", a.getDeviceUserNo() == null ? "" : a.getDeviceUserNo());
            m.put("todayIn", a.getTodayIn() == null ? "" : hhmm(a.getTodayIn()));
            m.put("todayOut", a.getTodayOut() == null ? "" : hhmm(a.getTodayOut()));
            m.put("todayLate", late);
            m.put("todayEarly", a.getTodayOut() != null && "EARLY_LEAVE".equals(outStatus(a.getTodayOut())));
            m.put("workDays", a.getWorkDays());
            m.put("late", a.getLateDays());
            m.put("early", a.getEarlyDays());
            // 남은 연차는 재직 중인 사람만 (퇴사자에게는 의미가 없다)
            m.put("leaveLeft", isServing ? LeaveService.remainingOf(grants.get(a.getEmployeeId()), a.getHireDate()) : null);
            items.add(m);
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("serving", serving);
        counts.put("punched", punched);
        counts.put("lateToday", lateToday);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("month", month);
        res.put("today", today.toString());
        res.put("counts", counts);
        res.put("items", items);
        return res;
    }

    private void requireServingEmployee(Long companyId, Long employeeId) {
        String status = employeeId == null ? null : dao.selectEmploymentStatus(companyId, employeeId);
        if (status == null) {
            throw new AttendanceException(404, "같은 업체의 직원이 아닙니다");
        }
        if (!"ST_SERVING".equals(status)) {
            throw new AttendanceException(409, "재직 중인 직원만 단말기에 연결할 수 있습니다");
        }
    }

    private static Map<String, Object> personView(DevicePersonDTO p, String userNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", p.getEmployeeId());
        m.put("employeeNo", p.getEmployeeNo());
        m.put("name", p.getEmployeeName());
        m.put("dept", p.getDepartmentName() == null ? "" : p.getDepartmentName());
        m.put("serving", "ST_SERVING".equals(p.getEmploymentStatus()));
        if (userNo != null) m.put("userNo", userNo);
        return m;
    }

    // ===== 도구 =====

    private static Map<String, String> punchView(AttendanceDTO a) {
        if (a == null) return null;
        Map<String, String> p = new LinkedHashMap<>();
        p.put("in", hhmm(a.getCheckIn()));
        p.put("out", a.getCheckOut() == null ? "" : hhmm(a.getCheckOut()));
        p.put("inStatus", inStatus(a.getCheckIn()));
        p.put("outStatus", a.getCheckOut() == null ? "" : outStatus(a.getCheckOut()));
        return p;
    }

    private static void requireMonth(String month) {
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new AttendanceException(400, "달은 YYYY-MM 형식으로 보내 주세요");
        }
    }

    private static String inStatus(String hms) {
        return hms.compareTo(LATE_AFTER) > 0 ? "LATE" : "NORMAL";
    }

    private static String outStatus(String hms) {
        return hms.compareTo(WORK_END) < 0 ? "EARLY_LEAVE" : "NORMAL";
    }

    private static String hhmm(String hms) {
        return hms.substring(0, 5);
    }

    /** 단말기 키는 해시(SHA-256, 16진수)로만 저장·비교한다 */
    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ===== 다른 모듈에 주는 메서드 (연동 계약, 10/30. 팀장이 정한 이름·인자 그대로, 구현은 LeaveService·HolidayService) =====

    /**
     * 휴가 기간을 하루씩 나눈다. 주말과 휴일(HOLIDAY)은 뺀다. 결재가 상신할 때 부른다.
     * 반차는 fromDate와 toDate가 같은 날이어야 한다. 휴일 초기 데이터가 없는 기간이 끼어 있거나
     * 남는 날이 없으면 IllegalArgumentException(계획서 4장 '근태·휴가의 시연 기준').
     *
     * @param leaveType LV_FULL(종일 연차)·LV_AM(오전 반차)·LV_PM(오후 반차)
     * @return 날짜순. leaveRequestDayId는 비어 있다(결재가 저장한 뒤 채운다)
     */
    public List<LeaveDayDTO> calculateLeaveDays(Long companyId, String leaveType, LocalDate fromDate, LocalDate toDate) {
        return leaveService.calculateLeaveDays(companyId, leaveType, fromDate, toDate);
    }

    /**
     * 오늘이 속한 연차 주기의 남은 일수(부여 일수 + 사용·복원 내역 합). 주기가 없으면 0.
     * 승인 대기 중인 휴가는 결재 쪽 자료라 여기서 빼지 않는다.
     * (구현 메모: 연차는 모든 직원 15일이고 자동 부여가 없어 주기는 입사일부터 하나다. 그 행이 아직 없으면 15일을 돌려준다.
     *  입사일이 없을 때만 0. 행은 첫 차감(useLeave) 때도 만든다. 그래서 행이 없던 기존 직원도 휴가를 낼 수 있다)
     */
    public BigDecimal getRemainingLeaveDays(Long companyId, Long employeeId) {
        return leaveService.getRemainingLeaveDays(companyId, employeeId);
    }

    /**
     * 이미 승인된 휴가와 날짜·시간대가 겹치는지 본다. 종일 연차는 오전·오후를 모두 쓴 것으로 본다.
     * 예: 승인된 오전 반차가 있는 날에 종일 연차나 오전 반차를 내면 true, 오후 반차면 false.
     * 결재가 상신할 때 부른다. 아직 승인되지 않은 문서끼리의 겹침은 보지 않는다(결재 쪽 자료).
     */
    public boolean hasApprovedLeaveOverlap(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
        return leaveService.hasApprovedLeaveOverlap(companyId, employeeId, days);
    }

    /**
     * 휴가 최종 승인 때 연차를 차감한다. 결재 최종 승인 트랜잭션 안에서 부른다.
     * 연차 주기 행(LEAVE_CYCLE)을 SELECT ... FOR UPDATE로 잠근 뒤 남은 일수와 날짜 겹침을 다시 확인하고,
     * 하루마다 시간대(LEAVE_SLOT)와 사용 내역(LEAVE_LEDGER, LC_USE)을 저장한다.
     * 남은 일수가 모자라거나 겹치면 IllegalStateException(결재도 함께 취소된다).
     *
     * @param employeeId     휴가를 쓰는 사람(기안자)
     * @param actorAccountId 최종 승인한 사람의 계정 ID(LoginUser.getUserAccountId())
     * @param days           결재가 저장한 휴가 날짜 행(leaveRequestDayId가 채워져 있어야 한다)
     */
    public void useLeave(Long companyId, Long employeeId, Long actorAccountId, List<LeaveDayDTO> days) {
        leaveService.useLeave(companyId, employeeId, actorAccountId, days);
    }

    /**
     * 새 사원에게 첫 연차 주기와 기본 15일을 준다(요구분석서 3.9 '최초 부여').
     * 사원을 만드는 쪽(직원 가입 승인, 체험 신청의 관리자 계정)이 같은 트랜잭션 안에서 부른다.
     *
     * @param hireDate 입사일. 이 날부터 첫 연차 주기를 센다
     * (구현 메모: 모든 직원 15일, 경력으로 바꾸지 않는다. 입사기념일 자동 부여는 없다. 이미 있으면 그대로 둔다)
     */
    public void grantInitialLeave(Long companyId, Long employeeId, LocalDate hireDate) {
        leaveService.grantInitialLeave(companyId, employeeId, hireDate);
    }

    /**
     * 기간 안의 휴일 목록(날짜순). 일정 달력이 부른다.
     */
    public List<HolidayDTO> getHolidays(Long companyId, LocalDate fromDate, LocalDate toDate) {
        return holidayService.holidays(companyId, fromDate, toDate);
    }

    /**
     * 오늘(서버 날짜) 출퇴근 기록. 출근 기록이 없으면 null. 헤더의 출퇴근 표시가 요청마다 부른다(AGENTS.md 72줄).
     */
    public TodayAttendanceDTO getTodayAttendance(Long companyId, Long employeeId) {
        return dao.selectTodayTimes(companyId, employeeId);
    }
}
