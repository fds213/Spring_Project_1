package com.ieum.gw.auth.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AuthEmpDto {

	private String empnum;
	
	@ToString.Exclude
	private String pw;
	
	private String name;
	private String role;
	private String status;
	

}
