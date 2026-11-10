package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.*;

class ApprovalDecisionServiceTest {
    private final List<String> calls = new ArrayList<>();

    class DecisionDao extends ApprovalServiceTest.FakeDao {
        List<Long> waitingIds = List.of(10L);
        @Override public List<Long> selectWaitingDocumentIds(Long companyId, Long employeeId) {
            assertEquals(1L, companyId);
            assertEquals(100L, employeeId);
            return waitingIds;
        }
        int stepResult = 1;
        int documentResult = 1;
        int stepWrites;
        int documentWrites;
        String opinion;
        RuntimeException historyError;
        RuntimeException lockError;
        List<LeaveDayDTO> storedDays = List.of(leaveDay(50L, "LV_FULL", BigDecimal.ONE),
                leaveDay(51L, "LV_AM", new BigDecimal("0.5")));

        @Override public List<LeaveDayDTO> selectLeaveDays(Long companyId, long documentId) {
            assertEquals(1L, companyId);
            assertEquals(10L, documentId);
            calls.add("leaveDays");
            return storedDays;
        }

        @Override public ApprovalEquipmentDTO selectEquipmentRequest(Long companyId, long documentId) {
            assertEquals(1L, companyId);
            assertEquals(10L, documentId);
            calls.add("equipmentRequest");
            return equipment;
        }

        @Override public ApprovalDocumentDTO lockDocument(Long companyId, long documentId) {
            calls.add("lock");
            if (lockError != null) throw lockError;
            return companyId.equals(1L) && documentId == 10L ? document : null;
        }

        @Override public List<ApprovalStepDTO> selectDocumentSteps(Long companyId, long documentId) {
            assertEquals(1L, companyId);
            assertEquals(10L, documentId);
            calls.add("steps");
            return steps;
        }

        @Override public int approveStep(Long companyId, Long employeeId, Integer stepNo,
                                         long documentId, String opinion) {
            assertEquals(1L, companyId);
            assertEquals(10L, documentId);
            calls.add("approveStep");
            stepWrites++;
            this.opinion = opinion;
            ApprovalStepDTO step = steps.get(stepNo - 1);
            assertEquals(step.getApproverEmployeeId(), employeeId);
            if (stepResult == 1) step.setStatusCode("APPROVED");
            return stepResult;
        }

        @Override public int approveDocument(Long companyId, long documentId) {
            assertEquals(1L, companyId);
            assertEquals(10L, documentId);
            calls.add("approveDocument");
            documentWrites++;
            return documentResult;
        }

        @Override public int rejectStep(Long companyId, Long employeeId, Integer stepNo,
                                        long documentId, String opinion) {
            assertEquals(1L, companyId);
            assertEquals(10L, documentId);
            calls.add("rejectStep");
            stepWrites++;
            this.opinion = opinion;
            ApprovalStepDTO step = steps.get(stepNo - 1);
            assertEquals(step.getApproverEmployeeId(), employeeId);
            if (stepResult == 1) step.setStatusCode("REJECTED");
            return stepResult;
        }

        @Override public int rejectDocument(Long companyId, long documentId) {
            assertEquals(1L, companyId);
            assertEquals(10L, documentId);
            calls.add("rejectDocument");
            documentWrites++;
            return documentResult;
        }

        @Override public void insertHistory(ApprovalHistoryDTO history) {
            calls.add("history");
            if (historyError != null) throw historyError;
            super.insertHistory(history);
        }
    }

    private final DecisionDao dao = new DecisionDao();
    private RuntimeException leaveError;
    private RuntimeException scheduleError;
    private RuntimeException ticketError;
    private final List<List<Object>> ticketRequests = new ArrayList<>();
    private final TicketService tickets = new TicketService(null, null) {
        @Override public Long createEquipmentTicket(Long companyId, Long requesterEmployeeId, Long documentId,
                                                    Long branchId, String assetType, String purpose) {
            calls.add("createTicket");
            ticketRequests.add(List.of(companyId, requesterEmployeeId, documentId, branchId, assetType, purpose));
            if (ticketError != null) throw ticketError;
            return 7000L;
        }
    };
    private final AttendanceService attendance = new ApprovalServiceTest.FakeAttendance() {
        @Override public void useLeave(Long companyId, Long employeeId, Long actorAccountId, List<LeaveDayDTO> days) {
            calls.add("useLeave");
            assertEquals(1L, companyId);
            assertEquals(1001L, employeeId);
            assertEquals(200L, actorAccountId);
            assertSame(dao.storedDays, days);
            if (leaveError != null) throw leaveError;
            if (days.isEmpty()) throw new IllegalArgumentException("차감할 휴가 날짜가 없습니다");
        }
    };
    private final ScheduleService schedule = new ScheduleService(null, null) {
        @Override public void addLeaveEvents(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
            calls.add("leaveEvents");
            assertEquals(1L, companyId);
            assertEquals(1001L, employeeId);
            assertSame(dao.storedDays, days);
            if (scheduleError != null) throw scheduleError;
        }
    };
    private final ApprovalServiceTest.FakeNotification notification = new ApprovalServiceTest.FakeNotification();
    private final EmployeeService employee = new ApprovalServiceTest.FakeEmployee() {
        @Override public Map<Long, String> getEmployeeNames(Long companyId, List<Long> employeeIds) {
            assertEquals(1L, companyId);
            assertEquals(List.of(1001L), employeeIds);
            return Map.of(1001L, "기안자");
        }
    };
    private final ApprovalService service = new ApprovalService(employee,
            attendance, dao, new ApprovalServiceTest.FakeBranch(), notification, schedule, tickets, null);

    ApprovalDecisionServiceTest() {
        dao.document = new ApprovalDocumentDTO();
        dao.document.setCompanyId(1L);
        dao.document.setApprovalDocumentId(10L);
        dao.document.setAuthorEmployeeId(1001L);
        dao.document.setDocumentType("DOC_LEAVE");
        dao.document.setTitle("휴가 신청");
        dao.document.setStatusCode("IN_PROGRESS");
        dao.steps.add(step(1, 100L, "WAITING"));
        dao.equipment = new ApprovalEquipmentDTO();
        dao.equipment.setCompanyId(1L);
        dao.equipment.setApprovalDocumentId(10L);
        dao.equipment.setBranchId(8L);
        dao.equipment.setAssetType("AT_LAPTOP");
        dao.equipment.setQuantity(1);
        dao.equipment.setPurpose("개발용 노트북");
    }

    private static ApprovalStepDTO step(int no, long employeeId, String status) {
        ApprovalStepDTO step = new ApprovalStepDTO();
        step.setCompanyId(1L);
        step.setApprovalDocumentId(10L);
        step.setStepNo(no);
        step.setApproverEmployeeId(employeeId);
        step.setStatusCode(status);
        return step;
    }

    private static LeaveDayDTO leaveDay(long id, String type, BigDecimal days) {
        LeaveDayDTO day = new LeaveDayDTO();
        day.setLeaveRequestDayId(id);
        day.setLeaveDate(LocalDate.of(2026, 11, id == 50L ? 10 : 11));
        day.setLeaveType(type);
        day.setDeductDays(days);
        return day;
    }

    private static LoginUser user(long companyId, long employeeId) {
        AuthEmpDTO auth = new AuthEmpDTO();
        auth.setCompanyId(companyId);
        auth.setEmployeeId(employeeId);
        auth.setUserAccountId(200L);
        return new LoginUser(auth);
    }

    private ApprovalDecisionResultDTO approve(long employeeId, String opinion) {
        ApprovalDecisionDTO request = new ApprovalDecisionDTO();
        request.setOpinion(opinion);
        return service.approve(user(1L, employeeId), request, 10L);
    }

    private ApprovalDecisionResultDTO reject(long employeeId, String opinion) {
        ApprovalDecisionDTO request = new ApprovalDecisionDTO();
        request.setOpinion(opinion);
        return service.reject(user(1L, employeeId), request, 10L);
    }

    private void fails(int status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getRawStatusCode());
    }

    @Test @DisplayName("Oracle 문서 잠금 시간초과는 승인·반려 모두 쓰기 없이 409와 재시도 안내로 응답한다")
    void documentLockTimeout() {
        for (int code : new int[] {54, 30006}) {
            dao.lockError = new SQLErrorCodeSQLExceptionTranslator("Oracle").translate(
                    "문서 잠금", "SELECT ... FOR UPDATE WAIT 5",
                    new SQLException("resource busy; acquire with WAIT timeout expired", "72000", code));
            assertTrue(dao.lockError instanceof CannotAcquireLockException);
            for (Runnable action : List.<Runnable>of(() -> approve(100L, null), () -> reject(100L, "반려 사유"))) {
                ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
                assertEquals(409, error.getRawStatusCode());
                assertEquals("다른 요청에서 이 문서를 처리 중입니다. 잠시 후 다시 시도해 주세요.", error.getReason());
                assertSame(dao.lockError, error.getCause());
            }
        }
        assertEquals(List.of("lock", "lock", "lock", "lock"), calls);
        noWrites();
    }

    private void noWrites() {
        assertEquals(0, dao.stepWrites);
        assertEquals(0, dao.documentWrites);
        assertNull(dao.history);
        assertTrue(notification.requests.isEmpty());
        assertTrue(notification.done.isEmpty());
        assertTrue(notification.rejected.isEmpty());
        assertTrue(ticketRequests.isEmpty());
    }

    @Test @DisplayName("없는 문서와 다른 업체 문서는 404로 거절하고 쓰지 않는다")
    void missingOrOtherCompany() {
        fails(404, () -> service.approve(user(2L, 100L), new ApprovalDecisionDTO(), 10L));
        fails(404, () -> service.approve(user(1L, 100L), new ApprovalDecisionDTO(), 99L));
        assertEquals(List.of("lock", "lock"), calls);
        noWrites();
    }

    @Test @DisplayName("진행 중이 아닌 문서는 결재선을 조회하기 전에 409로 거절한다")
    void documentNotInProgress() {
        for (String status : List.of("DRAFT", "APPROVED", "REJECTED")) {
            dao.document.setStatusCode(status);
            fails(409, () -> approve(100L, null));
        }
        assertEquals(List.of("lock", "lock", "lock"), calls);
        noWrites();
    }

    @Test @DisplayName("결재선에 없는 직원은 403으로 거절하고 쓰지 않는다")
    void notApprover() {
        fails(403, () -> approve(999L, null));
        noWrites();
    }

    @Test @DisplayName("이미 승인하거나 반려한 결재선은 409로 거절한다")
    void stepAlreadyProcessed() {
        for (String status : List.of("APPROVED", "REJECTED")) {
            dao.steps.get(0).setStatusCode(status);
            fails(409, () -> approve(100L, null));
        }
        noWrites();
    }

    @Test @DisplayName("앞 결재가 대기 또는 반려이면 2차 승인을 거절한다")
    void previousStepNotApproved() {
        dao.steps.add(step(2, 1002L, "WAITING"));
        for (String status : List.of("WAITING", "REJECTED")) {
            dao.steps.get(0).setStatusCode(status);
            fails(409, () -> approve(1002L, null));
        }
        noWrites();
    }

    @Test @DisplayName("1001자 의견은 400으로 거절하고 쓰지 않는다")
    void opinionTooLong() {
        fails(400, () -> approve(100L, "가".repeat(1001)));
        noWrites();
    }

    @Test @DisplayName("승인 의견은 선택이며 유니코드 1000자까지 저장한다")
    void optionalOpinionAndBoundary() {
        for (String opinion : new String[] {null, "", "가".repeat(1000), "😀".repeat(1000)}) {
            dao.steps.get(0).setStatusCode("WAITING");
            approve(100L, opinion);
            assertEquals(opinion, dao.opinion);
            assertEquals(opinion, dao.history.getOpinion());
        }
    }

    @Test @DisplayName("단독 결재자는 문서를 완료하고 기안자 이름과 최종 상태를 반환한다")
    void singleApproverCompletes() {
        ApprovalDecisionResultDTO response = approve(100L, "승인합니다");
        assertEquals(List.of("lock", "steps", "approveStep", "approveDocument", "leaveDays", "useLeave",
                "leaveEvents", "history"), calls);
        assertEquals(1, dao.stepWrites);
        assertEquals(1, dao.documentWrites);
        assertEquals("APR_APPROVE", dao.history.getActionCode());
        assertEquals("IN_PROGRESS", dao.history.getBeforeStatus());
        assertEquals("APPROVED", dao.history.getAfterStatus());
        assertEquals(1L, dao.history.getCompanyId());
        assertEquals(10L, dao.history.getApprovalDocumentId());
        assertEquals(200L, dao.history.getActorAccountId());
        assertEquals("기안자", response.getEmployeeName());
        assertEquals("DOC_LEAVE", response.getDocumentType());
        assertEquals("APPROVED", response.getStatusCode());
        assertNull(response.getTicketId());
        assertEquals(List.of(List.of(1L, 1001L, 10L, "휴가 신청")), notification.done);
        assertTrue(notification.requests.isEmpty());
        assertTrue(notification.rejected.isEmpty());
    }

    @Test @DisplayName("1차 승인 뒤 중복 요청은 거절하고 2차 승인 때만 문서를 완료한다")
    void twoApproversAndDuplicate() {
        dao.steps.add(step(2, 1002L, "WAITING"));
        ApprovalDecisionResultDTO intermediate = approve(100L, "1차 승인");
        assertEquals("IN_PROGRESS", intermediate.getStatusCode());
        assertNull(intermediate.getTicketId());
        assertEquals("IN_PROGRESS", dao.history.getAfterStatus());
        assertEquals(0, dao.documentWrites);
        assertTrue(notification.done.isEmpty());
        assertEquals(List.of(List.of(1L, 1002L, 10L, "휴가 신청")), notification.requests);
        assertEquals(List.of("lock", "steps", "approveStep", "history"), calls);

        fails(409, () -> approve(100L, "중복"));
        assertEquals(1, dao.stepWrites);
        assertEquals(1, notification.requests.size());

        assertEquals("APPROVED", approve(1002L, "2차 승인").getStatusCode());
        assertEquals(2, dao.stepWrites);
        assertEquals(1, dao.documentWrites);
        assertEquals("APPROVED", dao.history.getAfterStatus());
        assertEquals(1, notification.requests.size());
        assertEquals(List.of(List.of(1L, 1001L, 10L, "휴가 신청")), notification.done);
        assertTrue(notification.rejected.isEmpty());
    }

    @Test @DisplayName("결재선 갱신이 0건이면 409로 중단하고 문서와 이력을 처리하지 않는다")
    void stepUpdateConflict() {
        dao.stepResult = 0;
        fails(409, () -> approve(100L, null));
        assertEquals(0, dao.documentWrites);
        assertNull(dao.history);
        assertTrue(notification.requests.isEmpty());
        assertTrue(notification.done.isEmpty());
    }

    @Test @DisplayName("문서 갱신이 0건이면 409로 중단하고 승인 이력을 남기지 않는다")
    void documentUpdateConflict() {
        dao.documentResult = 0;
        fails(409, () -> approve(100L, null));
        assertNull(dao.history);
        assertTrue(notification.done.isEmpty());
    }

    @Test @DisplayName("이력 저장 오류는 숨기지 않고 트랜잭션 호출자에게 전파한다")
    void historyFailurePropagates() {
        dao.historyError = new IllegalStateException("이력 저장 실패");
        assertSame(dao.historyError, assertThrows(IllegalStateException.class, () -> approve(100L, null)));
    }

    @Test @DisplayName("반려는 빈 사유와 1001자 사유를 400으로 거절하고 쓰지 않는다")
    void rejectionRequiresOpinion() {
        for (String opinion : new String[] {null, "", " \t\n", "가".repeat(1001), "😀".repeat(1001)}) {
            fails(400, () -> reject(100L, opinion));
        }
        noWrites();
    }

    @Test @DisplayName("반려도 없는 문서와 다른 업체의 문서는 404로 거절한다")
    void rejectionIsCompanyScoped() {
        fails(404, () -> service.reject(user(2L, 100L), new ApprovalDecisionDTO(), 10L));
        fails(404, () -> service.reject(user(1L, 100L), new ApprovalDecisionDTO(), 99L));
        assertEquals(List.of("lock", "lock"), calls);
        noWrites();
    }

    @Test @DisplayName("반려도 문서·결재선 상태와 결재자·순번을 검사한다")
    void rejectionChecksPermissionAndState() {
        fails(403, () -> reject(999L, "사유"));
        dao.steps.add(step(2, 1002L, "WAITING"));
        fails(409, () -> reject(1002L, "사유"));
        for (String status : List.of("APPROVED", "REJECTED")) {
            dao.steps.get(0).setStatusCode(status);
            fails(409, () -> reject(100L, "사유"));
        }
        for (String status : List.of("DRAFT", "APPROVED", "REJECTED")) {
            dao.document.setStatusCode(status);
            fails(409, () -> reject(100L, "사유"));
        }
        noWrites();
    }

    @Test @DisplayName("1차 반려는 문서를 종료하고 기안자에게만 반려 알림과 이력을 남긴다")
    void firstApproverRejects() {
        dao.steps.add(step(2, 1002L, "WAITING"));
        String opinion = "일정을 조정해 주세요";
        ApprovalDecisionResultDTO response = reject(100L, opinion);
        assertEquals(List.of("lock", "steps", "rejectStep", "rejectDocument", "history"), calls);
        assertEquals("REJECTED", dao.steps.get(0).getStatusCode());
        assertEquals("WAITING", dao.steps.get(1).getStatusCode());
        assertEquals(1, dao.stepWrites);
        assertEquals(1, dao.documentWrites);
        assertEquals(opinion, dao.opinion);
        assertEquals(opinion, dao.history.getOpinion());
        assertEquals("APR_REJECT", dao.history.getActionCode());
        assertEquals("IN_PROGRESS", dao.history.getBeforeStatus());
        assertEquals("REJECTED", dao.history.getAfterStatus());
        assertEquals(1L, dao.history.getCompanyId());
        assertEquals(10L, dao.history.getApprovalDocumentId());
        assertEquals(200L, dao.history.getActorAccountId());
        assertEquals(List.of(List.of(1L, 1001L, 10L, "휴가 신청")), notification.rejected);
        assertTrue(notification.done.isEmpty());
        assertTrue(notification.requests.isEmpty());
        assertEquals("기안자", response.getEmployeeName());
        assertEquals("DOC_LEAVE", response.getDocumentType());
        assertEquals("REJECTED", response.getStatusCode());
        assertNull(response.getTicketId());
        fails(409, () -> reject(100L, opinion));
        assertEquals(1, dao.stepWrites);
        assertEquals(1, notification.rejected.size());
    }

    @Test @DisplayName("2차 반려는 이전 승인을 유지하고 유니코드 1000자 사유를 저장한다")
    void secondApproverRejects() {
        dao.steps.get(0).setStatusCode("APPROVED");
        dao.steps.add(step(2, 1002L, "WAITING"));
        String opinion = "😀".repeat(1000);
        assertEquals("REJECTED", reject(1002L, opinion).getStatusCode());
        assertEquals("APPROVED", dao.steps.get(0).getStatusCode());
        assertEquals("REJECTED", dao.steps.get(1).getStatusCode());
        assertEquals(opinion, dao.opinion);
        assertEquals(opinion, dao.history.getOpinion());
        assertEquals(1, dao.documentWrites);
        assertEquals(List.of(List.of(1L, 1001L, 10L, "휴가 신청")), notification.rejected);
        assertTrue(notification.requests.isEmpty());
        assertTrue(notification.done.isEmpty());
    }

    @Test @DisplayName("결재선 반려 갱신이 0건이면 문서·이력·알림을 처리하지 않는다")
    void rejectionStepConflict() {
        dao.stepResult = 0;
        fails(409, () -> reject(100L, "사유"));
        assertEquals(0, dao.documentWrites);
        assertNull(dao.history);
        assertTrue(notification.rejected.isEmpty());
    }

    @Test @DisplayName("문서 반려 갱신이 0건이면 이력·알림을 처리하지 않는다")
    void rejectionDocumentConflict() {
        dao.documentResult = 0;
        fails(409, () -> reject(100L, "사유"));
        assertNull(dao.history);
        assertTrue(notification.rejected.isEmpty());
    }

    @Test @DisplayName("반려 이력 저장 오류는 숨기지 않고 호출자에게 전파한다")
    void rejectionHistoryFailurePropagates() {
        dao.historyError = new IllegalStateException("반려 이력 저장 실패");
        assertSame(dao.historyError, assertThrows(IllegalStateException.class, () -> reject(100L, "사유")));
    }

    @Test @DisplayName("2차 최종 승인에서만 저장된 휴가 ID와 종일·반차를 차감과 일정에 함께 전달한다")
    void finalLeaveApprovalUsesSavedDaysOnce() {
        dao.steps.add(step(2, 1002L, "WAITING"));
        approve(100L, null);
        assertFalse(calls.contains("leaveDays"));
        assertFalse(calls.contains("useLeave"));
        assertFalse(calls.contains("leaveEvents"));
        calls.clear();

        approve(1002L, null);
        assertEquals(List.of("lock", "steps", "approveStep", "approveDocument", "leaveDays", "useLeave",
                "leaveEvents", "history"), calls);
        assertEquals(List.of(50L, 51L), List.of(dao.storedDays.get(0).getLeaveRequestDayId(),
                dao.storedDays.get(1).getLeaveRequestDayId()));
        calls.clear();
        fails(409, () -> approve(1002L, null));
        assertFalse(calls.contains("useLeave"));
        assertFalse(calls.contains("leaveEvents"));
    }

    @Test @DisplayName("장비 문서 최종 승인에서는 휴가 차감과 일정 생성을 호출하지 않는다")
    void equipmentDoesNotUseLeave() {
        dao.document.setDocumentType("DOC_EQUIPMENT");
        ApprovalDecisionResultDTO response = approve(100L, null);
        assertEquals("APPROVED", response.getStatusCode());
        assertEquals("7000", response.getTicketId());
        assertEquals(List.of("lock", "steps", "approveStep", "approveDocument", "equipmentRequest",
                "createTicket", "history"), calls);
        assertEquals(List.of(List.of(1L, 1001L, 10L, 8L, "AT_LAPTOP", "개발용 노트북")), ticketRequests);
        assertEquals(List.of(List.of(1L, 1001L, 10L, "휴가 신청")), notification.done);
    }

    @Test @DisplayName("휴가 날짜가 비어 있거나 연차 차감이 실패하면 일정·완료 알림·이력으로 진행하지 않는다")
    void leaveFailureStopsApproval() {
        dao.storedDays = List.of();
        assertThrows(IllegalArgumentException.class, () -> approve(100L, null));
        assertFalse(calls.contains("leaveEvents"));
        assertNull(dao.history);
        assertTrue(notification.done.isEmpty());

        dao.steps.get(0).setStatusCode("WAITING");
        leaveError = new IllegalStateException("남은 연차가 모자랍니다");
        assertSame(leaveError, assertThrows(IllegalStateException.class, () -> approve(100L, null)));
        assertFalse(calls.contains("leaveEvents"));
        assertNull(dao.history);
        assertTrue(notification.done.isEmpty());
    }

    @Test @DisplayName("일정 생성 실패는 완료 알림·이력을 막고 승인 트랜잭션을 롤백 경로로 보낸다")
    void scheduleFailureRollsBackApproval() {
        scheduleError = new IllegalStateException("일정 저장 실패");
        int[] rollbacks = {0};
        ApprovalService proxy = rollbackProxy(rollbacks);

        assertSame(scheduleError, assertThrows(IllegalStateException.class,
                () -> proxy.approve(user(1L, 100L), new ApprovalDecisionDTO(), 10L)));
        assertEquals(1, rollbacks[0]);
        assertTrue(calls.contains("useLeave"));
        assertTrue(calls.contains("leaveEvents"));
        assertNull(dao.history);
        assertTrue(notification.done.isEmpty());
    }

    private ApprovalService rollbackProxy(int[] rollbacks) {
        AbstractPlatformTransactionManager transactions = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { fail("실패한 승인은 커밋하면 안 됩니다"); }
            @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks[0]++; }
        };
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (ApprovalService) factory.getProxy();
    }

    @Test @DisplayName("장비 신청 상세가 없으면 409로 중단하고 티켓·완료 알림·이력을 생성하지 않는다")
    void missingEquipmentStopsApproval() {
        dao.document.setDocumentType("DOC_EQUIPMENT");
        dao.equipment = null;
        fails(409, () -> approve(100L, null));
        assertTrue(ticketRequests.isEmpty());
        assertTrue(notification.done.isEmpty());
        assertNull(dao.history);
    }

    @Test @DisplayName("장비의 중간 승인과 중복 승인에서는 지급 티켓을 생성하지 않는다")
    void equipmentTicketOnlyOnFinalApproval() {
        dao.document.setDocumentType("DOC_EQUIPMENT");
        dao.steps.add(step(2, 1002L, "WAITING"));
        ApprovalDecisionResultDTO intermediate = approve(100L, null);
        assertEquals("IN_PROGRESS", intermediate.getStatusCode());
        assertNull(intermediate.getTicketId());
        assertFalse(calls.contains("equipmentRequest"));
        assertTrue(ticketRequests.isEmpty());

        ApprovalDecisionResultDTO response = approve(1002L, null);
        assertEquals("APPROVED", response.getStatusCode());
        assertEquals("7000", response.getTicketId());
        assertEquals(1, ticketRequests.size());
        fails(409, () -> approve(1002L, null));
        assertEquals(1, ticketRequests.size());
    }

    @Test @DisplayName("장비 반려는 지급 티켓을 만들지 않는다")
    void equipmentRejectionDoesNotCreateTicket() {
        dao.document.setDocumentType("DOC_EQUIPMENT");
        ApprovalDecisionResultDTO response = reject(100L, "사유");
        assertEquals("REJECTED", response.getStatusCode());
        assertNull(response.getTicketId());
        assertFalse(calls.contains("equipmentRequest"));
        assertTrue(ticketRequests.isEmpty());
    }

    @Test @DisplayName("지급 티켓 생성 실패는 완료 알림·이력을 막고 승인 트랜잭션을 롤백 경로로 보낸다")
    void ticketFailureRollsBackApproval() {
        dao.document.setDocumentType("DOC_EQUIPMENT");
        ticketError = new IllegalStateException("티켓 저장 실패");
        int[] rollbacks = {0};
        ApprovalService proxy = rollbackProxy(rollbacks);
        assertSame(ticketError, assertThrows(IllegalStateException.class,
                () -> proxy.approve(user(1L, 100L), new ApprovalDecisionDTO(), 10L)));
        assertEquals(1, rollbacks[0]);
        assertNull(dao.history);
        assertTrue(notification.done.isEmpty());
    }
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void loginRetirementActor(Long companyId, String role) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(companyId);
        emp.setEmployeeId(900L);
        emp.setUserAccountId(901L);
        emp.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(emp), null, List.of()));
    }

    @Test @DisplayName("비활성화 자동 반려는 휴가와 장비 모두 처리하고 실행 계정·사유·알림을 남긴다")
    void retirementRejectsBothDocumentTypes() {
        loginRetirementActor(1L, "ROLE_ADMIN");
        for (String type : List.of("DOC_LEAVE", "DOC_EQUIPMENT")) {
            dao.document.setDocumentType(type);
            dao.steps.get(0).setStatusCode("WAITING");
            assertEquals(1, service.rejectWaitingForRetired(1L, 100L));
            assertEquals(901L, dao.history.getActorAccountId());
            assertEquals("APR_REJECT", dao.history.getActionCode());
            assertEquals("IN_PROGRESS", dao.history.getBeforeStatus());
            assertEquals("REJECTED", dao.history.getAfterStatus());
            assertEquals("결재자 계정 비활성화로 자동 반려되었습니다.", dao.opinion);
            assertEquals(dao.opinion, dao.history.getOpinion());
        }
        assertEquals(2, notification.rejected.size());
        assertTrue(ticketRequests.isEmpty());
        assertFalse(calls.contains("useLeave"));
        assertFalse(calls.contains("leaveEvents"));
    }

    @Test @DisplayName("자동 반려 대상이 없으면 0건이고 변경하지 않는다")
    void retirementWithoutCandidates() {
        loginRetirementActor(1L, "ROLE_HR");
        dao.waitingIds = List.of();
        assertEquals(0, service.rejectWaitingForRetired(1L, 100L));
        assertTrue(calls.isEmpty());
    }

    @Test @DisplayName("잠금 후 완료·처리된 결재선·사라진 결재자·이전 단계 대기는 자동 반려하지 않는다")
    void retirementRechecksCurrentTurn() {
        loginRetirementActor(1L, "ROLE_HR");
        dao.document.setStatusCode("APPROVED");
        assertEquals(0, service.rejectWaitingForRetired(1L, 100L));
        dao.document.setStatusCode("IN_PROGRESS");
        dao.steps.get(0).setStatusCode("APPROVED");
        assertEquals(0, service.rejectWaitingForRetired(1L, 100L));
        dao.steps.clear();
        assertEquals(0, service.rejectWaitingForRetired(1L, 100L));
        dao.steps.add(step(1, 101L, "WAITING"));
        dao.steps.add(step(2, 100L, "WAITING"));
        assertEquals(0, service.rejectWaitingForRetired(1L, 100L));
        assertEquals(0, dao.stepWrites);
        dao.steps.get(0).setStatusCode("APPROVED");
        assertEquals(1, service.rejectWaitingForRetired(1L, 100L));
    }

    @Test @DisplayName("자동 반려 잠금 시간 초과와 조건부 변경 실패는 예외로 전파한다")
    void retirementPropagatesFailures() {
        loginRetirementActor(1L, "ROLE_ADMIN");
        dao.lockError = new CannotAcquireLockException("locked");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.rejectWaitingForRetired(1L, 100L)).getRawStatusCode());
        dao.lockError = null;
        dao.stepResult = 0;
        assertThrows(ResponseStatusException.class, () -> service.rejectWaitingForRetired(1L, 100L));
        assertEquals(0, dao.documentWrites);
        dao.stepResult = 1;
        dao.documentResult = 0;
        assertThrows(ResponseStatusException.class, () -> service.rejectWaitingForRetired(1L, 100L));
        assertNull(dao.history);
        assertTrue(notification.rejected.isEmpty());
    }

    @Test @DisplayName("자동 반려는 인증 없음·다른 업체·일반 직원 호출을 거절한다")
    void retirementRequiresManagerInSameCompany() {
        assertThrows(ResponseStatusException.class, () -> service.rejectWaitingForRetired(1L, 100L));
        loginRetirementActor(2L, "ROLE_ADMIN");
        assertThrows(ResponseStatusException.class, () -> service.rejectWaitingForRetired(1L, 100L));
        loginRetirementActor(1L, "ROLE_EMP");
        assertThrows(ResponseStatusException.class, () -> service.rejectWaitingForRetired(1L, 100L));
        assertTrue(calls.isEmpty());
    }

    @Test @DisplayName("자동 반려 이력 저장 실패는 트랜잭션 롤백 경로로 전달되고 알림을 보내지 않는다")
    void retirementHistoryFailureRollsBack() {
        loginRetirementActor(1L, "ROLE_ADMIN");
        dao.historyError = new IllegalStateException("history failed");
        int[] rollbacks = {0};
        ApprovalService proxy = rollbackProxy(rollbacks);
        assertThrows(IllegalStateException.class, () -> proxy.rejectWaitingForRetired(1L, 100L));
        assertEquals(1, rollbacks[0]);
        assertTrue(notification.rejected.isEmpty());
    }

    @Test @DisplayName("여러 문서 중 유효한 휴가·장비만 합산하고 중복 호출은 0건을 반환한다")
    void retirementCountsDocumentsAndDoesNotRejectTwice() {
        loginRetirementActor(1L, "ROLE_ADMIN");
        Map<Long, ApprovalDocumentDTO> documents = new java.util.LinkedHashMap<>();
        Map<Long, ApprovalStepDTO> pendingSteps = new java.util.HashMap<>();
        for (long id : new long[] {10L, 20L, 40L}) {
            ApprovalDocumentDTO doc = new ApprovalDocumentDTO();
            doc.setApprovalDocumentId(id);
            doc.setCompanyId(1L);
            doc.setAuthorEmployeeId(1001L);
            doc.setTitle("문서 " + id);
            doc.setDocumentType(id == 10L ? "DOC_LEAVE" : "DOC_EQUIPMENT");
            doc.setStatusCode(id == 40L ? "APPROVED" : "IN_PROGRESS");
            documents.put(id, doc);
            pendingSteps.put(id, step(1, 100L, "WAITING"));
        }
        List<Long> locked = new ArrayList<>();
        List<ApprovalHistoryDTO> histories = new ArrayList<>();
        DecisionDao multiple = new DecisionDao() {
            @Override public List<Long> selectWaitingDocumentIds(Long companyId, Long employeeId) {
                assertEquals(1L, companyId);
                assertEquals(100L, employeeId);
                return List.of(10L, 20L, 30L, 40L);
            }
            @Override public ApprovalDocumentDTO lockDocument(Long companyId, long documentId) {
                locked.add(documentId);
                return documents.get(documentId);
            }
            @Override public List<ApprovalStepDTO> selectDocumentSteps(Long companyId, long documentId) {
                return List.of(pendingSteps.get(documentId));
            }
            @Override public int rejectStep(Long companyId, Long employeeId, Integer stepNo,
                                            long documentId, String opinion) {
                ApprovalStepDTO target = pendingSteps.get(documentId);
                assertEquals(target.getApproverEmployeeId(), employeeId);
                assertEquals(target.getStepNo(), stepNo);
                assertEquals("WAITING", target.getStatusCode());
                target.setStatusCode("REJECTED");
                return 1;
            }
            @Override public int rejectDocument(Long companyId, long documentId) {
                documents.get(documentId).setStatusCode("REJECTED");
                return 1;
            }
            @Override public void insertHistory(ApprovalHistoryDTO history) {
                histories.add(history);
            }
        };
        ApprovalService batch = new ApprovalService(employee, attendance, multiple,
                new ApprovalServiceTest.FakeBranch(), notification, schedule, tickets, null);
        assertEquals(2, batch.rejectWaitingForRetired(1L, 100L));
        assertEquals(List.of(10L, 20L, 30L, 40L), locked);
        assertEquals(List.of(10L, 20L), histories.stream()
                .map(ApprovalHistoryDTO::getApprovalDocumentId).collect(java.util.stream.Collectors.toList()));
        assertEquals(2, notification.rejected.size());
        assertEquals(0, batch.rejectWaitingForRetired(1L, 100L));
        assertEquals(2, histories.size());
        assertEquals(2, notification.rejected.size());
    }

}
