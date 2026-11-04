package com.ieum.gw.mapper;

import java.time.LocalDateTime;

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

    int claimRetry(@Param("mailOutboxId") Long mailOutboxId, @Param("staleMinutes") int staleMinutes);

    int countForRecipient(@Param("mailType") String mailType, @Param("recipientEmail") String recipientEmail,
            @Param("from") LocalDateTime from);
}
