package com.ieum.gw.common.security;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

public class LoginAuthenticationProvider extends DaoAuthenticationProvider{
	public LoginAuthenticationProvider() {
		
		// 비밀번호 확인 "전" => 계정이 잠겼나?
		setPreAuthenticationChecks(user ->{
			if(!user.isAccountNonLocked()) {
				throw new LockedException("잠긴계정");
			}
		});
		
		// 비밀번호 확인 "후" => 비활성계정인가요 ?
		setPostAuthenticationChecks(user ->{
			if(!user.isEnabled()) {
				throw new DisabledException("비활성 계정");
			}
		});
	}
}
