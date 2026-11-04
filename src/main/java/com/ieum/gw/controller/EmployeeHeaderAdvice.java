package com.ieum.gw.controller;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ieum.gw.dto.EmployeeLoginInfoDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;

import lombok.RequiredArgsConstructor;

/**
 * 모든 화면의 헤더(업체명·부서·직급)와 사이드바(부서장 여부)에 쓰는 값(layout.tag의 loginInfo)을 채운다.
 * 화면(HTML)을 여는 요청에만 조회한다. 부서장은 관리자가 언제든 바꾸므로 세션에 담지 않는다.
 * 조회가 실패해도 화면은 그대로 연다. 가입 승인을 실제로 막는 것은 이 값이 아니라 JoinService의 부서장 검사다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class EmployeeHeaderAdvice {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeHeaderAdvice.class);

    private final EmployeeService employeeService;

    @ModelAttribute("loginInfo")
    public EmployeeLoginInfoDTO loginInfo(HttpServletRequest request) {
        // ① 화면 요청만 (API·배지 요청은 헤더를 그리지 않으니 DB를 읽지 않는다)
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")) {
            return null;
        }
        // ② 로그인한 사람만 (공개 화면은 비운다)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        LoginUser user = (LoginUser) auth.getPrincipal();
        // ③ 조회. 실패해도 화면은 열어야 하므로 기록만 남기고 비운다
        try {
            return employeeService.getLoginInfo(user.getCompanyId(), user.getEmployeeId());
        } catch (RuntimeException e) {
            logger.error("헤더 사원 정보 조회 실패: companyId={}", user.getCompanyId(), e);
            return null;
        }
    }
}