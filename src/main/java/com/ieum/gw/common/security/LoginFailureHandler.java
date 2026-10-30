package com.ieum.gw.common.security;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.ieum.gw.common.service.LoginFailService;

@Component("loginFailureHandler")
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Autowired
    private LoginFailService loginFailService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        String compcode = request.getParameter("companyCode");   // login.jsp 업체 코드 칸 name
        String empnum   = request.getParameter("username");   // login.jsp 사번 칸 name

        String result;

        if (exception instanceof LockedException) {
            int minutes = loginFailService.getLockMinutes(compcode, empnum);
            result = "locked=" + minutes;
        } else if (exception instanceof DisabledException) {     // 비밀번호 맞음 + 비활성
            result = "disabled";
        } else {                                      // 비밀번호 틀림·계정 없음
            loginFailService.recordFail(compcode, empnum);
            result = "error";
        }

        response.sendRedirect(request.getContextPath() + "/login?" + result);
    }
}