package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;

import lombok.RequiredArgsConstructor;

/** 로그인한 본인의 계정 일 (ACC-07 비밀번호 변경). 로그인한 사람 누구나, 만료 업체도 됨(ReadOnlyInterceptor 예외) */
@Controller
@RequiredArgsConstructor
public class EmployeeAccountController {

	private final EmployeeService employeeService;

	/** 본인 비밀번호 변경. 계정은 로그인 정보로만 정하고 요청 값은 쓰지 않는다 (헤더 창, common.js 7) */
	@PostMapping("/employee/api/password")
	@ResponseBody
	public Map<String, Object> changePassword(@RequestBody Map<String, String> body,
			@AuthenticationPrincipal LoginUser loginUser) {
		employeeService.changePassword(loginUser.getCompanyId(), loginUser.getUserAccountId(),
				body.get("current"), body.get("password"));
		return Map.of("success", true);
	}

	/** 입력이 틀림 (현재 비밀번호 불일치, 8자 미만, 지금과 같음) → 400 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("success", false, "message", e.getMessage()));
	}

	/** 지금 상태로는 안 됨 (사용 중 계정 아님, 그사이 다른 창에서 바뀜) → 409 */
	@ExceptionHandler(IllegalStateException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("success", false, "message", e.getMessage()));
	}
}