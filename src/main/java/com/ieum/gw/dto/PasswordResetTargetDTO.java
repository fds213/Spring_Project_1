package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 비밀번호 찾기 대상 계정 (업체 코드 + 사번으로 찾은 사용 중 계정). 이메일은 코드를 보낼 곳 */
@Getter
@Setter
@ToString
public class PasswordResetTargetDTO {

	private Long companyId;
	private String companyCode;
	private Long userAccountId;
	private String employeeNo;
	private String email;
}