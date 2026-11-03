package com.ieum.gw.ops.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.ops.dto.OpsCompanyRowDTO;
import com.ieum.gw.ops.dto.OpsEmployeeDTO;
import com.ieum.gw.ops.dto.OpsGuestInquiryDTO;
import com.ieum.gw.ops.dto.OpsPaymentStatDTO;

@Mapper
public interface OpsMapper {

    List<OpsCompanyRowDTO> listCompanies();

    OpsCompanyRowDTO getCompany(@Param("companyCode") String companyCode);

    List<OpsEmployeeDTO> listEmployees(@Param("companyId") Long companyId);

    List<OpsPaymentStatDTO> listPaymentStats();

    List<OpsGuestInquiryDTO> listGuestInquiries();

    int countWaitingGuestInquiries();
}
