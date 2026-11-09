package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.SupportInquiryDTO;
import com.ieum.gw.dto.SupportInquirySaveDTO;
import com.ieum.gw.dto.SupportReplyDTO;
import com.ieum.gw.dto.SupportReplySaveDTO;

@Mapper
public interface SupportMapper {

    /** 장애 문의 1행 저장. 뽑은 번호를 dto.no에 채운다 */
    int insertInquiry(SupportInquirySaveDTO dto);

    /** 그 직원이 보낸 문의, 최신순 */
    List<SupportInquiryDTO> listMyInquiries(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    /** 그 직원이 보낸 문의에 달린 답변, 먼저 쓴 순 */
    List<SupportReplyDTO> listMyReplies(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    /** 업체 관리자: 그 업체 직원들이 보낸 문의와 보낸 사람 이름, 최신순 */
    List<SupportInquiryDTO> listCompanyInquiries(@Param("companyId") Long companyId);

    /** 업체 관리자: 그 업체 문의에 달린 답변, 먼저 쓴 순 */
    List<SupportReplyDTO> listCompanyReplies(@Param("companyId") Long companyId);

    /** 운영자: 모든 업체의 문의, 최신순 */
    List<SupportInquiryDTO> listAllInquiries();

    /** 운영자: 문의 한 건. 없으면 null */
    SupportInquiryDTO getInquiry(@Param("no") Long no);

    /** 운영자: 답변, 먼저 쓴 순. no가 null이면 모든 문의의 답변 */
    List<SupportReplyDTO> listReplies(@Param("no") Long no);

    /** 운영자: 답변 1행 저장. 뽑은 번호를 dto.replyId에 채운다 */
    int insertReply(SupportReplySaveDTO dto);

    /** 운영자: 그 문의의 그 답변 내용을 고친다. 바뀐 행 수를 돌려준다 */
    int updateReply(@Param("no") Long no, @Param("replyId") Long replyId, @Param("body") String body);

    /** 운영자: 문의가 from 상태일 때만 to로 바꾸고 담당 운영자를 적는다. 원문·작성자·업체는 건드리지 않는다 */
    int changeInquiryStatus(@Param("no") Long no, @Param("operatorAccountId") Long operatorAccountId,
                            @Param("from") String from, @Param("to") String to);
}
