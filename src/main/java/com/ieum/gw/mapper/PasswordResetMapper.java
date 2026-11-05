package com.ieum.gw.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.PasswordResetCodeDTO;
import com.ieum.gw.dto.PasswordResetTargetDTO;

/** 비밀번호 찾기(ACC-07). SQL은 mapper/PasswordResetMapper.xml */
@Mapper
public interface PasswordResetMapper {

    PasswordResetTargetDTO findTarget(@Param("companyCode") String companyCode, @Param("employeeNo") String employeeNo);

    PasswordResetTargetDTO findTargetByAccount(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId);

    Long lockAccount(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId);

    LocalDateTime getLastIssuedAt(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId);

    Integer getSentCountToday(@Param("companyId") Long companyId, @Param("email") String email,
            @Param("today") LocalDate today);

    int revokeLive(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId,
            @Param("now") LocalDateTime now);

    int insertCode(PasswordResetCodeDTO code);

    int recordSend(@Param("companyId") Long companyId, @Param("email") String email,
            @Param("today") LocalDate today, @Param("now") LocalDateTime now);

    PasswordResetCodeDTO findLiveCode(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId);

    int addFailure(@Param("id") Long id, @Param("companyId") Long companyId,
            @Param("maxFailures") int maxFailures, @Param("now") LocalDateTime now);

    int markVerified(@Param("id") Long id, @Param("companyId") Long companyId, @Param("tokenHash") String tokenHash,
            @Param("expiresAt") LocalDateTime expiresAt, @Param("now") LocalDateTime now);

    PasswordResetCodeDTO findByResetToken(@Param("tokenHash") String tokenHash);

    int consume(@Param("id") Long id, @Param("companyId") Long companyId, @Param("now") LocalDateTime now);

    int updatePassword(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId,
            @Param("passwordHash") String passwordHash, @Param("now") LocalDateTime now);
}