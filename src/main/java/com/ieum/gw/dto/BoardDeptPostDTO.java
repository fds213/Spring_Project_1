package com.ieum.gw.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 부서 게시판 글 한 건을 담는 상자. board.js의 목록·글 보기·수정 화면이 읽는 모양이다.
// 부서 게시판 글은 POST 테이블(게시판 종류 BOARD_DEPT)의 글이다.
@Getter
@Setter
@ToString
public class BoardDeptPostDTO {

    private Long id;            // 게시글 번호 (POST.POST_ID)
    private int no;             // 목록에 보이는 번호 (최신 글이 가장 큰 번호, Service가 붙임)
    private String title;       // 제목
    private String content;     // 내용 (에디터로 쓴 서식 글, 글 보기·수정 화면에서 씀)
    private String writerName;  // 작성자 이름 (EMPLOYEE.EMPLOYEE_NAME)
    private String createdAt;   // 작성일시 (예: 2026-11-09T15:20:00)
    private String updatedAt;   // 수정일시. 수정한 적 없으면 null
    private int commentCount;   // 댓글 수 → 제목 옆 [3]
    private int fileCount;      // 첨부 수 → 제목 옆 클립

    // ---------- 글 보기·수정 화면에서만 쓰는 칸 (Service가 채운다) ----------
    private boolean mine;                                        // 내가 쓴 글인가 → 수정·삭제 버튼
    private List<NoticeFileDTO> files = new ArrayList<>();       // 첨부파일 목록
    private List<NoticeCommentDTO> comments = new ArrayList<>(); // 댓글 목록

    @JsonIgnore                 // 화면에는 보내지 않는다 (본인 글인지 서버가 비교할 때만 쓴다)
    private Long authorId;      // 쓴 사람 사원 ID (POST.AUTHOR_EMPLOYEE_ID)
}
