package com.ieum.gw.ops.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ieum.gw.ops.dao.OpsDAO;
import com.ieum.gw.ops.dto.OpsApplicationDTO;
import com.ieum.gw.ops.dto.OpsCompanyDTO;
import com.ieum.gw.ops.dto.OpsCompanyDetailDTO;
import com.ieum.gw.ops.dto.OpsCompanyRowDTO;
import com.ieum.gw.ops.dto.OpsEmployeeDTO;
import com.ieum.gw.ops.dto.OpsPaymentDTO;
import com.ieum.gw.ops.dto.OpsPaymentStatDTO;
import com.ieum.gw.ops.dto.OpsStateDTO;
import com.ieum.gw.subscription.dto.CompanyPeriodDTO;
import com.ieum.gw.subscription.dto.PaymentRowDTO;
import com.ieum.gw.subscription.service.PaymentService;
import com.ieum.gw.subscription.service.SubscriptionService;

import lombok.RequiredArgsConstructor;

/**
 * 운영자의 업체 관리·신청 현황(OPS-01). /ops/**는 security-context.xml이 ROLE_OPS만 통과시킨다.
 * 고객 업체를 가로질러 보는 화면이라 업체를 로그인 사용자에서 꺼내지 않는다. 그래서 업체·계정·결제 같은 운영 기록만 읽고
 * 결재·게시판 같은 업무 자료는 읽지 않는다(OPS-02).
 * 이용 기간이 없는 업체(이음 운영 업체 ieum0000)는 고객 업체가 아니라 목록에 넣지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OpsService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String NO_VALUE = "-";
    private static final String NO_DEPT = "부서 없음";
    static final int NEW_HOURS = 24;

    private final OpsDAO opsDAO;
    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;

    /** 업체 관리 목록, 최신 신청 순 */
    public List<OpsCompanyDTO> listCompanies() {
        Map<Long, CompanyPeriodDTO> periods = subscriptionService.listCompanyPeriods();
        long unitPrice = paymentService.monthlyUnitPrice();
        List<OpsCompanyDTO> result = new ArrayList<>();
        for (OpsCompanyRowDTO row : opsDAO.listCompanies()) {
            CompanyPeriodDTO period = periods.get(row.getCompanyId());
            if (period != null) {
                OpsCompanyDTO company = new OpsCompanyDTO();
                fill(company, row, period, unitPrice);
                result.add(company);
            }
        }
        return result;
    }

    /**
     * 업체 관리에서 펼친 한 업체.
     *
     * @throws OpsNotFoundException 없는 업체 코드이거나 고객 업체가 아닐 때
     */
    public OpsCompanyDetailDTO getCompany(String companyCode) {
        OpsCompanyRowDTO row = opsDAO.getCompany(companyCode);
        CompanyPeriodDTO period = row == null ? null : subscriptionService.getCompanyPeriod(row.getCompanyId());
        if (period == null) {
            throw new OpsNotFoundException("업체를 찾을 수 없습니다.");
        }
        OpsCompanyDetailDTO detail = new OpsCompanyDetailDTO();
        fill(detail, row, period, paymentService.monthlyUnitPrice());
        detail.setNo(row.getCompanyId());
        detail.setEmail(row.getEmail());
        detail.setEmailVerified(row.getEmailVerifiedAt() != null);
        detail.setEmployees(employees(row.getCompanyId()));
        detail.setPayments(payments(paymentService.listPaymentRows(row.getCompanyId())));
        detail.setInquiries(List.of());
        return detail;
    }

    /** 신청 현황 목록, 최신 신청 순. 업체 하나에 한 줄이다 */
    public List<OpsApplicationDTO> listApplications() {
        Map<Long, CompanyPeriodDTO> periods = subscriptionService.listCompanyPeriods();
        Map<Long, OpsPaymentStatDTO> stats = new HashMap<>();
        for (OpsPaymentStatDTO stat : opsDAO.listPaymentStats()) {
            stats.put(stat.getCompanyId(), stat);
        }
        long unitPrice = paymentService.monthlyUnitPrice();
        LocalDateTime now = LocalDateTime.now(SEOUL);
        List<OpsApplicationDTO> result = new ArrayList<>();
        for (OpsCompanyRowDTO row : opsDAO.listCompanies()) {
            CompanyPeriodDTO period = periods.get(row.getCompanyId());
            if (period != null) {
                result.add(toApplication(row, period, stats.get(row.getCompanyId()), unitPrice, now));
            }
        }
        return result;
    }

    private static void fill(OpsCompanyDTO company, OpsCompanyRowDTO row, CompanyPeriodDTO period, long unitPrice) {
        company.setCompanyCode(row.getCompanyCode());
        company.setCompany(row.getCompanyName());
        company.setAdminName(orNoValue(row.getAdminName()));
        company.setAdminNo(orNoValue(row.getAdminNo()));
        company.setUsers(period.getSeats());
        company.setTotal(period.getSeats() * unitPrice);
        company.setState(stateOf(period));
        company.setAt(iso(row.getRequestedAt()));
    }

    // 상태 값은 ServicePeriods.statusOf가 정한 것을 그대로 보내고, 만료의 체험·유료만 글자로 가른다
    static OpsStateDTO stateOf(CompanyPeriodDTO period) {
        String label;
        switch (period.getStatus()) {
            case "TRIAL":
                label = "체험 중";
                break;
            case "PAID":
                label = "유료";
                break;
            default:
                label = period.isPaid() ? "유료 만료" : "체험 만료";
        }
        return new OpsStateDTO(period.getStatus(), label);
    }

    private List<OpsEmployeeDTO> employees(Long companyId) {
        List<OpsEmployeeDTO> list = opsDAO.listEmployees(companyId);
        for (OpsEmployeeDTO employee : list) {
            if (employee.getDept() == null) {
                employee.setDept(NO_DEPT);
            }
        }
        return list;
    }

    // rows는 최신순이라 뒤에서부터 1, 2, 3... 이다
    private static List<OpsPaymentDTO> payments(List<PaymentRowDTO> rows) {
        List<OpsPaymentDTO> list = new ArrayList<>();
        int seq = rows.size();
        for (PaymentRowDTO row : rows) {
            OpsPaymentDTO payment = new OpsPaymentDTO();
            if (!row.isApproved()) {
                payment.setResultLabel("결제 실패");
                payment.setResultTone("failed");
            } else if (row.getStartsAt() == null) {
                payment.setResultLabel("반영 대기");
                payment.setResultTone("pending");
            } else {
                payment.setResultLabel("결제 완료");
                payment.setResultTone("paid");
            }
            payment.setKindLabel("FIRST".equals(row.getKind()) ? "첫 결제" : "연장");
            payment.setPlanLabel("ANNUAL".equals(row.getPlan()) ? "연간" : "월간");
            payment.setAmount(row.getTotalAmount());
            payment.setAt(iso(row.getPaidAt()));
            payment.setUsers(row.getSeatCount());
            payment.setReason(row.getReason());
            payment.setSeq(seq--);
            list.add(payment);
        }
        return list;
    }

    private static OpsApplicationDTO toApplication(OpsCompanyRowDTO row, CompanyPeriodDTO period, OpsPaymentStatDTO stat,
                                                   long unitPrice, LocalDateTime now) {
        OpsApplicationDTO application = new OpsApplicationDTO();
        application.setNo(row.getCompanyId());
        application.setAt(iso(row.getRequestedAt()));
        application.setCompany(row.getCompanyName());
        application.setCompanyCode(row.getCompanyCode());
        application.setUsers(period.getSeats());
        application.setTotal(period.getSeats() * unitPrice);
        application.setEmail(row.getEmail());
        application.setEmailVerified(row.getEmailVerifiedAt() != null);
        application.setVerifiedAt(iso(row.getEmailVerifiedAt()));
        application.setAdminName(orNoValue(row.getAdminName()));
        application.setAdminNo(orNoValue(row.getAdminNo()));
        application.setStatus(period.getStatus());
        application.setPaid(period.isPaid());
        application.setPlan(period.getPlan());
        application.setPaidAt(stat == null ? null : iso(stat.getPaidAt()));
        application.setServiceFrom(period.isPaid() ? day(period.getFirstDay()) : null);
        application.setEnd(day(period.getLastDay()));
        application.setDaysLeft(period.getDaysLeft());
        application.setTrialEnd(day(period.getTrialLastDay()));
        application.setFailedPayments(stat == null ? 0 : stat.getFailedPayments());
        application.setNewApplication(isNew(row.getRequestedAt(), now));
        return application;
    }

    /** 새 신청: 신청한 지 24시간 안. 읽음을 저장할 칸이 없어 시각으로만 본다 */
    static boolean isNew(LocalDateTime requestedAt, LocalDateTime now) {
        return requestedAt.isAfter(now.minusHours(NEW_HOURS));
    }

    private static String orNoValue(String value) {
        return value == null ? NO_VALUE : value;
    }

    private static String iso(LocalDateTime at) {
        return at == null ? null : at.format(ISO);
    }

    private static String day(LocalDate date) {
        return date == null ? null : date.atStartOfDay().format(ISO);
    }
}
