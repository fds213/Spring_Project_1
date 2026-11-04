package com.ieum.gw.security;

import org.springframework.security.authentication.AccountStatusException;

/** 비밀번호는 맞지만 업체 이메일 인증을 아직 안 했다(ACC-06). LoginFailureHandler가 다시 보내기를 할 수 있게 업체를 세션에 적는다 */
public class EmailNotVerifiedException extends AccountStatusException {

    private final Long companyId;

    public EmailNotVerifiedException(Long companyId) {
        super("이메일 인증 전");
        this.companyId = companyId;
    }

    public Long getCompanyId() {
        return companyId;
    }
}
