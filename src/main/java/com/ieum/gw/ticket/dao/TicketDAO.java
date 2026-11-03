package com.ieum.gw.ticket.dao;

import org.springframework.stereotype.Repository;
import com.ieum.gw.ticket.dto.EquipmentTicketDTO;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketDAO {
    private final TicketMapper mapper;

    public int insertEquipmentTicket(Long companyId, Long requesterEmployeeId, Long approvalDocumentId,
                                     Long branchId, String purpose) {
        return mapper.insertEquipmentTicket(companyId, requesterEmployeeId, approvalDocumentId, branchId, purpose);
    }

    public EquipmentTicketDTO selectEquipmentTicket(Long companyId, Long approvalDocumentId) {
        return mapper.selectEquipmentTicket(companyId, approvalDocumentId);
    }
}
