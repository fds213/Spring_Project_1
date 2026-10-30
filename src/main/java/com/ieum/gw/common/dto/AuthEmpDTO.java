package com.ieum.gw.common.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AuthEmpDTO {

	private String compcode;
	private String empnum;

	@ToString.Exclude
	private String pw;

	private String name;
	private String role;
	private String status;
	private boolean locked;
	
	private Long userAccountId;   // 계정 번호
	private Long companyId;       // 업체 번호
	private Long employeeId;      // 사원 번호


}
