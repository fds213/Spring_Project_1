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
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.code.EmployeeOrgKind;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeAdminService;
import com.ieum.gw.service.EmployeeOrgAdminService;

import lombok.RequiredArgsConstructor;

/** 사원 관리 (업체 관리자·인사 담당) */
@Controller
@RequiredArgsConstructor
public class EmployeeAdminController {

	private final EmployeeAdminService employeeAdminService;
	private final EmployeeOrgAdminService orgAdminService;
	
	/** 사원 관리 화면. 목록은 화면이 /employee/admin/api/employees로 받는다 */
	@GetMapping("/employee/admin/list")
	public String list() {
		return "employee/admin/list";
	}
	
	/** 부서 관리 화면. 목록은 화면이 /employee/admin/api/depts로 받는다 */
	@GetMapping("/employee/admin/dept")
	public String dept() {
		return "employee/admin/dept";
	}

	/** 직급 관리 화면. 목록은 화면이 /employee/admin/api/ranks로 받는다 */
	@GetMapping("/employee/admin/rank")
	public String rank() {
		return "employee/admin/rank";
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
		return Map.of("success", true, "data", employeeAdminService.setHead(loginUser, empNo, body.get("head")));
	}

	/** 역할 바꾸기 (업체 관리자만, Service에서 확인). 바뀐 역할은 그 사람이 다시 로그인하면 반영 */
	@PostMapping("/employee/admin/api/employees/{empNo}/role")
	@ResponseBody
	public Map<String, Object> role(@PathVariable String empNo, @RequestBody Map<String, String> body,
			@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", employeeAdminService.setRole(loginUser, empNo, body.get("role")));
	}
	
	/** 계정 비활성화·다시 사용 (업체 관리자·인사 담당, Service에서 확인) */
	@PostMapping("/employee/admin/api/employees/{empNo}/status")
	@ResponseBody
	public Map<String, Object> status(@PathVariable String empNo, @RequestBody Map<String, String> body,
			@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", employeeAdminService.setStatus(loginUser, empNo, body.get("action")));
	}
	
	/** 인사 정보 저장 (업체 관리자·인사 담당, Service에서 확인) */
	@PostMapping("/employee/admin/api/employees/{empNo}/hr")
	@ResponseBody
	public Map<String, Object> hr(@PathVariable String empNo, @RequestBody Map<String, String> body,
			@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", employeeAdminService.setHrInfo(loginUser, empNo,
				body.get("empType"), body.get("work"), body.get("gender")));
	}
	
	/** 부서·직급 목록 (CFG-01, 업체 관리자만) */
	@GetMapping("/employee/admin/api/{kind}")
	@ResponseBody
	public Map<String, Object> orgList(@PathVariable String kind, @AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", orgAdminService.list(loginUser, orgKind(kind)));
	}

	/** 부서·직급 추가 */
	@PostMapping("/employee/admin/api/{kind}")
	@ResponseBody
	public Map<String, Object> orgAdd(@PathVariable String kind, @RequestBody Map<String, String> body,
			@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", orgAdminService.add(loginUser, orgKind(kind), body.get("name")));
	}

	/** 부서·직급 이름 바꾸기 */
	@PostMapping("/employee/admin/api/{kind}/{name}")
	@ResponseBody
	public Map<String, Object> orgRename(@PathVariable String kind, @PathVariable String name,
			@RequestBody Map<String, String> body, @AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", orgAdminService.rename(loginUser, orgKind(kind), name, body.get("name")));
	}

	/** 부서·직급 삭제 (사용 안 함) */
	@PostMapping("/employee/admin/api/{kind}/{name}/delete")
	@ResponseBody
	public Map<String, Object> orgDelete(@PathVariable String kind, @PathVariable String name,
			@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", orgAdminService.delete(loginUser, orgKind(kind), name));
	}

	/** 부서·직급 순서 바꾸기 */
	@PostMapping("/employee/admin/api/{kind}/{name}/move")
	@ResponseBody
	public Map<String, Object> orgMove(@PathVariable String kind, @PathVariable String name,
			@RequestBody Map<String, Integer> body, @AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", orgAdminService.move(loginUser, orgKind(kind), name, body.get("dir")));
	}

	/** 부서 형태 바꾸기 */
	@PostMapping("/employee/admin/api/depts/{name}/type")
	@ResponseBody
	public Map<String, Object> deptType(@PathVariable String name, @RequestBody Map<String, String> body,
			@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", orgAdminService.setType(loginUser, name, body.get("type")));
	}

	/** 주소의 depts·ranks → 종류. 그 밖의 값이면 404 */
	private EmployeeOrgKind orgKind(String path) {
		EmployeeOrgKind kind = EmployeeOrgKind.fromPath(path);
		if (kind == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 주소입니다");
		}
		return kind;
	}

	/** 권한이 없음 → 403 (부서장 지정·부서·직급 관리·역할은 업체 관리자만이라 인사 담당도 받는다) */
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
	
	/** Service가 정한 상태 코드 그대로 (없는 부서·직급 404, 결재 자동 반려의 문서 잠금 409) */
	@ExceptionHandler(ResponseStatusException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> statusError(ResponseStatusException e) {
		String message = e.getReason() == null ? "처리하지 못했습니다" : e.getReason();
		return ResponseEntity.status(e.getStatus()).body(Map.of("success", false, "message", message));
	}
}