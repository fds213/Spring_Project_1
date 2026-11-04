package com.ieum.gw.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.MailMessageDTO;

@Mapper
public interface MailMapper {

    int insertOutbox(MailMessageDTO dto);

    int updateResult(@Param("mailOutboxId") Long mailOutboxId, @Param("status") String status,
            @Param("success") String success, @Param("error") String error);

    int insertAttempt(@Param("mailOutboxId") Long mailOutboxId, @Param("success") String success,
            @Param("error") String error);
}
