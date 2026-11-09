package com.ieum.gw.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 일정 추가·수정 요청 상자. calendar.js가 보내는 JSON 본문 { scope, date, title, memo }를 담는다 (수정은 scope를 쓰지 않는다).
// 쓴 사람·업체는 받지 않는다 (로그인 정보에서만 꺼낸다, AGENTS.md)
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleSaveDTO {

    private String scope;  // PERSONAL·DEPT·COMPANY (추가할 때만)
    private String date;   // 날짜 (예: 2026-11-02)
    private String title;  // 제목 (40자까지)
    private String memo;   // 메모 (200자까지, 선택)
}
