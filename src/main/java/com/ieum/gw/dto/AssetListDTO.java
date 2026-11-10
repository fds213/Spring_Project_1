package com.ieum.gw.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetListDTO {
	private Long id;
	private String assetNo;
	private String name;
	private String serial;
	private String kind;
	private String status;
	private Long branchId;
	private Long version;
	
	/** 현재 미회수 지급 건의 사용자 이름. 지급 건이 없으면 null. */
	private String employeeName;
	
	/** 현재 미회수 지급 건의 지급 시각. 지급 건이 없으면 null. */
	@JsonSerialize(using = AssetDateTimeSerializer.class)
	private LocalDateTime issuedAt;
}
