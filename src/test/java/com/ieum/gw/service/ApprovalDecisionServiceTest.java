package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.*;

class ApprovalDecisionServiceTest {
    private final List<String> calls = new ArrayList<>();

    class DecisionDao extends ApprovalServiceTest.FakeDao {
        int stepResult = 1;
        int documentResult = 1;
        int stepWrites;
        int documentWrites;
        String opinion;
        RuntimeException historyError;

        @Override public ApprovalDocumentDTO lockDocument(Long companyId, long documentId) {
            calls.add("lock");
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

        @Override public void insertHistory(ApprovalHistoryDTO history) {
            calls.add("history");
            if (historyError != null) throw historyError;
            super.insertHistory(history);
        }
    }

    private final DecisionDao dao = new DecisionDao();
    private final ApprovalServiceTest.FakeNotification notification = new ApprovalServiceTest.FakeNotification();
    private final EmployeeService employee = new ApprovalServiceTest.FakeEmployee() {
        @Override public Map<Long, String> getEmployeeNames(Long companyId, List<Long> employeeIds) {
            assertEquals(1L, companyId);
            assertEquals(List.of(1001L), employeeIds);
            return Map.of(1001L, "기안자");
        }
    };
    private final ApprovalService service = new ApprovalService(employee,
            new ApprovalServiceTest.FakeAttendance(), dao, new ApprovalServiceTest.FakeBranch(), notification);

    ApprovalDecisionServiceTest() {
        dao.document = new ApprovalDocumentDTO();
        dao.document.setCompanyId(1L);
        dao.document.setApprovalDocumentId(10L);
        dao.document.setAuthorEmployeeId(1001L);
        dao.document.setDocumentType("DOC_LEAVE");
        dao.document.setTitle("휴가 신청");
        dao.document.setStatusCode("IN_PROGRESS");
        dao.steps.add(step(1, 100L, "WAITING"));
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

    private void fails(int status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getRawStatusCode());
    }

    private void noWrites() {
        assertEquals(0, dao.stepWrites);
        assertEquals(0, dao.documentWrites);
        assertNull(dao.history);
        assertTrue(notification.requests.isEmpty());
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
        assertEquals(List.of("lock", "steps", "approveStep", "approveDocument", "history"), calls);
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
    }

    @Test @DisplayName("1차 승인 뒤 중복 요청은 거절하고 2차 승인 때만 문서를 완료한다")
    void twoApproversAndDuplicate() {
        dao.steps.add(step(2, 1002L, "WAITING"));
        assertEquals("IN_PROGRESS", approve(100L, "1차 승인").getStatusCode());
        assertEquals("IN_PROGRESS", dao.history.getAfterStatus());
        assertEquals(0, dao.documentWrites);
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
    }

    @Test @DisplayName("결재선 갱신이 0건이면 409로 중단하고 문서와 이력을 처리하지 않는다")
    void stepUpdateConflict() {
        dao.stepResult = 0;
        fails(409, () -> approve(100L, null));
        assertEquals(0, dao.documentWrites);
        assertNull(dao.history);
        assertTrue(notification.requests.isEmpty());
    }

    @Test @DisplayName("문서 갱신이 0건이면 409로 중단하고 승인 이력을 남기지 않는다")
    void documentUpdateConflict() {
        dao.documentResult = 0;
        fails(409, () -> approve(100L, null));
        assertNull(dao.history);
    }

    @Test @DisplayName("이력 저장 오류는 숨기지 않고 트랜잭션 호출자에게 전파한다")
    void historyFailurePropagates() {
        dao.historyError = new IllegalStateException("이력 저장 실패");
        assertSame(dao.historyError, assertThrows(IllegalStateException.class, () -> approve(100L, null)));
    }
}
