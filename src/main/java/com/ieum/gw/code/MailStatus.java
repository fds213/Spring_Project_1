package com.ieum.gw.code;

/** 메일 발송 상태 (공통 코드 MAIL_STATUS, 기능명세서 1.7). DB에는 name() */
public enum MailStatus {
	MAIL_PENDING,
	MAIL_SENT,
	MAIL_FAILED
}
