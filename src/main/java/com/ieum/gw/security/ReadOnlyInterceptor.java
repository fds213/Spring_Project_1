package com.ieum.gw.security;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.SubscriptionService;

import lombok.RequiredArgsConstructor;

/**
 * 만료 읽기 전용 검사 (DEM-03·SUB-04, 요구분석서 3.11). 만료 업체의 GET이 아닌 요청을 409로 거절한다.
 * 로그아웃은 Security 필터에서 끝나서 여기까지 오지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ReadOnlyInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String MESSAGE = "이용 기간이 끝나 읽기 전용입니다. 자료 조회와 내려받기는 그대로 됩니다.";

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // ① 읽기 요청은 통과
        if (READ_METHODS.contains(request.getMethod())) {
            return true;
        }
        // ② 로그인 안 한 요청은 통과 (가입 신청·비밀번호 찾기는 각 Service가 업체 상태를 본다)
        LoginUser user = currentUser();
        if (user == null) {
            return true;
        }
        // ③ 만료 중에도 허용하는 쓰기
        if (allowed(request, user)) {
            return true;
        }
        // ④ 만료가 아니면 통과 (이용 기간이 없는 운영 업체는 null)
        CompanyUsageDTO usage = subscriptionService.getCompanyUsage(user.getCompanyId());
        if (usage == null || !usage.isExpired()) {
            return true;
        }
        // ⑤ 거절: Controller로 보내지 않고 여기서 응답
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("success", false, "message", MESSAGE));
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

    /** 요구분석서 3.11 '만료 중 허용하는 쓰기': 본인 비밀번호 변경, 업체 관리자 결제 */
    private boolean allowed(HttpServletRequest request, LoginUser user) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/employee/api/password")) {
            return true;
        }
        return path.startsWith("/subscription/api/orders") && "ROLE_ADMIN".equals(user.getRole());
    }
}