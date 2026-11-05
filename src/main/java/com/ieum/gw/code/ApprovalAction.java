package com.ieum.gw.code;

/** 결재 처리 이력 (공통 코드 APPROVAL_ACTION). DB에는 name(), 화면에는 label */
public enum ApprovalAction {
	APR_SUBMIT("제출"),
	APR_APPROVE("승인"),
	APR_REJECT("반려");

	private final String label;

	ApprovalAction(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static ApprovalAction from(String code) {
		for (ApprovalAction a : values()) {
			if (a.name().equals(code)) {
				return a;
			}
		}
		throw new IllegalArgumentException("알 수 없는 결재 처리입니다: " + code);
	}
}