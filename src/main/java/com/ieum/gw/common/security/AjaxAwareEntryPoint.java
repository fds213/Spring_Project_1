package com.ieum.gw.common.security;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

// 로그인 안 된 요청이 왔을 때 대답하는 클래스
// AJAX(이름표 있음) → 401, 화면 요청 → 원래대로 로그인 화면(302)
public class AjaxAwareEntryPoint extends LoginUrlAuthenticationEntryPoint {

    public AjaxAwareEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        // jQuery AJAX가 자동으로 붙이는 이름표
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));

        if (isAjax) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);   // 401
        } else {
            super.commence(request, response, authException);          // 302 → 로그인 화면
        }
    }
}