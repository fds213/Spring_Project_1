package com.ieum.gw.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.TicketRequestDTO;
import com.ieum.gw.service.TicketWorkService;
import lombok.RequiredArgsConstructor;

/** form-options와 기존 원시 댓글·이력 조회 경로는 다른 Controller에서 유지한다. */
@Controller
@RequiredArgsConstructor
public class TicketWorkController {
    private static final Logger logger = LoggerFactory.getLogger(TicketWorkController.class);
    private final TicketWorkService service;

    @GetMapping("/asset/api/assets")
    @ResponseBody
    public Map<String, Object> myEquipment(@AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.myEquipment(user));
    }

    @GetMapping("/ticket/admin/list")
    public String adminList() { return "ticket/admin/list"; }

    @GetMapping("/ticket/admin/api/tickets")
    @ResponseBody
    public Map<String, Object> adminList(@AuthenticationPrincipal LoginUser user, @RequestParam(required = false) String filter) {
        return Map.of("success", true, "data", service.listAdmin(user, filter));
    }

    @GetMapping("/ticket/admin/api/tickets/{id}")
    @ResponseBody
    public Map<String, Object> adminDetail(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return Map.of("success", true, "data", service.detailAdmin(user, id));
    }

    @GetMapping("/ticket/api/tickets")
    @ResponseBody
    public Map<String, Object> mine(@AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.listMine(user));
    }

    @PostMapping("/ticket/api/tickets")
    @ResponseBody
    public Map<String, Object> create(@AuthenticationPrincipal LoginUser user, @RequestBody TicketRequestDTO form) {
        return Map.of("success", true, "data", Map.of("id", service.create(user, form)));
    }

    @PostMapping("/ticket/api/tickets/{id}/comments")
    @ResponseBody
    public Map<String, Object> comment(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.comment(user, id, form, false);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/api/tickets/{id}/close")
    @ResponseBody
    public Map<String, Object> close(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.close(user, id, form);
        return Map.of("success", true);
    }

    /** 로그인한 요청자의 장애·문의 티켓 취소를 처리한다. */
    @PostMapping("/ticket/api/tickets/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancel(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
    	service.cancel(user, id, form);
    	
    	return Map.of("success", true);
    }
    
    @PostMapping("/ticket/admin/api/tickets/{id}/assign")
    @ResponseBody
    public Map<String, Object> assign(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.assign(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/start")
    @ResponseBody
    public Map<String, Object> start(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.start(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/resolve")
    @ResponseBody
    public Map<String, Object> resolve(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.resolve(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/reject")
    @ResponseBody
    public Map<String, Object> reject(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.reject(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/comments")
    @ResponseBody
    public Map<String, Object> adminComment(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.comment(user, id, form, true);
        return Map.of("success", true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rejected(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("success", false, "message", e.getReason()));
    }

    @ExceptionHandler({TypeMismatchException.class, HttpMessageNotReadableException.class})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "요청 값의 형식을 확인해 주세요."));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> failed(RuntimeException e) {
        logger.error("티켓 업무 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
