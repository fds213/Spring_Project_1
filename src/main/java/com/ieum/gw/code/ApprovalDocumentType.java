package com.ieum.gw.code;

/** 결재 문서 종류 (공통 코드 DOC_TYPE). DB에는 name(), 화면에는 label */
public enum ApprovalDocumentType {
	DOC_LEAVE("휴가 신청"),
	DOC_EQUIPMENT("장비 지급 신청");

	private final String label;

	ApprovalDocumentType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static ApprovalDocumentType from(String code) {
		for (ApprovalDocumentType t : values()) {
			if (t.name().equals(code)) {
				return t;
			}
		}
		throw new IllegalArgumentException("알 수 없는 결재 문서 종류입니다: " + code);
	}
}