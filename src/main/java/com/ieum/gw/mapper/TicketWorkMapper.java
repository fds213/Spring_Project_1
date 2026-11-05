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
}
