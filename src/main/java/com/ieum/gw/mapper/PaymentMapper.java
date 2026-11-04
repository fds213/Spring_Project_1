package com.ieum.gw.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.OrderStatus;
import com.ieum.gw.dto.PaymentDTO;
import com.ieum.gw.dto.PaymentOrderDTO;
import com.ieum.gw.dto.PaymentRowDTO;
import com.ieum.gw.dto.SubscriptionChangeDTO;
import com.ieum.gw.dto.SubscriptionPlanDTO;

@Mapper
public interface PaymentMapper {

    SubscriptionPlanDTO getMonthlyPlan();

    SubscriptionPlanDTO getYearlyPlan();

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
