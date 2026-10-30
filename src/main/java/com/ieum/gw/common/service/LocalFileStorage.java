package com.ieum.gw.common.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorage implements FileStorage {

    private static final long MIN_FREE_BYTES = 1024L * 1024 * 1024; // 1GB

    private final Path root;

    /** 서버 시작 시 1번: 루트 폴더 준비 */
    public LocalFileStorage(@Value("${file.root:C:/ieum-files}") String rootDir) throws IOException {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String save(MultipartFile file, Long companyId, String module) throws IOException {
    	if (companyId == null || module == null || !module.matches("[a-z]+")) {
            throw new IllegalArgumentException("업체 번호나 모듈 이름이 잘못되었습니다");
        }
    	// ① 확장자: 원래 이름에서 마지막 '.' 뒤, 소문자로
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null) {
            int dot = original.lastIndexOf('.');       // 점 위치 (없으면 -1)
            if (dot >= 0) {
                ext = original.substring(dot + 1).toLowerCase(); 
            }
        }

        FileTypeChecker.check(file, ext, module);
        
        // ② 저장 키
        String key = companyId + "/" + module + "/" + UUID.randomUUID() + "." + ext;

        // ③④ 실제 경로 + 탈출 검사
        Path target = resolve(key);

        // ⑤ 남은 공간
        if (root.toFile().getUsableSpace() < MIN_FREE_BYTES) {
            throw new IOException("저장 공간이 부족합니다");
        }

        // ⑥ 폴더 만들기
        Files.createDirectories(target.getParent());

        // ⑦ 쓰기
        file.transferTo(target);

        return key;
    }

    @Override
    public Resource load(String storageKey) throws IOException {
        Path path = resolve(storageKey);
        if (!Files.exists(path)) {
        	throw new IOException("파일이 없습니다.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageKey) throws IOException {
    	Path path = resolve(storageKey);
        Files.deleteIfExists(path);
    }

    /** 저장 키 → 실제 경로. 루트 밖으로 나가면 거절 */
    private Path resolve(String key) throws IOException {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IOException("잘못된 파일 경로입니다");
        }
        return p;
    }
}