package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;

/** 관리자 티켓 검색 결과의 상태 탭별 건수를 담는다. */
@Getter
@Setter
public class TicketAdminCountsDTO {
	private long unassignedCount;
	private long mineCount;
	private long inProgressCount;
	private long resolvedCount;
	private long allCount;
}
