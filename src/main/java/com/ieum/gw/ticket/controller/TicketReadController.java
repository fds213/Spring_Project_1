package com.ieum.gw.ticket.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.ticket.service.TicketReadService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class TicketReadController {
    private static final Logger logger = LoggerFactory.getLogger(TicketReadController.class);
    private final TicketReadService ticketReadService;

    @GetMapping("/ticket/new")
    public String newTicket() {
        return "ticket/new";
    }

    @GetMapping("/ticket/mine")
    public String mine() {
        return "ticket/mine";
    }

    @GetMapping("/ticket/api/tickets/{itTicketId}/comments")
    @ResponseBody
    public Map<String, Object> comments(@AuthenticationPrincipal LoginUser user, @PathVariable Long itTicketId) {
        return Map.of("success", true, "data", ticketReadService.getMyComments(user, itTicketId));
    }

    @GetMapping("/ticket/api/tickets/{itTicketId}/history")
    @ResponseBody
    public Map<String, Object> history(@AuthenticationPrincipal LoginUser user, @PathVariable Long itTicketId) {
        return Map.of("success", true, "data", ticketReadService.getMyHistory(user, itTicketId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> rejected(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("success", false, "message", e.getReason()));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidId(TypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "티켓 번호가 올바르지 않습니다."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> failed(RuntimeException e) {
        logger.error("티켓 조회 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "티켓 정보를 불러오지 못했습니다. 잠시 뒤 다시 해 주세요."));
    }
}
