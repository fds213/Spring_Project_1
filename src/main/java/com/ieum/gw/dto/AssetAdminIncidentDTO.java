package com.ieum.gw.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

/** 자산 상세에서 보여 줄 장애 티켓 요약 정보. */
@Getter
@Setter
public class AssetAdminIncidentDTO {
	private Long id;
	private String title;
	private String status;
	
	private String requesterName;
	private String assigneeName;
	
	@JsonSerialize(using = AssetDateTimeSerializer.class)
	private LocalDateTime createdAt;
}