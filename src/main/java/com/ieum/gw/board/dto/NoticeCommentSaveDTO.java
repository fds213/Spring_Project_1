package com.ieum.gw.board.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 댓글 등록 요청 상자. board.js가 보내는 JSON 본문 { text }를 담는다.
// 쓴 사람·업체는 받지 않는다 (로그인 정보에서만 꺼낸다, AGENTS.md)
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoticeCommentSaveDTO {

    private String text; // 댓글 내용
}