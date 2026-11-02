package com.ieum.gw.board.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 공지 저장 요청 상자. board.js가 저장 버튼을 누르면 보내는 JSON 본문을 담는다:
// { scope, title, content, pinned, files }
// 업체·작성자 값은 여기에 받지 않는다 (로그인 정보에서만 꺼낸다, AGENTS.md)
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // 모르는 값이 와도 오류 내지 않고 무시
public class NoticeSaveDTO {

    private String scope;    // 공지 범위: 지금은 "ALL"만 (부서 공지 보류)
    private String title;    // 제목
    private String content;  // 내용 (에디터 서식 글)
    private boolean pinned;  // 맨 위 고정: DB에 칸이 없어서 받기만 하고 저장하지 않는다 (보류)
    private List<NoticeFileDTO> files = new ArrayList<>(); // 붙일 첨부파일 (먼저 올려 받은 번호 id만 쓴다)
}