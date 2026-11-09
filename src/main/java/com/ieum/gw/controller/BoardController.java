package com.ieum.gw.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.NoticeCommentDTO;
import com.ieum.gw.dto.NoticeDTO;
import com.ieum.gw.dto.NoticeFileDTO;
import com.ieum.gw.dto.NoticeCommentSaveDTO;
import com.ieum.gw.dto.NoticeSaveDTO;
import com.ieum.gw.service.BoardDeptService;
import com.ieum.gw.service.NoticeService;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.BoardDeptPostDTO;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

// 게시판(공지사항·부서 게시판) Controller.
// 화면 주소 → JSP 연결은 docs/front-screen-api.md 1장, 데이터 주소는 2장(/board/api/...)을 따른다.
@Controller
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final NoticeService noticeService;
    private final BoardDeptService boardDeptService;

    // ---------- 화면 ----------

    // 공지사항: 네 주소 모두 같은 JSP(WEB-INF/views/board/notice.jsp)를 연다
    @GetMapping({"/notice", "/notice/view/{id}", "/notice/write", "/notice/edit/{id}"})
    public String notice() {
        return "board/notice";
    }

    // 부서 게시판: 네 주소 모두 같은 JSP(WEB-INF/views/board/dept.jsp)를 연다
    @GetMapping({"/dept", "/dept/view/{id}", "/dept/write", "/dept/edit/{id}"})
    public String dept() {
        return "board/dept";
    }

    // ---------- 데이터 (board.js가 부른다) ----------

    // 공지 목록: GET /board/api/notices?q=검색어&page=1
    @GetMapping("/api/notices")
    @ResponseBody
    public Map<String, Object> noticeList(@AuthenticationPrincipal LoginUser loginUser,
                                          @RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "1") int page) {
        return noticeService.getNoticeList(loginUser, q, page);
    }

    // 공지 쓰기·수정 화면을 열 때: GET /board/api/notices/form (수정이면 ?id=3)
    // 쓸 권한이 없으면 403 → 화면이 안내하고 목록(수정이면 그 글)으로 돌아간다
    @GetMapping("/api/notices/form")
    @ResponseBody
    public Map<String, Object> noticeForm(@AuthenticationPrincipal LoginUser loginUser,
                                          @RequestParam(required = false) Long id) {
        return noticeService.getNoticeForm(loginUser, id);
    }

    // 공지 글 보기: GET /board/api/notices/3
    // 없거나 볼 수 없는 공지면 404 → 화면이 안내하고 목록으로 돌아간다
    @GetMapping("/api/notices/{id}")
    @ResponseBody
    public NoticeDTO noticeDetail(@AuthenticationPrincipal LoginUser loginUser,
                                  @PathVariable Long id) {
        return noticeService.getNotice(loginUser, id);
    }

    // 공지 저장: POST /board/api/notices  (본문 JSON: { scope, title, content, pinned, files })
    // 성공하면 { id: 새 글 번호 } → 화면이 그 글 보기로 이동
    @PostMapping("/api/notices")
    @ResponseBody
    public Map<String, Object> noticeCreate(@AuthenticationPrincipal LoginUser loginUser,
                                            @RequestBody NoticeSaveDTO form) {
        return noticeService.createNotice(loginUser, form.getTitle(), form.getContent(), form.getFiles());
    }

    // 공지 수정: POST /board/api/notices/3  (본문은 저장과 같은 모양)
    @PostMapping("/api/notices/{id}")
    @ResponseBody
    public Map<String, Object> noticeUpdate(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long id,
                                            @RequestBody NoticeSaveDTO form) {
        return noticeService.updateNotice(loginUser, id, form.getTitle(), form.getContent(), form.getFiles());
    }

    // 공지 삭제: POST /board/api/notices/3/delete  (지우기는 주소 끝에 /delete를 붙여 POST, front-screen-api.md 2장)
    @PostMapping("/api/notices/{id}/delete")
    @ResponseBody
    public Map<String, Object> noticeDelete(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long id) {
        return noticeService.deleteNotice(loginUser, id);
    }

    // 공지 댓글 등록: POST /board/api/notices/3/comments  (본문 JSON: { text })
    // 성공하면 그 공지의 댓글 목록 전체 → 화면이 다시 그린다
    @PostMapping("/api/notices/{id}/comments")
    @ResponseBody
    public List<NoticeCommentDTO> noticeCommentAdd(@AuthenticationPrincipal LoginUser loginUser,
                                                   @PathVariable Long id,
                                                   @RequestBody NoticeCommentSaveDTO form) {
        return noticeService.addComment(loginUser, id, form.getText());
    }

    // 공지 댓글 삭제: POST /board/api/notices/3/comments/7/delete
    // 성공하면 남은 댓글 목록 전체
    @PostMapping("/api/notices/{id}/comments/{commentId}/delete")
    @ResponseBody
    public List<NoticeCommentDTO> noticeCommentDelete(@AuthenticationPrincipal LoginUser loginUser,
                                                      @PathVariable Long id,
                                                      @PathVariable Long commentId) {
        return noticeService.deleteComment(loginUser, id, commentId);
    }

    // 첨부파일 올리기: POST /board/api/files  (multipart, 이름 "files"로 여러 개. 공지·부서 게시판이 같이 쓴다)
    // 성공하면 [{ id, name, size, type }] → 화면이 번호를 들고 있다가 글 저장 때 보낸다
    @PostMapping("/api/files")
    @ResponseBody
    public List<NoticeFileDTO> fileUpload(@AuthenticationPrincipal LoginUser loginUser,
                                          @RequestPart("files") List<MultipartFile> files) throws IOException {
        return boardDeptService.uploadFiles(loginUser, files);
    }

    // 공지 첨부 내려받기: GET /board/api/notices/3/files/12
    @GetMapping("/api/notices/{id}/files/{fileId}")
    public ResponseEntity<Resource> fileDownload(@AuthenticationPrincipal LoginUser loginUser,
                                                 @PathVariable Long id,
                                                 @PathVariable Long fileId) throws IOException {
        return fileResponse(noticeService.getFile(loginUser, id, fileId));
    }

    // ---------- 부서 게시판 데이터 (board.js가 부른다) ----------

    // 글 목록: GET /board/api/dept/posts?q=검색어&page=1
    @GetMapping("/api/dept/posts")
    @ResponseBody
    public Map<String, Object> deptPostList(@AuthenticationPrincipal LoginUser loginUser,
                                            @RequestParam(defaultValue = "") String q,
                                            @RequestParam(defaultValue = "1") int page) {
        return boardDeptService.getPosts(loginUser, q, page);
    }

    // 글쓰기·수정 화면을 열 때: GET /board/api/dept/posts/form (수정이면 ?id=3)
    @GetMapping("/api/dept/posts/form")
    @ResponseBody
    public Map<String, Object> deptPostForm(@AuthenticationPrincipal LoginUser loginUser,
                                            @RequestParam(required = false) Long id) {
        return boardDeptService.getForm(loginUser, id);
    }

    // 글 보기: GET /board/api/dept/posts/3  (다른 부서 글이면 404)
    @GetMapping("/api/dept/posts/{id}")
    @ResponseBody
    public BoardDeptPostDTO deptPostDetail(@AuthenticationPrincipal LoginUser loginUser,
                                           @PathVariable Long id) {
        return boardDeptService.getPost(loginUser, id);
    }

    // 글 저장: POST /board/api/dept/posts  (본문 JSON: { title, content, files }. 부서는 서버가 정한다)
    @PostMapping("/api/dept/posts")
    @ResponseBody
    public Map<String, Object> deptPostCreate(@AuthenticationPrincipal LoginUser loginUser,
                                              @RequestBody NoticeSaveDTO form) {
        return boardDeptService.createPost(loginUser, form.getTitle(), form.getContent(), form.getFiles());
    }

    // 글 수정: POST /board/api/dept/posts/3  (본문은 저장과 같은 모양)
    @PostMapping("/api/dept/posts/{id}")
    @ResponseBody
    public Map<String, Object> deptPostUpdate(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable Long id,
                                              @RequestBody NoticeSaveDTO form) {
        return boardDeptService.updatePost(loginUser, id, form.getTitle(), form.getContent(), form.getFiles());
    }

    // 글 삭제: POST /board/api/dept/posts/3/delete
    @PostMapping("/api/dept/posts/{id}/delete")
    @ResponseBody
    public Map<String, Object> deptPostDelete(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable Long id) {
        return boardDeptService.deletePost(loginUser, id);
    }

    // 댓글 등록: POST /board/api/dept/posts/3/comments  (본문 JSON: { text }). 성공하면 댓글 목록 전체
    @PostMapping("/api/dept/posts/{id}/comments")
    @ResponseBody
    public List<NoticeCommentDTO> deptCommentAdd(@AuthenticationPrincipal LoginUser loginUser,
                                                 @PathVariable Long id,
                                                 @RequestBody NoticeCommentSaveDTO form) {
        return boardDeptService.addComment(loginUser, id, form.getText());
    }

    // 댓글 삭제: POST /board/api/dept/posts/3/comments/7/delete. 성공하면 남은 댓글 목록 전체
    @PostMapping("/api/dept/posts/{id}/comments/{commentId}/delete")
    @ResponseBody
    public List<NoticeCommentDTO> deptCommentDelete(@AuthenticationPrincipal LoginUser loginUser,
                                                    @PathVariable Long id,
                                                    @PathVariable Long commentId) {
        return boardDeptService.deleteComment(loginUser, id, commentId);
    }

    // 첨부 내려받기: GET /board/api/dept/posts/3/files/12
    @GetMapping("/api/dept/posts/{id}/files/{fileId}")
    public ResponseEntity<Resource> deptFileDownload(@AuthenticationPrincipal LoginUser loginUser,
                                                     @PathVariable Long id,
                                                     @PathVariable Long fileId) throws IOException {
        return fileResponse(boardDeptService.getFile(loginUser, id, fileId));
    }

    // 파일 응답을 만든다. 이미지는 화면에 바로 보이게(inline), 나머지는 내려받게(attachment)
    private ResponseEntity<Resource> fileResponse(AttachmentDTO file) throws IOException {
        Resource body = noticeService.loadFile(file);

        // 한글 파일 이름이 깨지지 않게 UTF-8로 적는다
        String encoded = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (file.getMimeType().startsWith("image/") ? "inline" : "attachment")
                + "; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(body);
    }

    // ---------- 오류 응답 ----------

    // Service가 던진 403·400·404를 화면이 읽는 모양 { "message": "안내 문구" }으로 바꾼다
    // (docs/front-screen-api.md 2장의 실패 규칙. 이 Controller 안에서만 동작한다)
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        String message = (e.getReason() != null) ? e.getReason() : "요청을 처리하지 못했습니다";
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", message));
    }

    // 공통 파일 기능이 던지는 안내(형식이 틀림·빈 파일·없는 파일 등) → 400 { message }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleBadFile(RuntimeException e) {
        String message = (e.getMessage() != null) ? e.getMessage() : "파일을 처리하지 못했습니다";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    // 파일을 디스크에 쓰거나 읽지 못함(저장 공간 부족 등) → 500 { message }
    @ExceptionHandler(IOException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleFileIo(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "파일을 저장하거나 읽지 못했습니다. 잠시 뒤 다시 해 주세요"));
    }
}