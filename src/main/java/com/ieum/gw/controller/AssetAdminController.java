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

    /** 자산 관리 화면에서 사용할 직원·지점 목록을 JSON으로 반환한다. */
    @GetMapping("/form-options")
    public Map<String, Object> formOptions(@AuthenticationPrincipal LoginUser user) {
    	return Map.of("success", true,
				  	  "data", service.getFormOptions(user));
    }

    /** 관리자가 선택한 자산의 지급·회수 이력을 JSON으로 반환한다. */
    @GetMapping("/{id}/assignments")
    public Map<String, Object> assignments(@AuthenticationPrincipal LoginUser user, 
    									   @PathVariable("id") Long assetId) {
    	return Map.of("success", true,
				  "data", service.getAssignments(user, assetId));
    }
    
    /** 같은 업체의 관리자용 자산 목록을 JSON으로 반환한다. */
    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal LoginUser user) {
    	return Map.of("success", true,
    				  "data", service.getAssetList(user));
    }
    
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
