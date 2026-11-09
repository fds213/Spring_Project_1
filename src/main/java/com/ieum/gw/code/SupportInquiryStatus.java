package com.ieum.gw.code;

/** 이음 장애 문의 상태 (공통 코드 SUPPORT_STATUS, 기능명세서 1.7). DB에는 name(). 최소판은 접수와 지원 중(답변이 달림)만 쓴다 */
public enum SupportInquiryStatus {
	SUP_RECEIVED,
	SUP_PROCESSING
}
