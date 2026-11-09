package com.ieum.gw.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dao.NoticeDAO;
import com.ieum.gw.dto.NoticeCommentDTO;
import com.ieum.gw.dto.NoticeDTO;
import com.ieum.gw.dto.NoticeFileDTO;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

// 공지사항 규칙을 맡는 Service.
// 업체는 로그인한 사람 정보(LoginUser)에서만 꺼낸다. 화면이 보낸 값으로 업체를 정하지 않는다 (AGENTS.md)
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final int PAGE_SIZE = 10; // 한 페이지에 10개
    private static final int RECENT_MAX = 10; // 메인 화면 최신 공지는 많아도 10개까지
    private static final int TITLE_MAX = 100;    // 제목 글자 수 (board.js와 같게)
    private static final int CONTENT_MAX = 2000; // 내용 글자 수: 서식을 뺀 글자 기준 (board.js와 같게)
    private static final int CONTENT_HTML_MAX = 100000; // 서식까지 포함한 길이 상한 (터무니없이 큰 요청 막기)
    // 없는 공지와 다른 업체·지운 공지는 같은 말로 답한다 (그런 글이 있는지조차 드러내지 않게)
    private static final String NOT_FOUND = "없는 공지이거나 볼 수 없는 공지입니다";
    private static final int COMMENT_MAX = 500;  // 댓글 글자 수 (board.js와 같게)
    private static final int FILE_MAX = 5;       // 공지 하나에 첨부 5개까지 (board.js와 같게)
    private static final String FILE_MODULE = "board"; // 파일 저장 폴더 이름 ({업체}/board/...)

    private final NoticeDAO dao;
    private final AttachmentService attachmentService; // 공통 파일 저장 (담당 1)

    // 공지 목록 화면에 보낼 값을 만든다 (board.js renderList가 읽는 모양)
    public Map<String, Object> getNoticeList(LoginUser user, String q, int page) {
        Long companyId = user.getCompanyId();
        List<String> words = splitWords(q);

        // 1. 전체 개수와 페이지 수
        int total = dao.countNotices(companyId, words);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages); // 1 ~ 마지막 페이지 사이로 맞춘다
        int offset = (page - 1) * PAGE_SIZE;

        // 2. 이번 페이지 공지 + 목록 번호 (최신 글이 가장 큰 번호)
        List<NoticeDTO> items = dao.selectNoticeList(companyId, words, offset, PAGE_SIZE);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setNo(total - offset - i);
        }

        // 3. 이 사람이 할 수 있는 일 (서버가 저장할 때도 다시 검사한다)
        boolean manager = isManager(user);
        Map<String, Object> perms = new HashMap<>();
        perms.put("canWrite", manager);   // 글쓰기 버튼: 업체 관리자·인사 담당
        perms.put("allDepts", manager);   // 모든 부서 공지를 보는 사람

        // 4. 화면에 보낼 모양
        Map<String, Object> result = new HashMap<>();
        result.put("page", page);
        result.put("pages", pages);
        result.put("total", total);
        result.put("items", items);
        result.put("pinned", new ArrayList<NoticeDTO>()); // 맨 위 고정: DB에 칸이 생기기 전까지 비어 있음
        result.put("myDept", "부서");                       // 부서 공지가 생기면 실제 부서 이름으로
        result.put("perms", perms);
        return result;
    }

    /**
     * 메인 화면 '공지사항' 칸에 보일 최신 공지 (홈 담당이 부른다).
     * 공지 목록 화면과 같은 조건(우리 업체·지우지 않은 공지)으로 최신 글부터 limit개.
     * 쓰는 값: id(글 번호 → /board/notice/view/{id}), title, createdAt, writerName, writerDept, commentCount
     */
    public List<NoticeDTO> getRecentNotices(Long companyId, int limit) {
        if (companyId == null) {
            throw new IllegalArgumentException("업체 ID가 비어 있습니다");
        }
        int size = Math.min(Math.max(1, limit), RECENT_MAX); // 1 ~ 10개 사이로 맞춘다
        return dao.selectNoticeList(companyId, new ArrayList<>(), 0, size);
    }

    // ---------- 글 보기 ----------

    // 공지 한 건 (board.js renderDetail이 읽는 모양). 우리 업체 직원이면 누구나 본다
    public NoticeDTO getNotice(LoginUser user, Long id) {
        NoticeDTO notice = findNotice(user, id);   // 없으면 여기서 404로 멈춘다
        notice.setCanEdit(isManager(user));        // 수정·삭제 버튼: 업체 관리자·인사 담당
        notice.setComments(listComments(user, id)); // 댓글 목록
        notice.setFiles(listFiles(user, id));       // 첨부파일 목록
        return notice;
    }

    // ---------- 글쓰기 · 수정 ----------

    // 공지 쓰기·수정 화면을 열 때 보낼 값 (board.js fillForm이 읽는 모양)
    // id가 없으면 새 글, 있으면 그 공지를 채워서 보낸다
    public Map<String, Object> getNoticeForm(LoginUser user, Long id) {
        checkManager(user); // 관리자·인사 담당이 아니면 여기서 403으로 멈춘다

        Map<String, Object> result = new HashMap<>();
        NoticeDTO notice = null;
        if (id != null) {                              // 수정이면 기존 글 + 기존 첨부
            notice = findNotice(user, id);
            notice.setFiles(listFiles(user, id));
        }
        result.put("notice", notice);
        result.put("scopes", List.of("ALL"));  // 고를 수 있는 범위: 부서 공지가 보류라 전사 공지만
        result.put("scopeLocked", false);      // 범위 선택 상자를 잠글지 (부서장용, 지금은 안 씀)
        result.put("myDept", "부서");           // 부서 공지가 생기면 실제 부서 이름으로
        return result;
    }

    // 공지 저장. 성공하면 새 글 번호를 돌려준다 → 화면이 그 글 보기로 이동
    @Transactional
    public Map<String, Object> createNotice(LoginUser user, String title, String content, List<NoticeFileDTO> files) {
        checkManager(user);

        // 1. 값 검사 (화면도 검사하지만, 주소로 직접 보내는 요청도 있으니 서버가 다시 본다)
        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        validate(title, content);

        // 2. 우리 업체의 공지 게시판 번호
        Long companyId = user.getCompanyId();
        Long boardId = dao.selectNoticeBoardId(companyId);
        if (boardId == null) { // 새 업체는 첫 글을 쓸 때 공지 게시판 줄을 만든다 (같은 트랜잭션)
            dao.insertNoticeBoard(companyId);
            boardId = dao.selectNoticeBoardId(companyId);
        }

        // 3. 번호를 먼저 뽑고 저장 (업체·작성자는 로그인 정보에서만 꺼낸다)
        Long postId = dao.selectNextPostId();
        dao.insertNotice(postId, companyId, boardId, user.getEmployeeId(), title, content);

        // 4. 첨부파일 붙이기 (먼저 올려 둔 파일 번호들)
        syncFiles(user, postId, files);

        Map<String, Object> result = new HashMap<>();
        result.put("id", postId);
        return result;
    }

    // 공지 수정. 성공하면 글 번호를 돌려준다 → 화면이 그 글 보기로 이동
    @Transactional
    public Map<String, Object> updateNotice(LoginUser user, Long id, String title, String content, List<NoticeFileDTO> files) {
        checkManager(user);

        title = (title == null) ? "" : title.trim();
        content = (content == null) ? "" : content;
        validate(title, content);

        // 우리 업체의 지우지 않은 공지만 고쳐진다. 고친 줄이 0이면 없는 글
        int changed = dao.updateNotice(user.getCompanyId(), id, title, content);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        // 첨부: 남긴 파일은 그대로, 뺀 파일은 떼고, 새로 올린 파일은 붙인다
        syncFiles(user, id, files);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    // ---------- 삭제 ----------

    // 공지 삭제 (진짜로 지우지 않고 삭제 표시)
    @Transactional
    public Map<String, Object> deleteNotice(LoginUser user, Long id) {
        checkManager(user);

        int changed = dao.deleteNotice(user.getCompanyId(), id);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        dao.deleteCommentsOfPost(user.getCompanyId(), id); // 그 공지의 댓글도 같이 삭제 표시 (같은 트랜잭션)

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    // ---------- 댓글 ----------

    // 댓글 등록: 그 공지를 볼 수 있는 사람(우리 업체 직원)이면 누구나.
    // 등록 뒤 그 공지의 댓글 목록 전체를 돌려준다 (board.js가 목록을 다시 그린다)
    @Transactional
    public List<NoticeCommentDTO> addComment(LoginUser user, Long postId, String text) {
        // 공지 줄을 잠근 뒤 저장한다. 공지 삭제와 겹치면 삭제가 먼저일 때는 여기서 404,
        // 댓글이 먼저일 때는 삭제가 이 댓글까지 삭제 표시한다 (부서 게시판과 같은 방식)
        if (postId == null || dao.lockNotice(user.getCompanyId(), postId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        dao.insertComment(user.getCompanyId(), postId, user.getEmployeeId(), checkComment(text));
        return listComments(user, postId);
    }

    /** 댓글 글자 검사 (공지·부서 게시판 같이 씀). 앞뒤 공백을 뺀 글자를 돌려주고, 틀리면 400과 안내 문구 */
    public String checkComment(String text) {
        text = (text == null) ? "" : text.trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글을 입력해 주세요");
        }
        if (text.length() > COMMENT_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글은 " + COMMENT_MAX + "자까지 쓸 수 있습니다");
        }
        return text;
    }

    // 댓글 삭제: 본인 댓글, 또는 업체 관리자·인사 담당은 모든 댓글.
    // 삭제 뒤 남은 댓글 목록 전체를 돌려준다
    @Transactional
    public List<NoticeCommentDTO> deleteComment(LoginUser user, Long postId, Long commentId) {
        findNotice(user, postId); // 없거나 볼 수 없는 공지면 404

        Long authorId = dao.selectCommentAuthor(user.getCompanyId(), postId, commentId);
        if (authorId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 댓글이거나 이미 삭제된 댓글입니다");
        }
        if (!isManager(user) && !authorId.equals(user.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다");
        }

        dao.deleteComment(user.getCompanyId(), postId, commentId);
        return listComments(user, postId);
    }

    // 댓글 목록 + 각 댓글의 삭제 버튼 여부
    private List<NoticeCommentDTO> listComments(LoginUser user, Long postId) {
        boolean manager = isManager(user);
        List<NoticeCommentDTO> comments = dao.selectComments(user.getCompanyId(), postId);
        for (NoticeCommentDTO c : comments) {
            c.setCanDelete(manager || c.getAuthorId().equals(user.getEmployeeId()));
        }
        return comments;
    }

    // ---------- 첨부파일 ----------

    // 파일 올리기: 글을 저장하기 전에 파일부터 서버에 올리고 번호를 받는다.
    // 저장·형식 검사·크기는 공통 파일 기능(AttachmentService)이 맡는다.
    // 공지·부서 게시판이 같이 쓰는 주소라 권한은 BoardDeptService.uploadFiles가 먼저 본다
    public List<NoticeFileDTO> storeFiles(LoginUser user, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올릴 파일을 골라 주세요");
        }
        if (files.size() > FILE_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "첨부파일은 " + FILE_MAX + "개까지입니다");
        }

        List<NoticeFileDTO> result = new ArrayList<>();
        for (MultipartFile file : files) {
            AttachmentDTO saved = attachmentService.upload(file, user, FILE_MODULE); // 형식이 틀리면 여기서 예외
            NoticeFileDTO f = new NoticeFileDTO();
            f.setId(saved.getAttachmentId());
            f.setName(saved.getOriginalName());
            f.setSize(saved.getByteSize());
            f.setType(saved.getMimeType());
            result.add(f);
        }
        return result;
    }

    // 내려받기 전 확인: 그 공지를 볼 수 있고, 그 파일이 정말 그 공지에 붙은 파일인지
    public AttachmentDTO getFile(LoginUser user, Long postId, Long fileId) {
        findNotice(user, postId); // 없거나 볼 수 없는 공지면 404
        if (!dao.selectFileIds(user.getCompanyId(), postId).contains(fileId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 파일입니다");
        }
        return attachmentService.get(fileId, user.getCompanyId());
    }

    // 파일 내용 (내려받기·이미지 보기)
    public Resource loadFile(AttachmentDTO file) throws IOException {
        return attachmentService.load(file);
    }

    // 글의 첨부를 화면이 보낸 목록에 맞춘다 (공지·부서 게시판의 새 글·수정이 같이 쓴다)
    public void syncFiles(LoginUser user, Long postId, List<NoticeFileDTO> files) {
        Long companyId = user.getCompanyId();

        // 1. 화면이 남기겠다고 보낸 파일 번호들 (같은 번호가 두 번 와도 한 번만)
        Set<Long> wanted = new LinkedHashSet<>();
        if (files != null) {
            for (NoticeFileDTO f : files) {
                if (f != null && f.getId() != null) wanted.add(f.getId());
            }
        }
        if (wanted.size() > FILE_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "첨부파일은 " + FILE_MAX + "개까지입니다");
        }

        // 2. 지금 붙어 있는 파일 중 목록에 없는 것 → 떼기
        List<Long> current = dao.selectFileIds(companyId, postId);
        for (Long id : current) {
            if (!wanted.contains(id)) dao.deletePostFile(companyId, postId, id);
        }

        // 3. 목록에 있는데 아직 안 붙은 것 → 내가 올린 파일인지 확인하고 붙이기
        for (Long id : wanted) {
            if (current.contains(id)) continue;
            AttachmentDTO file = attachmentService.get(id, companyId); // 우리 업체 파일이 아니면 예외
            if (!file.getUploaderAccountId().equals(user.getUserAccountId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "붙일 수 없는 파일입니다");
            }
            dao.insertPostFile(companyId, postId, id);
            attachmentService.markAttached(id, companyId); // 이미 다른 글에 붙은 파일이면 예외
        }
    }

    // 첨부 목록 + 각 파일의 내려받기 주소
    private List<NoticeFileDTO> listFiles(LoginUser user, Long postId) {
        List<NoticeFileDTO> files = dao.selectFiles(user.getCompanyId(), postId);
        for (NoticeFileDTO f : files) {
            f.setUrl("/board/api/notices/" + postId + "/files/" + f.getId());
        }
        return files;
    }

    // ---------- 같이 쓰는 도구 ----------

    // 우리 업체의 공지 한 건을 찾는다. 없거나 다른 업체·지운 글이면 404
    private NoticeDTO findNotice(LoginUser user, Long id) {
        NoticeDTO notice = (id == null) ? null : dao.selectNotice(user.getCompanyId(), id);
        if (notice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return notice;
    }

    // 관리자·인사 담당이 아니면 403
    private void checkManager(LoginUser user) {
        if (!isManager(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공지를 쓸 권한이 없습니다");
        }
    }

    // 제목·내용 검사 (공지·부서 게시판 같이 씀). 틀리면 400과 안내 문구
    public void validate(String title, String content) {
        String text = textOf(content);
        String message = null;
        if (title.isEmpty())                          message = "제목을 입력해 주세요";
        else if (title.length() > TITLE_MAX)          message = "제목은 " + TITLE_MAX + "자까지 쓸 수 있습니다";
        else if (text.isEmpty())                      message = "내용을 입력해 주세요";
        else if (text.length() > CONTENT_MAX)         message = "내용은 " + CONTENT_MAX + "자까지 쓸 수 있습니다";
        else if (content.length() > CONTENT_HTML_MAX) message = "내용이 너무 깁니다";
        if (message != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    // 서식(HTML 태그)을 뺀 글자만 남긴다 (board.js textOf와 같은 기준으로 센다)
    private String textOf(String html) {
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")           // 줄바꿈
                .replaceAll("(?i)</(p|div|li)>", "\n")         // 문단·목록 끝도 줄바꿈
                .replaceAll("<[^>]*>", "")                     // 나머지 태그는 지운다
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replace("\u200B", "")                         // 에디터가 넣는 보이지 않는 글자
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    // 검색어를 띄어쓰기로 나눈다 (공지·부서 게시판 같이 씀). 비어 있으면 null (= 검색 안 함)
    public List<String> splitWords(String q) {
        if (q == null || q.trim().isEmpty()) return null;
        return Arrays.asList(q.trim().split("\\s+"));
    }

    // 업체 관리자 또는 인사 담당인가
    public boolean isManager(LoginUser user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
    }
}