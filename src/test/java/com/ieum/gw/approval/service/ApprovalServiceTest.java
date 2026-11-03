package com.ieum.gw.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.approval.dto.ApprovalSubmitDTO;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.service.EmployeeService;

class ApprovalServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long MY_ID = 100L;

    /** 정해 둔 사원만 재직자로 보고, 물어본 값을 적어 두는 가짜 사원 */
    static class FakeEmployee extends EmployeeService {
        final Set<Long> activeIds = Set.of(1001L, 1002L, 1003L);
        final List<Long> askedCompanyIds = new ArrayList<>();

        @Override
        public boolean isActiveEmployee(Long companyId, Long employeeId) {
            askedCompanyIds.add(companyId);
            return activeIds.contains(employeeId);
        }
    }

    private final FakeEmployee employee = new FakeEmployee();
    private final ApprovalService service = new ApprovalService(employee);

    private static LoginUser user() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(COMPANY_ID);
        emp.setEmployeeId(MY_ID);
        emp.setUserAccountId(200L);
        return new LoginUser(emp);
    }

    private static ApprovalSubmitDTO form(String documentType, String... line) {
        ApprovalSubmitDTO dto = new ApprovalSubmitDTO();
        dto.setDocumentType(documentType);
        dto.setLine(line == null ? null : Arrays.asList(line));
        return dto;
    }

    private String rejectedMessage(ApprovalSubmitDTO dto) {
        return assertThrows(IllegalArgumentException.class, () -> service.submit(user(), dto)).getMessage();
    }

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
}
