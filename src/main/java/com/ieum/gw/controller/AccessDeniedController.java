package com.ieum.gw.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 권한 없음 안내 화면. Security가 권한 없는 화면 요청을 여기로 보낸다(AjaxAwareAccessDeniedHandler) */
@Controller
public class AccessDeniedController {

	/** 권한 없음 안내 (?expired면 CSRF 토큰 만료 안내). 상태 코드는 403 */
	@GetMapping("/access-denied")
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public String page() {
		return "common/access-denied";
	}
}