package com.ieum.gw.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 공지 첨부파일 하나를 담는 상자 (POST_FILE + ATTACHMENT).
// 화면에 보낼 때는 이름·크기·종류·내려받기 주소를, 저장 요청에서 받을 때는 id만 쓴다
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoticeFileDTO {

    private Long id;       // 파일 번호 (ATTACHMENT.ATTACHMENT_ID)
    private String name;   // 원래 파일 이름 (예: 보고서.pdf)
    private Long size;     // 바이트 크기
    private String type;   // MIME 종류 (예: image/png) → 화면이 이미지면 미리보기를 보인다
    private String url;    // 내려받기 주소 (컨텍스트 경로 뒤: /board/api/notices/3/files/12)
}