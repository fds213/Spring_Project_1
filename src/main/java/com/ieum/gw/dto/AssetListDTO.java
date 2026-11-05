package com.ieum.gw.dto;

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
}
