package com.ieum.gw.security;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * 로그인은 했지만 권한이 없는 요청(Security 단계 403)에 대답한다. 로그인 안 됨(401)은 AjaxAwareEntryPoint.
 * AJAX → 403 JSON { success:false, message }, 화면 → /access-denied 안내 화면으로 이동.
 * CSRF 토큰이 없거나 만료된 것도 같은 403이라 문구를 나눈다.
 */
public class AjaxAwareAccessDeniedHandler implements AccessDeniedHandler {

    static final String MSG_DENIED = "접근 권한이 없습니다";
    static final String MSG_EXPIRED = "화면이 오래되었습니다. 새로 고친 뒤 다시 시도해 주세요";

    /** AJAX면 403 JSON, 화면이면 안내 화면으로 이동 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException e) throws IOException {
        boolean expired = e instanceof CsrfException;

        if (isAjax(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            // 문구는 위 상수 두 개뿐이라 JSON 이스케이프가 필요 없다
            response.getWriter().write("{\"success\":false,\"message\":\"" + (expired ? MSG_EXPIRED : MSG_DENIED) + "\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/access-denied" + (expired ? "?expired" : ""));
        }
    }

    /** jQuery AJAX의 이름표, 또는 JSON을 받겠다는 요청 */
    private boolean isAjax(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || (accept != null && accept.contains("application/json"));
    }
}