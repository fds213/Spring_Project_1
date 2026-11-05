package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.OpsCompanyRowDTO;
import com.ieum.gw.dto.OpsEmployeeDTO;
import com.ieum.gw.dto.OpsGuestInquiryDTO;
import com.ieum.gw.dto.OpsGuestReplyDTO;
import com.ieum.gw.dto.OpsGuestReplySaveDTO;
import com.ieum.gw.dto.OpsPaymentStatDTO;

@Mapper
public interface OpsMapper {

    List<OpsCompanyRowDTO> listCompanies();

    OpsCompanyRowDTO getCompany(@Param("companyCode") String companyCode);

    List<OpsEmployeeDTO> listEmployees(@Param("companyId") Long companyId);

    List<OpsPaymentStatDTO> listPaymentStats();

    List<OpsGuestInquiryDTO> listGuestInquiries();

    int countWaitingGuestInquiries();

    OpsGuestInquiryDTO getGuestInquiry(@Param("no") Long no);

    OpsGuestInquiryDTO lockGuestInquiry(@Param("no") Long no);

    List<OpsGuestReplyDTO> listGuestReplies(@Param("no") Long no, @Param("staleMinutes") int staleMinutes);

    int insertGuestReply(OpsGuestReplySaveDTO dto);

    int markGuestInquiryAnswered(@Param("no") Long no);
}
