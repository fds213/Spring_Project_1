package com.ieum.gw.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.ieum.gw.dao.AttendanceCorrectionDAO;
import com.ieum.gw.dao.AttendanceDAO;
import com.ieum.gw.dto.AttendanceCorrectionDTO;
import com.ieum.gw.dto.AttendanceDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.mapper.AttendanceCorrectionMapper;
import com.ieum.gw.service.AttendanceCorrectionService;
import com.ieum.gw.service.AttendanceException;
import com.ieum.gw.service.NotificationService;

/**
 * 근태 정정 요청 (퇴근 누락 1차). 오늘은 2026-10-30(AttendanceServiceTest.FakeMapper), 업체 10.
 * 직원 102·103 재직, 105 퇴사. 102·103의 부서장은 103, 104는 부서장 없는 부서. 900·901은 업체 관리자.
 */
class AttendanceCorrectionServiceTest {

    private static final long CO = 10L;

    private FakeCorrectionMapper mapper;
    private AttendanceServiceTest.FakeMapper attMapper;
    private RecordingNotifications notifications;
    private AttendanceCorrectionService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeCorrectionMapper();
        attMapper = new AttendanceServiceTest.FakeMapper();
        attMapper.status.put(104L, "ST_SERVING");
        notifications = new RecordingNotifications();
        service = new AttendanceCorrectionService(new AttendanceCorrectionDAO(mapper), new AttendanceDAO(attMapper), notifications);
        mapper.attendance(102L, "2026-10-29", "09:05:00", null);   // 퇴근 누락
        mapper.attendance(102L, "2026-10-28", "08:50:00", "18:10:00");
        mapper.attendance(103L, "2026-10-29", "09:00:00", null);   // 부서장 본인의 퇴근 누락
        mapper.attendance(104L, "2026-10-29", "09:00:00", null);
        mapper.attendance(105L, "2026-10-29", "09:00:00", null);
        mapper.head.put(102L, 103L);
        mapper.head.put(103L, 103L);
        mapper.head.put(104L, null);
        mapper.head.put(105L, 103L);
    }

    /** 같은 업체(CO)의 로그인 사용자 */
    private static LoginUser user(long employeeId, String role) {
        AuthEmpDTO a = new AuthEmpDTO();
        a.setCompanyId(CO);
        a.setEmployeeId(employeeId);
        a.setUserAccountId(employeeId + 1000);
        a.setRole(role);
        return new LoginUser(a);
    }

    private static final LoginUser EMP_102 = user(102L, "ROLE_EMP");
    private static final LoginUser HEAD_103 = user(103L, "ROLE_EMP");
    private static final LoginUser EMP_104 = user(104L, "ROLE_EMP");
    private static final LoginUser ADMIN_900 = user(900L, "ROLE_ADMIN");

    /** 거절된 요청의 상태 코드 */
    private static int status(Runnable r) {
        return assertThrows(AttendanceException.class, r::run).getStatus();
    }

    /** 102 의 10/29 퇴근 누락을 18:20 으로 요청하고 번호를 돌려준다 */
    private Long request102() {
        return (Long) service.request(EMP_102, "2026-10-29", null, "18:20", "단말기 고장으로 못 찍음").get("id");
    }

    @Test
    @DisplayName("요청: 지난 날 퇴근 누락만, 원래 출퇴근은 근태 행에서 복사한다")
    void requestCopiesOrigin() {
        Long id = request102();
        AttendanceCorrectionDTO saved = mapper.byId(id);
        assertEquals("ACR_REQUESTED", saved.getStatusCode());
        assertEquals("09:05:00", saved.getOriginCheckIn());
        assertNull(saved.getOriginCheckOut());
        assertEquals("18:20", saved.getReqCheckOut());
        assertEquals("단말기 고장으로 못 찍음", saved.getReason());
    }

    @Test
    @DisplayName("요청 거절: 오늘·앞날 409, 시각·날짜·사유 형식 400, 출근 없는 날에 출근 시각 없음 400, 퇴근 있음 409, 출근보다 이른 퇴근 400, 퇴사자 403")
    void requestRejected() {
        assertEquals(409, status(() -> service.request(EMP_102, "2026-10-30", null, "18:00", "사유")));
        assertEquals(409, status(() -> service.request(EMP_102, "2026-11-02", null, "18:00", "사유")));
        assertEquals(400, status(() -> service.request(EMP_102, "2026-10-29", null, "6pm", "사유")));
        assertEquals(400, status(() -> service.request(EMP_102, "2026/10/29", null, "18:00", "사유")));
        assertEquals(400, status(() -> service.request(EMP_102, "2026-10-29", null, "18:00", "  ")));
        assertEquals(400, status(() -> service.request(EMP_102, "2026-10-29", null, "18:00", "가".repeat(501))));
        assertEquals(400, status(() -> service.request(EMP_102, "2026-10-27", null, "18:00", "기록 없는 날(결근)인데 출근 시각 없음")));
        assertEquals(409, status(() -> service.request(EMP_102, "2026-10-28", null, "19:00", "퇴근 있음")));
        assertEquals(400, status(() -> service.request(EMP_102, "2026-10-29", null, "09:00", "출근보다 이름")));
        assertEquals(400, status(() -> service.request(EMP_102, "2026-10-29", "08:00", "18:00", "퇴근 누락 날에 출근까지")));
        assertEquals(403, status(() -> service.request(user(105L, "ROLE_EMP"), "2026-10-29", null, "18:00", "퇴사자")));
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    @DisplayName("기한: 근무일로부터 7일까지(오늘 10/30 → 10/23 까지), 8일 전은 409")
    void requestWindow() {
        AttendanceException late = assertThrows(AttendanceException.class,
                () -> service.request(EMP_102, "2026-10-22", "09:00", "18:00", "8일 전"));
        assertEquals(409, late.getStatus());
        assertTrue(late.getMessage().contains("7일"));
        service.request(EMP_102, "2026-10-23", "09:00", "18:00", "딱 7일 전(결근)");
        assertEquals(1, mapper.rows.size());
    }

    @Test
    @DisplayName("결근 정정: 기록 없는 근무일에 출근·퇴근 시각을 받고, 승인하면 그날 근태가 생긴다")
    @SuppressWarnings("unchecked")
    void absenceApprove() {
        Long id = (Long) service.request(EMP_102, "2026-10-27", "09:00", "18:05", "외근 후 바로 퇴근").get("id");
        AttendanceCorrectionDTO c = mapper.byId(id);
        assertNull(c.getAttendanceId());
        assertEquals("09:00", c.getReqCheckIn());
        assertEquals("ABSENT", ((List<Map<String, Object>>) (List<?>) service.inbox(HEAD_103).get("items")).get(0).get("kind"));
        service.approve(HEAD_103, id);
        AttendanceDTO made = mapper.att.get("102/2026-10-27");
        assertEquals("09:00:00", made.getCheckIn());
        assertEquals("18:05:00", made.getCheckOut());
        assertEquals("ACR_APPROVED", mapper.byId(id).getStatusCode());
    }

    @Test
    @DisplayName("결근 정정 거절: 주말·휴일 409, 퇴근이 출근보다 이르면 400. 승인 전에 그날 근태가 생기면 409(UQ_36_01)")
    void absenceRejected() {
        assertEquals(409, status(() -> service.request(EMP_102, "2026-10-25", "09:00", "18:00", "일요일")));
        attMapper.holidays.put(java.time.LocalDate.parse("2026-10-26"), "임시 휴일");
        assertEquals(409, status(() -> service.request(EMP_102, "2026-10-26", "09:00", "18:00", "휴일")));
        assertEquals(400, status(() -> service.request(EMP_102, "2026-10-27", "18:00", "09:00", "거꾸로")));
        mapper.hire.put(102L, "2026-10-28");
        assertEquals(409, status(() -> service.request(EMP_102, "2026-10-27", "09:00", "18:00", "입사 전 날")));
        mapper.hire.put(102L, "2026-10-27"); // 입사일 당일은 된다

        Long id = (Long) service.request(EMP_102, "2026-10-27", "09:00", "18:00", "결근 정정").get("id");
        mapper.attendance(102L, "2026-10-27", "09:30", null); // 그새 그날 근태가 생겼다
        assertEquals(409, status(() -> service.approve(HEAD_103, id)));
        assertEquals("ACR_REQUESTED", mapper.byId(id).getStatusCode());
    }

    @Test
    @DisplayName("같은 날 대기 요청은 하나만(UX_82_01 → 409). 취소하면 다시 요청할 수 있다")
    void onePendingPerDay() {
        Long id = request102();
        assertEquals(409, status(this::request102));
        service.cancel(EMP_102, id);
        assertEquals("ACR_CANCELED", mapper.byId(id).getStatusCode());
        request102();
        assertEquals(2, mapper.rows.size());
    }

    @Test
    @DisplayName("승인(부서장): 요청한 퇴근 시각이 그날 근태에 들어가고 승인으로 바뀐다")
    void approveByHead() {
        Long id = request102();
        service.approve(HEAD_103, id);
        assertEquals("18:20:00", mapper.att.get("102/2026-10-29").getCheckOut());
        AttendanceCorrectionDTO c = mapper.byId(id);
        assertEquals("ACR_APPROVED", c.getStatusCode());
        assertEquals(Long.valueOf(103L), mapper.approver.get(id));
        assertEquals(409, status(() -> service.approve(HEAD_103, id))); // 두 번은 안 된다
        assertEquals(409, status(() -> service.reject(HEAD_103, id, "늦게 반려")));
        assertEquals(409, status(() -> service.cancel(EMP_102, id)));
    }

    @Test
    @DisplayName("처리자: 부서장이 있으면 부서장만(업체 관리자도 안 됨), 없거나 부서장 본인 요청이면 업체 관리자. 본인 요청은 403")
    void whoDecides() {
        Long id = request102();
        assertEquals(403, status(() -> service.approve(EMP_102, id)));    // 본인
        assertEquals(403, status(() -> service.approve(EMP_104, id)));    // 상관없는 직원
        assertEquals(403, status(() -> service.approve(ADMIN_900, id)));  // 부서장이 있으면 관리자 아님

        Long headOwn = (Long) service.request(HEAD_103, "2026-10-29", null, "18:00", "부서장 본인").get("id");
        assertEquals(403, status(() -> service.approve(HEAD_103, headOwn)));
        service.approve(ADMIN_900, headOwn);

        Long noHead = (Long) service.request(EMP_104, "2026-10-29", null, "18:30", "부서장 없는 부서").get("id");
        assertEquals(403, status(() -> service.approve(HEAD_103, noHead)));
        service.approve(ADMIN_900, noHead);
        assertEquals("18:30:00", mapper.att.get("104/2026-10-29").getCheckOut());
    }

    @Test
    @DisplayName("부서장이 퇴사·휴직했으면 없는 것으로 보고 업체 관리자가 처리한다 (그대로 두면 아무도 처리 못 함)")
    @SuppressWarnings("unchecked")
    void headLeft() {
        Long id = request102();
        mapper.leftHeads.add(103L);
        assertEquals(403, status(() -> service.approve(HEAD_103, id)));
        assertEquals(1, ((List<?>) service.inbox(ADMIN_900).get("items")).size());
        assertTrue(((List<?>) service.inbox(HEAD_103).get("items")).isEmpty());
        service.approve(ADMIN_900, id);
        assertEquals("ACR_APPROVED", mapper.byId(id).getStatusCode());
    }

    @Test
    @DisplayName("반려: 사유가 꼭 있고, 근태는 그대로(퇴근 누락으로 남는다)")
    void reject() {
        Long id = request102();
        assertEquals(400, status(() -> service.reject(HEAD_103, id, " ")));
        service.reject(HEAD_103, id, "그날 18시 전에 나간 기록이 있습니다");
        AttendanceCorrectionDTO c = mapper.byId(id);
        assertEquals("ACR_REJECTED", c.getStatusCode());
        assertEquals("그날 18시 전에 나간 기록이 있습니다", c.getRejectReason());
        assertNull(mapper.att.get("102/2026-10-29").getCheckOut());
    }

    @Test
    @DisplayName("취소: 본인 대기 요청만. 남의 요청은 404, 없는 번호 404")
    void cancel() {
        Long id = request102();
        assertEquals(404, status(() -> service.cancel(EMP_104, id)));
        assertEquals(404, status(() -> service.cancel(EMP_102, 99999L)));
        service.cancel(EMP_102, id);
        assertEquals(409, status(() -> service.approve(HEAD_103, id)));
    }

    @Test
    @DisplayName("승인 때 그날 퇴근이 이미 채워져 있으면 409, 요청은 대기 그대로")
    void approveWhenAlreadyOut() {
        Long id = request102();
        mapper.att.get("102/2026-10-29").setCheckOut("18:00:00");
        assertEquals(409, status(() -> service.approve(HEAD_103, id)));
        assertEquals("ACR_REQUESTED", mapper.byId(id).getStatusCode());
    }

    @Test
    @DisplayName("처리할 목록: 부서장은 부서원 것(본인 것 빼고), 업체 관리자는 부서장 없거나 부서장 본인 것, 그 밖은 빈 목록")
    @SuppressWarnings("unchecked")
    void inbox() {
        request102();
        service.request(HEAD_103, "2026-10-29", null, "18:00", "부서장 본인");
        service.request(EMP_104, "2026-10-29", null, "18:00", "부서장 없는 부서");
        List<Map<String, Object>> head = (List<Map<String, Object>>) service.inbox(HEAD_103).get("items");
        assertEquals(List.of(102L), head.stream().map(m -> mapper.byId((Long) m.get("id")).getEmployeeId()).collect(Collectors.toList()));
        List<Map<String, Object>> admin = (List<Map<String, Object>>) service.inbox(ADMIN_900).get("items");
        assertEquals(List.of(103L, 104L), admin.stream().map(m -> mapper.byId((Long) m.get("id")).getEmployeeId()).sorted().collect(Collectors.toList()));
        assertTrue(((List<?>) service.inbox(EMP_104).get("items")).isEmpty());
    }

    @Test
    @DisplayName("내 요청 목록: 그 달 것만 최근 순, 상태는 짧은 이름. 달 형식이 틀리면 400")
    @SuppressWarnings("unchecked")
    void mine() {
        Long first = request102();
        service.cancel(EMP_102, first);
        request102();
        List<Map<String, Object>> items = (List<Map<String, Object>>) service.myCorrections(EMP_102, "2026-10").get("items");
        assertEquals(List.of("REQUESTED", "CANCELED"), items.stream().map(m -> m.get("status")).collect(Collectors.toList()));
        assertEquals("09:05", items.get(0).get("in"));
        assertTrue(((List<?>) service.myCorrections(EMP_102, "2026-09").get("items")).isEmpty());
        assertEquals(400, status(() -> service.myCorrections(EMP_102, "2026-13")));
    }

    @Test
    @DisplayName("알림(요청): 부서장이 있으면 부서장 한 명에게, 요청자 이름·근무일과 함께")
    void notifyHeadOnRequest() {
        Long id = request102();
        assertEquals(List.of("REQUEST 103 " + id + " 직원102 2026-10-29"), notifications.sent);
    }

    @Test
    @DisplayName("알림(요청): 부서장 본인 요청·부서장 없는 부서·부서장 퇴사면 업체 관리자 전원에게(요청자 본인은 빠진다)")
    void notifyAdminsOnRequest() {
        Long headOwn = (Long) service.request(HEAD_103, "2026-10-29", null, "18:00", "부서장 본인").get("id");
        Long noHead = (Long) service.request(EMP_104, "2026-10-29", null, "18:30", "부서장 없는 부서").get("id");
        mapper.leftHeads.add(103L);
        Long headLeft = request102();
        mapper.admins.add(102L); // 관리자가 본인 요청을 하면 본인에게는 가지 않는다
        Long adminOwn = (Long) service.request(EMP_102, "2026-10-27", "09:00", "18:00", "관리자 본인(결근)").get("id");
        assertEquals(List.of(
                "REQUEST 900 " + headOwn + " 직원103 2026-10-29", "REQUEST 901 " + headOwn + " 직원103 2026-10-29",
                "REQUEST 900 " + noHead + " 직원104 2026-10-29", "REQUEST 901 " + noHead + " 직원104 2026-10-29",
                "REQUEST 900 " + headLeft + " 직원102 2026-10-29", "REQUEST 901 " + headLeft + " 직원102 2026-10-29",
                "REQUEST 900 " + adminOwn + " 직원102 2026-10-27", "REQUEST 901 " + adminOwn + " 직원102 2026-10-27"),
                notifications.sent);
    }

    @Test
    @DisplayName("알림(처리): 승인·반려는 요청한 직원에게 결과와 함께")
    void notifyRequesterOnDecision() {
        Long approved = request102();
        Long rejected = (Long) service.request(EMP_102, "2026-10-27", "09:00", "18:00", "결근 정정").get("id");
        notifications.sent.clear();
        service.approve(HEAD_103, approved);
        service.reject(HEAD_103, rejected, "그날 외근 기록이 없습니다");
        assertEquals(List.of("DONE 102 " + approved + " 2026-10-29 승인", "DONE 102 " + rejected + " 2026-10-27 반려"),
                notifications.sent);
    }

    @Test
    @DisplayName("알림 없음: 취소, 거절된 요청, 이미 처리된 요청, 반영하지 못한 승인")
    void noNotifyWhenNothingHappened() {
        Long canceled = request102();
        notifications.sent.clear();
        service.cancel(EMP_102, canceled);
        assertEquals(409, status(() -> service.request(EMP_102, "2026-10-28", null, "19:00", "퇴근 있음")));
        assertEquals(409, status(() -> service.approve(HEAD_103, canceled)));
        assertTrue(notifications.sent.isEmpty());

        Long failing = request102();
        notifications.sent.clear();
        mapper.att.get("102/2026-10-29").setCheckOut("18:00:00");
        assertEquals(409, status(() -> service.approve(HEAD_103, failing)));
        assertTrue(notifications.sent.isEmpty());
    }

    /** 알림을 저장하지 않고 부른 순서대로 적어 두는 가짜 */
    static class RecordingNotifications extends NotificationService {
        final List<String> sent = new ArrayList<>();

        RecordingNotifications() {
            super(null);
        }

        @Override
        public void notifyAttendanceCorrectionRequest(Long companyId, Long receiverEmployeeId, Long attendanceCorrectionId,
                                                      String requesterName, String workDate) {
            sent.add("REQUEST " + receiverEmployeeId + " " + attendanceCorrectionId + " " + requesterName + " " + workDate);
        }

        @Override
        public void notifyAttendanceCorrectionDone(Long companyId, Long receiverEmployeeId, Long attendanceCorrectionId,
                                                   String workDate, boolean approved) {
            sent.add("DONE " + receiverEmployeeId + " " + attendanceCorrectionId + " " + workDate + " " + (approved ? "승인" : "반려"));
        }
    }

    /** 근태·정정 표 메모리판. UX_82_01(같은 직원·같은 날 대기 하나)을 흉내 낸다 */
    static class FakeCorrectionMapper implements AttendanceCorrectionMapper {
        final Map<String, AttendanceDTO> att = new HashMap<>();   // "직원/날짜" → 근태
        final Map<Long, Long> head = new HashMap<>();             // 직원 → 부서장 (null 이면 부서장 없음)
        final List<AttendanceCorrectionDTO> rows = new ArrayList<>();
        final Map<Long, Long> approver = new HashMap<>();
        long seq = 1000;

        /** 근태 한 줄을 넣는다 */
        void attendance(long employeeId, String date, String in, String out) {
            AttendanceDTO a = new AttendanceDTO();
            a.setAttendanceId(employeeId * 100 + Long.parseLong(date.substring(8)));
            a.setWorkDate(date);
            a.setCheckIn(in);
            a.setCheckOut(out);
            att.put(employeeId + "/" + date, a);
        }

        /** 번호로 정정 요청을 찾는다 */
        AttendanceCorrectionDTO byId(Long id) {
            return rows.stream().filter(r -> r.getAttendanceCorrectionId().equals(id)).findFirst().orElse(null);
        }

        @Override public AttendanceDTO selectAttendanceOnForUpdate(Long companyId, Long employeeId, String workDate) {
            return att.get(employeeId + "/" + workDate);
        }

        @Override public int insertCorrection(AttendanceCorrectionDTO dto) {
            AttendanceDTO a = att.values().stream().filter(x -> x.getAttendanceId().equals(dto.getAttendanceId())).findFirst().orElse(null);
            if (a == null) return 0;
            if (rows.stream().anyMatch(r -> r.getEmployeeId().equals(dto.getEmployeeId()) && r.getWorkDate().equals(a.getWorkDate())
                    && "ACR_REQUESTED".equals(r.getStatusCode()))) {
                throw new DuplicateKeyException("UX_82_01");
            }
            dto.setAttendanceCorrectionId(seq++);
            dto.setWorkDate(a.getWorkDate());
            dto.setOriginCheckIn(a.getCheckIn());
            dto.setOriginCheckOut(a.getCheckOut());
            dto.setStatusCode("ACR_REQUESTED");
            dto.setCreatedAt("2026-10-30T10:00:" + (seq % 60 < 10 ? "0" : "") + (seq % 60));
            rows.add(dto);
            return 1;
        }

        @Override public int insertAbsenceCorrection(AttendanceCorrectionDTO dto) {
            if (rows.stream().anyMatch(r -> r.getEmployeeId().equals(dto.getEmployeeId()) && r.getWorkDate().equals(dto.getWorkDate())
                    && "ACR_REQUESTED".equals(r.getStatusCode()))) {
                throw new DuplicateKeyException("UX_82_01");
            }
            dto.setAttendanceCorrectionId(seq++);
            dto.setStatusCode("ACR_REQUESTED");
            dto.setCreatedAt("2026-10-30T10:00:" + (seq % 60 < 10 ? "0" : "") + (seq % 60));
            rows.add(dto);
            return 1;
        }

        final Map<Long, String> hire = new HashMap<>(); // 직원 → 입사일 (없으면 null)

        @Override public String selectHireDate(Long companyId, Long employeeId) { return hire.get(employeeId); }

        @Override public int insertAttendanceFromCorrection(Long companyId, Long id) {
            AttendanceCorrectionDTO r = byId(id);
            if (r == null || r.getReqCheckIn() == null) return 0;
            if (att.containsKey(r.getEmployeeId() + "/" + r.getWorkDate())) throw new DuplicateKeyException("UQ_36_01");
            attendance(r.getEmployeeId(), r.getWorkDate(), r.getReqCheckIn() + ":00", r.getReqCheckOut() + ":00");
            return 1;
        }

        @Override public List<AttendanceCorrectionDTO> selectMyCorrections(Long companyId, Long employeeId, String month) {
            return rows.stream().filter(r -> r.getEmployeeId().equals(employeeId) && r.getWorkDate().startsWith(month))
                    .sorted(Comparator.comparing(AttendanceCorrectionDTO::getAttendanceCorrectionId).reversed())
                    .collect(Collectors.toList());
        }

        final java.util.Set<Long> leftHeads = new java.util.HashSet<>(); // 퇴사·휴직한 부서장 (SQL activeHead 처럼 없는 것으로 본다)

        /** 재직 중인 부서장 (없으면 null) */
        Long activeHead(Long employeeId) {
            Long h = head.get(employeeId);
            return h == null || leftHeads.contains(h) ? null : h;
        }

        @Override public AttendanceCorrectionDTO selectCorrectionForUpdate(Long companyId, Long id) {
            AttendanceCorrectionDTO r = byId(id);
            if (r != null) {
                r.setHeadEmployeeId(activeHead(r.getEmployeeId()));
                r.setEmployeeName("직원" + r.getEmployeeId());
            }
            return r;
        }

        final List<Long> admins = new ArrayList<>(List.of(900L, 901L)); // 사용 중인 업체 관리자

        @Override public List<Long> selectAdminEmployeeIds(Long companyId, Long exceptEmployeeId) {
            return admins.stream().filter(a -> !a.equals(exceptEmployeeId)).collect(Collectors.toList());
        }

        @Override public List<AttendanceCorrectionDTO> selectInbox(Long companyId, Long approverId, boolean admin) {
            return rows.stream().filter(r -> "ACR_REQUESTED".equals(r.getStatusCode()) && !r.getEmployeeId().equals(approverId))
                    .filter(r -> {
                        Long h = activeHead(r.getEmployeeId());
                        boolean headDecides = h != null && !h.equals(r.getEmployeeId());
                        return headDecides ? h.equals(approverId) : admin;
                    }).collect(Collectors.toList());
        }

        @Override public int cancelCorrection(Long companyId, Long id, Long employeeId) {
            AttendanceCorrectionDTO r = byId(id);
            if (r == null || !r.getEmployeeId().equals(employeeId) || !"ACR_REQUESTED".equals(r.getStatusCode())) return 0;
            r.setStatusCode("ACR_CANCELED");
            return 1;
        }

        @Override public int decideCorrection(Long companyId, Long id, String status, Long approverId, String rejectReason) {
            AttendanceCorrectionDTO r = byId(id);
            if (r == null || !"ACR_REQUESTED".equals(r.getStatusCode())) return 0;
            r.setStatusCode(status);
            r.setRejectReason(rejectReason);
            approver.put(id, approverId);
            return 1;
        }

        @Override public int applyCheckOut(Long companyId, Long id) {
            AttendanceCorrectionDTO r = byId(id);
            AttendanceDTO a = att.get(r.getEmployeeId() + "/" + r.getWorkDate());
            if (a == null || a.getCheckOut() != null) return 0;
            a.setCheckOut(r.getReqCheckOut() + ":00");
            return 1;
        }
    }
}
