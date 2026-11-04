package com.ieum.gw.security;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;

import lombok.RequiredArgsConstructor;

/**
 * 계정 상태 검사 (#127 리뷰). 로그인한 사람의 계정이 지금도 사용 중(재직 + ACTIVE)인지 요청마다 본다.
 * 로그인 때 만든 LoginUser는 세션에 남으므로, 비활성화·퇴사 뒤에도 기존 세션으로 쓰지 못하게 여기서 끊는다.
 */
@Component
@RequiredArgsConstructor
public class AccountStatusInterceptor implements HandlerInterceptor {

    private final EmployeeService employeeService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // ①·② 로그인하지 않았거나 지금도 사용 중이면 통과
        LoginUser user = currentUser();
        if (user == null || employeeService.isActiveEmployee(user.getCompanyId(), user.getEmployeeId())) {
            return true;
        }

        // ③ 세션을 끝낸다. 다음 요청부터는 로그인하지 않은 사람이 된다
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        // Ajax는 401(AjaxAwareEntryPoint와 같은 판별), 화면은 사용 중지 안내가 있는 로그인 화면으로
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendRedirect(request.getContextPath() + "/login?disabled");
        }
        return false;
    }

    /** 로그인 정보는 서버의 인증 정보에서만 꺼낸다 */
    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }
}