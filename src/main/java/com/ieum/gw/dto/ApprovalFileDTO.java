package com.ieum.gw.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ApprovalFileDTO {	//첨부 하나를 담는 상자

	private Long id; // 파일 번호 (ATTACHMENT.ATTACHMENT_ID)
	private String name; // 원래 파일 이름 (예: 보고서.pdf)
	private Long size; // 바이트 크기
	private String type; // MIME 종류 (예: image/png) → 화면이 이미지면 미리보기를 보인다
	private String url;  // 내려받기 주소 (컨텍스트 경로 뒤: /approval/api/documents/3/files/12)
	private Long approvalDocumentId; // 어느 문서의 파일인지 (결재함에서 문서별로 나눌 때 씀)
}
