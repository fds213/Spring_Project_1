package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeAdminService;

import lombok.RequiredArgsConstructor;

/** 사원 관리 (업체 관리자·인사 담당) */
@Controller
@RequiredArgsConstructor
public class EmployeeAdminController {

	private final EmployeeAdminService employeeAdminService;
	
	/** 사원 관리 화면. 목록은 화면이 /employee/admin/api/employees로 받는다 */
	@GetMapping("/employee/admin/list")
	public String list() {
		return "employee/admin/list";
	}
	
	/** 사원 관리 목록 (CFG-02) */
	@GetMapping("/employee/admin/api/employees")
	@ResponseBody
	public Map<String, Object> employees(@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", employeeAdminService.getList(loginUser));
	}
	
	/** 부서장 지정·해제 (업체 관리자만, Service에서 확인) */
	@PostMapping("/employee/admin/api/employees/{empNo}/head")
	@ResponseBody
	public Map<String, Object> head(@PathVariable String empNo, @RequestBody Map<String, Boolean> body,
			@AuthenticationPrincipal LoginUser loginUser) {
		boolean head = Boolean.TRUE.equals(body.get("head"));
		return Map.of("success", true, "data", employeeAdminService.setHead(loginUser, empNo, head));
	}

	@ExceptionHandler(AccessDeniedException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("success", false, "message", e.getMessage()));
	}
	
	/** 없는 사원 등 요청 값이 틀림 → 400 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("success", false, "message", e.getMessage()));
	}

	/** 지금 상태로는 안 됨 (부서 없음, 비활성 계정, 이미 해제됨) → 409 */
	@ExceptionHandler(IllegalStateException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("success", false, "message", e.getMessage()));
	}
}