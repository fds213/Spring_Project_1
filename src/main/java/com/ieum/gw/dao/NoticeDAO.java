package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.NoticeCommentDTO;
import com.ieum.gw.dto.NoticeDTO;
import com.ieum.gw.dto.NoticeFileDTO;
import com.ieum.gw.mapper.NoticeMapper;

import lombok.RequiredArgsConstructor;

// 공지사항 DAO: NoticeMapper를 감싸서 Service에 건네준다.
// Service는 Mapper를 직접 부르지 않고 이 DAO만 부른다 (AGENTS.md)
@Repository
@RequiredArgsConstructor
public class NoticeDAO {

    private final NoticeMapper mapper;

    // 공지 목록 (한 페이지)
    public List<NoticeDTO> selectNoticeList(Long companyId, List<String> words, int offset, int size) {
        return mapper.selectNoticeList(companyId, words, offset, size);
    }

    // 공지 전체 개수
    public int countNotices(Long companyId, List<String> words) {
        return mapper.countNotices(companyId, words);
    }
    // ---------- 첨부파일 ----------

    // 공지 한 개의 첨부파일 목록
    public List<NoticeFileDTO> selectFiles(Long companyId, Long postId) {
        return mapper.selectFiles(companyId, postId);
    }

    // 공지에 붙은 파일 번호들
    public List<Long> selectFileIds(Long companyId, Long postId) {
        return mapper.selectFileIds(companyId, postId);
    }

    // 공지에 파일 붙이기
    public int insertPostFile(Long companyId, Long postId, Long attachmentId) {
        return mapper.insertPostFile(companyId, postId, attachmentId);
    }

    // 공지에서 파일 떼기
    public int deletePostFile(Long companyId, Long postId, Long attachmentId) {
        return mapper.deletePostFile(companyId, postId, attachmentId);
    }
    // ---------- 글 보기 ----------

    // 공지 한 건 (없으면 null)
    public NoticeDTO selectNotice(Long companyId, Long postId) {
        return mapper.selectNotice(companyId, postId);
    }

    // ---------- 수정 · 삭제 ----------

    // 공지 고치기 (성공하면 1)
    public int updateNotice(Long companyId, Long postId, String title, String content) {
        return mapper.updateNotice(companyId, postId, title, content);
    }

    // 공지 지우기 (성공하면 1)
    public int deleteNotice(Long companyId, Long postId) {
        return mapper.deleteNotice(companyId, postId);
    }

    // ---------- 댓글 ----------

    // 공지 한 개의 댓글 목록
    public List<NoticeCommentDTO> selectComments(Long companyId, Long postId) {
        return mapper.selectComments(companyId, postId);
    }

    // 댓글 쓴 사람의 사원 ID (없으면 null)
    public Long selectCommentAuthor(Long companyId, Long postId, Long commentId) {
        return mapper.selectCommentAuthor(companyId, postId, commentId);
    }

    // 댓글 저장
    public int insertComment(Long companyId, Long postId, Long authorId, String content) {
        return mapper.insertComment(companyId, postId, authorId, content);
    }

    // 댓글 하나 지우기
    public int deleteComment(Long companyId, Long postId, Long commentId) {
        return mapper.deleteComment(companyId, postId, commentId);
    }

    // 공지의 댓글 모두 지우기
    public int deleteCommentsOfPost(Long companyId, Long postId) {
        return mapper.deleteCommentsOfPost(companyId, postId);
    }

    // ---------- 글쓰기 ----------

    // 우리 업체의 공지 게시판 번호 (없으면 null)
    public Long selectNoticeBoardId(Long companyId) {
        return mapper.selectNoticeBoardId(companyId);
    }

    // 우리 업체의 공지 게시판 줄 만들기
    public int insertNoticeBoard(Long companyId) {
        return mapper.insertNoticeBoard(companyId);
    }

    // 새 게시글 번호 하나 뽑기
    public Long selectNextPostId() {
        return mapper.selectNextPostId();
    }

    // 공지 저장 (성공하면 1)
    public int insertNotice(Long postId, Long companyId, Long boardId, Long authorId, String title, String content) {
        return mapper.insertNotice(postId, companyId, boardId, authorId, title, content);
    }
}