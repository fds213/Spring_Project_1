package com.ieum.gw.common.service;

import static java.util.Map.entry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

/** 첨부파일 형식 검사: 허용 확장자 + 실제 파일 앞부분(매직 바이트) + mime·종류 */
public final class FileTypeChecker {

    private static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47 };
    private static final byte[] GIF = { 0x47, 0x49, 0x46, 0x38 };
    private static final byte[] PDF = { 0x25, 0x50, 0x44, 0x46 };
    private static final byte[] ZIP = { 0x50, 0x4B, 0x03, 0x04 };                        // docx·xlsx·pptx·hwpx
    private static final byte[] OLE = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0 };   // hwp

    /** 형식 하나의 정보: 앞부분 표식, mime, 종류 코드(FILE_CATEGORY) */
    private static final class FileType {
        final byte[] magic;
        final String mime;
        final String category;

        FileType(byte[] magic, String mime, String category) {
            this.magic = magic;
            this.mime = mime;
            this.category = category;
        }
    }

    /** 허용 형식 (추가는 entry 한 줄) */
    private static final Map<String, FileType> TYPES = Map.ofEntries(
        entry("jpg",  new FileType(JPG, "image/jpeg", "FC_IMAGE")),
        entry("jpeg", new FileType(JPG, "image/jpeg", "FC_IMAGE")),
        entry("png",  new FileType(PNG, "image/png",  "FC_IMAGE")),
        entry("gif",  new FileType(GIF, "image/gif",  "FC_IMAGE")),
        entry("pdf",  new FileType(PDF, "application/pdf", "FC_DOCUMENT")),
        entry("docx", new FileType(ZIP, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "FC_DOCUMENT")),
        entry("xlsx", new FileType(ZIP, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "FC_SPREADSHEET")),
        entry("pptx", new FileType(ZIP, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "FC_PRESENTATION")),
        entry("hwp",  new FileType(OLE, "application/x-hwp", "FC_DOCUMENT")),
        entry("hwpx", new FileType(ZIP, "application/hwp+zip", "FC_DOCUMENT"))
    );

    /** 채팅은 이미지만 (요구분석서: JPG·PNG) */
    private static final Set<String> CHAT_ONLY = Set.of("jpg", "jpeg", "png");

    private FileTypeChecker() {}   // 객체 만들지 않고 FileTypeChecker.check(...)로만 사용

    public static void check(MultipartFile file, String ext, String module) throws IOException {
        // ① 빈 파일
        if (file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 올릴 수 없습니다");
        }

        // ② 허용 확장자인지
        FileType type = TYPES.get(ext);
        if (type == null) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다");
        }

        // ③ 채팅이면 이미지만
        if ("chat".equals(module) && !CHAT_ONLY.contains(ext)) {
            throw new IllegalArgumentException("채팅에는 JPG·PNG 이미지만 올릴 수 있습니다");
        }

        // ④ 실제 앞부분 비교
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(type.magic.length);
            if (!Arrays.equals(head, type.magic)) {
                throw new IllegalArgumentException("파일 내용이 확장자와 다릅니다");
            }
        }
    }

    /** check를 통과한 확장자의 mime (예: "pdf" → "application/pdf") */
    public static String mimeOf(String ext) {
        return TYPES.get(ext).mime;
    }

    /** check를 통과한 확장자의 종류 코드 (예: "pdf" → "FC_DOCUMENT") */
    public static String categoryOf(String ext) {
        return TYPES.get(ext).category;
    }
}