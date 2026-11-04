package com.ieum.gw.dao;

import org.springframework.stereotype.Repository;
import com.ieum.gw.dto.EquipmentTicketDTO;
import com.ieum.gw.mapper.TicketMapper;
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
