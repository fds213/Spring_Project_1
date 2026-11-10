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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.BranchSaveRequestDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.BranchService;
import lombok.RequiredArgsConstructor;

/** 지점 선택 목록만 제공한다. 등록·수정·사용 중지 화면은 필수 기능 뒤의 범위다. */
@Controller
@RequiredArgsConstructor
public class BranchController {
    private static final Logger logger = LoggerFactory.getLogger(BranchController.class);
    private final BranchService branchService;

    /** 업체 값은 요청에서 받지 않고 인증 정보에서만 가져온다. */
    @GetMapping("/location/api/branches")
    @ResponseBody
    public Map<String, Object> branches(@AuthenticationPrincipal LoginUser user) {
        if (user == null || user.getCompanyId() == null || user.getCompanyId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return Map.of("success", true, "data", branchService.getBranches(user.getCompanyId()));
    }
    
    /** 업체 관리자에게 지점 관리 목록을 반환한다. */
    @GetMapping("/location/admin/api/branches")
    @ResponseBody
    public Map<String, Object> adminBranches(@AuthenticationPrincipal LoginUser user) {
    	return Map.of("success", true, "data", branchService.getAdminBranches(user));
    }
    
    /** 업체 관리자의 지점 등록 요청을 처리한다. */
    @PostMapping("/location/admin/api/branches")
    @ResponseBody
    public Map<String, Object> createBranch(@AuthenticationPrincipal LoginUser user, @RequestBody BranchSaveRequestDTO request) {
    	branchService.createBranch(user, request);
    	
    	return Map.of("success", true);
    }
    
    /** 업체 관리자의 지점 정보 수정 요청을 처리한다. */
    @PostMapping("/location/admin/api/branches/{branchId}/update")
    @ResponseBody
    public Map<String, Object> updateBranch(@AuthenticationPrincipal LoginUser user, @PathVariable("branchId") Long branchId, @RequestBody BranchSaveRequestDTO request) {
    	branchService.updateBranch(user, branchId, request);
    	
    	return Map.of("success", true);
    }
    
    /** 업체 관리자의 지점 사용 중지 요청을 처리한다. */
    @PostMapping("/location/admin/api/branches/{branchId}/disable")
    @ResponseBody
    public Map<String, Object> disableBranch(@AuthenticationPrincipal LoginUser user, @PathVariable("branchId") Long branchId) {
        branchService.disableBranch(user, branchId);
        
        return Map.of("success", true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> rejected(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("success", false, "message", e.getReason()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> failed(RuntimeException e) {
        logger.error("지점 목록 조회 실패", e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "지점 목록을 불러오지 못했습니다. 잠시 뒤 다시 해 주세요."));
    }
    
    /** 읽을 수 없는 지점 등록 요청에 400으로 응답한다. */
    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> invalidRequest() {
    	return ResponseEntity.badRequest().body(Map.of("success", false, "message", "지점 등록 요청 값을 확인해 주세요."));
    }
}
