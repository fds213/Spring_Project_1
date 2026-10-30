package com.ieum.gw.common.dto;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class LoginUser implements UserDetails {

	private final AuthEmpDTO emp; // 포장할 사원 정보

	public LoginUser(AuthEmpDTO emp) {
		this.emp = emp;
	}

	public String getEmpnum() {
		return emp.getEmpnum();
	}

	public String getName() {
		return emp.getName();
	}

	public String getCompcode() {
		return emp.getCompcode();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// TODO Auto-generated method stub
		return List.of(new SimpleGrantedAuthority(emp.getRole()));
	}

	@Override
	public String getPassword() {
		// TODO Auto-generated method stub
		return emp.getPw();
	}

	@Override
	public String getUsername() {
		// TODO Auto-generated method stub
		return emp.getEmpnum();
	}

	public Long getUserAccountId() {
		return emp.getUserAccountId();
	}

	public Long getCompanyId() {
		return emp.getCompanyId();
	}

	public Long getEmployeeId() {
		return emp.getEmployeeId();
	}

	@Override
	public boolean isEnabled() {
		// TODO Auto-generated method stub
		return "ACTIVE".equals(emp.getStatus());
	}

	@Override
	public boolean isAccountNonLocked() {
		return !emp.isLocked();
	}

	@Override
	public boolean isAccountNonExpired() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		// TODO Auto-generated method stub
		return true;
	}

}
