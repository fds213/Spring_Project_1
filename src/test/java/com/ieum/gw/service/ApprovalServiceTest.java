package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.dao.ApprovalDAO;
import com.ieum.gw.dto.ApprovalBoxDTO;
import com.ieum.gw.dto.ApprovalBoxHistoryDTO;
import com.ieum.gw.dto.ApprovalBoxStepDTO;
import com.ieum.gw.dto.ApprovalDocumentDTO;
import com.ieum.gw.dto.ApprovalEquipmentDTO;
import com.ieum.gw.dto.ApprovalFieldsDTO;
import com.ieum.gw.dto.ApprovalHistoryDTO;
import com.ieum.gw.dto.ApprovalLeaveDTO;
import com.ieum.gw.dto.ApprovalStepDTO;
import com.ieum.gw.dto.ApprovalSubmitDTO;
import com.ieum.gw.dto.ApprovedEquipmentDTO;
import com.ieum.gw.dto.ApproverDTO;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.EmpSummaryDTO;
import com.ieum.gw.dto.BranchDTO;

class ApprovalServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long MY_ID = 100L;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** 정해 둔 사원만 재직자로 보고, 물어본 값을 적어 두는 가짜 사원 */
    static class FakeEmployee extends EmployeeService {
        final Set<Long> activeIds = Set.of(1001L, 1002L, 1003L);
        final List<Long> askedCompanyIds = new ArrayList<>();
        List<EmpSummaryDTO> activeList = List.of();

        @Override
        public boolean isActiveEmployee(Long companyId, Long employeeId) {
            askedCompanyIds.add(companyId);
            return activeIds.contains(employeeId);
        }

        @Override
        public List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId) {
            return activeList;
        }
    }

    /** 정해 둔 휴가 날짜·남은 연차·승인 겹침을 돌려주고, 받은 값을 적어 두는 가짜 근태 */
    static class FakeAttendance extends AttendanceService {
        List<LeaveDayDTO> days = List.of(day("2026-11-10", "LV_FULL"), day("2026-11-11", "LV_FULL"));
        BigDecimal remaining = new BigDecimal("15");
        boolean approvedOverlap;
        IllegalArgumentException calculateError;
        Long companyId;
        String leaveType;
        LocalDate from;
        LocalDate to;
        final List<String> submitCalls = new ArrayList<>();
        Long lockedCompanyId;
        Long lockedEmployeeId;
        RuntimeException lockError;

        FakeAttendance() {
            super(null, null, null);
        }

        @Override
        public List<LeaveDayDTO> calculateLeaveDays(Long companyId, String leaveType, LocalDate fromDate, LocalDate toDate) {
            submitCalls.add("calculate");
            this.companyId = companyId;
            this.leaveType = leaveType;
            this.from = fromDate;
            this.to = toDate;
            if (calculateError != null) throw calculateError;
            return days;
        }

        @Override
        public BigDecimal getRemainingLeaveDays(Long companyId, Long employeeId) {
            submitCalls.add("remaining");
            return remaining;
        }

        @Override
        public void lockLeaveForSubmit(Long companyId, Long employeeId) {
            submitCalls.add("lock");
            lockedCompanyId = companyId;
            lockedEmployeeId = employeeId;
            if (lockError != null) throw lockError;
        }

        @Override
        public boolean hasApprovedLeaveOverlap(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
            submitCalls.add("approvedOverlap");
            return approvedOverlap;
        }
    }

    /** 결재 중 휴가 일수·날짜를 정해 두고, 받은 기간과 저장한 행을 적어 두는 가짜 DAO */
    static class FakeDao extends ApprovalDAO {
        static final Long NEW_DOCUMENT_ID = 5000L;

        BigDecimal pendingSum = BigDecimal.ZERO;
        List<LeaveDayDTO> pendingDays = List.of();
        String fromDate;
        String toDate;

        ApprovalDocumentDTO document;
        final List<ApprovalStepDTO> steps = new ArrayList<>();
        ApprovalHistoryDTO history;
        ApprovalLeaveDTO leave;
        final List<LeaveDayDTO> leaveDays = new ArrayList<>();
        final List<Long> leaveDayCompanyIds = new ArrayList<>();
        final List<Long> leaveDayDocumentIds = new ArrayList<>();
        ApprovalEquipmentDTO equipment;

        ApprovedEquipmentDTO approvedEquipment;
        Long approvedQueryCompanyId;
        Long approvedQueryDocumentId;

        List<ApprovalBoxDTO> boxDocuments = List.of();
        List<ApprovalBoxStepDTO> boxSteps = List.of();
        List<ApprovalBoxHistoryDTO> boxHistory = List.of();
        int waitingCount;
        Long boxQueryCompanyId;
        Long boxQueryEmployeeId;
        final List<String> submitCalls;

        FakeDao() {
            this(new ArrayList<>());
        }

        FakeDao(List<String> submitCalls) {
            super(null);
            this.submitCalls = submitCalls;
        }

        /** 저장한 행이 하나도 없는지 */
        boolean savedNothing() {
            return document == null && steps.isEmpty() && history == null && leave == null && leaveDays.isEmpty()
                    && equipment == null;
        }

        @Override
        public void insertDocument(ApprovalDocumentDTO document) {
            submitCalls.add("saveDocument");
            document.setApprovalDocumentId(NEW_DOCUMENT_ID); // selectKey가 하는 일
            this.document = document;
        }

        @Override
        public void insertStep(ApprovalStepDTO step) {
            steps.add(step);
        }

        @Override
        public void insertHistory(ApprovalHistoryDTO history) {
            this.history = history;
        }

        @Override
        public void insertLeaveRequest(ApprovalLeaveDTO leave) {
            submitCalls.add("saveLeave");
            this.leave = leave;
        }

        @Override
        public void insertLeaveDay(Long companyId, Long documentId, LeaveDayDTO day) {
            submitCalls.add("saveDay");
            leaveDayCompanyIds.add(companyId);
            leaveDayDocumentIds.add(documentId);
            leaveDays.add(day);
        }

        @Override
        public void insertEquipmentRequest(ApprovalEquipmentDTO equipment) {
            this.equipment = equipment;
        }

        @Override
        public ApprovedEquipmentDTO selectApprovedEquipment(Long companyId, Long approvalDocumentId) {
            approvedQueryCompanyId = companyId;
            approvedQueryDocumentId = approvalDocumentId;
            return approvedEquipment;
        }

        @Override
        public List<ApprovalBoxDTO> selectBoxDocuments(Long companyId, Long employeeId) {
            boxQueryCompanyId = companyId;
            boxQueryEmployeeId = employeeId;
            return boxDocuments;
        }

        @Override
        public List<ApprovalBoxStepDTO> selectBoxSteps(Long companyId, Long employeeId) {
            return boxSteps;
        }

        @Override
        public List<ApprovalBoxHistoryDTO> selectBoxHistory(Long companyId, Long employeeId) {
            return boxHistory;
        }

        @Override
        public int countWaitingForMe(Long companyId, Long employeeId) {
            boxQueryCompanyId = companyId;
            boxQueryEmployeeId = employeeId;
            return waitingCount;
        }

        @Override
        public BigDecimal sumPendingLeaveDays(Long companyId, Long employeeId) {
            submitCalls.add("pendingSum");
            return pendingSum;
        }

        @Override
        public List<LeaveDayDTO> selectPendingLeaveDays(Long companyId, Long employeeId, String fromDate, String toDate) {
            submitCalls.add("pendingDays");
            this.fromDate = fromDate;
            this.toDate = toDate;
            return pendingDays;
        }
    }

    /** 우리 업체의 사용 중 지점을 정해 두고, 물어본 업체를 적어 두는 가짜 지점 */
    static class FakeBranch extends BranchService {
        List<BranchDTO> branches = List.of(branch(10L, "본사"), branch(11L, "강남지점"));
        final List<Long> askedCompanyIds = new ArrayList<>();

        FakeBranch() {
            super(null);
        }

        @Override
        public List<BranchDTO> getBranches(Long companyId) {
            askedCompanyIds.add(companyId);
            return branches;
        }
    }

    /** 결재 알림을 저장하지 않고 받은 값만 적어 두는 가짜 알림 */
    static class FakeNotification extends NotificationService {
        final List<List<Object>> requests = new ArrayList<>();
        final List<List<Object>> done = new ArrayList<>();
        final List<List<Object>> rejected = new ArrayList<>();

        FakeNotification() {
            super(null);
        }

        @Override
        public void notifyApprovalRequest(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
            requests.add(List.of(companyId, receiverEmployeeId, approvalDocumentId, documentTitle));
        }

        @Override
        public void notifyApprovalDone(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
            done.add(List.of(companyId, receiverEmployeeId, approvalDocumentId, documentTitle));
        }

        @Override
        public void notifyApprovalRejected(Long companyId, Long receiverEmployeeId, Long approvalDocumentId, String documentTitle) {
            rejected.add(List.of(companyId, receiverEmployeeId, approvalDocumentId, documentTitle));
        }
    }

    private final FakeEmployee employee = new FakeEmployee();
    private final FakeAttendance attendance = new FakeAttendance();
    private final FakeDao dao = new FakeDao(attendance.submitCalls);
    private final FakeBranch branch = new FakeBranch();
    private final FakeNotification notification = new FakeNotification();
    private final ApprovalService service = new ApprovalService(employee, attendance, dao, branch, notification,
            new ScheduleService(null, null), new TicketService(null));

    private static BranchDTO branch(Long id, String name) {
        BranchDTO b = new BranchDTO();
        b.setBranchId(id);
        b.setBranchName(name);
        return b;
    }

    /** 결재자 검사를 통과하는 장비 지급 신청서 */
    private static ApprovalSubmitDTO equipment(String assetType, Integer quantity, String branchId, String purpose) {
        ApprovalSubmitDTO dto = form("DOC_EQUIPMENT", "1001", "");
        ApprovalFieldsDTO f = new ApprovalFieldsDTO();
        f.setAssetType(assetType);
        f.setQuantity(quantity);
        f.setBranchId(branchId);
        f.setPurpose(purpose);
        dto.setFields(f);
        return dto;
    }

    private static ApprovalSubmitDTO validEquipment() {
        return equipment("AT_LAPTOP", 1, "10", "개발용 노트북 교체");
    }

    private static LoginUser user() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(COMPANY_ID);
        emp.setEmployeeId(MY_ID);
        emp.setUserAccountId(200L);
        return new LoginUser(emp);
    }

    private static LeaveDayDTO day(String date, String leaveType) {
        LeaveDayDTO d = new LeaveDayDTO();
        d.setLeaveDate(LocalDate.parse(date));
        d.setLeaveType(leaveType);
        d.setDeductDays("LV_FULL".equals(leaveType) ? BigDecimal.ONE : HALF);
        return d;
    }

    private static ApprovalSubmitDTO form(String documentType, String... line) {
        ApprovalSubmitDTO dto = new ApprovalSubmitDTO();
        dto.setDocumentType(documentType);
        dto.setLine(line == null ? null : Arrays.asList(line));
        return dto;
    }

    /** 결재자 검사를 통과하는 휴가 신청서 */
    private static ApprovalSubmitDTO leave(String leaveType, String fromDate, String toDate, String reason) {
        ApprovalSubmitDTO dto = form("DOC_LEAVE", "1001", "");
        ApprovalFieldsDTO f = new ApprovalFieldsDTO();
        f.setLeaveType(leaveType);
        f.setFromDate(fromDate);
        f.setToDate(toDate);
        f.setReason(reason);
        dto.setFields(f);
        return dto;
    }

    private static ApprovalSubmitDTO validLeave() {
        return leave("LV_FULL", "2026-11-10", "2026-11-11", "개인 사정");
    }

    private String rejectedMessage(ApprovalSubmitDTO dto) {
        return assertThrows(IllegalArgumentException.class, () -> service.submit(user(), dto)).getMessage();
    }

    // ---------- 양식 ----------

    @Test
    @DisplayName("양식 검사: 휴가·장비가 아닌 양식은 거절한다")
    void rejectsUnknownForm() {
        assertEquals("지원하지 않는 양식", rejectedMessage(form("DOC_ETC", "1001", "")));
    }

    @Test
    @DisplayName("양식 검사: 양식 값이 없으면 NullPointerException이 아니라 안내로 거절한다")
    void rejectsMissingForm() {
        assertEquals("지원하지 않는 양식", rejectedMessage(form(null, "1001", "")));
    }

    // ---------- 결재선 ----------

    @Test
    @DisplayName("결재선 검사: line이 없으면 1단계 결재자를 고르라고 안내한다")
    void rejectsMissingLine() {
        assertEquals("1단계 결재자를 골라주세요.", rejectedMessage(form("DOC_LEAVE", (String[]) null)));
    }

    @Test
    @DisplayName("결재선 검사: 빈 목록이면 1단계 결재자를 고르라고 안내한다")
    void rejectsEmptyLine() {
        assertEquals("1단계 결재자를 골라주세요.", rejectedMessage(form("DOC_LEAVE")));
    }

    @Test
    @DisplayName("결재선 검사: 두 칸 모두 비어 있으면 1단계 결재자를 고르라고 안내한다")
    void rejectsBlankLine() {
        assertEquals("1단계 결재자를 골라주세요.", rejectedMessage(form("DOC_LEAVE", "", "")));
    }

    @Test
    @DisplayName("결재선 검사: 결재자가 3명이면 거절한다")
    void rejectsThreeApprovers() {
        assertEquals("결재자는 최대 2명입니다.", rejectedMessage(form("DOC_LEAVE", "1001", "1002", "1003")));
    }

    @Test
    @DisplayName("결재선 검사: 숫자가 아닌 결재자 값은 거절한다")
    void rejectsNonNumericApprover() {
        assertThrows(IllegalArgumentException.class, () -> service.submit(user(), form("DOC_LEAVE", "abc", "")));
    }

    @Test
    @DisplayName("결재선 검사: 본인을 결재자로 고르면 거절한다")
    void rejectsSelfApproval() {
        assertEquals("자신은 결재자로 선택할 수 없습니다.", rejectedMessage(form("DOC_LEAVE", "100", "")));
    }

    @Test
    @DisplayName("결재선 검사: 2단계에 본인을 골라도 거절한다")
    void rejectsSelfApprovalOnSecondStep() {
        assertEquals("자신은 결재자로 선택할 수 없습니다.", rejectedMessage(form("DOC_LEAVE", "1001", "100")));
    }

    @Test
    @DisplayName("결재선 검사: 같은 사람을 두 단계에 고르면 거절한다")
    void rejectsDuplicateApprover() {
        assertEquals("결재자는 모두 다른 사람이어야 합니다.", rejectedMessage(form("DOC_LEAVE", "1001", "1001")));
    }

    @Test
    @DisplayName("결재선 검사: Long 캐시 범위를 넘는 같은 ID도 중복으로 본다")
    void rejectsDuplicateLargeApprover() {
        assertEquals("결재자는 모두 다른 사람이어야 합니다.", rejectedMessage(form("DOC_LEAVE", "99999", "99999")));
    }

    @Test
    @DisplayName("결재선 검사: 퇴사자나 다른 업체 사원은 거절한다")
    void rejectsInactiveApprover() {
        assertEquals("재직 중인 사람만 결재자로 지정할 수 있습니다.", rejectedMessage(form("DOC_LEAVE", "9999", "")));
    }

    @Test
    @DisplayName("결재선 검사: 2단계 결재자가 재직자가 아니어도 거절한다")
    void rejectsInactiveSecondApprover() {
        assertEquals("재직 중인 사람만 결재자로 지정할 수 있습니다.", rejectedMessage(form("DOC_LEAVE", "1001", "9999")));
    }

    @Test
    @DisplayName("업체 분리: 재직 확인에는 로그인 사용자의 업체 ID를 쓴다")
    void checksApproverInLoginCompany() {
        rejectedMessage(form("DOC_LEAVE", "1001", "9999"));

        assertEquals(List.of(COMPANY_ID, COMPANY_ID), employee.askedCompanyIds);
    }

    // ---------- 휴가 입력값 ----------

    @Test
    @DisplayName("신청 정보: 휴가·장비 모두 신청 내용이 없으면 거절한다")
    void rejectsMissingFields() {
        assertEquals("신청 정보를 입력하세요.", rejectedMessage(form("DOC_LEAVE", "1001", "")));
        assertEquals("신청 정보를 입력하세요.", rejectedMessage(form("DOC_EQUIPMENT", "1001", "")));
    }

    @Test
    @DisplayName("휴가 입력: 사유가 공백뿐이면 거절한다")
    void rejectsBlankReason() {
        assertEquals("사유를 입력하세요.", rejectedMessage(leave("LV_FULL", "2026-11-10", "2026-11-11", "   ")));
    }

    @Test
    @DisplayName("휴가 입력: 사유가 1000자를 넘으면 거절한다")
    void rejectsLongReason() {
        assertEquals("사유는 1000자까지 입력할 수 있습니다.",
                rejectedMessage(leave("LV_FULL", "2026-11-10", "2026-11-11", "가".repeat(1001))));
    }

    @Test
    @DisplayName("휴가 입력: 사유가 딱 1000자면 받는다")
    void acceptsReasonAtLimit() {
        assertDoesNotThrow(() -> service.submit(user(), leave("LV_FULL", "2026-11-10", "2026-11-11", "가".repeat(1000))));
    }

    @Test
    @DisplayName("휴가 입력: 시작일이 비어 있으면 거절한다")
    void rejectsBlankDate() {
        assertEquals("휴가 기간이 올바르지 않습니다.", rejectedMessage(leave("LV_FULL", "", "2026-11-11", "개인 사정")));
    }

    @Test
    @DisplayName("휴가 입력: 없는 날짜는 500이 아니라 안내로 거절한다")
    void rejectsInvalidDate() {
        assertEquals("휴가 기간이 올바르지 않습니다.", rejectedMessage(leave("LV_FULL", "2026-13-01", "2026-11-11", "개인 사정")));
    }

    // ---------- 날짜 계산·연차 ----------

    @Test
    @DisplayName("날짜 계산: 근태에 로그인 업체·휴가 구분·기간을 그대로 넘긴다")
    void passesLeaveToAttendance() {
        service.submit(user(), leave("LV_AM", "2026-11-10", "2026-11-10", "병원"));

        assertEquals(COMPANY_ID, attendance.companyId);
        assertEquals("LV_AM", attendance.leaveType);
        assertEquals(LocalDate.parse("2026-11-10"), attendance.from);
        assertEquals(LocalDate.parse("2026-11-10"), attendance.to);
    }

    @Test
    @DisplayName("날짜 계산: 근태가 거절한 안내 문구를 그대로 보여 준다")
    void passesAttendanceMessage() {
        attendance.calculateError = new IllegalArgumentException("2027년 휴일 자료가 없어 휴가 날짜를 계산할 수 없습니다");

        assertEquals("2027년 휴일 자료가 없어 휴가 날짜를 계산할 수 없습니다", rejectedMessage(validLeave()));
    }

    @Test
    @DisplayName("날짜 계산: 주말·휴일만 골라 쓸 날이 없으면 거절한다")
    void rejectsNoWorkingDay() {
        attendance.days = List.of();

        assertEquals("휴가로 쓸 날이 없습니다.", rejectedMessage(validLeave()));
    }

    @Test
    @DisplayName("연차: 남은 연차보다 많이 신청하면 거절한다")
    void rejectsNotEnoughLeave() {
        attendance.remaining = BigDecimal.ONE;

        assertEquals("남은 연차가 부족합니다. (남은 연차 1일)", rejectedMessage(validLeave()));
    }

    @Test
    @DisplayName("연차: 결재 중인 휴가 일수를 빼고 남은 연차를 본다")
    void subtractsPendingLeave() {
        attendance.remaining = new BigDecimal("3");
        dao.pendingSum = new BigDecimal("2");

        assertEquals("남은 연차가 부족합니다. (남은 연차 1일)", rejectedMessage(validLeave()));
    }

    @Test
    @DisplayName("연차: 반차가 섞인 남은 일수도 0.5일 단위로 보여 준다")
    void showsHalfDayRemaining() {
        attendance.remaining = new BigDecimal("2.0");
        dao.pendingSum = new BigDecimal("0.5");
        attendance.days = List.of(day("2026-11-10", "LV_FULL"), day("2026-11-11", "LV_FULL"));

        assertEquals("남은 연차가 부족합니다. (남은 연차 1.5일)", rejectedMessage(validLeave()));
    }

    @Test
    @DisplayName("연차: 남은 연차와 신청 일수가 같으면 받는다")
    void acceptsExactRemaining() {
        attendance.remaining = new BigDecimal("2");

        assertDoesNotThrow(() -> service.submit(user(), validLeave()));
    }

    // ---------- 겹침 ----------

    @Test
    @DisplayName("겹침: 이미 승인된 휴가와 겹치면 거절한다")
    void rejectsApprovedOverlap() {
        attendance.approvedOverlap = true;

        assertEquals("이미 승인된 휴가와 겹칩니다.", rejectedMessage(validLeave()));
    }

    @Test
    @DisplayName("겹침: 결재 중 휴가는 신청 기간으로 조회한다")
    void queriesPendingInRequestedRange() {
        service.submit(user(), validLeave());

        assertEquals("2026-11-10", dao.fromDate);
        assertEquals("2026-11-11", dao.toDate);
    }

    @Test
    @DisplayName("겹침: 결재 중인 종일 연차와 같은 날 반차는 거절한다")
    void rejectsHalfOnPendingFullDay() {
        dao.pendingDays = List.of(day("2026-11-10", "LV_FULL"));
        attendance.days = List.of(day("2026-11-10", "LV_PM"));

        assertEquals("결재 중인 휴가와 겹칩니다.", rejectedMessage(leave("LV_PM", "2026-11-10", "2026-11-10", "개인 사정")));
    }

    @Test
    @DisplayName("겹침: 결재 중인 반차가 있는 날 종일 연차는 거절한다")
    void rejectsFullOnPendingHalfDay() {
        dao.pendingDays = List.of(day("2026-11-11", "LV_AM"));

        assertEquals("결재 중인 휴가와 겹칩니다.", rejectedMessage(validLeave()));
    }

    @Test
    @DisplayName("겹침: 결재 중인 오전 반차가 있는 날 오전 반차는 거절한다")
    void rejectsSameHalfDay() {
        dao.pendingDays = List.of(day("2026-11-10", "LV_AM"));
        attendance.days = List.of(day("2026-11-10", "LV_AM"));

        assertEquals("결재 중인 휴가와 겹칩니다.", rejectedMessage(leave("LV_AM", "2026-11-10", "2026-11-10", "개인 사정")));
    }

    @Test
    @DisplayName("겹침: 결재 중인 오전 반차가 있는 날 오후 반차는 받는다")
    void acceptsOtherHalfDay() {
        dao.pendingDays = List.of(day("2026-11-10", "LV_AM"));
        attendance.days = List.of(day("2026-11-10", "LV_PM"));

        assertDoesNotThrow(() -> service.submit(user(), leave("LV_PM", "2026-11-10", "2026-11-10", "개인 사정")));
    }

    @Test
    @DisplayName("겹침: 날짜가 다르면 결재 중인 종일 연차가 있어도 받는다")
    void acceptsDifferentDate() {
        dao.pendingDays = List.of(day("2026-11-12", "LV_FULL"));

        assertDoesNotThrow(() -> service.submit(user(), validLeave()));
    }

    // ---------- 저장 ----------

    @Test
    @DisplayName("저장: 상신하면 새 문서 번호를 돌려준다")
    void returnsNewDocumentId() {
        assertEquals(FakeDao.NEW_DOCUMENT_ID, service.submit(user(), validLeave()));
    }

    @Test
    @DisplayName("저장: 문서는 로그인 업체·기안자로 결재 중 상태로 저장한다")
    void savesDocument() {
        service.submit(user(), validLeave());

        ApprovalDocumentDTO doc = dao.document;
        assertEquals(COMPANY_ID, doc.getCompanyId());
        assertEquals(MY_ID, doc.getAuthorEmployeeId());
        assertEquals("DOC_LEAVE", doc.getDocumentType());
        assertEquals("IN_PROGRESS", doc.getStatusCode());
        assertEquals("휴가 신청서 - 2026-11-10 ~ 2026-11-11", doc.getTitle());
        assertEquals("개인 사정", doc.getContent());
    }

    @Test
    @DisplayName("저장: 결재선은 고른 순서대로 1단계·2단계로 저장하고 업체는 로그인 업체다")
    void savesStepsInOrder() {
        ApprovalSubmitDTO dto = validLeave();
        dto.setLine(List.of("1002", "1001"));

        service.submit(user(), dto);

        assertEquals(2, dao.steps.size());
        assertEquals(1, dao.steps.get(0).getStepNo());
        assertEquals(1002L, dao.steps.get(0).getApproverEmployeeId());
        assertEquals(2, dao.steps.get(1).getStepNo());
        assertEquals(1001L, dao.steps.get(1).getApproverEmployeeId());
        for (ApprovalStepDTO s : dao.steps) {
            assertEquals(COMPANY_ID, s.getCompanyId());
            assertEquals(FakeDao.NEW_DOCUMENT_ID, s.getApprovalDocumentId());
        }
    }

    @Test
    @DisplayName("저장: 결재자가 1명이면 결재선은 1단계 하나만 저장한다")
    void savesOneStep() {
        service.submit(user(), validLeave());

        assertEquals(1, dao.steps.size());
        assertEquals(1, dao.steps.get(0).getStepNo());
        assertEquals(1001L, dao.steps.get(0).getApproverEmployeeId());
    }

    @Test
    @DisplayName("저장: 이력은 제출(APR_SUBMIT)로, 처리한 사람은 사원 ID가 아니라 계정 ID로 남긴다")
    void savesSubmitHistory() {
        service.submit(user(), validLeave());

        ApprovalHistoryDTO h = dao.history;
        assertEquals(COMPANY_ID, h.getCompanyId());
        assertEquals(FakeDao.NEW_DOCUMENT_ID, h.getApprovalDocumentId());
        assertEquals(200L, h.getActorAccountId());
        assertEquals("APR_SUBMIT", h.getActionCode());
        assertNull(h.getBeforeStatus());
        assertEquals("IN_PROGRESS", h.getAfterStatus());
        assertNull(h.getOpinion());
    }

    @Test
    @DisplayName("저장: 휴가 상세에 사유와 총 신청 일수(반차 0.5일 포함)를 저장한다")
    void savesLeaveRequest() {
        attendance.days = List.of(day("2026-11-10", "LV_FULL"), day("2026-11-11", "LV_AM"));

        service.submit(user(), validLeave());

        assertEquals(COMPANY_ID, dao.leave.getCompanyId());
        assertEquals(FakeDao.NEW_DOCUMENT_ID, dao.leave.getApprovalDocumentId());
        assertEquals("개인 사정", dao.leave.getReason());
        assertEquals(0, new BigDecimal("1.5").compareTo(dao.leave.getRequestedDays()));
    }

    @Test
    @DisplayName("저장: 휴가 날짜는 근태가 계산한 날마다 한 줄씩 같은 문서 번호로 저장한다")
    void savesEveryLeaveDay() {
        service.submit(user(), validLeave());

        assertEquals(attendance.days, dao.leaveDays);
        assertEquals(List.of(COMPANY_ID, COMPANY_ID), dao.leaveDayCompanyIds);
        assertEquals(List.of(FakeDao.NEW_DOCUMENT_ID, FakeDao.NEW_DOCUMENT_ID), dao.leaveDayDocumentIds);
    }

    @Test
    @DisplayName("저장: 검사에서 거절되면 아무것도 저장하지 않는다")
    void savesNothingWhenRejected() {
        attendance.remaining = BigDecimal.ONE;

        rejectedMessage(validLeave());

        assertTrue(dao.savedNothing());
    }

    // ---------- 장비 지급 신청서 ----------

    @Test
    @DisplayName("장비: 문서·결재선·이력·장비 상세를 저장하고 휴가 자료와 근태는 건드리지 않는다")
    void savesEquipmentRequest() {
        Long id = service.submit(user(), validEquipment());

        assertEquals(FakeDao.NEW_DOCUMENT_ID, id);
        assertEquals("DOC_EQUIPMENT", dao.document.getDocumentType());
        assertEquals("IN_PROGRESS", dao.document.getStatusCode());
        assertEquals("장비 지급 신청서 - 노트북", dao.document.getTitle());
        assertEquals("개발용 노트북 교체", dao.document.getContent());
        assertEquals(1, dao.steps.size());
        assertEquals("APR_SUBMIT", dao.history.getActionCode());

        ApprovalEquipmentDTO e = dao.equipment;
        assertEquals(FakeDao.NEW_DOCUMENT_ID, e.getApprovalDocumentId());
        assertEquals(COMPANY_ID, e.getCompanyId());
        assertEquals(10L, e.getBranchId());
        assertEquals("AT_LAPTOP", e.getAssetType());
        assertEquals(1, e.getQuantity());
        assertEquals("개발용 노트북 교체", e.getPurpose());

        assertNull(dao.leave);
        assertTrue(dao.leaveDays.isEmpty());
        assertNull(attendance.leaveType);
    }

    @Test
    @DisplayName("장비: 공통 코드에서 사용하지 않는 장비 종류(프린터)는 거절한다")
    void rejectsUnusedAssetType() {
        assertEquals("장비 종류가 올바르지 않습니다.", rejectedMessage(equipment("AT_PRINTER", 1, "10", "출력용")));
    }

    @Test
    @DisplayName("장비: 없는 장비 종류나 빈 종류는 거절한다")
    void rejectsUnknownAssetType() {
        assertEquals("장비 종류가 올바르지 않습니다.", rejectedMessage(equipment("노트북", 1, "10", "개발용")));
        assertEquals("장비 종류가 올바르지 않습니다.", rejectedMessage(equipment(null, 1, "10", "개발용")));
    }

    @Test
    @DisplayName("장비: 수량은 1개만 받는다 (계획서 3장 B)")
    void rejectsQuantityOtherThanOne() {
        assertEquals("장비는 한 번에 1개만 신청할 수 있습니다.", rejectedMessage(equipment("AT_LAPTOP", 2, "10", "개발용")));
        assertEquals("장비는 한 번에 1개만 신청할 수 있습니다.", rejectedMessage(equipment("AT_LAPTOP", 0, "10", "개발용")));
        assertEquals("장비는 한 번에 1개만 신청할 수 있습니다.", rejectedMessage(equipment("AT_LAPTOP", null, "10", "개발용")));
    }

    @Test
    @DisplayName("장비: 수령 지점을 고르지 않았거나 숫자가 아니면 거절한다")
    void rejectsMissingBranch() {
        assertEquals("수령 지점을 골라 주세요.", rejectedMessage(equipment("AT_LAPTOP", 1, "", "개발용")));
        assertEquals("수령 지점을 골라 주세요.", rejectedMessage(equipment("AT_LAPTOP", 1, null, "개발용")));
        assertEquals("수령 지점을 골라 주세요.", rejectedMessage(equipment("AT_LAPTOP", 1, "abc", "개발용")));
    }

    @Test
    @DisplayName("업체 분리: 우리 업체의 사용 중 지점이 아닌 번호(다른 업체·사용 중지)는 거절하고, 지점은 로그인 업체로 찾는다")
    void rejectsOtherCompanyBranch() {
        assertEquals("수령 지점을 다시 골라 주세요.", rejectedMessage(equipment("AT_LAPTOP", 1, "99", "개발용")));
        assertEquals(List.of(COMPANY_ID), branch.askedCompanyIds);
        assertTrue(dao.savedNothing());
    }

    @Test
    @DisplayName("장비: 사용 목적이 공백뿐이면 거절한다")
    void rejectsBlankPurpose() {
        assertEquals("사용 목적을 입력하세요.", rejectedMessage(equipment("AT_LAPTOP", 1, "10", "   ")));
    }

    @Test
    @DisplayName("장비: 사용 목적이 1000자를 넘으면 거절한다")
    void rejectsLongPurpose() {
        assertEquals("사용 목적은 1000자까지 입력할 수 있습니다.", rejectedMessage(equipment("AT_LAPTOP", 1, "10", "가".repeat(1001))));
    }

    @Test
    @DisplayName("장비: 사용 목적은 글자 수로 센다(이모지 1000자는 티켓과 같이 받는다)")
    void countsPurposeByCharacters() {
        assertDoesNotThrow(() -> service.submit(user(), equipment("AT_LAPTOP", 1, "10", "😀".repeat(1000))));
    }

    // ---------- 알림 ----------

    @Test
    @DisplayName("알림: 휴가를 상신하면 1단계 결재자에게만 결재 요청 알림을 한 번 보낸다")
    void notifiesFirstApproverOnLeave() {
        ApprovalSubmitDTO dto = validLeave();
        dto.setLine(List.of("1002", "1001"));

        service.submit(user(), dto);

        assertEquals(List.of(List.of(COMPANY_ID, 1002L, FakeDao.NEW_DOCUMENT_ID, "휴가 신청서 - 2026-11-10 ~ 2026-11-11")),
                notification.requests);
    }

    @Test
    @DisplayName("알림: 장비를 상신해도 1단계 결재자에게 문서 제목으로 알린다")
    void notifiesFirstApproverOnEquipment() {
        service.submit(user(), validEquipment());

        assertEquals(List.of(List.of(COMPANY_ID, 1001L, FakeDao.NEW_DOCUMENT_ID, "장비 지급 신청서 - 노트북")),
                notification.requests);
    }

    @Test
    @DisplayName("알림: 검사에서 거절되면 알림을 보내지 않는다")
    void noNotificationWhenRejected() {
        attendance.remaining = BigDecimal.ONE;

        rejectedMessage(validLeave());

        assertTrue(notification.requests.isEmpty());
    }

    // ---------- 다른 모듈에 주는 조회 ----------

    @Test
    @DisplayName("장비 지급 정보: 받은 업체·문서 번호로 결재 완료 장비 정보를 찾아 그대로 돌려준다")
    void returnsApprovedEquipmentInfo() {
        ApprovedEquipmentDTO info = new ApprovedEquipmentDTO();
        info.setAssetType("AT_MONITOR");
        info.setQuantity(1);
        info.setPurpose("듀얼 모니터");
        dao.approvedEquipment = info;

        assertSame(info, service.getApprovedEquipmentInfo(COMPANY_ID, 5000L));
        assertEquals(COMPANY_ID, dao.approvedQueryCompanyId);
        assertEquals(5000L, dao.approvedQueryDocumentId);
    }

    @Test
    @DisplayName("장비 지급 정보: 업체나 문서 번호가 없으면 조회하지 않고 null")
    void approvedEquipmentInfoNeedsIds() {
        assertNull(service.getApprovedEquipmentInfo(null, 5000L));
        assertNull(service.getApprovedEquipmentInfo(COMPANY_ID, null));
        assertNull(dao.approvedQueryCompanyId);
    }

    // ---------- 기안 화면 ----------

    @Test
    @DisplayName("기안 화면: 수령 지점은 로그인 업체의 사용 중 지점, 장비 종류는 사용 중인 4개를 보낸다")
    void formOptionsListsBranchesAndKinds() {
        Map<String, Object> options = service.getFormOptions(COMPANY_ID, MY_ID);

        assertSame(branch.branches, options.get("branches"));
        assertEquals(List.of(COMPANY_ID), branch.askedCompanyIds);
        assertEquals(List.of(
                Map.of("code", "AT_LAPTOP", "codeName", "노트북"),
                Map.of("code", "AT_DESKTOP", "codeName", "데스크톱"),
                Map.of("code", "AT_MONITOR", "codeName", "모니터"),
                Map.of("code", "AT_OTHER", "codeName", "기타 장비")), options.get("equipKinds"));
    }

    @Test
    @DisplayName("기안 화면: 남은 연차는 결재 중 일수를 뺀 값과 결재 중 일수를 함께 보낸다")
    void formOptionsShowsAvailableLeave() {
        attendance.remaining = new BigDecimal("15");
        dao.pendingSum = new BigDecimal("2.5");

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> leave = (Map<String, BigDecimal>) service.getFormOptions(COMPANY_ID, MY_ID).get("leave");

        assertEquals(0, new BigDecimal("12.5").compareTo(leave.get("available")));
        assertEquals(0, new BigDecimal("2.5").compareTo(leave.get("pendingDays")));
    }

    @Test
    @DisplayName("기안 화면: 결재자 목록에 이름·부서·직급을 담는다")
    void formOptionsListsApprovers() {
        EmpSummaryDTO e = new EmpSummaryDTO();
        e.setEmployeeId(1001L);
        e.setEmployeeName("김결재");
        e.setDepartmentName("인사팀");
        e.setGradeName("팀장");
        employee.activeList = List.of(e);

        @SuppressWarnings("unchecked")
        List<ApproverDTO> approvers = (List<ApproverDTO>) service.getFormOptions(COMPANY_ID, MY_ID).get("approvers");

        assertEquals(1, approvers.size());
        assertEquals(1001L, approvers.get(0).getEmployeeId());
        assertEquals("김결재", approvers.get(0).getEmployeeName());
        assertEquals("인사팀", approvers.get(0).getDepartmentName());
        assertEquals("팀장", approvers.get(0).getGradeName());
    }

    private static ApprovalBoxDTO boxDoc(Long id, String status, boolean myTurn) {
        ApprovalBoxDTO d = new ApprovalBoxDTO();
        d.setApprovalDocumentId(id);
        d.setStatusCode(status);
        d.setMyTurn(myTurn);
        d.setFields(new ApprovalFieldsDTO());
        return d;
    }

    @Test
    @DisplayName("결재함: 탭 5개 키를 항상 만들고 DRAFT는 비어 있다")
    void boxAlwaysHasFiveTabs() {
        Map<String, List<ApprovalBoxDTO>> box = service.box(user());

        assertEquals(List.of("WAIT", "PROGRESS", "DONE", "REJECTED", "DRAFT"), new ArrayList<>(box.keySet()));
        box.values().forEach(list -> assertTrue(list.isEmpty()));
    }

    @Test
    @DisplayName("결재함: 상태별로 탭에 나누고 내 순번 문서는 WAIT에만 담고 진행 탭에는 넣지 않는다")
    void boxSplitsByStatusAndMyTurn() {
        dao.boxDocuments = List.of(boxDoc(1L, "IN_PROGRESS", true), boxDoc(2L, "IN_PROGRESS", false),
                boxDoc(3L, "APPROVED", false), boxDoc(4L, "REJECTED", false));

        Map<String, List<ApprovalBoxDTO>> box = service.box(user());

        assertEquals(1, box.get("WAIT").size());
        assertEquals(1, box.get("PROGRESS").size());
        assertEquals(2L, box.get("PROGRESS").get(0).getApprovalDocumentId());
        assertEquals(1, box.get("DONE").size());
        assertEquals(1, box.get("REJECTED").size());
        assertEquals(COMPANY_ID, dao.boxQueryCompanyId);
        assertEquals(MY_ID, dao.boxQueryEmployeeId);
    }

    @Test
    @DisplayName("결재함: canDecide는 내 순번일 때만 true")
    void boxCanDecideOnlyOnMyTurn() {
        dao.boxDocuments = List.of(boxDoc(1L, "IN_PROGRESS", true), boxDoc(2L, "IN_PROGRESS", false));

        Map<String, List<ApprovalBoxDTO>> box = service.box(user());

        assertTrue(box.get("WAIT").get(0).isCanDecide());
        assertTrue(!box.get("PROGRESS").get(0).isCanDecide());
    }

    @Test
    @DisplayName("결재함: 결재선·이력을 문서 번호별로 붙이고, 없으면 빈 목록")
    void boxAttachesStepsAndHistoryPerDocument() {
        dao.boxDocuments = List.of(boxDoc(1L, "IN_PROGRESS", false), boxDoc(2L, "APPROVED", false));
        ApprovalBoxStepDTO step = new ApprovalBoxStepDTO();
        step.setApprovalDocumentId(1L);
        ApprovalBoxHistoryDTO h = new ApprovalBoxHistoryDTO();
        h.setApprovalDocumentId(1L);
        dao.boxSteps = List.of(step);
        dao.boxHistory = List.of(h);

        Map<String, List<ApprovalBoxDTO>> box = service.box(user());

        ApprovalBoxDTO first = box.get("PROGRESS").get(0);
        ApprovalBoxDTO second = box.get("DONE").get(0);
        assertEquals(1, first.getSteps().size());
        assertEquals(1, first.getHistory().size());
        assertTrue(second.getSteps().isEmpty());
        assertTrue(second.getHistory().isEmpty());
    }

    @Test
    @DisplayName("결재함: 장비 문서는 장비 종류의 한글 이름을 채우고 휴가 문서는 비운다")
    void boxFillsAssetTypeName() {
        ApprovalBoxDTO equip = boxDoc(1L, "IN_PROGRESS", false);
        equip.getFields().setAssetType("AT_LAPTOP");
        dao.boxDocuments = List.of(equip, boxDoc(2L, "IN_PROGRESS", false));

        Map<String, List<ApprovalBoxDTO>> box = service.box(user());

        assertEquals("노트북", box.get("PROGRESS").get(0).getAssetTypeName());
        assertNull(box.get("PROGRESS").get(1).getAssetTypeName());
    }

    @Test
    @DisplayName("내 순번 대기 건수: 업체·사원을 그대로 넘겨 센다")
    void countWaitingForMeUsesDao() {
        dao.waitingCount = 3;

        assertEquals(3, service.countWaitingForMe(COMPANY_ID, MY_ID));
        assertEquals(COMPANY_ID, dao.boxQueryCompanyId);
        assertEquals(MY_ID, dao.boxQueryEmployeeId);
    }

    @Test
    @DisplayName("결재선: 결재자 값이 숫자가 아니면 내부 오류 문구 대신 안내 문구로 거절")
    void nonNumericApproverIsRejectedWithGuide() {
        assertEquals("결재자를 다시 골라 주세요.", rejectedMessage(form("DOC_LEAVE", "abc", "")));
    }

    @Test
    @DisplayName("휴가 상신은 로그인 업체·기안자의 연차를 잠근 뒤 잔여·대기·겹침을 조회하고 저장한다")
    void locksBeforeReadingLeaveAndSaving() {
        service.submit(user(), validLeave());

        assertEquals(COMPANY_ID, attendance.lockedCompanyId);
        assertEquals(MY_ID, attendance.lockedEmployeeId);
        assertEquals(List.of("calculate", "lock", "remaining", "pendingSum", "pendingDays",
                "approvedOverlap", "saveDocument", "saveLeave", "saveDay", "saveDay"), attendance.submitCalls);
    }

    @Test
    @DisplayName("상신 잠금이 실패하면 예외를 전파하고 연차 조회·문서 저장·알림을 진행하지 않는다")
    void lockFailureStopsSubmission() {
        attendance.lockError = new IllegalStateException("연차 잠금 실패");

        assertSame(attendance.lockError,
                assertThrows(IllegalStateException.class, () -> service.submit(user(), validLeave())));
        assertEquals(List.of("calculate", "lock"), attendance.submitCalls);
        assertTrue(dao.savedNothing());
        assertTrue(notification.requests.isEmpty());
    }

    @Test
    @DisplayName("잠금 획득 시점에 늘어난 대기 일수를 다시 읽어 연차 초과 상신을 거절한다")
    void readsPendingDaysAfterLock() {
        FakeAttendance changedAttendance = new FakeAttendance() {
            @Override public void lockLeaveForSubmit(Long companyId, Long employeeId) {
                super.lockLeaveForSubmit(companyId, employeeId);
                dao.pendingSum = new BigDecimal("14");
            }
        };
        ApprovalService subject = new ApprovalService(employee, changedAttendance, dao, branch, notification,
                new ScheduleService(null, null), new TicketService(null));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> subject.submit(user(), validLeave()));
        assertTrue(error.getMessage().startsWith("남은 연차가 부족합니다."));
        assertTrue(dao.savedNothing());
        assertTrue(notification.requests.isEmpty());
    }

    @Test
    @DisplayName("잠금 획득 시점에 추가된 대기 휴가 날짜를 다시 읽어 겹치는 상신을 거절한다")
    void readsPendingOverlapAfterLock() {
        FakeAttendance changedAttendance = new FakeAttendance() {
            @Override public void lockLeaveForSubmit(Long companyId, Long employeeId) {
                super.lockLeaveForSubmit(companyId, employeeId);
                dao.pendingDays = List.of(day("2026-11-10", "LV_FULL"));
            }
        };
        ApprovalService subject = new ApprovalService(employee, changedAttendance, dao, branch, notification,
                new ScheduleService(null, null), new TicketService(null));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> subject.submit(user(), validLeave()));
        assertEquals("결재 중인 휴가와 겹칩니다.", error.getMessage());
        assertTrue(dao.savedNothing());
        assertTrue(notification.requests.isEmpty());
    }

    @Test
    @DisplayName("장비 상신은 연차 행을 잠그거나 휴가 대기 일수를 조회하지 않는다")
    void equipmentDoesNotLockLeave() {
        service.submit(user(), validEquipment());

        assertEquals(List.of("saveDocument"), attendance.submitCalls);
        assertNull(attendance.lockedCompanyId);
        assertNull(attendance.lockedEmployeeId);
    }
}
