package com.ieum.gw.ops.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.ops.dto.OpsCompanyRowDTO;
import com.ieum.gw.ops.dto.OpsEmployeeDTO;
import com.ieum.gw.ops.dto.OpsGuestInquiryDTO;
import com.ieum.gw.ops.dto.OpsPaymentStatDTO;
import com.ieum.gw.ops.mapper.OpsMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OpsDAO {

    private final OpsMapper mapper;

    /** 모든 업체, 최신 신청 순 */
    public List<OpsCompanyRowDTO> listCompanies() {
        return mapper.listCompanies();
    }

    /** 업체 코드로 찾은 업체. 없으면 null */
    public OpsCompanyRowDTO getCompany(String companyCode) {
        return mapper.getCompany(companyCode);
    }

    /** 업체의 계정 목록, 사번 순 */
    public List<OpsEmployeeDTO> listEmployees(Long companyId) {
        return mapper.listEmployees(companyId);
    }

    /** 업체별 첫 결제 시각과 실패한 결제 수. 결제 기록이 없는 업체는 들어 있지 않다 */
    public List<OpsPaymentStatDTO> listPaymentStats() {
        return mapper.listPaymentStats();
    }

    /** 비회원 접수 전체, 최신 접수 순 */
    public List<OpsGuestInquiryDTO> listGuestInquiries() {
        return mapper.listGuestInquiries();
    }

    /** 아직 답하지 않은 비회원 접수 수 */
    public int countWaitingGuestInquiries() {
        return mapper.countWaitingGuestInquiries();
    }
}
