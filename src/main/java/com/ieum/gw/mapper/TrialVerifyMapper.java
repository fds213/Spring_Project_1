package com.ieum.gw.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.TrialVerificationDTO;
import com.ieum.gw.dto.TrialVerifyTargetDTO;

@Mapper
public interface TrialVerifyMapper {

    TrialVerifyTargetDTO getTarget(@Param("companyId") Long companyId);

    LocalDateTime getLastIssuedAt(@Param("companyId") Long companyId, @Param("purpose") String purpose);

    Integer getResendCount(@Param("companyId") Long companyId, @Param("email") String email,
            @Param("purpose") String purpose, @Param("today") LocalDate today);

    int revokeLive(@Param("companyId") Long companyId, @Param("purpose") String purpose,
            @Param("now") LocalDateTime now);

    int insertVerification(@Param("v") TrialVerificationDTO verification, @Param("purpose") String purpose);

    int recordIssue(@Param("companyId") Long companyId, @Param("email") String email,
            @Param("purpose") String purpose, @Param("today") LocalDate today,
            @Param("resends") int resends, @Param("now") LocalDateTime now);

    TrialVerificationDTO findByTokenHash(@Param("tokenHash") String tokenHash, @Param("purpose") String purpose);

    int consume(@Param("id") Long emailVerificationId, @Param("companyId") Long companyId,
            @Param("now") LocalDateTime now);
}
