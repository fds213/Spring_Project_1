package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class EmployeeOptionDTO {
	private Long employeeId;      
	private String employeeName;
	private String departmentName;
}