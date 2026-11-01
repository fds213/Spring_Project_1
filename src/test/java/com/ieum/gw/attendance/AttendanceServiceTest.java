package com.ieum.gw.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.ieum.gw.attendance.dao.AttendanceDAO;
import com.ieum.gw.attendance.dto.AttendanceDTO;
import com.ieum.gw.attendance.dto.AttendanceDeviceDTO;
import com.ieum.gw.attendance.dto.DevicePersonDTO;
import com.ieum.gw.attendance.dto.HolidayDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.dto.PunchDTO;
import com.ieum.gw.attendance.mapper.AttendanceMapper;
import com.ieum.gw.attendance.service.AttendanceException;
import com.ieum.gw.attendance.service.AttendanceService;

/** 지문 단말기 출퇴근 규칙 (요구분석서 3.4·3.10, AGENTS.md 테스트: 만료 읽기 전용을 단말기로 우회하는 요청 거절) */
class AttendanceServiceTest {

    private static final String KEY = "test-device-key";

    private FakeMapper mapper;
    private AttendanceService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeMapper();
        service = new AttendanceService(new AttendanceDAO(mapper));
    }

    @Test
    @DisplayName("출근하면 받은 시각으로 기록하고, 9시가 지났으면 지각으로 답한다")
    void checkInLate() {
        mapper.clock = "09:05:12";
        Map<String, Object> r = service.punch(KEY, "0002", "IN");
        assertEquals("IN", r.get("type"));
        assertEquals("09:05:12", r.get("time"));
        assertEquals("LATE", r.get("status"));
        assertEquals(1, mapper.events.size());
    }

    @Test
    @DisplayName("같은 날 두 번째 출근은 409로 거절한다")
    void duplicateCheckIn() {
        service.punch(KEY, "0002", "IN");
        AttendanceException e = assertThrows(AttendanceException.class, () -> service.punch(KEY, "0002", "IN"));
        assertEquals(409, e.getStatus());
        assertEquals(1, mapper.events.size());
    }

    @Test
    @DisplayName("출근 없는 퇴근은 409로 거절한다")
    void checkOutWithoutCheckIn() {
        AttendanceException e = assertThrows(AttendanceException.class, () -> service.punch(KEY, "0002", "OUT"));
        assertEquals(409, e.getStatus());
    }

    @Test
    @DisplayName("퇴근은 하루 한 번: 18시 전 퇴근은 조퇴, 두 번째 퇴근은 409")
    void checkOutOnce() {
        mapper.clock = "08:50:00";
        service.punch(KEY, "0002", "IN");
        mapper.clock = "17:30:00";
        assertEquals("EARLY_LEAVE", service.punch(KEY, "0002", "OUT").get("status"));
        AttendanceException e = assertThrows(AttendanceException.class, () -> service.punch(KEY, "0002", "OUT"));
        assertEquals(409, e.getStatus());
    }

    @Test
    @DisplayName("09:00:00 정각 출근과 18:00:00 정각 퇴근은 정상, 1초만 지나거나 모자라면 지각·조퇴")
    void boundaryTimes() {
        mapper.clock = "09:00:00";
        assertEquals("NORMAL", service.punch(KEY, "0002", "IN").get("status"));
        mapper.clock = "18:00:00";
        assertEquals("NORMAL", service.punch(KEY, "0002", "OUT").get("status"));

        mapper.today.clear();
        mapper.clock = "09:00:01";
        assertEquals("LATE", service.punch(KEY, "0002", "IN").get("status"));
        mapper.clock = "17:59:59";
        assertEquals("EARLY_LEAVE", service.punch(KEY, "0002", "OUT").get("status"));
    }

    @Test
    @DisplayName("출근·퇴근이 아닌 종류와 빈 사용자 번호는 400, 키 앞뒤 공백은 지우고 본다")
    void punchInput() {
        assertEquals(400, assertThrows(AttendanceException.class, () -> service.punch(KEY, "0002", "SLEEP")).getStatus());
        assertEquals(400, assertThrows(AttendanceException.class, () -> service.punch(KEY, "  ", "IN")).getStatus());
        assertEquals(0, mapper.events.size());
        assertEquals("IN", service.punch("  " + KEY + " ", "0002", "IN").get("type"));
    }

    @Test
    @DisplayName("월별 기록: 달 형식이 틀리면 400, 2월은 28일, 찍은 날은 기록과 지각 수가 채워진다")
    void records() {
        assertEquals(400, assertThrows(AttendanceException.class, () -> service.records(10L, 102L, "2026-13")).getStatus());
        assertEquals(400, assertThrows(AttendanceException.class, () -> service.records(10L, 102L, "202610")).getStatus());

        Map<String, Object> feb = service.records(10L, 102L, "2026-02");
        assertEquals(28, ((List<?>) feb.get("days")).size());

        mapper.clock = "09:10:00";
        service.punch(KEY, "0002", "IN");
        Map<String, Object> r = service.records(10L, 102L, "2026-10");
        assertEquals("0002", r.get("deviceId"));
        assertEquals(31, ((List<?>) r.get("days")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> today = (Map<String, Object>) r.get("today");
        @SuppressWarnings("unchecked")
        Map<String, String> punch = (Map<String, String>) today.get("punch");
        assertEquals("09:10", punch.get("in"));
        assertEquals("LATE", punch.get("inStatus"));
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) r.get("thisMonth");
        assertEquals(1, counts.get("late"));
        assertEquals(0, counts.get("early"));
    }

    @Test
    @DisplayName("연결을 끊으면 번호가 비고, 단말기가 없는 업체의 연결은 409")
    void unlinkAndNoDevice() {
        assertEquals("", service.unlinkDevice(10L, 102L).get("deviceId"));
        assertEquals("", service.records(10L, 102L, "2026-10").get("deviceId"));
        mapper.hasDevice = false;
        assertEquals(409, assertThrows(AttendanceException.class, () -> service.linkDevice(10L, 103L, "0009")).getStatus());
    }

    @Test
    @DisplayName("이용 기간이 끝난 업체의 단말기 체크는 403으로 거절한다 (읽기 전용 우회 막기)")
    void expiredCompany() {
        mapper.expired = true;
        AttendanceException e = assertThrows(AttendanceException.class, () -> service.punch(KEY, "0002", "IN"));
        assertEquals(403, e.getStatus());
        assertEquals(0, mapper.events.size());
    }

    @Test
    @DisplayName("단말기 키가 없거나 틀리면 401로 거절한다")
    void wrongKey() {
        assertEquals(401, assertThrows(AttendanceException.class, () -> service.punch(null, "0002", "IN")).getStatus());
        assertEquals(401, assertThrows(AttendanceException.class, () -> service.punch("other-key", "0002", "IN")).getStatus());
    }

    @Test
    @DisplayName("단말기에 없는 번호는 404, 퇴사한 직원은 403으로 거절한다")
    void unknownOrRetired() {
        assertEquals(404, assertThrows(AttendanceException.class, () -> service.punch(KEY, "0099", "IN")).getStatus());
        assertEquals(403, assertThrows(AttendanceException.class, () -> service.punch(KEY, "0005", "IN")).getStatus());
    }

    @Test
    @DisplayName("본인 연결 번호는 숫자 4자리, 같은 업체의 다른 직원이 쓰는 번호면 409")
    void linkDevice() {
        assertEquals(400, assertThrows(AttendanceException.class, () -> service.linkDevice(10L, 103L, "12a")).getStatus());
        assertEquals(409, assertThrows(AttendanceException.class, () -> service.linkDevice(10L, 103L, "0002")).getStatus());
        assertEquals("0009", service.linkDevice(10L, 103L, "0009").get("deviceId"));
    }

    @Test
    @DisplayName("단말기가 없는 업체는 키를 발급하면 단말기가 함께 등록되고, 그 키로 바로 찍을 수 있다")
    void issueKeyCreatesDevice() {
        mapper.hasDevice = false;
        Map<String, Object> r = service.issueDeviceKey(10L);
        assertEquals(true, r.get("created"));
        String newKey = (String) r.get("key");
        assertEquals("IN", service.punch(newKey, "0002", "IN").get("type"));
    }

    @Test
    @DisplayName("키를 다시 발급하면 예전 키는 바로 401로 막힌다")
    void reissueKeyBlocksOldKey() {
        String newKey = (String) service.issueDeviceKey(10L).get("key");
        assertEquals(401, assertThrows(AttendanceException.class, () -> service.punch(KEY, "0002", "IN")).getStatus());
        assertEquals("IN", service.punch(newKey, "0002", "IN").get("type"));
    }

    @Test
    @DisplayName("만료 업체는 키를 발급할 수 없고, 관리자는 다른 업체 직원·퇴사자를 연결할 수 없다")
    void adminRules() {
        mapper.expired = true;
        assertEquals(403, assertThrows(AttendanceException.class, () -> service.issueDeviceKey(10L)).getStatus());
        mapper.expired = false;
        assertEquals(404, assertThrows(AttendanceException.class, () -> service.adminLinkDevice(10L, 999L, "0007")).getStatus());
        assertEquals(409, assertThrows(AttendanceException.class, () -> service.adminLinkDevice(10L, 105L, "0007")).getStatus());
        assertEquals("0007", service.adminLinkDevice(10L, 103L, "0007").get("deviceId"));
    }

    /** 팀 DB 없이 규칙만 시험하는 메모리 Mapper: 업체 10, 단말기 1, 사원 0002(재직)·0005(퇴사) */
    static class FakeMapper implements AttendanceMapper {
        String clock = "08:55:00";
        boolean expired = false;
        boolean hasDevice = true;
        String keyHash = sha(KEY);
        final Map<Long, String> status = new HashMap<>(Map.of(102L, "ST_SERVING", 103L, "ST_SERVING", 105L, "ST_RETIRED"));
        final Map<Long, AttendanceDTO> today = new HashMap<>();
        final List<PunchDTO> events = new ArrayList<>();
        final Map<String, Long> links = new HashMap<>(Map.of("0002", 102L, "0005", 105L));
        long seq = 1000;

        private DevicePersonDTO person(String no, long id, String status) {
            DevicePersonDTO p = new DevicePersonDTO();
            p.setDeviceUserNo(no);
            p.setEmployeeId(id);
            p.setEmployeeName("사원" + no);
            p.setEmploymentStatus(status);
            return p;
        }

        private AttendanceDeviceDTO device() {
            AttendanceDeviceDTO d = new AttendanceDeviceDTO();
            d.setAttendanceDeviceId(1L);
            d.setCompanyId(10L);
            d.setCompanyName("테스트업체");
            d.setDeviceName("정문 단말기");
            return d;
        }

        @Override public String selectToday() { return "2026-10-30"; }

        @Override public AttendanceDeviceDTO selectDeviceByKeyHash(String hash) {
            return hasDevice && hash.equals(keyHash) ? device() : null;
        }

        @Override public AttendanceDeviceDTO selectCompanyDevice(Long companyId) { return hasDevice && companyId == 10L ? device() : null; }

        @Override public int insertDevice(Long companyId, String deviceName, String hash) { hasDevice = true; keyHash = hash; return 1; }

        @Override public int updateDeviceKey(Long deviceId, Long companyId, String hash) { keyHash = hash; return 1; }

        @Override public List<DevicePersonDTO> selectCompanyEmployees(Long companyId) { return selectDevicePeople(1L); }

        @Override public String selectEmploymentStatus(Long companyId, Long employeeId) { return companyId == 10L ? status.get(employeeId) : null; }

        @Override public int isCompanyExpired(Long companyId) { return expired ? 1 : 0; }

        @Override public List<DevicePersonDTO> selectDevicePeople(Long id) {
            return List.of(person("0002", 102, "ST_SERVING"), person("0005", 105, "ST_RETIRED"));
        }

        @Override public DevicePersonDTO selectDevicePerson(Long id, String no) {
            if ("0002".equals(no)) return person("0002", 102, "ST_SERVING");
            if ("0005".equals(no)) return person("0005", 105, "ST_RETIRED");
            return null;
        }

        @Override public int insertCheckIn(PunchDTO dto) {
            if (today.containsKey(dto.getEmployeeId())) throw new DuplicateKeyException("UQ_36_01");
            AttendanceDTO a = new AttendanceDTO();
            a.setAttendanceId(++seq);
            a.setWorkDate(selectToday());
            a.setCheckIn(clock);
            dto.setAttendanceId(a.getAttendanceId());
            today.put(dto.getEmployeeId(), a);
            return 1;
        }

        @Override public int updateCheckOut(PunchDTO dto) {
            for (AttendanceDTO a : today.values()) {
                if (a.getAttendanceId().equals(dto.getAttendanceId()) && a.getCheckOut() == null) {
                    a.setCheckOut(clock);
                    return 1;
                }
            }
            return 0;
        }

        @Override public int insertEvent(PunchDTO dto) { events.add(dto); return 1; }

        @Override public AttendanceDTO selectTodayAttendance(Long companyId, Long employeeId) { return today.get(employeeId); }

        @Override public List<AttendanceDTO> selectMonthAttendance(Long companyId, Long employeeId, String month) {
            return today.containsKey(employeeId) ? List.of(today.get(employeeId)) : List.of();
        }

        @Override public List<HolidayDTO> selectMonthHolidays(Long companyId, String month) { return List.of(); }

        @Override public List<LeaveDayDTO> selectMonthLeaveDays(Long companyId, Long employeeId, String month) { return List.of(); }

        @Override public String selectMyDeviceUserNoInCompany(Long companyId, Long employeeId) { return selectMyDeviceUserNo(1L, employeeId); }

        String selectMyDeviceUserNo(Long deviceId, Long employeeId) { // 가짜 안에서만 쓰는 도우미
            return links.entrySet().stream().filter(e -> e.getValue().equals(employeeId)).map(Map.Entry::getKey).findFirst().orElse(null);
        }

        @Override public int countDeviceUserNoOfOthers(Long deviceId, String no, Long employeeId) {
            Long owner = links.get(no);
            return owner != null && !owner.equals(employeeId) ? 1 : 0;
        }

        @Override public int deleteMyDeviceLink(Long deviceId, Long employeeId) {
            return links.values().removeIf(v -> v.equals(employeeId)) ? 1 : 0;
        }

        @Override public int insertDeviceLink(Long companyId, Long deviceId, String no, Long employeeId) {
            if (links.containsKey(no)) throw new DuplicateKeyException("PK_DEVICE_EMPLOYEE");
            links.put(no, employeeId);
            return 1;
        }

        private static String sha(String s) {
            try {
                byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : d) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
