package com.ieum.gw.employee.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString

public class JoinRequestDTO {
	private Long accountRequestId;
	private Long companyId;
	private Long userAccountId;
	private String requestName;
	private Long departmentId;
	private Long jobGradeId;
	private String requestKeyHash;
}
