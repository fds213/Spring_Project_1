package com.ieum.gw.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.OrderStatus;
import com.ieum.gw.dto.PaymentDTO;
import com.ieum.gw.dto.PaymentOrderDTO;
import com.ieum.gw.dto.PaymentRowDTO;
import com.ieum.gw.dto.SubscriptionChangeDTO;
import com.ieum.gw.dto.SubscriptionPlanDTO;
import com.ieum.gw.mapper.PaymentMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaymentDAO {

    private final PaymentMapper mapper;

    /** 쓰는 중인 월간 요금제. 없으면 null */
    public SubscriptionPlanDTO getMonthlyPlan() {
        return mapper.getMonthlyPlan();
    }

    /** 쓰는 중인 연간 요금제. 없으면 null */
    public SubscriptionPlanDTO getYearlyPlan() {
        return mapper.getYearlyPlan();
    }

    /** 주문 1행 기록. 성공하면 dto.paymentOrderId에 새 번호 */
    public int insertOrder(PaymentOrderDTO dto) {
        return mapper.insertOrder(dto);
    }

    /** 이 업체의 주문. 다른 업체의 주문 번호나 없는 번호면 null */
    public PaymentOrderDTO getOrder(Long companyId, String orderNo) {
        return mapper.getOrder(companyId, orderNo);
    }

    /** 주문이 from 상태일 때만 to로 바꾼다. 바뀐 행 수(0이면 이미 다른 상태) */
    public int changeOrderStatus(Long companyId, Long orderId, OrderStatus from, OrderStatus to) {
        return mapper.changeOrderStatus(companyId, orderId, from, to);
    }

    /** 토스 승인을 확인한 주문을 승인 확인 상태로. 바뀐 행 수(0이면 다른 요청이 이미 기록했다) */
    public int confirmOrder(Long companyId, Long orderId) {
        return mapper.confirmOrder(companyId, orderId);
    }

    /** 이 업체의 결제 전 주문을 모두 닫는다. 닫은 행 수 */
    public int closeOpenOrders(Long companyId) {
        return mapper.closeOpenOrders(companyId);
    }

    /** 승인을 기록했지만 아직 반영하지 못한 주문. 없으면 null */
    public PaymentOrderDTO getConfirmedOrder(Long companyId) {
        return mapper.getConfirmedOrder(companyId);
    }

    /** 승인 확인 상태의 주문을 반영 완료로. 바뀐 행 수 */
    public int markOrderApplied(Long companyId, Long orderId, LocalDateTime appliedAt) {
        return mapper.markOrderApplied(companyId, orderId, appliedAt);
    }

    /** 승인 성공 1행 기록. 성공하면 dto.paymentId에 새 번호 */
    public int insertApprovedPayment(PaymentDTO dto) {
        return mapper.insertApprovedPayment(dto);
    }

    /** 승인 실패 1행 기록 */
    public int insertFailedPayment(PaymentDTO dto) {
        return mapper.insertFailedPayment(dto);
    }

    /** 이 주문의 승인 성공 행. 없으면 null */
    public PaymentDTO getApprovedPayment(Long companyId, Long orderId) {
        return mapper.getApprovedPayment(companyId, orderId);
    }

    /** 이용 기간 변경 1행 기록 */
    public int insertSubscriptionChange(SubscriptionChangeDTO dto) {
        return mapper.insertSubscriptionChange(dto);
    }

    /** 업체의 결제 이력, 최신순 */
    public List<PaymentRowDTO> listPayments(Long companyId) {
        return mapper.listPayments(companyId);
    }
}
