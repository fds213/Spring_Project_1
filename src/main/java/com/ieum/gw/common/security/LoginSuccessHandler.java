package com.ieum.gw.common.security;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.common.service.LoginFailService;

@Component("loginSuccessHandler")
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private LoginFailService loginFailService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {

        // 1. 로그인한 사람 꺼내기
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        // 2. 연속 실패 횟수·잠금 초기화
        loginFailService.resetFail(loginUser.getCompcode(), loginUser.getEmpnum());

        // 3. 운영자면 운영자 화면, 아니면 홈
        boolean isOps = authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_OPS"));

        String target = isOps ? "/ops/company" : "/home";
        response.sendRedirect(request.getContextPath() + target);
    }
}