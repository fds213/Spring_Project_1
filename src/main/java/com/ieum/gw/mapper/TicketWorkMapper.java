package com.ieum.gw.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ieum.gw.dto.TicketWorkDTO;
import com.ieum.gw.dto.TicketAdminCountsDTO;
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
    
    int insertComment(@Param("companyId") Long companyId, @Param("id") Long id, @Param("employeeId") Long employeeId, @Param("body") String body);
    
    List<TicketCommentDTO> comments(@Param("companyId") Long companyId, @Param("id") Long id);
    
    List<TicketHistoryDTO> history(@Param("companyId") Long companyId, @Param("id") Long id);
    
    /** 제목 검색 결과의 상태 탭별 건수를 조회한다. */
    TicketAdminCountsDTO countAdminTicketTabs(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("keyword") String keyword);
    
    /** 검색어와 상태 탭에 맞는 관리자 티켓 목록을 한 페이지 조회한다. */
    List<TicketWorkDTO> selectAdminTicketPage(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("keyword") String keyword, @Param("filter") String filter, @Param("offset") long offset, @Param("size") int size);
}
