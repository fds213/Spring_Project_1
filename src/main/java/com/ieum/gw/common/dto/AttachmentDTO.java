package com.ieum.gw.common.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** ATTACHMENT 한 행 (첨부파일 정보) */
@Getter
@Setter
public class AttachmentDTO {
    // ── 누구 것인가 ──
    private Long attachmentId;        // ATTACHMENT_ID       파일 번호 (insert 전에는 null)
    private Long companyId;           // COMPANY_ID          업체 분리 검사 기준
    private Long uploaderAccountId;   // UPLOADER_ACCOUNT_ID 올린 사람 (loginUser.getUserAccountId())

    // ── 파일 정보 ──
    private String originalName;      // ORIGINAL_NAME  "보고서.pdf" (다운로드 이름)
    private String storageKey;        // STORAGE_KEY    "1/board/UUID.pdf" (디스크 위치)
    private String mimeType;          // MIME_TYPE      "application/pdf"
    private Long byteSize;            // BYTE_SIZE      바이트 크기
    private String sha256;            // SHA256         지금은 비워 둠 (null 허용)
    private String categoryCode;      // CATEGORY_CODE  FC_IMAGE, FC_DOCUMENT ...

    // ── 상태 ──
    private String statusCode;        // STATUS_CODE    FILE_UPLOADED → FILE_ATTACHED → FILE_REMOVED
    private LocalDateTime removedAt;  // REMOVED_AT
    private LocalDateTime createdAt;  // CREATED_AT     DB 기본값 (자동)
    private LocalDateTime updatedAt;  // UPDATED_AT     DB 기본값 (자동)
}