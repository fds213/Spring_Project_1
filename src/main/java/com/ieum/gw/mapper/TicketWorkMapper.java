package com.ieum.gw.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.dto.TicketWorkDTO;
import com.ieum.gw.dto.TicketCommentDTO;
import com.ieum.gw.dto.TicketHistoryDTO;

@Mapper
public interface TicketWorkMapper {
    List<TicketWorkDTO> selectMine(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);
    
    List<TicketWorkDTO> selectCompany(@Param("companyId") Long companyId);
    
    TicketWorkDTO selectOne(@Param("companyId") Long companyId, @Param("id") Long id);
    
    TicketWorkDTO lock(@Param("companyId") Long companyId, @Param("id") Long id);
    
    int insert(TicketWorkDTO row);
    
    int update(Map<String, Object> change);
    
    int insertHistory(Map<String, Object> change);
    
    int insertComment(@Param("companyId") Long companyId, @Param("id") Long id,
            @Param("employeeId") Long employeeId, @Param("body") String body);
    
    List<TicketCommentDTO> comments(@Param("companyId") Long companyId, @Param("id") Long id);
    
    List<TicketHistoryDTO> history(@Param("companyId") Long companyId, @Param("id") Long id);
    
    /** 티켓과 첨부파일을 연결한다. */
    int insertTicketFile(@Param("companyId") Long companyId, @Param("ticketId") Long ticketId, @Param("attachmentId") Long attachmentId);
    
    /** 같은 업체의 티켓에 연결된 첨부파일 ID를 조회한다. */
    List<Long> selectTicketFileIds(@Param("companyId") Long companyId, @Param("ticketId") Long ticketId);
}
