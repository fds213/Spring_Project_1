package com.ieum.gw.ticket.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.ticket.dto.EquipmentTicketDTO;

@Mapper
public interface TicketMapper {
    int insertEquipmentTicket(@Param("companyId") Long companyId,
            @Param("requesterEmployeeId") Long requesterEmployeeId,
            @Param("approvalDocumentId") Long approvalDocumentId,
            @Param("branchId") Long branchId, @Param("purpose") String purpose);

    EquipmentTicketDTO selectEquipmentTicket(@Param("companyId") Long companyId,
            @Param("approvalDocumentId") Long approvalDocumentId);
}
