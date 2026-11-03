package com.ieum.gw.subscription.controller;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.subscription.dto.CompanyBillingDTO;
import com.ieum.gw.subscription.service.SubscriptionService;

import lombok.RequiredArgsConstructor;

/**
 * 모든 화면 헤더의 이용 상태 띠와 읽기 전용 표시(layout.tag의 companyBilling)를 채운다.
 * 화면(HTML)을 여는 요청에만 조회한다. 결제·만료로 로그인 중에도 바뀌므로 세션에 담지 않는다.
 * 조회가 실패해도 화면은 그대로 연다. 쓰기를 실제로 막는 것은 이 값이 아니라 서버의 만료 읽기 전용 검사다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class SubscriptionHeaderAdvice {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionHeaderAdvice.class);

    private final SubscriptionService subscriptionService;

    @ModelAttribute("companyBilling")
    public CompanyBillingDTO companyBilling(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")) {
            return null;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        Long companyId = ((LoginUser) auth.getPrincipal()).getCompanyId();
        try {
            return subscriptionService.getCompanyBilling(companyId);
        } catch (RuntimeException e) {
            logger.error("헤더 이용 상태 조회 실패: companyId={}", companyId, e);
            return null;
        }
    }
}
