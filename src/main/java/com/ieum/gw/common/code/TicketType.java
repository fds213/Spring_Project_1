package com.ieum.gw.common.code;

/** 티켓 종류 (공통 코드 TICKET_TYPE). DB에는 name(), 화면에는 label */
public enum TicketType {
	TK_INCIDENT("장애"),
	TK_EQUIPMENT("장비 지급"),
	TK_QUESTION("IT 문의");

	private final String label;

	TicketType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static TicketType from(String code) {
		for (TicketType t : values()) {
			if (t.name().equals(code)) {
				return t;
			}
		}
		throw new IllegalArgumentException("알 수 없는 티켓 종류입니다: " + code);
	}
}