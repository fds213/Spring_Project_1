package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 지점 관리 화면에서 입력한 지점명과 주소를 담는다. */
@Getter
@Setter
public class BranchSaveRequestDTO {
	private String branchName;
	private String address;
	private String detailAddress;
}
