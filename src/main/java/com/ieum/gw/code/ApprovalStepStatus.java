package com.ieum.gw.code;

/** 결재선 단계 상태 (APPROVAL_LINE.STATUS_CODE). DB에는 name(), 화면에는 label */
public enum ApprovalStepStatus {
	WAITING("대기"),
	APPROVED("승인"),
	REJECTED("반려");

	private final String label;

	ApprovalStepStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static ApprovalStepStatus from(String code) {
		for (ApprovalStepStatus s : values()) {
			if (s.name().equals(code)) {
				return s;
			}
		}
		throw new IllegalArgumentException("알 수 없는 결재 단계 상태입니다: " + code);
	}
}