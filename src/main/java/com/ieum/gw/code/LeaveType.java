package com.ieum.gw.code;

/** 연차 종류 (공통 코드 LEAVE_TYPE). 결재·근태가 같이 쓴다. DB에는 name(), 화면에는 label */
public enum LeaveType {
	LV_FULL("종일 연차"),
	LV_AM("오전 반차"),
	LV_PM("오후 반차");

	private final String label;

	LeaveType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static LeaveType from(String code) {
		for (LeaveType t : values()) {
			if (t.name().equals(code)) {
				return t;
			}
		}
		throw new IllegalArgumentException("알 수 없는 연차 종류입니다: " + code);
	}
}