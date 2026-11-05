package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class JoinRequestFormDTO {
	private String name;
	private String dept;
	private String rank;
	private String email;
	
	@ToString.Exclude
	private String password;
}
