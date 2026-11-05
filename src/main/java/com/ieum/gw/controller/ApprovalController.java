package com.ieum.gw.controller;

import com.ieum.gw.dto.ApprovalSubmitDTO;
import com.ieum.gw.service.ApprovalService;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.ApprovalDecisionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = "/approval")
public class ApprovalController {
    private final ApprovalService service;

    @GetMapping(value = "/write")
    public String write(Model model) {
        return "approval/write";
    }

    @GetMapping(value = "/list")
    public String list(Model model) {
        return "approval/list";
    }

    /* ---------------API----------------- */

    @GetMapping(value = "/api/form-options")
    @ResponseBody
    public Map<String, Object> formOptions(@AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.getFormOptions(user.getCompanyId(), user.getEmployeeId()));
    }

    @PostMapping(value = "/api/documents")
    @ResponseBody
    public Map<String, Object> submit(@AuthenticationPrincipal LoginUser user, @RequestBody ApprovalSubmitDTO dto) {
        return Map.of("success", true, "data", service.submit(user,dto));
    }

    @GetMapping(value = "/api/box")
    @ResponseBody
    public Map<String, Object> box(@AuthenticationPrincipal LoginUser user){
        return Map.of("success", true, "data", service.box(user));
    }

    @PostMapping(value = "/api/documents/{documentId}/approve")
    @ResponseBody
    public Map<String, Object> approve(@RequestBody ApprovalDecisionDTO dto, @PathVariable long documentId, @AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.approve(user, dto, documentId));
    }

    @PostMapping(value = "/api/documents/{documentId}/reject")
    @ResponseBody
    public Map<String, Object> reject(@RequestBody ApprovalDecisionDTO dto, @PathVariable long documentId, @AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.reject(user, dto, documentId));
    }



    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException e) {
        String message = (e.getMessage() != null ? e.getMessage() : "요청을 처리하지 못했습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Map.of("message", e.getReason()));
    }


}
