package com.ieum.gw.ops.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.ops.dao.OpsDAO;
import com.ieum.gw.ops.dto.OpsApplicationDTO;
import com.ieum.gw.ops.dto.OpsCompanyDTO;
import com.ieum.gw.ops.dto.OpsCompanyDetailDTO;
import com.ieum.gw.ops.dto.OpsCompanyRowDTO;
import com.ieum.gw.ops.dto.OpsEmployeeDTO;
import com.ieum.gw.ops.dto.OpsPaymentDTO;
import com.ieum.gw.ops.dto.OpsPaymentStatDTO;
import com.ieum.gw.subscription.dto.CompanyPeriodDTO;
import com.ieum.gw.subscription.dto.PaymentRowDTO;
import com.ieum.gw.subscription.service.PaymentService;
import com.ieum.gw.subscription.service.SubscriptionService;

/** DB 없이 화면 값으로 바꾸는 규칙을 확인한다. SQL은 임시 Oracle에서 따로 본다 */
class OpsServiceTest {

    static class FakeDAO extends OpsDAO {
        final List<OpsCompanyRowDTO> rows = new ArrayList<>();
        final Map<Long, List<OpsEmployeeDTO>> employees = new HashMap<>();
        final List<OpsPaymentStatDTO> stats = new ArrayList<>();

        FakeDAO() {
            super(null);
        }

        @Override
        public List<OpsCompanyRowDTO> listCompanies() {
            return rows;
        }

        @Override
        public OpsCompanyRowDTO getCompany(String code) {
            return rows.stream().filter(r -> r.getCompanyCode().equals(code)).findFirst().orElse(null);
        }

        @Override
        public List<OpsEmployeeDTO> listEmployees(Long companyId) {
            return employees.getOrDefault(companyId, new ArrayList<>());
        }

        @Override
        public List<OpsPaymentStatDTO> listPaymentStats() {
            return stats;
        }
    }

    static class FakeSubscription extends SubscriptionService {
        final Map<Long, CompanyPeriodDTO> periods = new HashMap<>();

        FakeSubscription() {
            super(null, null);
        }

        @Override
        public Map<Long, CompanyPeriodDTO> listCompanyPeriods() {
            return periods;
        }

        @Override
        public CompanyPeriodDTO getCompanyPeriod(Long companyId) {
            return periods.get(companyId);
        }
    }

    static class FakePayment extends PaymentService {
        final Map<Long, List<PaymentRowDTO>> rows = new HashMap<>();

        FakePayment() {
            super(null, null, null, null, null);
        }

        @Override
        public long monthlyUnitPrice() {
            return 4000;
        }

        @Override
        public List<PaymentRowDTO> listPaymentRows(Long companyId) {
            return rows.getOrDefault(companyId, new ArrayList<>());
        }
    }

    private final FakeDAO dao = new FakeDAO();
    private final FakeSubscription subscription = new FakeSubscription();
    private final FakePayment payment = new FakePayment();
    private final OpsService service = new OpsService(dao, subscription, payment);

    private OpsCompanyRowDTO row(long id, String code) {
        OpsCompanyRowDTO row = new OpsCompanyRowDTO();
        row.setCompanyId(id);
        row.setCompanyCode(code);
        row.setCompanyName("업체" + id);
        row.setEmail(code + "@test.com");
        row.setRequestedAt(LocalDateTime.parse("2026-11-01T09:30:15.123456"));
        row.setAdminName("관리자");
        row.setAdminNo("20260001");
        dao.rows.add(row);
        return row;
    }

    private CompanyPeriodDTO period(long companyId, String status, boolean paid, int seats) {
        CompanyPeriodDTO period = new CompanyPeriodDTO();
        period.setStatus(status);
        period.setPaid(paid);
        period.setSeats(seats);
        period.setFirstDay(LocalDate.parse("2026-11-02"));
        period.setLastDay(LocalDate.parse("2026-12-01"));
        period.setDaysLeft(28);
        period.setTrialLastDay(LocalDate.parse("2026-11-14"));
        subscription.periods.put(companyId, period);
        return period;
    }

    private static PaymentRowDTO paymentRow(boolean approved, boolean applied, String paidAt) {
        PaymentRowDTO row = new PaymentRowDTO();
        row.setKind("FIRST");
        row.setPlan("MONTHLY");
        row.setSeatCount(12);
        row.setTotalAmount(48000L);
        row.setApproved(approved);
        row.setPaidAt(LocalDateTime.parse(paidAt));
        row.setStartsAt(applied ? LocalDateTime.parse(paidAt) : null);
        row.setReason(approved ? null : "카드 한도 초과");
        return row;
    }

    @Test
    @DisplayName("이용 기간이 없는 업체(이음 운영 업체)는 업체 관리·신청 현황에 나오지 않는다")
    void companyWithoutPeriodIsNotListed() {
        row(1, "ieum0000");
        row(2, "ieum0002");
        period(2, "TRIAL", false, 5);

        List<OpsCompanyDTO> companies = service.listCompanies();
        assertEquals(1, companies.size());
        assertEquals("ieum0002", companies.get(0).getCompanyCode());
        assertEquals(1, service.listApplications().size());
    }

    @Test
    @DisplayName("업체 목록: 이용 인원은 구매 인원, 월 이용료는 인원 × 단가, 신청 시각은 초까지인 문자열이다")
    void companyListValues() {
        row(2, "ieum0002");
        period(2, "PAID", true, 12);

        OpsCompanyDTO company = service.listCompanies().get(0);
        assertEquals(12, company.getUsers());
        assertEquals(48000, company.getTotal());
        assertEquals("2026-11-01T09:30:15", company.getAt());
        assertEquals("PAID", company.getState().getStatus());
    }

    @Test
    @DisplayName("이용 상태 글자는 네 가지다: 체험 중, 유료, 체험 만료, 유료 만료")
    void stateLabels() {
        assertEquals("체험 중", OpsService.stateOf(period(1, "TRIAL", false, 1)).getLabel());
        assertEquals("유료", OpsService.stateOf(period(1, "PAID", true, 1)).getLabel());
        assertEquals("체험 만료", OpsService.stateOf(period(1, "EXPIRED", false, 1)).getLabel());
        assertEquals("유료 만료", OpsService.stateOf(period(1, "EXPIRED", true, 1)).getLabel());
    }

    @Test
    @DisplayName("관리자가 빠진 자료도 업체 줄은 보이고 이름·사번은 '-'다")
    void missingAdminShowsDash() {
        OpsCompanyRowDTO row = row(2, "ieum0002");
        row.setAdminName(null);
        row.setAdminNo(null);
        period(2, "TRIAL", false, 5);

        assertEquals("-", service.listCompanies().get(0).getAdminName());
        assertEquals("-", service.listApplications().get(0).getAdminNo());
    }

    @Test
    @DisplayName("상세: 없는 업체 코드와 이용 기간이 없는 업체는 찾을 수 없다고 거절한다")
    void detailNotFound() {
        row(1, "ieum0000");
        assertThrows(OpsNotFoundException.class, () -> service.getCompany("ieum9999"));
        assertThrows(OpsNotFoundException.class, () -> service.getCompany("ieum0000"));
    }

    @Test
    @DisplayName("상세: 부서 없는 계정은 '부서 없음', 회원 접수는 빈 목록이다")
    void detailEmployees() {
        OpsCompanyRowDTO row = row(2, "ieum0002");
        row.setEmailVerifiedAt(LocalDateTime.parse("2026-11-01T10:00:00"));
        period(2, "TRIAL", false, 5);
        OpsEmployeeDTO employee = new OpsEmployeeDTO();
        employee.setName("관리자");
        dao.employees.put(2L, new ArrayList<>(List.of(employee)));

        OpsCompanyDetailDTO detail = service.getCompany("ieum0002");
        assertEquals(2L, detail.getNo());
        assertEquals("ieum0002@test.com", detail.getEmail());
        assertTrue(detail.isEmailVerified());
        assertEquals("부서 없음", detail.getEmployees().get(0).getDept());
        assertTrue(detail.getInquiries().isEmpty());
    }

    @Test
    @DisplayName("상세: 목록과 같은 칸(상태·인원·이용료·관리자·신청 시각)도 채운다")
    void detailHasListValues() {
        row(2, "ieum0002");
        period(2, "EXPIRED", true, 12);

        OpsCompanyDetailDTO detail = service.getCompany("ieum0002");
        assertEquals("유료 만료", detail.getState().getLabel());
        assertEquals("EXPIRED", detail.getState().getStatus());
        assertEquals(12, detail.getUsers());
        assertEquals(48000, detail.getTotal());
        assertEquals("관리자", detail.getAdminName());
        assertEquals("2026-11-01T09:30:15", detail.getAt());
    }

    @Test
    @DisplayName("상세: 고른 업체의 계정·결제만 담고 다른 업체 자료는 섞지 않는다")
    void detailOnlyHasThatCompany() {
        row(2, "ieum0002");
        row(3, "ieum0003");
        period(2, "PAID", true, 12);
        period(3, "PAID", true, 12);
        OpsEmployeeDTO other = new OpsEmployeeDTO();
        other.setName("다른 업체 직원");
        dao.employees.put(3L, new ArrayList<>(List.of(other)));
        payment.rows.put(3L, List.of(paymentRow(true, true, "2026-11-02T14:00:00")));

        OpsCompanyDetailDTO detail = service.getCompany("ieum0002");
        assertTrue(detail.getEmployees().isEmpty());
        assertTrue(detail.getPayments().isEmpty());
        assertEquals(1, service.getCompany("ieum0003").getEmployees().size());
        assertEquals(1, service.getCompany("ieum0003").getPayments().size());
    }

    @Test
    @DisplayName("상세: 결제 이력은 완료·반영 대기·실패를 가르고, 먼저 기록된 시도의 번호가 작다")
    void detailPayments() {
        row(2, "ieum0002");
        period(2, "PAID", true, 12);
        payment.rows.put(2L, List.of(
                paymentRow(true, true, "2026-11-02T14:00:00"),
                paymentRow(true, false, "2026-11-02T13:50:00"),
                paymentRow(false, false, "2026-11-02T13:40:00")));

        List<OpsPaymentDTO> list = service.getCompany("ieum0002").getPayments();
        assertEquals("결제 완료", list.get(0).getResultLabel());
        assertEquals("paid", list.get(0).getResultTone());
        assertEquals(3, list.get(0).getSeq());
        assertEquals("반영 대기", list.get(1).getResultLabel());
        assertEquals("pending", list.get(1).getResultTone());
        assertEquals("결제 실패", list.get(2).getResultLabel());
        assertEquals("failed", list.get(2).getResultTone());
        assertEquals("카드 한도 초과", list.get(2).getReason());
        assertEquals(1, list.get(2).getSeq());
        assertEquals("첫 결제", list.get(0).getKindLabel());
        assertEquals("월간", list.get(0).getPlanLabel());
        assertEquals("2026-11-02T14:00:00", list.get(0).getAt());
    }

    @Test
    @DisplayName("신청 현황: 유료 업체는 요금제·유료 기간·첫 결제 시각·실패 수를 보낸다")
    void paidApplication() {
        row(2, "ieum0002");
        period(2, "PAID", true, 12);
        OpsPaymentStatDTO stat = new OpsPaymentStatDTO();
        stat.setCompanyId(2L);
        stat.setPaidAt(LocalDateTime.parse("2026-11-02T14:00:00"));
        stat.setFailedPayments(2);
        dao.stats.add(stat);

        OpsApplicationDTO application = service.listApplications().get(0);
        assertEquals(2L, application.getNo());
        assertTrue(application.isPaid());
        assertEquals("MONTHLY", application.getPlan());
        assertEquals("2026-11-02T14:00:00", application.getPaidAt());
        assertEquals("2026-11-02T00:00:00", application.getServiceFrom());
        assertEquals("2026-12-01T00:00:00", application.getEnd());
        assertEquals("2026-11-14T00:00:00", application.getTrialEnd());
        assertEquals(2, application.getFailedPayments());
        assertEquals(48000, application.getTotal());
    }

    @Test
    @DisplayName("신청 현황: 체험 업체는 요금제·유료 기간·결제 시각이 비어 있고 실패 수는 0이다")
    void trialApplication() {
        row(2, "ieum0002");
        period(2, "TRIAL", false, 5);

        OpsApplicationDTO application = service.listApplications().get(0);
        assertFalse(application.isPaid());
        assertNull(application.getPlan());
        assertNull(application.getPaidAt());
        assertNull(application.getServiceFrom());
        assertNull(application.getVerifiedAt());
        assertFalse(application.isEmailVerified());
        assertEquals(0, application.getFailedPayments());
    }

    @Test
    @DisplayName("새 신청은 신청한 지 24시간 안이다: 딱 24시간이면 새 신청이 아니다")
    void newApplicationIsWithin24Hours() {
        LocalDateTime now = LocalDateTime.parse("2026-11-03T12:00:00");
        assertTrue(OpsService.isNew(LocalDateTime.parse("2026-11-02T12:00:01"), now));
        assertFalse(OpsService.isNew(LocalDateTime.parse("2026-11-02T12:00:00"), now));
    }
}
