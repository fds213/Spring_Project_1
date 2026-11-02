package com.ieum.gw.subscription.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.subscription.dto.CompanyDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;
import com.ieum.gw.subscription.mapper.SubscriptionMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SubscriptionDAO {

    private final SubscriptionMapper mapper;

    /** 이 이메일로 만든 업체가 있는지 (만료·자료 삭제된 업체 포함) */
    public boolean existsTrialEmail(String trialEmail) {
        return mapper.countTrialEmail(trialEmail) > 0;
    }

    /** 업체 1행 기록. 성공하면 dto.companyId에 새 번호. 업체 코드가 겹치면 DuplicateKeyException */
    public int insertCompany(CompanyDTO dto) {
        return mapper.insertCompany(dto);
    }

    /** 체험 기간 1행 기록. 성공하면 dto.servicePeriodId에 새 번호 */
    public int insertTrialPeriod(ServicePeriodDTO dto) {
        return mapper.insertTrialPeriod(dto);
    }

    /** 업체의 이용 기간 전부 (시작 순). 없으면 빈 목록 */
    public List<ServicePeriodDTO> listPeriods(Long companyId) {
        return mapper.listPeriods(companyId);
    }

    /** 업체 이메일 인증 시각. 인증 전이면 null */
    public LocalDateTime getEmailVerifiedAt(Long companyId) {
        return mapper.getEmailVerifiedAt(companyId);
    }
}
