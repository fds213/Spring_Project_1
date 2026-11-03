package com.ieum.gw.ticket.dao;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.ieum.gw.ticket.dto.TicketCommentDTO;
import com.ieum.gw.ticket.dto.TicketHistoryDTO;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketReadDAO {
    private final TicketReadMapper mapper;

    public int countMyTicket(Long companyId, Long employeeId, Long itTicketId) {
        return mapper.countMyTicket(companyId, employeeId, itTicketId);
    }
    public List<TicketCommentDTO> selectMyComments(Long companyId, Long employeeId, Long itTicketId) {
        return mapper.selectMyComments(companyId, employeeId, itTicketId);
    }
    public List<TicketHistoryDTO> selectMyHistory(Long companyId, Long employeeId, Long itTicketId) {
        return mapper.selectMyHistory(companyId, employeeId, itTicketId);
    }
}
