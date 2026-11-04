package com.ieum.gw.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ieum.gw.service.AttendanceService;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 모든 화면 헤더의 "오늘 출근 08:52"(layout.tag 의 todayPunch)를 채운다.
 * 화면(HTML)을 여는 요청에만 조회한다. JSON 을 받는 요청(/attendance/api/…, /badges 폴링)은 Accept 가 다르니 건너뛴다.
 * 조회가 실패해도 화면은 그대로 연다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class AttendanceHeaderAdvice {

    private final AttendanceService service;

    @ModelAttribute("todayPunch")
    public Map<String, String> todayPunch(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) return null;
        LoginUser user = (LoginUser) auth.getPrincipal();
        if (user.getEmployeeId() == null) return null;
        try {
            return service.todayPunchForHeader(user.getCompanyId(), user.getEmployeeId());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
