package com.ieum.gw.subscription.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.service.EmployeeService;
import com.ieum.gw.subscription.dao.PaymentDAO;
import com.ieum.gw.subscription.dao.SubscriptionDAO;
import com.ieum.gw.subscription.dto.BillingDTO;
import com.ieum.gw.subscription.dto.BillingPaymentDTO;
import com.ieum.gw.subscription.dto.CompanyDTO;
import com.ieum.gw.subscription.dto.ConfirmFormDTO;
import com.ieum.gw.subscription.dto.FailFormDTO;
import com.ieum.gw.subscription.dto.OrderFormDTO;
import com.ieum.gw.subscription.dto.OrderResultDTO;
import com.ieum.gw.subscription.dto.OrderStatus;
import com.ieum.gw.subscription.dto.PaymentOrderDTO;
import com.ieum.gw.subscription.dto.PaymentRowDTO;
import com.ieum.gw.subscription.dto.QuoteDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;
import com.ieum.gw.subscription.dto.SubscriptionPlanDTO;
import com.ieum.gw.subscription.dto.TossPaymentDTO;

import lombok.RequiredArgsConstructor;

/**
 * 토스 테스트 결제 1회(월간, 인원 × 4,000원, 요구분석서 3장 범위 표).
 * 주문 만들기 → 토스 결제창(브라우저) → 승인 확인(토스에 승인 요청·응답 검증) → 승인 기록 → 이용 기간 반영.
 * 결제 성공 화면으로 돌아온 것만으로는 아무것도 바꾸지 않는다(PAY-02). 토스 호출이 트랜잭션 밖이어야 해서 이 클래스에는
 * 트랜잭션을 붙이지 않고, 저장은 PaymentRecordService에 맡긴다.
 *
 * 업체 관리자만 쓴다. 구독 권한 위임은 이번 제외, 연간·연장은 핵심 뒤 추가, 만료 후 재구매는 이번 제외라
 * 유료 기간이 한 번도 없는 업체(체험 중·체험 만료)의 첫 결제만 받는다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static final int MAX_SEATS = 500; // 이용 결제 화면의 입력 칸과 같은 값
    private static final int MAX_ORDER_NO = 64;     // 토스 orderId 제한, PAYMENT_ORDER.EXTERNAL_ORDER_NO
    private static final int MAX_PAYMENT_KEY = 200; // 토스 paymentKey 제한, PAYMENT.PAYMENT_KEY
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String PLAN_MONTHLY = "MONTHLY";
    private static final String MODE_FIRST = "FIRST";
    private static final String TOSS_DONE = "DONE";
    private static final String TOSS_CURRENCY = "KRW";
    private static final String TOSS_USER_CANCEL = "PAY_PROCESS_CANCELED"; // 구매자가 결제창을 닫았다. 실패가 아니다

    private final PaymentDAO paymentDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final EmployeeService employeeService;
    private final PaymentRecordService recordService;
    private final TossPaymentsClient tossClient;

    /** 이용 결제 화면 전체 값 */
    public BillingDTO getBilling(LoginUser user) {
        requireAdmin(user);
        Long companyId = user.getCompanyId();
        LocalDateTime now = LocalDateTime.now(SEOUL);
        List<ServicePeriodDTO> periods = subscriptionDAO.listPeriods(companyId);
        ServicePeriodDTO period = ServicePeriods.pick(periods, now);
        if (period == null) {
            throw new BillingException(404, "이 업체의 이용 기간이 없습니다.");
        }
        CompanyDTO company = subscriptionDAO.getCompany(companyId);
        LocalDate lastDay = ServicePeriods.lastDay(period.getEndsAt());
        boolean paid = !period.isTrial();

        BillingDTO billing = new BillingDTO();
        billing.setStatus(ServicePeriods.statusOf(period, now));
        billing.setPaid(paid);
        billing.setPlan(paid ? PLAN_MONTHLY : null);
        billing.setCompany(company.getCompanyName());
        billing.setCompanyCode(company.getCompanyCode());
        billing.setUsers(period.getPurchasedSeats());
        billing.setAccountsInUse(accountsInUse(companyId));
        billing.setMaxUsers(MAX_SEATS);
        billing.setPricePerUser(monthlyPlan().getMonthlyUnitPrice());
        billing.setTrialSeats(ServicePeriods.TRIAL_SEAT_LIMIT);
        billing.setEnd(day(lastDay));
        billing.setDaysLeft(ServicePeriods.daysLeft(period, now));
        for (ServicePeriodDTO p : periods) {
            if (p.isTrial()) {
                billing.setTrialFrom(day(p.getStartsAt().toLocalDate()));
                billing.setTrialEnd(day(ServicePeriods.lastDay(p.getEndsAt())));
            }
        }
        billing.setServiceFrom(paid ? day(period.getStartsAt().toLocalDate()) : null);
        billing.setCanPay(!hasPaidPeriod(periods));
        billing.setMode(MODE_FIRST);

        List<PaymentRowDTO> rows = paymentDAO.listPayments(companyId);
        List<BillingPaymentDTO> payments = new ArrayList<>();
        int failed = 0;
        for (PaymentRowDTO row : rows) {
            payments.add(toPayment(row));
            if (!row.isApproved()) {
                failed++;
            }
        }
        billing.setPayments(payments);
        billing.setFailedPayments(failed);
        return billing;
    }

    /** 금액 미리 보기. 저장하지 않는다 */
    public QuoteDTO quote(LoginUser user, String plan, int users) {
        requireAdmin(user);
        requireMonthly(plan);
        requireSeatRange(users);
        SubscriptionPlanDTO monthly = monthlyPlan();
        int months = monthly.getDurationMonths();
        long unitPrice = monthly.getMonthlyUnitPrice();
        LocalDate today = LocalDate.now(SEOUL);

        QuoteDTO quote = new QuoteDTO();
        quote.setPlan(PLAN_MONTHLY);
        quote.setMode(MODE_FIRST);
        quote.setUsers(users);
        quote.setMonths(months);
        quote.setAmount(totalAmount(users, unitPrice, months));
        quote.setFormula(String.format(Locale.KOREA, "%,d명 × %,d원 × %d개월", users, unitPrice, months));
        quote.setPct(0);
        quote.setWhy("월간은 할인 없음");
        quote.setFrom(day(today));
        quote.setTo(day(ServicePeriods.lastDay(ServicePeriods.paidEndsAt(today, months))));
        return quote;
    }

    /**
     * 주문을 만든다. 금액은 서버가 계산한 값만 쓰고(PAY-02), 화면이 보여 준 금액과 다르면 결제하지 않고 다시 보여 주게 한다.
     * 주문 번호는 무작위라 남의 주문을 짐작할 수 없다(토스 규칙: 영문·숫자·-·_·= 6~64자).
     * 결제창을 닫고 다시 누르면 주문이 새로 생기고 전 주문은 닫힌다(PaymentRecordService.saveNewOrder).
     */
    public OrderResultDTO createOrder(LoginUser user, OrderFormDTO form) {
        requireAdmin(user);
        requireMonthly(form.getPlan());
        Long companyId = user.getCompanyId();
        List<ServicePeriodDTO> periods = subscriptionDAO.listPeriods(companyId);
        if (periods.isEmpty()) {
            throw new BillingException(404, "이 업체의 이용 기간이 없습니다.");
        }
        if (hasPaidPeriod(periods)) {
            throw new BillingException(409, "이미 유료로 바뀐 업체입니다. 연장은 아직 제공하지 않습니다.");
        }
        PaymentOrderDTO approved = paymentDAO.getConfirmedOrder(companyId);
        if (approved != null) {
            // 승인됐는데 저장이 실패한 채 화면을 떠난 주문: 또 결제하면 이중 결제이니 그 주문을 먼저 반영한다
            recordService.applyOrder(approved);
            throw new BillingException(409, "이미 승인된 결제를 반영했습니다. 이용 기간을 확인해 주세요.");
        }
        if (form.getUsers() == null) {
            throw new BillingException(400, "이용 인원을 입력해 주세요.");
        }
        int seats = form.getUsers();
        requireSeatRange(seats);
        requireSeatsCoverAccounts(seats, companyId);

        SubscriptionPlanDTO plan = monthlyPlan();
        long amount = totalAmount(seats, plan.getMonthlyUnitPrice(), plan.getDurationMonths());
        if (form.getAmount() == null || form.getAmount() != amount) {
            throw new BillingException(409, "결제 금액이 바뀌었습니다. 금액을 확인하고 다시 진행해 주세요.");
        }
        if (!tossClient.isConfigured()) {
            logger.error("토스 테스트 키가 없어 결제창을 열 수 없음: config/toss.properties 확인");
            throw new BillingException(503, "결제 설정이 되어 있지 않습니다. 운영자에게 문의해 주세요.");
        }

        PaymentOrderDTO order = new PaymentOrderDTO();
        order.setCompanyId(companyId);
        order.setExternalOrderNo("ieum-" + UUID.randomUUID().toString().replace("-", ""));
        order.setBuyerAccountId(user.getUserAccountId());
        order.setSubscriptionPlanId(plan.getSubscriptionPlanId());
        order.setSeatCount(seats);
        order.setMonthlyUnitPrice(plan.getMonthlyUnitPrice());
        order.setTotalAmount(amount);
        order.setPricedAt(LocalDateTime.now(SEOUL));
        recordService.saveNewOrder(order);

        OrderResultDTO result = new OrderResultDTO();
        result.setOrderNo(order.getExternalOrderNo());
        result.setOrderName("이음 월간 이용 " + seats + "명");
        result.setAmount(amount);
        result.setClientKey(tossClient.getClientKey());
        return result;
    }

    /**
     * 토스 결제창에서 돌아온 뒤 승인을 확인하고 반영한다. 같은 주문으로 몇 번을 불러도 유료 기간은 하나다:
     * 끝난 주문은 결과만 돌려주고, 승인만 기록된 주문은 토스를 다시 부르지 않고 반영만 다시 한다.
     *
     * @throws BillingException retry가 true면 승인 결과를 아직 모르니 같은 값으로 다시 부른다
     */
    public BillingPaymentDTO confirm(LoginUser user, ConfirmFormDTO form) {
        requireAdmin(user);
        Long companyId = user.getCompanyId();
        PaymentOrderDTO order = requireOrder(companyId, form.getOrderId());
        if (order.getStatusCode() == OrderStatus.ORD_FAILED) {
            throw new BillingException(409, "실패했거나 새 주문으로 바뀐 주문입니다. 처음부터 다시 결제해 주세요.");
        }
        if (order.getStatusCode() == OrderStatus.ORD_CREATED) {
            recordService.recordApproval(order, approve(order, form));
        }
        recordService.applyOrder(order);
        return toPayment(findRow(companyId, order.getExternalOrderNo()));
    }

    /**
     * 결제창이 실패로 돌아왔다(failUrl). 값은 브라우저가 보낸 것이라 자기 업체의 아직 승인 안 된 주문만 닫는다.
     * 잘못 보내도 권한은 생기지 않고, 그사이 토스가 승인했다면 승인 기록이 앞선다(PaymentRecordService.recordApproval).
     */
    public void reportFailure(LoginUser user, FailFormDTO form) {
        requireAdmin(user);
        if (TOSS_USER_CANCEL.equals(form.getCode()) || !validOrderNo(form.getOrderId())) {
            return;
        }
        PaymentOrderDTO order = paymentDAO.getOrder(user.getCompanyId(), form.getOrderId());
        if (order != null) {
            recordService.recordFailure(order, form.getCode(), form.getMessage());
        }
    }

    // 토스에 승인을 요청하고 응답을 검증한다. 승인되면 되돌릴 수 없으니 막을 것은 부르기 전에 막는다
    private TossPaymentDTO approve(PaymentOrderDTO order, ConfirmFormDTO form) {
        Long companyId = order.getCompanyId();
        if (form.getPaymentKey() == null || form.getPaymentKey().isBlank()
                || form.getPaymentKey().length() > MAX_PAYMENT_KEY) {
            throw new BillingException(400, "결제 정보를 확인할 수 없습니다.");
        }
        if (!Objects.equals(form.getAmount(), order.getTotalAmount())) {
            logger.warn("결제 금액이 주문과 다름: orderNo={}, 받은 금액={}, 주문 금액={}",
                    order.getExternalOrderNo(), form.getAmount(), order.getTotalAmount());
            throw new BillingException(400, "결제 금액이 주문과 다릅니다. 처음부터 다시 결제해 주세요.");
        }
        if (hasPaidPeriod(subscriptionDAO.listPeriods(companyId))) {
            throw new BillingException(409, "이미 유료로 바뀐 업체입니다. 이 결제는 진행하지 않았습니다.");
        }
        requireSeatsCoverAccounts(order.getSeatCount(), companyId); // 주문 뒤 계정이 늘었을 수 있다. 결제 시점 기준이다(PAY-01)

        TossPaymentDTO toss = askToss(order, form.getPaymentKey());
        verify(order, toss);
        return toss;
    }

    private TossPaymentDTO askToss(PaymentOrderDTO order, String paymentKey) {
        String orderNo = order.getExternalOrderNo();
        try {
            return tossClient.confirm(paymentKey, orderNo, order.getTotalAmount(), orderNo);
        } catch (TossApiException e) {
            logger.warn("토스 승인 오류: orderNo={}, status={}, code={}", orderNo, e.getStatus(), e.getCode());
            if (e.isRejected()) {
                recordService.recordFailure(order, e.getCode(), e.getMessage());
                throw new BillingException(400, e.getMessage());
            }
            if (e.isInProgress()) {
                throw new BillingException(409, "결제를 처리하고 있습니다. 잠시 뒤 다시 확인해 주세요.", true);
            }
            // 시간 초과·5xx·이미 처리됨: 승인됐는지 모른다. 실패로 닫지 않고 토스에 물어본다
            TossPaymentDTO found = findApproved(orderNo);
            if (found != null) {
                return found;
            }
            throw new BillingException(503, "결제 결과를 확인하지 못했습니다. 잠시 뒤 다시 확인해 주세요.", true);
        }
    }

    private TossPaymentDTO findApproved(String orderNo) {
        try {
            TossPaymentDTO found = tossClient.findByOrderId(orderNo);
            return found != null && TOSS_DONE.equals(found.getStatus()) ? found : null;
        } catch (TossApiException e) {
            logger.warn("토스 결제 조회 오류: orderNo={}, status={}, code={}", orderNo, e.getStatus(), e.getCode());
            return null;
        }
    }

    // 승인 응답이 이 주문의 것인지 본다. 토스도 금액을 확인하지만 응답을 믿고 반영하기 전에 서버가 다시 맞춘다(PAY-02)
    private void verify(PaymentOrderDTO order, TossPaymentDTO toss) {
        boolean ok = TOSS_DONE.equals(toss.getStatus())
                && order.getExternalOrderNo().equals(toss.getOrderId())
                && TOSS_CURRENCY.equals(toss.getCurrency())
                && Objects.equals(order.getTotalAmount(), toss.getTotalAmount())
                && toss.getPaymentKey() != null;
        if (!ok) {
            logger.error("토스 승인 응답이 주문과 다름, 반영하지 않음: orderNo={}, toss={}", order.getExternalOrderNo(), toss);
            throw new BillingException(502, "결제 승인 결과가 주문과 달라 반영하지 않았습니다. 운영자에게 문의해 주세요.");
        }
    }

    private PaymentOrderDTO requireOrder(Long companyId, String orderNo) {
        PaymentOrderDTO order = validOrderNo(orderNo) ? paymentDAO.getOrder(companyId, orderNo) : null;
        if (order == null) {
            throw new BillingException(404, "주문을 찾을 수 없습니다.");
        }
        return order;
    }

    private static boolean validOrderNo(String orderNo) {
        return orderNo != null && !orderNo.isBlank() && orderNo.length() <= MAX_ORDER_NO;
    }

    private PaymentRowDTO findRow(Long companyId, String orderNo) {
        return paymentDAO.listPayments(companyId).stream()
                .filter(row -> row.isApproved() && orderNo.equals(row.getOrderNo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("반영한 주문의 결제 행이 없습니다: orderNo=" + orderNo));
    }

    private static void requireAdmin(LoginUser user) {
        boolean admin = user.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
        if (!admin) {
            throw new BillingException(403, "결제는 업체 관리자만 할 수 있습니다.");
        }
    }

    private static void requireMonthly(String plan) {
        if (!PLAN_MONTHLY.equals(plan)) {
            throw new BillingException(400, "지금은 월간 요금제만 결제할 수 있습니다.");
        }
    }

    private static void requireSeatRange(int seats) {
        if (seats < 1 || seats > MAX_SEATS) {
            throw new BillingException(400, "이용 인원은 1~" + MAX_SEATS + "명으로 입력해 주세요.");
        }
    }

    private void requireSeatsCoverAccounts(int seats, Long companyId) {
        int accounts = accountsInUse(companyId);
        if (seats < accounts) {
            throw new BillingException(409, "지금 계정이 " + accounts + "명이라 " + accounts + "명보다 적게 정할 수 없습니다.");
        }
    }

    // 사용 중 계정 수. 사원 쪽 재직자 목록으로 센다. 이번 범위는 승인하면 바로 사용이라(단순화 ①) 재직자가 곧 사용 중 계정이다.
    // TODO(최준원): 김상준의 '사용 중 계정 수' 메서드(구매 인원 초과 승인 거절에 쓰는 것)가 생기면 그것으로 바꾼다
    private int accountsInUse(Long companyId) {
        return employeeService.listActiveEmp(companyId, null).size();
    }

    private SubscriptionPlanDTO monthlyPlan() {
        SubscriptionPlanDTO plan = paymentDAO.getMonthlyPlan();
        if (plan == null) {
            logger.error("월간 요금제 행(SUBSCRIPTION_PLAN.PLAN_MONTHLY)이 없음");
            throw new BillingException(503, "요금제 정보가 없습니다. 운영자에게 문의해 주세요.");
        }
        return plan;
    }

    private static boolean hasPaidPeriod(List<ServicePeriodDTO> periods) {
        return periods.stream().anyMatch(p -> !p.isTrial());
    }

    /** 결제 금액: 인원 × 1인 월 단가 × 개월. 부가세는 따로 계산하지 않는다(요구분석서 3.5) */
    static long totalAmount(int seats, long monthlyUnitPrice, int months) {
        return seats * monthlyUnitPrice * months;
    }

    private static BillingPaymentDTO toPayment(PaymentRowDTO row) {
        BillingPaymentDTO payment = new BillingPaymentDTO();
        payment.setId(row.getOrderNo());
        payment.setKind(row.getKind());
        payment.setPlan(row.getPlan());
        payment.setMonths(row.getDurationMonths());
        payment.setUsers(row.getSeatCount());
        payment.setAmount(row.getTotalAmount());
        payment.setPct(row.getDiscountRate() == null ? 0 : row.getDiscountRate().movePointRight(2).intValue());
        payment.setAt(row.getPaidAt().truncatedTo(ChronoUnit.SECONDS).toString());
        if (row.getStartsAt() != null) {
            payment.setFrom(day(row.getStartsAt().toLocalDate()));
            payment.setTo(day(ServicePeriods.lastDay(row.getEndsAt())));
        }
        payment.setResult(row.isApproved() ? "OK" : "FAILED");
        return payment;
    }

    // 화면 JS가 new Date()로 읽을 때 시간대가 끼어들지 않게 시각까지 붙여 보낸다
    private static String day(LocalDate date) {
        return date.atStartOfDay().toString();
    }
}
