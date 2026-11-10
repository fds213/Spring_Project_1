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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.ApprovalDecisionDTO;
import com.ieum.gw.dto.ApprovalSubmitDTO;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.ApprovalService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = "/approval")
public class ApprovalController {
    private final ApprovalService service;

    @GetMapping(value = "/write")
    public String write(Model model) {
        return "approval/write";
    }

    @GetMapping(value = "/list")
    public String list(Model model) {
        return "approval/list";
    }

    /* ---------------API----------------- */

    @GetMapping(value = "/api/form-options")
    @ResponseBody
    public Map<String, Object> formOptions(@AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.getFormOptions(user.getCompanyId(), user.getEmployeeId()));
    }

    @PostMapping(value = "/api/documents")
    @ResponseBody
    public Map<String, Object> submit(@AuthenticationPrincipal LoginUser user, @RequestBody ApprovalSubmitDTO dto) {
        return Map.of("success", true, "data", service.submit(user,dto));
    }

    @GetMapping(value = "/api/box")
    @ResponseBody
    public Map<String, Object> box(@AuthenticationPrincipal LoginUser user){
        return Map.of("success", true, "data", service.box(user));
    }

    @PostMapping(value = "/api/documents/{documentId}/approve")
    @ResponseBody
    public Map<String, Object> approve(@RequestBody ApprovalDecisionDTO dto, @PathVariable long documentId, @AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.approve(user, dto, documentId));
    }

    @PostMapping(value = "/api/documents/{documentId}/reject")
    @ResponseBody
    public Map<String, Object> reject(@RequestBody ApprovalDecisionDTO dto, @PathVariable long documentId, @AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.reject(user, dto, documentId));
    }

    // 첨부 올리기: POST /approval/api/files (multipart "files", 최대 5개). 상신 전에 먼저 올려 번호를 받는다
    @PostMapping(value = "/api/files")
    @ResponseBody
    public Map<String, Object> fileUpload(@AuthenticationPrincipal LoginUser loginUser,
                                          @RequestPart("files") List<MultipartFile> files) throws IOException {
        return Map.of("success", true, "data", service.storeFiles(loginUser, files));
    }
    
    // 첨부 내려받기: GET /approval/api/documents/3/files/12 (결재함에 보이는 문서만)
    @GetMapping(value = "/api/documents/{documentId}/files/{fileId}")
    public ResponseEntity<Resource> fileDownload(@AuthenticationPrincipal LoginUser loginUser,
                                                 @PathVariable long documentId,
                                                 @PathVariable long fileId) throws IOException {
        AttachmentDTO file = service.getFile(loginUser, documentId, fileId);
        Resource body = service.loadFile(file);

        // 한글 파일 이름이 깨지지 않게 UTF-8로 적는다. 이미지는 바로 보이게, 나머지는 내려받게
        String encoded = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (file.getMimeType().startsWith("image/") ? "inline" : "attachment")
                + "; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(body);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException e) {
        String message = (e.getMessage() != null ? e.getMessage() : "요청을 처리하지 못했습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Map.of("message", e.getReason()));
    }


}
