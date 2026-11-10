package com.ieum.gw.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;
import com.ieum.gw.mapper.TicketWorkMapper;
import com.ieum.gw.dto.TicketWorkDTO;
import com.ieum.gw.dto.TicketAdminCountsDTO;
import com.ieum.gw.dto.TicketCommentDTO;
import com.ieum.gw.dto.TicketHistoryDTO;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketWorkDAO {
    private final TicketWorkMapper mapper;
    
    public List<TicketWorkDTO> selectMine(Long companyId, Long employeeId) { return mapper.selectMine(companyId, employeeId); }
    
    public List<TicketWorkDTO> selectCompany(Long companyId) { return mapper.selectCompany(companyId); }
    
    public TicketWorkDTO selectOne(Long companyId, Long id) { return mapper.selectOne(companyId, id); }
    
    public TicketWorkDTO lock(Long companyId, Long id) { return mapper.lock(companyId, id); }
    
    public int insert(TicketWorkDTO row) { return mapper.insert(row); }
    
    public int update(Map<String, Object> change) { return mapper.update(change); }
    
    public int insertHistory(Map<String, Object> change) { return mapper.insertHistory(change); }
    
    public int insertComment(Long companyId, Long id, Long employeeId, String body) { return mapper.insertComment(companyId, id, employeeId, body); }
    
    public List<TicketCommentDTO> comments(Long companyId, Long id) { return mapper.comments(companyId, id); }
    
    public List<TicketHistoryDTO> history(Long companyId, Long id) { return mapper.history(companyId, id); }

    /** 제목 검색 결과의 상태 탭별 건수를 조회한다. */
    public TicketAdminCountsDTO countAdminTicketTabs(Long companyId, Long employeeId, String keyword) {
    	return mapper.countAdminTicketTabs(companyId, employeeId, keyword);
    }
    
    /** 검색어와 상태 탭에 맞는 관리자 티켓 목록을 한 페이지 조회한다. */
    public List<TicketWorkDTO> selectAdminTicketPage(Long companyId, Long employeeId, String keyword, String filter, long offset, int size) {
    	return mapper.selectAdminTicketPage(companyId, employeeId, keyword, filter, offset, size);
    }

    /** 티켓과 첨부파일을 연결하고 저장된 행 수를 반환한다. */
    public int insertTicketFile(Long companyId, Long ticketId, Long attachmentId) {
    	return mapper.insertTicketFile(companyId, ticketId, attachmentId);
    }
    
    /** 같은 업체의 티켓에 연결된 첨부파일 ID를 조회한다. */
    public List<Long> selectTicketFileIds(Long companyId, Long ticketId) {
    	return mapper.selectTicketFileIds(companyId, ticketId);
    }
}
