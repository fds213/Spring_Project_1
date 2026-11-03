package com.ieum.gw.common.code;

/** 자산 상태 (공통 코드 ASSET_STATUS). DB에는 name(), 화면에는 label */
public enum AssetStatus {
	AS_AVAILABLE("지급 가능"),
	AS_IN_USE("사용 중"),
	AS_INACTIVE("사용 중지"),
	AS_DISPOSED("폐기");

	private final String label;

	AssetStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static AssetStatus from(String code) {
		for (AssetStatus s : values()) {
			if (s.name().equals(code)) {
				return s;
			}
		}
		throw new IllegalArgumentException("알 수 없는 자산 상태입니다: " + code);
	}
}