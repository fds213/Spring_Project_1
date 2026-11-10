package com.ieum.gw.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 자료실 글 한 건을 담는 상자. board.js의 목록·글 보기·수정 화면이 읽는 모양이다.
// 자료실 글은 POST 테이블(게시판 종류 BOARD_LIBRARY)의 글이고, 파일은 공지·부서 게시판과 같은 POST_FILE로 붙는다.
@Getter
@Setter
@ToString
public class BoardLibraryPostDTO {

    private Long id;            // 게시글 번호 (POST.POST_ID)
    private int no;             // 목록에 보이는 번호 (최신 글이 가장 큰 번호, Service가 붙임)
    private String title;       // 제목
    private String content;     // 내용 (에디터로 쓴 서식 글, 글 보기·수정 화면에서 씀)
    private String writerName;  // 올린 사람 이름 (EMPLOYEE.EMPLOYEE_NAME)
    private String writerDept;  // 올린 사람 부서 이름. 부서가 없는 사람(체험 신청으로 만든 관리자 등)은 null
    private String createdAt;   // 작성일시 (예: 2026-11-10T15:20:00)
    private String updatedAt;   // 수정일시. 수정한 적 없으면 null
    private int fileCount;      // 첨부 수 → 제목 옆 클립

    // ---------- 글 보기·수정 화면에서만 쓰는 칸 (Service가 채운다) ----------
    private boolean mine;                                    // 내가 올린 글인가 → 수정·삭제 버튼
    private List<NoticeFileDTO> files = new ArrayList<>();   // 첨부파일 목록

    @JsonIgnore                 // 화면에는 보내지 않는다 (본인 글인지 서버가 비교할 때만 쓴다)
    private Long authorId;      // 올린 사람 사원 ID (POST.AUTHOR_EMPLOYEE_ID)
}
