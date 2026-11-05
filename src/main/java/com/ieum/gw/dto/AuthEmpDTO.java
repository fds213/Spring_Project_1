package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AuthEmpDTO implements java.io.Serializable  {

	private String compcode;
	private String empnum;

	@ToString.Exclude
	private String pw;

	private String name;
	private String role;
	private String status;
	private boolean locked;
	private boolean emailVerified;        // 업체 이메일 인증을 마쳤는가(COMPANY.EMAIL_VERIFIED_AT)
	private boolean verificationPending;  // 인증을 요구하는데 아직 안 했는가(CustomUserDetailsService가 설정과 함께 판단)
	
	private Long userAccountId;   // 계정 번호
	private Long companyId;       // 업체 번호
	private Long employeeId;      // 사원 번호


}
