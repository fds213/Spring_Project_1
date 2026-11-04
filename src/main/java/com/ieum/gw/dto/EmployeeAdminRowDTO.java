package com.ieum.gw.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EmployeeAdminRowDTO {
	
	private Long employeeId;
	private String employeeName;
	private String employeeNo;
	private String departmentName;
	private String departmentType;
	private boolean head;
	private String gradeName;
	private String roleCode;
	private String statusCode;
	private LocalDateTime createdAt;
	private String employmentStatus;
	private String employmentType;
	private String genderCode;
}
