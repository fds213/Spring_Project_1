package com.ieum.gw.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.CompanyDTO;
import com.ieum.gw.dto.ServicePeriodDTO;

@Mapper
public interface SubscriptionMapper {

    int countTrialEmail(@Param("trialEmail") String trialEmail);

    int insertCompany(CompanyDTO dto);

    int insertTrialPeriod(ServicePeriodDTO dto);

    int insertPaidPeriod(ServicePeriodDTO dto);

    Long lockCompany(@Param("companyId") Long companyId);

    CompanyDTO getCompany(@Param("companyId") Long companyId);

    List<ServicePeriodDTO> listPeriods(@Param("companyId") Long companyId);

    List<ServicePeriodDTO> listAllPeriods();

    LocalDateTime getEmailVerifiedAt(@Param("companyId") Long companyId);

    int markEmailVerified(@Param("companyId") Long companyId, @Param("verifiedAt") LocalDateTime verifiedAt);
}
