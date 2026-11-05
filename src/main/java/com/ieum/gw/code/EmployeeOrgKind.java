package com.ieum.gw.code;

import lombok.Getter;

/**
 * 부서·직급 관리의 종류. SQL의 테이블·컬럼 이름(${})은 사용자 입력이 아니라 이 enum의 고정값만 쓴다(AGENTS.md MyBatis).
 */
@Getter
public enum EmployeeOrgKind {
	DEPT("department", "department_id", "department_name", "부서", 30),
	RANK("job_grade", "job_grade_id", "grade_name", "직급", 15);

	private final String table;
	private final String idCol;
	private final String nameCol;
	private final String word;
	private final int max;

	EmployeeOrgKind(String table, String idCol, String nameCol, String word, int max) {
		this.table = table;
		this.idCol = idCol;
		this.nameCol = nameCol;
		this.word = word;
		this.max = max;
	}

	/** 부서인가 (부서 형태·부서장·가입 열기는 부서에만 있다) */
	public boolean isDept() {
		return this == DEPT;
	}

	/** 주소의 depts·ranks → 종류. 그 밖의 값이면 null */
	public static EmployeeOrgKind fromPath(String path) {
		if ("depts".equals(path)) {
			return DEPT;
		}
		if ("ranks".equals(path)) {
			return RANK;
		}
		return null;
	}
}