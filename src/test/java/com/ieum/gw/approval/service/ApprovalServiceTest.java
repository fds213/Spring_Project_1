package com.ieum.gw.approval.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.approval.dao.ApprovalDAO;
import com.ieum.gw.approval.dto.ApprovalFieldsDTO;
import com.ieum.gw.approval.dto.ApprovalSubmitDTO;
import com.ieum.gw.approval.dto.ApproverDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.service.AttendanceService;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.dto.EmpSummaryDTO;
import com.ieum.gw.employee.service.EmployeeService;

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

        FakeAttendance() {
            super(null, null, null);
        }

        @Override
        public List<LeaveDayDTO> calculateLeaveDays(Long companyId, String leaveType, LocalDate fromDate, LocalDate toDate) {
            this.companyId = companyId;
            this.leaveType = leaveType;
            this.from = fromDate;
            this.to = toDate;
            if (calculateError != null) throw calculateError;
            return days;
        }

        @Override
        public BigDecimal getRemainingLeaveDays(Long companyId, Long employeeId) {
            return remaining;
        }

        @Override
        public boolean hasApprovedLeaveOverlap(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
            return approvedOverlap;
        }
    }

    /** 결재 중 휴가 일수·날짜를 정해 두고, 받은 기간을 적어 두는 가짜 DAO */
    static class FakeDao extends ApprovalDAO {
        BigDecimal pendingSum = BigDecimal.ZERO;
        List<LeaveDayDTO> pendingDays = List.of();
        String fromDate;
        String toDate;

        FakeDao() {
            super(null);
        }

        @Override
        public BigDecimal sumPendingLeaveDays(Long companyId, Long employeeId) {
            return pendingSum;
        }

        @Override
        public List<LeaveDayDTO> selectPendingLeaveDays(Long companyId, Long employeeId, String fromDate, String toDate) {
            this.fromDate = fromDate;
            this.toDate = toDate;
            return pendingDays;
        }
    }

    private final FakeEmployee employee = new FakeEmployee();
    private final FakeAttendance attendance = new FakeAttendance();
    private final FakeDao dao = new FakeDao();
    private final ApprovalService service = new ApprovalService(employee, attendance, dao);

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
    @DisplayName("양식 검사: 장비 신청서는 아직 받지 않는다")
    void rejectsEquipmentForm() {
        assertEquals("지원하지 않는 양식", rejectedMessage(form("DOC_EQUIPMENT", "1001", "")));
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
    @DisplayName("휴가 입력: 휴가 정보가 없으면 거절한다")
    void rejectsMissingFields() {
        assertEquals("휴가 정보를 입력하세요.", rejectedMessage(form("DOC_LEAVE", "1001", "")));
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

    // ---------- 기안 화면 ----------

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
}
