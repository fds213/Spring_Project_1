package com.ieum.gw.employee.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.employee.dto.JoinOpenFormDTO;
import com.ieum.gw.employee.dto.JoinRequestFormDTO;
import com.ieum.gw.employee.service.JoinService;

import lombok.RequiredArgsConstructor;

/** 직원 가입: 관리자·부서장의 가입 승인 화면, 새 직원의 계정 만들기 화면 */
@Controller
@RequiredArgsConstructor
public class EmployeeJoinController {

	private final JoinService joinService;

	// ---------- 화면 ----------

	/** 가입 승인 (업체 관리자·부서장). 누가 볼 수 있는지는 API에서 확인한다 */
	@GetMapping("/employee/join")
	public String joinAdmin() {
		return "employee/join";
	}

	/** 계정 만들기 (로그인 없이 여는 주소, security-context.xml의 /join/**) */
	@GetMapping("/join")
	public String join() {
		return "common/join";
	}

	// ---------- 가입 승인 API ----------

	/** 가입 승인 화면에 보여 줄 내용 (employee.js renderJoin) */
	@GetMapping("/employee/api/join")
	@ResponseBody
	public Map<String, Object> board(@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", joinService.getBoard(loginUser));
	}
	
	/** 가입 승인 (관리자·부서장). 사번 발급 + 계정 사용 시작 */
	@PostMapping("/employee/api/join/requests/{reqId}/approve")
	@ResponseBody
	public Map<String, Object> approve(@PathVariable Long reqId, @AuthenticationPrincipal LoginUser loginUser) {
		Map<String, Object> result = joinService.approveRequest(loginUser, reqId);
		return Map.of("success", true, "data", result);
	}

	/** 가입 열기 (employee.js 가입 활성화). 관리자는 고른 부서들, 부서장은 자기 부서 */
	@PostMapping("/employee/api/join/openings")
	@ResponseBody
	public Map<String, Object> open(@RequestBody JoinOpenFormDTO form, @AuthenticationPrincipal LoginUser loginUser) {
		Map<String, Object> result = joinService.openJoin(loginUser, form);
		return Map.of("success", true, "data", result);
	}

	/** 한 부서 끄기. 부서장은 자기 부서만 */
	@PostMapping("/employee/api/join/openings/{dept}/delete")
	@ResponseBody
	public Map<String, Object> close(@PathVariable String dept, @AuthenticationPrincipal LoginUser loginUser) {
		joinService.closeJoin(loginUser, dept);
		return Map.of("success", true);
	}

	/** 모두 끄기. 관리자만 */
	@PostMapping("/employee/api/join/openings/delete")
	@ResponseBody
	public Map<String, Object> closeAll(@AuthenticationPrincipal LoginUser loginUser) {
		joinService.closeAllJoin(loginUser);
		return Map.of("success", true);
	}

	/** 업체 코드 확인 (계정 만들기 화면, 로그인 없음). 열린 부서·직급 목록 */
	@GetMapping("/join/api/companies/{code}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> checkCompany(@PathVariable String code) {
		Map<String, Object> result = joinService.checkCompany(code); // Service 부르기

		if (result == null) { // 받을 수 없는 업체
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "가입을 받을 수 없는 업체 코드입니다"));
		}
		return ResponseEntity.ok(Map.of("success", true, "data", result)); // 성공
	}

	// ① 가입 신청
	@PostMapping("/join/api/companies/{code}/requests")
	@ResponseBody
	public Map<String, Object> requestJoin(@PathVariable String code, @RequestBody JoinRequestFormDTO form) {
		Map<String, Object> view = joinService.requestJoin(code, form);
		return Map.of("success", true, "data", view);
	}

	// ② 내 신청 보기 (없으면 404)
	@GetMapping("/join/api/companies/{code}/requests/{reqId}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> myRequest(@PathVariable String code, @PathVariable String reqId) {
		Map<String, Object> view = joinService.getMyRequest(code, reqId);
		if (view == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "신청을 찾을 수 없습니다"));
		}
		return ResponseEntity.ok(Map.of("success", true, "data", view));
	}

	// ③ 내 신청 취소
	@PostMapping("/join/api/companies/{code}/requests/{reqId}/delete")
	@ResponseBody
	public Map<String, Object> cancelRequest(@PathVariable String code, @PathVariable String reqId) {
		joinService.cancelMyRequest(code, reqId);
		return Map.of("success", true);
	}

	// ---------- 오류 응답 ----------

	// ④ 409 처리기
	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	@ResponseBody
	public Map<String, Object> conflict(IllegalStateException e) {
		return Map.of("message", e.getMessage());
	}


	/** 관리자·부서장이 아님 → 403 { message } */
	@ExceptionHandler(AccessDeniedException.class)
	@ResponseBody
	public ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseBody
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
	}
}