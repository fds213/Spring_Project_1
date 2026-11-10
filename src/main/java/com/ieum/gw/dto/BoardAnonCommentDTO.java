package com.ieum.gw.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 익명 게시판 댓글 한 개를 담는 상자 (POST_COMMENT 테이블). board.js renderCommentList가 읽는 모양.
// 댓글도 익명이라 쓴 사람의 이름·부서 칸을 두지 않는다 (공지·부서 게시판의 NoticeCommentDTO와 다른 점)
@Getter
@Setter
@ToString
public class BoardAnonCommentDTO {

    private Long id;            // 댓글 번호 (POST_COMMENT.POST_COMMENT_ID)
    private String text;        // 댓글 내용 (서식 없는 글자. 화면이 글자 그대로 보여 준다)
    private String createdAt;   // 쓴 시각 (예: 2026-11-10T10:20:00)
    private boolean canDelete;  // 삭제 버튼을 보일지 (본인 댓글만) — Service가 채운다

    @JsonIgnore                 // 화면에는 보내지 않는다 (본인 댓글인지 서버가 비교할 때만 쓴다)
    private Long authorId;      // 쓴 사람 사원 ID (POST_COMMENT.AUTHOR_EMPLOYEE_ID)
}
