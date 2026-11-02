package com.ieum.gw.subscription.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.subscription.dto.CompanyDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;

@Mapper
public interface SubscriptionMapper {

    int countTrialEmail(@Param("trialEmail") String trialEmail);

    int insertCompany(CompanyDTO dto);

    int insertTrialPeriod(ServicePeriodDTO dto);

    List<ServicePeriodDTO> listPeriods(@Param("companyId") Long companyId);

    LocalDateTime getEmailVerifiedAt(@Param("companyId") Long companyId);
}
