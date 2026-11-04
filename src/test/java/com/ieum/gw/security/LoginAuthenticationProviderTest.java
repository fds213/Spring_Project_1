package com.ieum.gw.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;

class LoginAuthenticationProviderTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String HASH = ENCODER.encode("password1");

    private static AuthEmpDTO account() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(42L);
        emp.setEmpnum("20260001");
        emp.setPw(HASH);
        emp.setRole("ROLE_ADMIN");
        emp.setStatus("ACTIVE");
        return emp;
    }

    private static org.springframework.security.core.Authentication login(AuthEmpDTO emp, String password) {
        LoginAuthenticationProvider provider = new LoginAuthenticationProvider();
        provider.setPasswordEncoder(ENCODER);
        provider.setUserDetailsService(username -> new LoginUser(emp));
        return provider.authenticate(new UsernamePasswordAuthenticationToken("20260001", password));
    }

    @Test
    @DisplayName("이메일 인증 전 업체: 비밀번호가 맞으면 인증 전으로 거절하고 업체 번호를 넘긴다")
    void pendingWithRightPassword() {
        AuthEmpDTO emp = account();
        emp.setVerificationPending(true);

        EmailNotVerifiedException e = assertThrows(EmailNotVerifiedException.class, () -> login(emp, "password1"));
        assertEquals(42L, e.getCompanyId());
    }

    @Test
    @DisplayName("이메일 인증 전 업체라도 비밀번호가 틀리면 같은 오류다(상태가 드러나지 않는다)")
    void pendingWithWrongPassword() {
        AuthEmpDTO emp = account();
        emp.setVerificationPending(true);

        assertThrows(BadCredentialsException.class, () -> login(emp, "wrong-password"));
    }

    @Test
    @DisplayName("인증을 요구하지 않거나 마친 계정은 로그인된다")
    void notPending() {
        assertTrue(login(account(), "password1").isAuthenticated());
    }

    @Test
    @DisplayName("잠금·비활성 검사는 그대로다")
    void otherChecksStay() {
        AuthEmpDTO locked = account();
        locked.setLocked(true);
        assertThrows(LockedException.class, () -> login(locked, "password1"));

        AuthEmpDTO retired = account();
        retired.setStatus("RETIRED");
        retired.setVerificationPending(true);
        assertThrows(DisabledException.class, () -> login(retired, "password1"));
    }
}
