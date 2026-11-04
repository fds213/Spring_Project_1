package com.ieum.gw.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.dto.TicketCommentDTO;
import com.ieum.gw.dto.TicketHistoryDTO;

@Mapper
public interface TicketReadMapper {
    int countMyTicket(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
            @Param("itTicketId") Long itTicketId);
    List<TicketCommentDTO> selectMyComments(@Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId, @Param("itTicketId") Long itTicketId);
    List<TicketHistoryDTO> selectMyHistory(@Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId, @Param("itTicketId") Long itTicketId);
}
