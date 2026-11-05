package com.ieum.gw.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;

import lombok.RequiredArgsConstructor;

/** 조직도 (요구분석서 3.4, 사이드 메뉴 '회사 > 조직도'). 로그인한 직원 누구나, 자기 업체만 */
@Controller
@RequiredArgsConstructor
public class EmployeeOrgController {

	private final EmployeeService employeeService;

	/** 조직도 화면. 목록은 화면(org.js)이 /employee/api/org로 받는다 */
	@GetMapping("/employee/org")
	public String page() {
		return "employee/org";
	}

	/** 조직도 데이터: { me: 내 employeeId, departments: [{ departmentId, departmentName, members }] } */
	@GetMapping("/employee/api/org")
	@ResponseBody
	public Map<String, Object> api(@AuthenticationPrincipal LoginUser loginUser) {
		Map<String, Object> body = new HashMap<>(); // me가 null일 수 있어 Map.of 대신
		body.put("me", loginUser.getEmployeeId());
		body.put("departments", employeeService.getOrgChart(loginUser.getCompanyId()));
		return body;
	}
}