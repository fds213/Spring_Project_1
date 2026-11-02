package com.ieum.gw.common.dao;

import org.springframework.stereotype.Repository;

import com.ieum.gw.common.dto.AttachmentDTO;
import com.ieum.gw.common.mapper.AttachmentMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AttachmentDAO {

	private final AttachmentMapper mapper;

	/** 1행 기록. 성공하면 dto.attachmentId에 새 번호가 들어 있음 */
	public int insert(AttachmentDTO dto) {
		return mapper.insert(dto);
	}

	/** 같은 업체 파일만 조회. 없거나 다른 업체면 null */
	public AttachmentDTO getById(Long attachmentId, Long companyId) {
		return mapper.getById(attachmentId, companyId);
	}

	/** FILE_UPLOADED → FILE_ATTACHED. 바뀐 행 수 (0이면 이미 연결됐거나 다른 업체) */
	public int markAttached(Long attachmentId, Long companyId) {
		return mapper.markAttached(attachmentId, companyId);
	}
}