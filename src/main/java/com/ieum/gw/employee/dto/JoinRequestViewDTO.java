package com.ieum.gw.employee.dto;

import java.time.LocalDateTime;

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
	private LocalDateTime requestedAt;
	private Long departmentId;
	private Long jobGradeId;
}
