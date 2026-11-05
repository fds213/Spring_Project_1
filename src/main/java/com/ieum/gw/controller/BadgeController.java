package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.BadgeService;

import lombok.RequiredArgsConstructor;

/** 사이드바 배지 수 (common, AGENTS.md). 로그인한 사람만 (Security의 /** 규칙) */
@Controller
@RequiredArgsConstructor
public class BadgeController {

	private final BadgeService badgeService;

	/** 사이드바 배지 수 { success, data: { 키: 수 } } (common.js가 15초마다) */
	@GetMapping("/badges")
	@ResponseBody
	public Map<String, Object> badges(@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("success", true, "data", badgeService.getBadges(loginUser));
	}
}