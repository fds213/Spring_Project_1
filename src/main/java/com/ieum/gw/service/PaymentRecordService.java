package com.ieum.gw.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.dao.PaymentDAO;
import com.ieum.gw.dao.SubscriptionDAO;
import com.ieum.gw.dto.OrderPurpose;
import com.ieum.gw.dto.OrderStatus;
import com.ieum.gw.dto.PaymentDTO;
import com.ieum.gw.dto.PaymentOrderDTO;
import com.ieum.gw.dto.ServicePeriodDTO;
import com.ieum.gw.dto.SubscriptionChangeDTO;
import com.ieum.gw.dto.TossPaymentDTO;

import lombok.RequiredArgsConstructor;

/**
 * 결제 결과를 DB에 남기는 트랜잭션들. 토스 호출은 트랜잭션 밖이어야 해서(AGENTS.md '트랜잭션·동시 처리')
 * 호출 순서는 PaymentService가 정하고, 트랜잭션이 붙는 저장만 여기로 뗐다.
 *
 * 승인 기록과 이용 반영을 나눈 까닭: 반영이 실패해도 '토스가 승인했다'는 기록(ORD_CONFIRMED)이 남아,
 * 같은 요청을 다시 보내면 토스를 다시 부르지 않고 반영만 다시 한다. 운영자 복구(OPS-07, 이번 제외)도 이 상태에서 이어 만든다.
 */
@Service
@RequiredArgsConstructor
public class PaymentRecordService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRecordService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_FAILURE_CODE = 100;     // PAYMENT.FAILURE_CODE
    private static final int MAX_FAILURE_MESSAGE = 1000; // PAYMENT.FAILURE_MESSAGE
    private static final String FAILURE_KEY_SUFFIX = "-fail"; // 실패 행이 있어도 승인 행이 주문 번호를 멱등키로 쓸 수 있게(UQ_22_02)

    private final PaymentDAO paymentDAO;
    private final SubscriptionDAO subscriptionDAO;

    /**
     * 새 주문을 저장한다. 업체당 결제 전 주문은 하나만 살려 둔다: 탭 두 개에서 만든 주문이 둘 다 승인되는 일을 막는다.
     * 닫힌 주문은 승인 확인 때 토스를 부르지 않으므로 결제창에서 인증까지 했어도 돈이 나가지 않는다.
     */
    @Transactional
    public void saveNewOrder(PaymentOrderDTO order) {
        subscriptionDAO.lockCompany(order.getCompanyId());
        paymentDAO.closeOpenOrders(order.getCompanyId());
        paymentDAO.insertOrder(order);
    }

    /**
     * 토스 승인을 기록한다. 조건부 UPDATE가 문이다: 같은 주문의 요청이 동시에 와도 한 요청만 승인 행을 넣는다.
     * 승인을 기다리는 사이 실패 알림이나 새 주문으로 닫혔어도 기록한다. 토스가 승인한 결제를 잃으면 안 된다.
     */
    @Transactional
    public void recordApproval(PaymentOrderDTO order, TossPaymentDTO toss) {
        Long companyId = order.getCompanyId();
        Long orderId = order.getPaymentOrderId();
        if (paymentDAO.confirmOrder(companyId, orderId) == 0) {
            return; // 다른 요청이 먼저 기록했다
        }
        PaymentDTO payment = new PaymentDTO();
        payment.setCompanyId(companyId);
        payment.setPaymentOrderId(orderId);
        payment.setPaymentKey(toss.getPaymentKey());
        payment.setIdempotencyKey(order.getExternalOrderNo());
        payment.setApprovedAmount(toss.getTotalAmount());
        payment.setApprovedAt(approvedAt(toss.getApprovedAt()));
        paymentDAO.insertApprovedPayment(payment);
    }

    /**
     * 결제 전 주문을 실패로 닫고 사유를 남긴다. 이미 승인·반영·실패한 주문이면 아무것도 하지 않는다.
     * 결제 키는 비운다: 실패 건의 키는 브라우저가 보낸 값이라 믿을 수 없고, 고유 제약을 차지하면 안 된다.
     */
    @Transactional
    public void recordFailure(PaymentOrderDTO order, String code, String message) {
        Long companyId = order.getCompanyId();
        Long orderId = order.getPaymentOrderId();
        if (paymentDAO.changeOrderStatus(companyId, orderId, OrderStatus.ORD_CREATED, OrderStatus.ORD_FAILED) == 0) {
            return;
        }
        PaymentDTO payment = new PaymentDTO();
        payment.setCompanyId(companyId);
        payment.setPaymentOrderId(orderId);
        payment.setIdempotencyKey(order.getExternalOrderNo() + FAILURE_KEY_SUFFIX);
        payment.setFailureCode(left(code, MAX_FAILURE_CODE));
        payment.setFailureMessage(left(message, MAX_FAILURE_MESSAGE));
        paymentDAO.insertFailedPayment(payment);
    }

    /**
     * 승인을 기록한 주문을 이용 기간에 반영한다. 업체 행을 잠그고 주문을 다시 읽어, 같은 주문은 한 번만 반영하고
     * 다른 주문이 거의 동시에 승인돼도 유료 기간이 겹쳐 생기지 않는다(AGENTS.md '트랜잭션·동시 처리').
     * 첫 결제: 체험 기간은 그대로 두고 그 위에 유료 기간을 만든다(ServicePeriods.pick이 유료로 본다). 남은 체험은 더하지 않는다(PAY-02).
     * 연장: 지금 만료 시각에 새 기간을 이어 붙이고 최초 기준일을 물려받는다(SUB-03). 주문 뒤 만료된 다음 승인됐으면 승인 시각부터 다시 센다.
     * 연장 두 건이 모두 승인되면 잠금 순서대로 차례로 이어 붙는다. 둘 다 돈을 냈으니 둘 다 기간을 준다.
     *
     * @throws BillingException 첫 결제 주문인데 이미 유료 기간이 있을 때. 주문은 ORD_CONFIRMED로 남는다
     */
    @Transactional
    public void applyOrder(PaymentOrderDTO confirmed) {
        Long companyId = confirmed.getCompanyId();
        subscriptionDAO.lockCompany(companyId);

        PaymentOrderDTO order = paymentDAO.getOrder(companyId, confirmed.getExternalOrderNo());
        if (order.getStatusCode() == OrderStatus.ORD_APPLIED) {
            return;
        }
        if (order.getStatusCode() != OrderStatus.ORD_CONFIRMED) {
            throw new IllegalStateException("승인 기록이 없는 주문입니다: orderNo=" + order.getExternalOrderNo());
        }
        List<ServicePeriodDTO> periods = subscriptionDAO.listPeriods(companyId);
        boolean renew = order.getPurposeCode() == OrderPurpose.PP_RENEW;
        if (!renew && periods.stream().anyMatch(p -> !p.isTrial())) {
            logger.error("이미 유료인 업체의 다른 주문이 승인됨, 반영하지 않음: companyId={}, orderNo={}",
                    companyId, order.getExternalOrderNo());
            throw new BillingException(409, "이미 유료로 바뀐 업체라 이 결제는 반영하지 않았습니다. 운영자에게 문의해 주세요.");
        }

        LocalDateTime now = LocalDateTime.now(SEOUL);
        // 연장은 유료 기간만 본다. 시연 업체처럼 체험이 유료보다 늦게 끝나도 체험 만료에 잇거나 유료 만료를 놓치지 않게
        ServicePeriodDTO current = ServicePeriods.pick(renew ? paidOnly(periods) : periods, now);
        PaymentDTO payment = paymentDAO.getApprovedPayment(companyId, order.getPaymentOrderId());
        LocalDateTime startsAt = payment.getApprovedAt();
        LocalDate anchor = startsAt.toLocalDate();
        LocalDateTime endsAt = ServicePeriods.paidEndsAt(anchor, order.getDurationMonths());
        if (renew && startsAt.isBefore(current.getEndsAt())) {
            startsAt = current.getEndsAt();
            anchor = current.getBillingAnchorDate();
            endsAt = ServicePeriods.extendedEndsAt(startsAt.toLocalDate(), anchor, order.getDurationMonths());
        }

        ServicePeriodDTO paid = new ServicePeriodDTO();
        paid.setCompanyId(companyId);
        paid.setSubscriptionPlanId(order.getSubscriptionPlanId());
        paid.setSourceOrderId(order.getPaymentOrderId());
        paid.setStartsAt(startsAt);
        paid.setEndsAt(endsAt);
        paid.setPurchasedSeats(order.getSeatCount());
        paid.setBillingAnchorDate(anchor);
        subscriptionDAO.insertPaidPeriod(paid);

        SubscriptionChangeDTO change = new SubscriptionChangeDTO();
        change.setCompanyId(companyId);
        change.setPaymentId(payment.getPaymentId());
        change.setPaymentOrderId(order.getPaymentOrderId());
        change.setServicePeriodId(paid.getServicePeriodId());
        change.setChangeType(order.getPurposeCode());
        if (current != null) {
            change.setBeforeSeats(ServicePeriods.seatLimitOf(current));
            change.setBeforeEndsAt(current.getEndsAt());
        }
        change.setAfterSeats(order.getSeatCount());
        change.setAfterEndsAt(paid.getEndsAt());
        change.setAppliedAt(now);
        paymentDAO.insertSubscriptionChange(change);

        if (paymentDAO.markOrderApplied(companyId, order.getPaymentOrderId(), now) == 0) {
            throw new IllegalStateException("주문을 반영 완료로 바꾸지 못했습니다: orderNo=" + order.getExternalOrderNo());
        }
    }

    private static List<ServicePeriodDTO> paidOnly(List<ServicePeriodDTO> periods) {
        return periods.stream().filter(p -> !p.isTrial()).collect(Collectors.toList());
    }

    // 토스 승인 시각을 한국 시간으로 바꾼다. 없거나 알아볼 수 없으면 지금 시각으로 본다
    private static LocalDateTime approvedAt(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(SEOUL).toLocalDateTime();
        } catch (NullPointerException | DateTimeException e) {
            logger.warn("토스 승인 시각을 읽지 못해 지금 시각을 씀: {}", value);
            return LocalDateTime.now(SEOUL);
        }
    }

    private static String left(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
