package com.ieum.gw.subscription.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.subscription.dto.OrderStatus;
import com.ieum.gw.subscription.dto.PaymentDTO;
import com.ieum.gw.subscription.dto.PaymentOrderDTO;
import com.ieum.gw.subscription.dto.PaymentRowDTO;
import com.ieum.gw.subscription.dto.SubscriptionChangeDTO;
import com.ieum.gw.subscription.dto.SubscriptionPlanDTO;

@Mapper
public interface PaymentMapper {

    SubscriptionPlanDTO getMonthlyPlan();

    int insertOrder(PaymentOrderDTO dto);

    PaymentOrderDTO getOrder(@Param("companyId") Long companyId, @Param("orderNo") String orderNo);

    int changeOrderStatus(@Param("companyId") Long companyId, @Param("orderId") Long orderId,
                          @Param("from") OrderStatus from, @Param("to") OrderStatus to);

    int confirmOrder(@Param("companyId") Long companyId, @Param("orderId") Long orderId);

    int closeOpenOrders(@Param("companyId") Long companyId);

    PaymentOrderDTO getConfirmedOrder(@Param("companyId") Long companyId);

    int markOrderApplied(@Param("companyId") Long companyId, @Param("orderId") Long orderId,
                         @Param("appliedAt") LocalDateTime appliedAt);

    int insertApprovedPayment(PaymentDTO dto);

    int insertFailedPayment(PaymentDTO dto);

    PaymentDTO getApprovedPayment(@Param("companyId") Long companyId, @Param("orderId") Long orderId);

    int insertSubscriptionChange(SubscriptionChangeDTO dto);

    List<PaymentRowDTO> listPayments(@Param("companyId") Long companyId);
}
