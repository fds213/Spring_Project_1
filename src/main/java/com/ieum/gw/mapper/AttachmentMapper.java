package com.ieum.gw.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.AttachmentDTO;

@Mapper
public interface AttachmentMapper {

	int insert(AttachmentDTO dto);

	AttachmentDTO getById(@Param("attachmentId") Long attachmentId, @Param("companyId") Long companyId);

	int markAttached(@Param("attachmentId") Long attachmentId, @Param("companyId") Long companyId);
}