package com.ieum.gw.code;

/** 결재 문서 상태 (APPROVAL_DOCUMENT.STATUS_CODE). DB에는 name(), 화면에는 label */
public enum ApprovalDocumentStatus {
	DRAFT("임시저장"),
	IN_PROGRESS("진행"),
	APPROVED("완료"),
	REJECTED("반려");

	private final String label;

	ApprovalDocumentStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static ApprovalDocumentStatus from(String code) {
		for (ApprovalDocumentStatus s : values()) {
			if (s.name().equals(code)) {
				return s;
			}
		}
		throw new IllegalArgumentException("알 수 없는 결재 문서 상태입니다: " + code);
	}
}