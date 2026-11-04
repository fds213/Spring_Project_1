package com.ieum.gw.security;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.ieum.gw.service.LoginFailService;
import com.ieum.gw.service.TrialVerifyService;

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
        } else if (exception instanceof EmailNotVerifiedException) { // 비밀번호 맞음 + 이메일 인증 전(ACC-06)
            // 다시 보내기는 주소에 업체를 적지 않고, 비밀번호를 맞힌 이 세션의 업체에만 보낸다
            request.getSession().setAttribute(TrialVerifyService.SESSION_COMPANY_ID,
                    ((EmailNotVerifiedException) exception).getCompanyId());
            result = "unverified";
        } else {                                      // 비밀번호 틀림·계정 없음
            loginFailService.recordFail(compcode, empnum);
            result = "error";
        }

        response.sendRedirect(request.getContextPath() + "/login?" + result);
    }
}