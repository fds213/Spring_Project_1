package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.AssetAllocationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RestController
@RequestMapping("/asset/admin/api/assets")
@RequiredArgsConstructor
public class AssetAdminController {
    private final AssetAllocationService service;

    @GetMapping("/{id}")
    public Map<String, Object> detail(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return Map.of("success", true, "data", service.detail(user, id));
    }

    @PostMapping("/{id}/assign")
    public Map<String, Object> assign(@AuthenticationPrincipal LoginUser user, @PathVariable Long id,
                                      @RequestBody AssetChange request) {
        service.assign(user, id, request.getEmployeeId(), request.getVersion());
        return Map.of("success", true);
    }

    @PostMapping("/{id}/return")
    public Map<String, Object> returnAsset(@AuthenticationPrincipal LoginUser user, @PathVariable Long id,
                                           @RequestBody AssetChange request) {
        service.returnAsset(user, id, request.getVersion(), request.getReason());
        return Map.of("success", true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("message", exception.getReason()));
    }

    @Getter
    @Setter
    public static class AssetChange {
        private Long employeeId;
        private Long version;
        private String reason;
    }
}
