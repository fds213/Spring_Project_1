package com.ieum.gw.common.service;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ieum.gw.common.dao.AttachmentDAO;
import com.ieum.gw.common.dto.AttachmentDTO;
import com.ieum.gw.common.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 첨부파일 공통 기능. 각 모듈은 자기 자료의 권한을 검사한 뒤 이 메서드를 부른다. 업체·업로더는 로그인 정보에서만 꺼낸다.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

	private final FileStorage fileStorage;
	private final AttachmentDAO attachmentDAO;

	/** 저장 + 기록. module: board, approval, ticket, chat */
	@Transactional
	public AttachmentDTO upload(MultipartFile file, LoginUser loginUser, String module) throws IOException {
		
		// ⓪ 원래 이름 길이 (ORIGINAL_NAME 255자)
		String original = file.getOriginalFilename();
		if (original == null || original.length() > 255) {
			throw new IllegalArgumentException("파일 이름은 255자 이하여야 합니다");
		} 
		
		// ① 디스크 저장 (안에서 형식 검사·경로·공간 검사)
		String key = fileStorage.save(file, loginUser.getCompanyId(), module);
		String ext = key.substring(key.lastIndexOf('.') + 1);

		// ② 기록할 값
		AttachmentDTO dto = new AttachmentDTO();
		dto.setCompanyId(loginUser.getCompanyId());
		dto.setUploaderAccountId(loginUser.getUserAccountId());
		dto.setOriginalName(file.getOriginalFilename());
		dto.setStorageKey(key);
		dto.setMimeType(FileTypeChecker.mimeOf(ext));
		dto.setByteSize(file.getSize());
		dto.setCategoryCode(FileTypeChecker.categoryOf(ext));

		// ③ DB 기록. 실패하면 디스크 파일도 지움
		try {
			attachmentDAO.insert(dto);
		} catch (RuntimeException e) {
			fileStorage.delete(key);
			throw e;
		}
		return dto; // dto.getAttachmentId() = 새 번호
	}

	/** 같은 업체 파일만. 없거나 다른 업체면 예외 */
	public AttachmentDTO get(Long attachmentId, Long companyId) {
		AttachmentDTO dto = attachmentDAO.getById(attachmentId, companyId);
		if (dto == null) {
			throw new IllegalArgumentException("파일을 찾을 수 없습니다");
		}
		return dto;
	}

	/** 파일 내용 (다운로드·이미지 표시용) */
	public Resource load(AttachmentDTO dto) throws IOException {
		return fileStorage.load(dto.getStorageKey());
	}

	/** 업무 자료에 연결됨으로 표시. 이미 연결됐거나 다른 업체면 예외 */
	@Transactional
	public void markAttached(Long attachmentId, Long companyId) {
		int rows = attachmentDAO.markAttached(attachmentId, companyId);
		if (rows == 0) {
			throw new IllegalStateException("연결할 수 없는 파일입니다");
		}
	}
}