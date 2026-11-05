package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 조직도의 사람 한 줄 (이름·직급·부서장 여부만. 사번·연락처는 넣지 않는다) */
@Getter
@Setter
@ToString
public class OrgMemberDTO {

	private Long departmentId;   // 어느 부서 카드에 넣을지 (부서 없으면 null)
	private Long employeeId;
	private String employeeName;
	private String gradeName;
	private boolean head;        // 그 부서의 부서장인가 (사원 관리 목록의 head와 같은 기준)
}