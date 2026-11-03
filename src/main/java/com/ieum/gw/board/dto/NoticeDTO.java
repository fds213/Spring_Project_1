package com.ieum.gw.board.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 공지 한 건을 담는 상자. 목록과 글 보기 화면에 보낼 값을 담는다.
// 공지는 POST 테이블(게시판 종류 BOARD_NOTICE)의 글이다.
@Getter
@Setter
@ToString
public class NoticeDTO {

    private Long id;            // 게시글 번호 (POST.POST_ID)
    private int no;             // 목록에 보이는 번호 (최신 글이 가장 큰 번호, Service가 붙임)
    private String scope;       // 공지 범위: 지금은 전사 공지만 있어서 항상 "ALL"
    private String title;       // 제목
    private String content;     // 내용 (에디터로 쓴 서식 글, 글 보기 화면에서 씀)
    private String writerName;  // 작성자 이름 (EMPLOYEE.EMPLOYEE_NAME)
    private String writerDept;  // 작성자 부서 이름 (DEPARTMENT.DEPARTMENT_NAME)
    private String createdAt;   // 작성일시 (예: 2026-10-30T15:20:00)
    private String updatedAt;   // 수정일시. 수정한 적 없으면 비어 있음(null) → 화면에 "수정됨" 안 보임
    private int commentCount;   // 댓글 수 → 제목 옆 [3]
    private int fileCount;      // 첨부 수 → 제목 옆 "첨부"
    private boolean pinned;     // 맨 위 고정 여부 (DB에 칸이 생기기 전까지 항상 false)

    // ---------- 글 보기 화면에서만 쓰는 칸 (Service가 채운다) ----------
    private boolean canEdit;                        // 수정·삭제 버튼을 보일지 (업체 관리자·인사 담당)
    private List<NoticeFileDTO> files = new ArrayList<>();       // 첨부파일 목록 (글 보기에서 Service가 채운다)
    private List<NoticeCommentDTO> comments = new ArrayList<>(); // 댓글 목록 (글 보기에서 Service가 채운다)
}