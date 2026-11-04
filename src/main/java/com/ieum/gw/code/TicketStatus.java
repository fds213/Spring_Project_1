package com.ieum.gw.code;

/** 티켓 상태 (공통 코드 WORKFLOW_STATUS 중 티켓용). DB에는 name(), 화면에는 label */
public enum TicketStatus {
	RECEIVED("접수"),
	ASSIGNED("배정"),
	// 공통 코드 표시명은 결재와 같이 쓰는 '진행 중'이지만, 티켓 화면·기능명세서는 '처리 중'이라 여기서 맞춘다 (#68 리뷰)
	IN_PROGRESS("처리 중"),
	RESOLVED("처리 완료"),
	CLOSED("종료"),
	CANCELED("취소"),
	REJECTED("반려");

	private final String label;

	TicketStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static TicketStatus from(String code) {
		for (TicketStatus s : values()) {
			if (s.name().equals(code)) {
				return s;
			}
		}
		throw new IllegalArgumentException("알 수 없는 티켓 상태입니다: " + code);
	}
}