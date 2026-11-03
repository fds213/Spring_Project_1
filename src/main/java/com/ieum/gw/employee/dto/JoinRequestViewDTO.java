package com.ieum.gw.employee.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class JoinRequestViewDTO {
	private Long accountRequestId;
	private String requestStatus;
	private String requestName;
	private String departmentName;
	private String gradeName;
	private String email;
	private Long companyId;
	private String companyCode;
	private String companyName;
	private String employeeNo;
	private Long userAccountId;
}
