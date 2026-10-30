package com.ieum.gw.attendance.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.attendance.dao.AttendanceDAO;
import com.ieum.gw.attendance.dto.AttendanceDTO;
import com.ieum.gw.attendance.dto.AttendanceDeviceDTO;
import com.ieum.gw.attendance.dto.DevicePersonDTO;
import com.ieum.gw.attendance.dto.HolidayDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.dto.PunchDTO;

import lombok.RequiredArgsConstructor;

/**
 * 근태: 지문 단말기(시뮬레이터)의 출퇴근 수신과, 로그인한 본인의 근태 조회 (요구분석서 3.4·3.10, 기능명세서 1.9).
 * - 출퇴근 시각은 이음이 받은 서울 시각이다. 단말기가 보낸 시각은 받지 않는다.
 * - 같은 날 출근·퇴근은 각 1건. 중복 출근, 출근 없는 퇴근, 두 번째 퇴근은 거절한다.
 * - 단말기는 키(해시로 저장)로 업체를 찾는다. 다른 업체의 같은 사용자 번호와 섞이지 않는다.
 * - 이용 기간이 끝난 업체(읽기 전용)의 체크는 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    // 기본 근무 09:00~18:00 (요구분석서 3.4)
    private static final String WORK_START = "09:00:00";
    private static final String WORK_END = "18:00:00";

    // 휴가 구분 공통 코드 → 화면 값
    private static final Map<String, String> LEAVE_KIND = Map.of("LV_FULL", "FULL", "LV_AM", "AM", "LV_PM", "PM");

    private final AttendanceDAO dao;

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
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new AttendanceException(400, "달은 YYYY-MM 형식으로 보내 주세요");
        }
        String deviceNo = dao.selectMyDeviceUserNoInCompany(companyId, employeeId);
        String today = dao.selectToday();

        Map<String, AttendanceDTO> byDate = new HashMap<>();
        for (AttendanceDTO a : dao.selectMonthAttendance(companyId, employeeId, month)) {
            byDate.put(a.getWorkDate(), a);
        }
        Map<String, String> holidays = new HashMap<>();
        for (HolidayDTO h : dao.selectMonthHolidays(companyId, month)) {
            holidays.put(h.getHolidayDate(), h.getHolidayName());
        }
        // 최종 승인된 휴가: 오전·오후 반차가 같은 날 둘 다 있으면 종일로 본다
        Map<String, String> leaves = new HashMap<>();
        for (LeaveDayDTO l : dao.selectMonthLeaveDays(companyId, employeeId, month)) {
            String kind = LEAVE_KIND.getOrDefault(l.getLeaveType(), "FULL");
            String before = leaves.get(l.getLeaveDate());
            leaves.put(l.getLeaveDate(), before == null || before.equals(kind) ? kind : "FULL");
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
        LocalDate first = LocalDate.parse(month + "-01");
        for (LocalDate d = first; d.getMonthValue() == first.getMonthValue(); d = d.plusDays(1)) {
            String date = d.toString();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("weekday", d.getDayOfWeek().getValue() % 7); // 0 = 일요일
            day.put("holiday", holidays.getOrDefault(date, ""));
            day.put("punch", punchView(byDate.get(date)));
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
        AttendanceDTO a = dao.selectTodayAttendance(companyId, employeeId);
        if (a == null) return null;
        Map<String, String> m = new HashMap<>();
        m.put("inTime", hhmm(a.getCheckIn()));
        m.put("outTime", a.getCheckOut() == null ? "" : hhmm(a.getCheckOut()));
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

    private static String inStatus(String hms) {
        return hms.compareTo(WORK_START) > 0 ? "LATE" : "NORMAL";
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
}
