package com.ieum.gw.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 조직도의 부서 카드 하나. departmentId가 null이면 '부서 미지정' 묶음 */
@Getter
@Setter
@ToString
public class OrgDeptDTO {

	private Long departmentId;
	private String departmentName;
	private List<OrgMemberDTO> members = new ArrayList<>();
}