package com.ieum.gw.common.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import static java.util.Map.entry;

import org.springframework.web.multipart.MultipartFile;

/** 첨부파일 형식 검사: 허용 확장자 + 실제 파일 앞부분(매직 바이트) */
public final class FileTypeChecker {

    private static final byte[] JPG  = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG  = { (byte) 0x89, 0x50, 0x4E, 0x47 };
    private static final byte[] GIF  = { 0x47, 0x49, 0x46, 0x38 };
    private static final byte[] PDF  = { 0x25, 0x50, 0x44, 0x46 };
    private static final byte[] ZIP  = { 0x50, 0x4B, 0x03, 0x04 };          // docx·xlsx·pptx·hwpx
    private static final byte[] OLE  = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0 }; // hwp

    /** 허용 확장자 → 매직 바이트 (추가는 entry 한 줄) */
    private static final Map<String, byte[]> MAGIC = Map.ofEntries(
        entry("jpg",  JPG),
        entry("jpeg", JPG),
        entry("png",  PNG),
        entry("gif",  GIF),
        entry("pdf",  PDF),
        entry("docx", ZIP),
        entry("xlsx", ZIP),
        entry("pptx", ZIP),
        entry("hwpx", ZIP),
        entry("hwp",  OLE)
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
        byte[] magic = MAGIC.get(ext);
        if (magic == null) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다");
        }

        // ③ 채팅이면 이미지만
        if ("chat".equals(module) && !CHAT_ONLY.contains(ext)) {
            throw new IllegalArgumentException("채팅에는 JPG·PNG 이미지만 올릴 수 있습니다");
        }

        // ④ 실제 앞부분 비교
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(magic.length);
            if (!Arrays.equals(head, magic)) {
                throw new IllegalArgumentException("파일 내용이 확장자와 다릅니다");
            }
        }
    }
}