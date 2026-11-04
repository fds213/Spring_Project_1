package com.ieum.gw.dao;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import com.ieum.gw.mapper.TicketWorkMapper;
import com.ieum.gw.dto.TicketWorkDTO;
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
}
