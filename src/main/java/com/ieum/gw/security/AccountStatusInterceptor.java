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
 * 역할이 바뀐 경우(CFG-01 역할 바꾸기)도 옛 권한이 남지 않게 같은 방식으로 끊는다.
  */
@Component
@RequiredArgsConstructor
public class AccountStatusInterceptor implements HandlerInterceptor {

    static final String MSG_DISABLED = "사용이 중지된 계정입니다. 업체 관리자에게 문의해 주세요";
    static final String MSG_CHANGED = "역할이 바뀌어 다시 로그인해 주세요";
    
    private final EmployeeService employeeService;

    /** 로그인한 계정이 지금도 사용 중이고 역할이 그대로인지 매 요청 확인. 아니면 세션을 끊고 AJAX는 401, 화면은 로그인 화면 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // ① 로그인하지 않았으면 통과
        LoginUser user = currentUser();
        if (user == null) {
            return true;
        }
        // ② 지금도 사용 중이고 역할이 로그인 때와 같으면 통과 (사용 중이 아니면 role은 null)
        String role = employeeService.getActiveRole(user.getCompanyId(), user.getEmployeeId());
        if (role != null && role.equals(user.getRole())) {
            return true;
        }

        // ③ 세션을 끝낸다. 다음 요청부터는 로그인하지 않은 사람이 된다
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        // Ajax는 401 + 이유(common.js가 같은 안내의 로그인 화면으로 보냄). 화면은 바로 그 로그인 화면으로
        String reason = role == null ? "disabled" : "changed";
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            // 문구는 아래 두 가지뿐이라 JSON 이스케이프가 필요 없다
            String message = role == null ? MSG_DISABLED : MSG_CHANGED;
            response.getWriter().write("{\"success\":false,\"reason\":\"" + reason + "\",\"message\":\"" + message + "\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login?" + reason);
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