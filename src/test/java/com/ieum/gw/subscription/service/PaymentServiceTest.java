package com.ieum.gw.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.dto.EmpSummaryDTO;
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
import com.ieum.gw.subscription.dto.PaymentDTO;
import com.ieum.gw.subscription.dto.PaymentOrderDTO;
import com.ieum.gw.subscription.dto.PaymentRowDTO;
import com.ieum.gw.subscription.dto.QuoteDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;
import com.ieum.gw.subscription.dto.SubscriptionChangeDTO;
import com.ieum.gw.subscription.dto.SubscriptionPlanDTO;
import com.ieum.gw.subscription.dto.TossPaymentDTO;

/**
 * DB·토스 없이 흐름을 확인한다: DAO와 토스 호출을 가짜로 바꾼다.
 * 트랜잭션과 잠금, SQL·제약은 여기서 볼 수 없다(실제 DB로 따로 확인).
 */
class PaymentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long COMPANY = 10L;
    private static final Long OTHER_COMPANY = 20L;
    private static final String KEY = "pay_key_1";

    /** 주문·결제·변경 표를 흉내 낸다. 상태 변경은 실제 SQL처럼 조건부다 */
    static class FakePaymentDAO extends PaymentDAO {
        final List<PaymentOrderDTO> orders = new ArrayList<>();
        final List<PaymentDTO> approved = new ArrayList<>();
        final List<PaymentDTO> failed = new ArrayList<>();
        final List<SubscriptionChangeDTO> changes = new ArrayList<>();
        final SubscriptionDAO periods;
        SubscriptionPlanDTO plan = monthly();
        long nextId = 100;

        FakePaymentDAO(SubscriptionDAO periods) {
            super(null);
            this.periods = periods;
        }

        static SubscriptionPlanDTO monthly() {
            SubscriptionPlanDTO plan = new SubscriptionPlanDTO();
            plan.setSubscriptionPlanId(1L);
            plan.setDurationMonths(1);
            plan.setMonthlyUnitPrice(4000L);
            return plan;
        }

        @Override
        public SubscriptionPlanDTO getMonthlyPlan() {
            return plan;
        }

        @Override
        public int insertOrder(PaymentOrderDTO dto) {
            dto.setPaymentOrderId(nextId++);
            dto.setStatusCode(OrderStatus.ORD_CREATED);
            dto.setDurationMonths(plan.getDurationMonths());
            orders.add(dto);
            return 1;
        }

        @Override
        public PaymentOrderDTO getOrder(Long companyId, String orderNo) {
            return orders.stream()
                    .filter(o -> o.getCompanyId().equals(companyId) && o.getExternalOrderNo().equals(orderNo))
                    .findFirst().map(FakePaymentDAO::copy).orElse(null);
        }

        private static PaymentOrderDTO copy(PaymentOrderDTO o) {
            PaymentOrderDTO c = new PaymentOrderDTO();
            c.setPaymentOrderId(o.getPaymentOrderId());
            c.setCompanyId(o.getCompanyId());
            c.setExternalOrderNo(o.getExternalOrderNo());
            c.setSubscriptionPlanId(o.getSubscriptionPlanId());
            c.setStatusCode(o.getStatusCode());
            c.setSeatCount(o.getSeatCount());
            c.setTotalAmount(o.getTotalAmount());
            c.setDurationMonths(o.getDurationMonths());
            return c;
        }

        private PaymentOrderDTO byId(Long id) {
            return orders.stream().filter(o -> o.getPaymentOrderId().equals(id)).findFirst().orElseThrow();
        }

        @Override
        public int changeOrderStatus(Long companyId, Long orderId, OrderStatus from, OrderStatus to) {
            PaymentOrderDTO o = byId(orderId);
            if (o.getStatusCode() != from) {
                return 0;
            }
            o.setStatusCode(to);
            return 1;
        }

        @Override
        public int confirmOrder(Long companyId, Long orderId) {
            PaymentOrderDTO o = byId(orderId);
            if (o.getStatusCode() != OrderStatus.ORD_CREATED && o.getStatusCode() != OrderStatus.ORD_FAILED) {
                return 0;
            }
            o.setStatusCode(OrderStatus.ORD_CONFIRMED);
            return 1;
        }

        @Override
        public int closeOpenOrders(Long companyId) {
            int closed = 0;
            for (PaymentOrderDTO o : orders) {
                if (o.getCompanyId().equals(companyId) && o.getStatusCode() == OrderStatus.ORD_CREATED) {
                    o.setStatusCode(OrderStatus.ORD_FAILED);
                    closed++;
                }
            }
            return closed;
        }

        @Override
        public PaymentOrderDTO getConfirmedOrder(Long companyId) {
            return orders.stream()
                    .filter(o -> o.getCompanyId().equals(companyId) && o.getStatusCode() == OrderStatus.ORD_CONFIRMED)
                    .findFirst().map(FakePaymentDAO::copy).orElse(null);
        }

        @Override
        public int markOrderApplied(Long companyId, Long orderId, LocalDateTime appliedAt) {
            return changeOrderStatus(companyId, orderId, OrderStatus.ORD_CONFIRMED, OrderStatus.ORD_APPLIED);
        }

        @Override
        public int insertApprovedPayment(PaymentDTO dto) {
            dto.setPaymentId(nextId++);
            approved.add(dto);
            return 1;
        }

        @Override
        public int insertFailedPayment(PaymentDTO dto) {
            failed.add(dto);
            return 1;
        }

        @Override
        public PaymentDTO getApprovedPayment(Long companyId, Long orderId) {
            return approved.stream().filter(p -> p.getPaymentOrderId().equals(orderId)).findFirst().orElse(null);
        }

        @Override
        public int insertSubscriptionChange(SubscriptionChangeDTO dto) {
            changes.add(dto);
            return 1;
        }

        @Override
        public List<PaymentRowDTO> listPayments(Long companyId) {
            List<PaymentRowDTO> rows = new ArrayList<>();
            for (PaymentDTO p : approved) {
                rows.add(row(p, true));
            }
            for (PaymentDTO p : failed) {
                rows.add(row(p, false));
            }
            return rows;
        }

        private PaymentRowDTO row(PaymentDTO p, boolean ok) {
            PaymentOrderDTO o = byId(p.getPaymentOrderId());
            PaymentRowDTO r = new PaymentRowDTO();
            r.setOrderNo(o.getExternalOrderNo());
            r.setKind("FIRST");
            r.setPlan("MONTHLY");
            r.setDurationMonths(o.getDurationMonths());
            r.setSeatCount(o.getSeatCount());
            r.setTotalAmount(o.getTotalAmount());
            r.setDiscountRate(BigDecimal.ZERO);
            r.setApproved(ok);
            r.setPaidAt(ok ? p.getApprovedAt() : LocalDateTime.now(SEOUL));
            for (ServicePeriodDTO sp : periods.listPeriods(COMPANY)) {
                if (o.getPaymentOrderId().equals(sp.getSourceOrderId())) {
                    r.setStartsAt(sp.getStartsAt());
                    r.setEndsAt(sp.getEndsAt());
                }
            }
            return r;
        }
    }

    /** 이용 기간 표를 흉내 낸다. 유료 기간은 주문당 하나다(UQ_17_01) */
    static class FakeSubscriptionDAO extends SubscriptionDAO {
        final List<ServicePeriodDTO> periods = new ArrayList<>();
        boolean failNextPaidInsert;
        int locks;

        FakeSubscriptionDAO() {
            super(null);
        }

        @Override
        public int insertPaidPeriod(ServicePeriodDTO dto) {
            if (failNextPaidInsert) {
                failNextPaidInsert = false;
                throw new IllegalStateException("저장 실패");
            }
            dto.setServicePeriodId(500L + periods.size());
            periods.add(dto);
            return 1;
        }

        @Override
        public void lockCompany(Long companyId) {
            locks++;
        }

        @Override
        public CompanyDTO getCompany(Long companyId) {
            CompanyDTO c = new CompanyDTO();
            c.setCompanyCode("ieum0001");
            c.setCompanyName("가나상사");
            return c;
        }

        @Override
        public List<ServicePeriodDTO> listPeriods(Long companyId) {
            return companyId.equals(COMPANY) ? periods : Collections.emptyList();
        }
    }

    /** 정해 둔 답을 돌려주거나 던지는 토스. 불린 횟수를 센다 */
    static class FakeToss extends TossPaymentsClient {
        RuntimeException confirmError;
        TossPaymentDTO confirmAnswer;
        TossPaymentDTO found;
        RuntimeException findError;
        int confirmCalls;
        String idempotencyKey;

        FakeToss(String clientKey, String secretKey) {
            super(clientKey, secretKey, "http://unused");
        }

        @Override
        public TossPaymentDTO confirm(String paymentKey, String orderId, long amount, String key) {
            confirmCalls++;
            idempotencyKey = key;
            if (confirmError != null) {
                throw confirmError;
            }
            return confirmAnswer != null ? confirmAnswer : done(orderId, amount, "2026-11-02T14:03:22+09:00");
        }

        @Override
        public TossPaymentDTO findByOrderId(String orderId) {
            if (findError != null) {
                throw findError;
            }
            return found;
        }
    }

    static class FakeEmployees extends EmployeeService {
        int active = 1;

        @Override
        public List<EmpSummaryDTO> listActiveEmp(Long companyId, Long excludeEmpId) {
            return Collections.nCopies(active, new EmpSummaryDTO());
        }
    }

    private static TossPaymentDTO done(String orderId, long amount, String approvedAt) {
        TossPaymentDTO t = new TossPaymentDTO();
        t.setPaymentKey(KEY);
        t.setOrderId(orderId);
        t.setStatus("DONE");
        t.setCurrency("KRW");
        t.setTotalAmount(amount);
        t.setApprovedAt(approvedAt);
        return t;
    }

    private FakeSubscriptionDAO subs;
    private FakePaymentDAO payments;
    private FakeToss toss;
    private FakeEmployees employees;
    private PaymentService service;

    private void build(FakeToss client) {
        toss = client;
        service = new PaymentService(payments, subs, employees, new PaymentRecordService(payments, subs), toss);
    }

    @BeforeEach
    void setUp() {
        subs = new FakeSubscriptionDAO();
        payments = new FakePaymentDAO(subs);
        employees = new FakeEmployees();
        build(new FakeToss("test_ck_a", "test_sk_a"));
        subs.periods.add(trial(LocalDateTime.now(SEOUL).minusDays(1), LocalDateTime.now(SEOUL).plusDays(13)));
    }

    private static ServicePeriodDTO trial(LocalDateTime starts, LocalDateTime ends) {
        ServicePeriodDTO p = new ServicePeriodDTO();
        p.setTrial(true);
        p.setStartsAt(starts);
        p.setEndsAt(ends);
        p.setPurchasedSeats(5);
        return p;
    }

    private static LoginUser user(String role, Long companyId) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole(role);
        emp.setCompanyId(companyId);
        emp.setUserAccountId(7L);
        return new LoginUser(emp);
    }

    private static LoginUser admin() {
        return user("ROLE_ADMIN", COMPANY);
    }

    private OrderResultDTO order(int seats) {
        OrderFormDTO form = new OrderFormDTO();
        form.setPlan("MONTHLY");
        form.setUsers(seats);
        form.setAmount(seats * 4000L);
        return service.createOrder(admin(), form);
    }

    private static ConfirmFormDTO confirmForm(OrderResultDTO order) {
        ConfirmFormDTO form = new ConfirmFormDTO();
        form.setOrderId(order.getOrderNo());
        form.setPaymentKey(KEY);
        form.setAmount(order.getAmount());
        return form;
    }

    private BillingPaymentDTO confirm(OrderResultDTO order) {
        return service.confirm(admin(), confirmForm(order));
    }

    private OrderStatus status(OrderResultDTO order) {
        return payments.getOrder(COMPANY, order.getOrderNo()).getStatusCode();
    }

    private long paidPeriods() {
        return subs.periods.stream().filter(p -> !p.isTrial()).count();
    }

    private void assertRejected(int status, Runnable call) {
        assertEquals(status, assertThrows(BillingException.class, call::run).getStatus());
    }

    // ---------- 주문 ----------

    @Test
    @DisplayName("금액은 인원 × 4,000원이고, 주문만으로는 유료가 되지 않는다")
    void orderDoesNotGrantPaid() {
        OrderResultDTO order = order(3);

        assertEquals(12000L, order.getAmount());
        assertEquals("test_ck_a", order.getClientKey());
        assertTrue(order.getOrderNo().matches("[A-Za-z0-9_=-]{6,64}"), "토스 orderId 규칙");
        assertEquals(OrderStatus.ORD_CREATED, status(order));
        assertEquals(0, paidPeriods());
        assertEquals(0, toss.confirmCalls);
    }

    @Test
    @DisplayName("주문 번호는 주문마다 다르다")
    void orderNoIsUnique() {
        assertFalse(order(3).getOrderNo().equals(order(3).getOrderNo()));
    }

    @Test
    @DisplayName("업체 관리자가 아니면 화면 값·견적·주문·승인 확인 모두 거절한다")
    void onlyAdmin() {
        LoginUser emp = user("ROLE_EMP", COMPANY);
        OrderResultDTO order = order(3);
        OrderFormDTO form = new OrderFormDTO();
        form.setPlan("MONTHLY");
        form.setUsers(3);
        form.setAmount(12000L);

        assertRejected(403, () -> service.getBilling(emp));
        assertRejected(403, () -> service.quote(emp, "MONTHLY", 3));
        assertRejected(403, () -> service.createOrder(emp, form));
        assertRejected(403, () -> service.confirm(emp, confirmForm(order)));
        assertRejected(403, () -> service.reportFailure(emp, new FailFormDTO()));
        assertEquals(0, toss.confirmCalls);
    }

    @Test
    @DisplayName("연간은 아직 주문·견적을 받지 않는다(핵심 뒤 추가)")
    void monthlyOnly() {
        OrderFormDTO form = new OrderFormDTO();
        form.setPlan("ANNUAL");
        form.setUsers(3);
        form.setAmount(1L);

        assertRejected(400, () -> service.quote(admin(), "ANNUAL", 3));
        assertRejected(400, () -> service.createOrder(admin(), form));
    }

    @Test
    @DisplayName("인원은 1~500명이고 사용 중 계정 수보다 적으면 거절한다")
    void seatRules() {
        employees.active = 4;

        assertRejected(400, () -> order(0));
        assertRejected(400, () -> order(PaymentService.MAX_SEATS + 1));
        assertRejected(409, () -> order(3));
        assertEquals(16000L, order(4).getAmount());
    }

    @Test
    @DisplayName("화면이 보여 준 금액이 서버 계산과 다르면 주문을 만들지 않는다")
    void amountMustMatchQuote() {
        OrderFormDTO form = new OrderFormDTO();
        form.setPlan("MONTHLY");
        form.setUsers(3);
        form.setAmount(100L);

        assertRejected(409, () -> service.createOrder(admin(), form));
        assertTrue(payments.orders.isEmpty());
    }

    @Test
    @DisplayName("요금제 행이 없으면 503으로 알린다")
    void noPlanRow() {
        payments.plan = null;

        assertRejected(503, () -> service.quote(admin(), "MONTHLY", 3));
    }

    @Test
    @DisplayName("테스트 키가 아니면 주문을 만들지 않는다(실제 대금 방지)")
    void liveKeyIsNotConfigured() {
        build(new FakeToss("live_ck_a", "live_sk_a"));

        assertRejected(503, () -> order(3));
        assertTrue(payments.orders.isEmpty());
    }

    @Test
    @DisplayName("체험이 끝난 업체도 첫 결제를 할 수 있다")
    void expiredTrialCanPay() {
        subs.periods.clear();
        subs.periods.add(trial(LocalDateTime.now(SEOUL).minusDays(20), LocalDateTime.now(SEOUL).minusDays(6)));

        OrderResultDTO order = order(3);
        confirm(order);

        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("이용 기간이 없는 업체(운영 업체)는 404다")
    void noPeriod() {
        subs.periods.clear();

        assertRejected(404, () -> service.getBilling(admin()));
        assertRejected(404, () -> order(3));
    }

    // ---------- 승인 확인 ----------

    @Test
    @DisplayName("승인을 확인하면 승인일부터 1개월 유료가 되고, 승인·변경 기록이 하나씩 남는다")
    void confirmAppliesPaidPeriod() {
        OrderResultDTO order = order(3);

        BillingPaymentDTO result = confirm(order);

        assertEquals(OrderStatus.ORD_APPLIED, status(order));
        ServicePeriodDTO paid = subs.periods.get(1);
        assertFalse(paid.isTrial());
        assertEquals(LocalDateTime.of(2026, 11, 2, 14, 3, 22), paid.getStartsAt());
        assertEquals(LocalDateTime.of(2026, 12, 2, 0, 0), paid.getEndsAt());
        assertEquals(3, paid.getPurchasedSeats());
        assertEquals(LocalDate.of(2026, 11, 2), paid.getBillingAnchorDate());
        assertEquals(1, payments.approved.size());
        assertEquals(KEY, payments.approved.get(0).getPaymentKey());
        assertEquals(12000L, payments.approved.get(0).getApprovedAmount());
        assertEquals(1, payments.changes.size());
        assertEquals(10, payments.changes.get(0).getBeforeSeats());
        assertEquals(3, payments.changes.get(0).getAfterSeats());
        assertEquals(2, subs.locks, "주문 저장과 반영 때 업체 행을 잠근다");
        assertEquals(order.getOrderNo(), toss.idempotencyKey);
        assertEquals(order.getOrderNo(), result.getId());
        assertEquals("OK", result.getResult());
        assertEquals("2026-12-01T00:00", result.getTo());
    }

    @Test
    @DisplayName("1월 31일에 승인되면 2월 말일 0시에 만료한다(월말 조정)")
    void monthEnd() {
        OrderResultDTO order = order(1);
        toss.confirmAnswer = done(order.getOrderNo(), 4000L, "2027-01-31T23:59:00+09:00");

        confirm(order);

        assertEquals(LocalDateTime.of(2027, 2, 28, 0, 0), subs.periods.get(1).getEndsAt());
    }

    @Test
    @DisplayName("토스 승인 시각은 한국 시간으로 바꿔 저장한다")
    void approvedAtToSeoul() {
        OrderResultDTO order = order(1);
        toss.confirmAnswer = done(order.getOrderNo(), 4000L, "2026-11-02T15:00:00Z");

        confirm(order);

        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0), payments.approved.get(0).getApprovedAt());
    }

    @Test
    @DisplayName("같은 승인 건을 두 번 보내도 토스는 한 번만 부르고 유료 기간은 하나다")
    void confirmTwice() {
        OrderResultDTO order = order(3);

        confirm(order);
        BillingPaymentDTO second = confirm(order);

        assertEquals(1, toss.confirmCalls);
        assertEquals(1, paidPeriods());
        assertEquals(1, payments.approved.size());
        assertEquals(1, payments.changes.size());
        assertEquals("OK", second.getResult());
    }

    @Test
    @DisplayName("동시에 온 두 번째 요청: 승인 기록과 반영이 이미 끝났으면 중복으로 기록하지 않는다")
    void concurrentSecondRequest() {
        OrderResultDTO order = order(3);
        PaymentOrderDTO stale = payments.getOrder(COMPANY, order.getOrderNo()); // 두 번째 요청이 읽은 ORD_CREATED
        confirm(order);

        PaymentRecordService record = new PaymentRecordService(payments, subs);
        record.recordApproval(stale, done(order.getOrderNo(), 12000L, "2026-11-02T14:03:22+09:00"));
        record.applyOrder(stale);

        assertEquals(1, payments.approved.size());
        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("다른 업체의 주문 번호로는 승인 확인할 수 없다")
    void otherCompanyOrder() {
        OrderResultDTO order = order(3);

        assertRejected(404, () -> service.confirm(user("ROLE_ADMIN", OTHER_COMPANY), confirmForm(order)));
        assertEquals(0, toss.confirmCalls);
        assertEquals(OrderStatus.ORD_CREATED, status(order));
    }

    @Test
    @DisplayName("돌아온 금액이 주문과 다르면 토스를 부르지 않는다")
    void tamperedAmount() {
        OrderResultDTO order = order(3);
        ConfirmFormDTO form = confirmForm(order);
        form.setAmount(100L);

        assertRejected(400, () -> service.confirm(admin(), form));
        assertEquals(0, toss.confirmCalls);
        assertEquals(0, paidPeriods());
    }

    @Test
    @DisplayName("없는 주문 번호·결제 키가 비거나 너무 길면 토스를 부르지 않는다")
    void badInput() {
        OrderResultDTO order = order(3);
        ConfirmFormDTO noOrder = confirmForm(order);
        noOrder.setOrderId("x".repeat(65));
        ConfirmFormDTO noKey = confirmForm(order);
        noKey.setPaymentKey(" ");
        ConfirmFormDTO longKey = confirmForm(order);
        longKey.setPaymentKey("k".repeat(201));

        assertRejected(404, () -> service.confirm(admin(), noOrder));
        assertRejected(400, () -> service.confirm(admin(), noKey));
        assertRejected(400, () -> service.confirm(admin(), longKey));
        assertEquals(0, toss.confirmCalls);
    }

    @Test
    @DisplayName("승인 응답의 금액·주문·상태·통화가 주문과 다르면 반영하지 않는다")
    void responseMustMatchOrder() {
        Map<String, java.util.function.Consumer<TossPaymentDTO>> tampered = new HashMap<>();
        tampered.put("금액", t -> t.setTotalAmount(1L));
        tampered.put("주문", t -> t.setOrderId("other"));
        tampered.put("상태", t -> t.setStatus("WAITING_FOR_DEPOSIT"));
        tampered.put("통화", t -> t.setCurrency("USD"));
        tampered.forEach((name, change) -> {
            OrderResultDTO order = order(3);
            TossPaymentDTO answer = done(order.getOrderNo(), 12000L, "2026-11-02T14:03:22+09:00");
            change.accept(answer);
            toss.confirmAnswer = answer;

            assertRejected(502, () -> confirm(order));
            assertEquals(OrderStatus.ORD_CREATED, status(order), name);
            assertEquals(0, paidPeriods(), name);
        });
    }

    @Test
    @DisplayName("카드 거절이면 주문을 실패로 닫고 유료 권한을 주지 않으며, 그 주문은 다시 쓸 수 없다")
    void rejectedByToss() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException(403, "REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다.");

        BillingException e = assertThrows(BillingException.class, () -> confirm(order));

        assertEquals(400, e.getStatus());
        assertFalse(e.isRetry());
        assertEquals(OrderStatus.ORD_FAILED, status(order));
        assertEquals("REJECT_CARD_PAYMENT", payments.failed.get(0).getFailureCode());
        assertNull(payments.failed.get(0).getPaymentKey());
        assertEquals(0, paidPeriods());

        toss.confirmError = null;
        assertRejected(409, () -> confirm(order));
        assertEquals(1, toss.confirmCalls);
    }

    @Test
    @DisplayName("시간 초과인데 토스에 승인돼 있으면 반영한다")
    void timeoutButApproved() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException("연결 실패", new java.io.IOException());
        toss.found = done(order.getOrderNo(), 12000L, "2026-11-02T14:03:22+09:00");

        confirm(order);

        assertEquals(OrderStatus.ORD_APPLIED, status(order));
        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("시간 초과에 승인 여부도 모르면 실패로 닫지 않고, 다시 확인할 수 있게 알린다")
    void timeoutUnknown() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException(500, "FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 오류");
        toss.findError = new TossApiException("연결 실패", new java.io.IOException());

        BillingException e = assertThrows(BillingException.class, () -> confirm(order));

        assertEquals(503, e.getStatus());
        assertTrue(e.isRetry());
        assertEquals(OrderStatus.ORD_CREATED, status(order));
        assertTrue(payments.failed.isEmpty());

        // 다시 확인: 이번에는 승인된다
        toss.confirmError = null;
        confirm(order);
        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("토스에 승인 안 된 결제면(인증만 됨) 다시 확인하도록 알린다")
    void foundButNotDone() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException(500, "UNKNOWN", "오류");
        TossPaymentDTO inProgress = done(order.getOrderNo(), 12000L, null);
        inProgress.setStatus("IN_PROGRESS");
        toss.found = inProgress;

        assertRejected(503, () -> confirm(order));
        assertEquals(0, paidPeriods());
    }

    @Test
    @DisplayName("이미 처리된 결제라는 답이면 토스에서 찾아 반영한다")
    void alreadyProcessed() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException(400, "ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제 입니다.");
        toss.found = done(order.getOrderNo(), 12000L, "2026-11-02T14:03:22+09:00");

        confirm(order);

        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("같은 승인 요청이 처리 중이면 실패로 닫지 않고 다시 확인하게 한다")
    void inProgress() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException(409, "IDEMPOTENT_REQUEST_PROCESSING", "처리 중");

        BillingException e = assertThrows(BillingException.class, () -> confirm(order));

        assertTrue(e.isRetry());
        assertEquals(OrderStatus.ORD_CREATED, status(order));
    }

    @Test
    @DisplayName("키가 잘못됐으면 구매자 잘못이 아니므로 주문을 실패로 닫지 않는다")
    void wrongKeyIsNotRejection() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException(401, "UNAUTHORIZED_KEY", "인증되지 않은 키");

        assertRejected(503, () -> confirm(order));
        assertEquals(OrderStatus.ORD_CREATED, status(order));
    }

    @Test
    @DisplayName("승인 뒤 저장이 실패해도 승인 기록이 남고, 다시 보내면 토스를 다시 부르지 않고 반영한다")
    void applyFailsThenRetry() {
        OrderResultDTO order = order(3);
        subs.failNextPaidInsert = true;

        assertThrows(IllegalStateException.class, () -> confirm(order));
        assertEquals(OrderStatus.ORD_CONFIRMED, status(order));
        assertEquals(1, payments.approved.size());
        assertEquals(0, paidPeriods());

        confirm(order);

        assertEquals(1, toss.confirmCalls);
        assertEquals(1, payments.approved.size());
        assertEquals(OrderStatus.ORD_APPLIED, status(order));
        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("새 주문을 만들면 전 주문은 닫히고, 전 주문으로는 토스를 부르지 않는다")
    void newOrderClosesOld() {
        OrderResultDTO first = order(3);
        OrderResultDTO second = order(5);

        assertEquals(OrderStatus.ORD_FAILED, status(first));
        assertEquals(OrderStatus.ORD_CREATED, status(second));
        assertRejected(409, () -> confirm(first));
        assertEquals(0, toss.confirmCalls);
        assertTrue(payments.failed.isEmpty(), "닫힌 주문은 실패 건수에 들어가지 않는다");

        confirm(second);
        assertEquals(5, subs.periods.get(1).getPurchasedSeats());
    }

    @Test
    @DisplayName("이미 유료가 된 뒤에는 새 주문을 만들 수 없다")
    void noOrderAfterPaid() {
        confirm(order(3));

        assertRejected(409, () -> order(1));
        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("승인을 기다리는 사이 실패 알림이 와도 토스가 승인했으면 기록하고 반영한다")
    void approvalBeatsFailureReport() {
        OrderResultDTO order = order(3);
        PaymentOrderDTO inFlight = payments.getOrder(COMPANY, order.getOrderNo()); // 승인 요청 중인 요청이 읽은 주문
        FailFormDTO fail = new FailFormDTO();
        fail.setOrderId(order.getOrderNo());
        fail.setCode("PAY_PROCESS_ABORTED");
        service.reportFailure(admin(), fail);
        assertEquals(OrderStatus.ORD_FAILED, status(order));

        PaymentRecordService record = new PaymentRecordService(payments, subs);
        record.recordApproval(inFlight, done(order.getOrderNo(), 12000L, "2026-11-02T14:03:22+09:00"));
        record.applyOrder(inFlight);

        assertEquals(OrderStatus.ORD_APPLIED, status(order));
        assertEquals(1, payments.approved.size());
        assertEquals(1, paidPeriods());
        assertEquals(order.getOrderNo(), payments.approved.get(0).getIdempotencyKey());
        assertEquals(order.getOrderNo() + "-fail", payments.failed.get(0).getIdempotencyKey(), "멱등키 고유 제약이 겹치지 않는다");
        assertEquals("OK", service.confirm(admin(), confirmForm(order)).getResult());
    }

    @Test
    @DisplayName("승인을 기다리는 사이 새 주문이 생겨도 승인된 주문을 반영하고, 반영 뒤 새 주문은 토스를 부르지 않는다")
    void approvalBeatsNewOrder() {
        OrderResultDTO first = order(3);
        PaymentOrderDTO inFlight = payments.getOrder(COMPANY, first.getOrderNo());
        OrderResultDTO second = order(5);

        PaymentRecordService record = new PaymentRecordService(payments, subs);
        record.recordApproval(inFlight, done(first.getOrderNo(), 12000L, "2026-11-02T14:03:22+09:00"));
        record.applyOrder(inFlight);

        assertEquals(OrderStatus.ORD_APPLIED, status(first));
        assertRejected(409, () -> confirm(second));
        assertEquals(0, toss.confirmCalls);
        assertEquals(1, paidPeriods());
    }

    @Test
    @DisplayName("승인됐는데 반영 못 한 주문이 있으면 새 주문을 만들지 않고 그 주문을 반영한다(이중 결제 방지)")
    void stuckOrderIsAppliedInsteadOfNewOrder() {
        OrderResultDTO order = order(3);
        subs.failNextPaidInsert = true;
        assertThrows(IllegalStateException.class, () -> confirm(order));
        assertEquals(OrderStatus.ORD_CONFIRMED, status(order));

        assertRejected(409, () -> order(5));

        assertEquals(OrderStatus.ORD_APPLIED, status(order));
        assertEquals(1, payments.orders.size());
        assertEquals(1, paidPeriods());
        assertEquals(3, subs.periods.get(1).getPurchasedSeats());
    }

    @Test
    @DisplayName("주문 둘의 승인 기록이 모두 있어도 유료 기간은 하나다. 늦은 주문은 승인 기록만 남는다")
    void twoConfirmedOrders() {
        OrderResultDTO first = order(3);
        PaymentOrderDTO firstRead = payments.getOrder(COMPANY, first.getOrderNo());
        OrderResultDTO second = order(5);
        PaymentOrderDTO secondRead = payments.getOrder(COMPANY, second.getOrderNo());
        PaymentRecordService record = new PaymentRecordService(payments, subs);
        TossPaymentDTO firstPaid = done(first.getOrderNo(), 12000L, "2026-11-02T14:03:22+09:00");
        TossPaymentDTO secondPaid = done(second.getOrderNo(), 20000L, "2026-11-02T14:03:23+09:00");
        secondPaid.setPaymentKey("pay_key_2");
        record.recordApproval(firstRead, firstPaid);
        record.recordApproval(secondRead, secondPaid);
        record.applyOrder(firstRead);

        assertRejected(409, () -> record.applyOrder(secondRead));

        assertEquals(1, paidPeriods());
        assertEquals(3, subs.periods.get(1).getPurchasedSeats());
        assertEquals(OrderStatus.ORD_CONFIRMED, status(second));
        assertEquals(2, payments.approved.size());
    }

    @Test
    @DisplayName("주문 뒤 사용 중 계정이 구매 인원보다 많아지면 승인 전에 거절한다")
    void accountsGrewAfterOrder() {
        OrderResultDTO order = order(3);
        employees.active = 4;

        assertRejected(409, () -> confirm(order));
        assertEquals(0, toss.confirmCalls);
    }

    // ---------- 실패 알림 ----------

    @Test
    @DisplayName("결제 실패 알림은 주문을 실패로 닫고 사유를 남긴다. 긴 값은 칸 길이에 맞춰 자른다")
    void reportFailure() {
        OrderResultDTO order = order(3);
        FailFormDTO form = new FailFormDTO();
        form.setOrderId(order.getOrderNo());
        form.setCode("c".repeat(150));
        form.setMessage("m".repeat(1500));

        service.reportFailure(admin(), form);

        assertEquals(OrderStatus.ORD_FAILED, status(order));
        assertEquals(100, payments.failed.get(0).getFailureCode().length());
        assertEquals(1000, payments.failed.get(0).getFailureMessage().length());
    }

    @Test
    @DisplayName("구매자가 결제창을 닫은 것은 실패로 기록하지 않는다")
    void userCancelIsNotFailure() {
        OrderResultDTO order = order(3);
        FailFormDTO form = new FailFormDTO();
        form.setOrderId(order.getOrderNo());
        form.setCode("PAY_PROCESS_CANCELED");

        service.reportFailure(admin(), form);

        assertEquals(OrderStatus.ORD_CREATED, status(order));
        assertTrue(payments.failed.isEmpty());
    }

    @Test
    @DisplayName("이미 반영한 주문·다른 업체 주문의 실패 알림은 무시한다")
    void failureCannotUndoApplied() {
        OrderResultDTO order = order(3);
        confirm(order);
        FailFormDTO form = new FailFormDTO();
        form.setOrderId(order.getOrderNo());
        form.setCode("PAY_PROCESS_ABORTED");

        service.reportFailure(admin(), form);
        service.reportFailure(user("ROLE_ADMIN", OTHER_COMPANY), form);

        assertEquals(OrderStatus.ORD_APPLIED, status(order));
        assertTrue(payments.failed.isEmpty());
    }

    // ---------- 화면 값 ----------

    @Test
    @DisplayName("체험 중 화면 값: 결제할 수 있고, 마지막 날과 남은 일수를 날짜로 센다")
    void billingWhileTrial() {
        LocalDate today = LocalDate.now(SEOUL);
        subs.periods.clear();
        subs.periods.add(trial(today.minusDays(1).atStartOfDay(), today.plusDays(13).atStartOfDay()));
        employees.active = 2;

        BillingDTO billing = service.getBilling(admin());

        assertEquals("TRIAL", billing.getStatus());
        assertFalse(billing.isPaid());
        assertTrue(billing.isCanPay());
        assertEquals("FIRST", billing.getMode());
        assertEquals(2, billing.getAccountsInUse());
        assertEquals(4000L, billing.getPricePerUser());
        assertEquals(10, billing.getTrialSeats());
        assertEquals(12, billing.getDaysLeft());
        assertEquals(today.plusDays(12).atStartOfDay().toString(), billing.getEnd());
        assertEquals("가나상사", billing.getCompany());
        assertNull(billing.getServiceFrom());
    }

    @Test
    @DisplayName("결제 뒤 화면 값: 유료이고 더 결제할 수 없으며 이력에 방금 결제가 있다")
    void billingAfterPaid() {
        OrderResultDTO order = order(3);
        toss.confirmAnswer = done(order.getOrderNo(), 12000L,
                LocalDateTime.now(SEOUL).atZone(SEOUL).toOffsetDateTime().toString());
        confirm(order);

        BillingDTO billing = service.getBilling(admin());

        assertEquals("PAID", billing.getStatus());
        assertTrue(billing.isPaid());
        assertEquals("MONTHLY", billing.getPlan());
        assertFalse(billing.isCanPay());
        assertEquals(3, billing.getUsers());
        assertNotNull(billing.getServiceFrom());
        assertEquals(order.getOrderNo(), billing.getPayments().get(0).getId());
        assertEquals(0, billing.getFailedPayments());
    }

    @Test
    @DisplayName("결제 실패 건수를 센다")
    void billingCountsFailures() {
        OrderResultDTO order = order(3);
        toss.confirmError = new TossApiException(403, "REJECT_CARD_PAYMENT", "거절");
        assertThrows(BillingException.class, () -> confirm(order));

        BillingDTO billing = service.getBilling(admin());

        assertEquals(1, billing.getFailedPayments());
        assertEquals("FAILED", billing.getPayments().get(0).getResult());
        assertNull(billing.getPayments().get(0).getFrom());
    }

    @Test
    @DisplayName("견적: 인원 × 4,000원, 오늘부터 달력 1개월, 할인 없음")
    void quote() {
        QuoteDTO quote = service.quote(admin(), "MONTHLY", 1200 / 100);

        assertEquals(48000L, quote.getAmount());
        assertEquals("12명 × 4,000원 × 1개월", quote.getFormula());
        assertEquals(0, quote.getPct());
        LocalDate today = LocalDate.now(SEOUL);
        assertEquals(today.atStartOfDay().toString(), quote.getFrom());
        assertEquals(today.plusMonths(1).minusDays(1).atStartOfDay().toString(), quote.getTo());
    }

    @Test
    @DisplayName("결제 금액 계산: 500명 × 4,000원 × 1개월")
    void totalAmount() {
        assertEquals(2_000_000L, PaymentService.totalAmount(500, 4000L, 1));
    }
}
