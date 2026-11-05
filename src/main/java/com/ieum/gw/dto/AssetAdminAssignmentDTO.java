package com.ieum.gw.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetAdminAssignmentDTO {
	private Long assetAssignmentId;
	private Long assetId;
	
	private Long employeeId;
	private String employeeName;
	
	private Long itTicketId;
	
	private Long issuedBy;
	private String issuedByName;
	
	@JsonSerialize(using = AssetDateTimeSerializer.class)
	private LocalDateTime issuedAt;
	
	private Long returnedBy;
	private String returnedByName;
	
	@JsonSerialize(using = AssetDateTimeSerializer.class)
	private LocalDateTime returnedAt;
}
