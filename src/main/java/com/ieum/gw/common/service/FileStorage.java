package com.ieum.gw.common.service;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일 저장소. 각 모듈은 이 인터페이스만 사용한다.
 * 저장 키 = "{companyId}/{module}/{UUID}.{확장자}" (루트 폴더 제외 상대 경로)
 */
public interface FileStorage {

    /** 파일을 저장하고 저장 키를 돌려준다 */
    String save(MultipartFile file, Long companyId, String module) throws IOException;

    /** 저장 키로 파일을 찾는다 (다운로드용) */
    Resource load(String storageKey) throws IOException;

    /** 저장 키의 파일을 지운다 (DB 기록 실패 시 정리용) */
    void delete(String storageKey) throws IOException;
}