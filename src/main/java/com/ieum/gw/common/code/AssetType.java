package com.ieum.gw.common.code;

/** 장비 종류 (공통 코드 ASSET_TYPE). DB에는 name(), 화면에는 label */
public enum AssetType {
	AT_LAPTOP("노트북"),
	AT_DESKTOP("데스크톱"),
	AT_MONITOR("모니터"),
	AT_OTHER("기타 장비"),
	AT_PRINTER("프린터"),
	AT_NETWORK("네트워크 장비"),
	AT_TABLET("태블릿"),
	AT_PHONE("업무용 전화");

	private final String label;

	AssetType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** DB 코드 → enum. 모르는 코드면 IllegalArgumentException */
	public static AssetType from(String code) {
		for (AssetType t : values()) {
			if (t.name().equals(code)) {
				return t;
			}
		}
		throw new IllegalArgumentException("알 수 없는 장비 종류입니다: " + code);
	}
}