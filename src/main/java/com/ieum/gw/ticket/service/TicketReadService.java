package com.ieum.gw.ticket.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.ticket.dao.TicketReadDAO;
import com.ieum.gw.ticket.dto.TicketCommentDTO;
import com.ieum.gw.ticket.dto.TicketHistoryDTO;
import lombok.RequiredArgsConstructor;

/** 내 티켓의 읽기 기능. 배정·처리·종료 같은 상태 변경은 이 클래스에서 하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketReadService {
    private final TicketReadDAO dao;

    public List<TicketCommentDTO> getMyComments(LoginUser user, Long itTicketId) {
        requireMyTicket(user, itTicketId);
        return dao.selectMyComments(user.getCompanyId(), user.getEmployeeId(), itTicketId);
    }

    public List<TicketHistoryDTO> getMyHistory(LoginUser user, Long itTicketId) {
        requireMyTicket(user, itTicketId);
        return dao.selectMyHistory(user.getCompanyId(), user.getEmployeeId(), itTicketId);
    }

    private void requireMyTicket(LoginUser user, Long itTicketId) {
        if (user == null || user.getCompanyId() == null || user.getCompanyId() <= 0
                || user.getEmployeeId() == null || user.getEmployeeId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (itTicketId == null || itTicketId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "티켓 번호가 올바르지 않습니다.");
        }
        if (dao.countMyTicket(user.getCompanyId(), user.getEmployeeId(), itTicketId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "티켓을 찾을 수 없습니다.");
        }
    }
}
