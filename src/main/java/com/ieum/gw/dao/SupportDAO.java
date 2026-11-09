package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.code.SupportInquiryStatus;
import com.ieum.gw.dto.SupportInquiryDTO;
import com.ieum.gw.dto.SupportInquirySaveDTO;
import com.ieum.gw.dto.SupportReplyDTO;
import com.ieum.gw.dto.SupportReplySaveDTO;
import com.ieum.gw.mapper.SupportMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SupportDAO {

    private final SupportMapper mapper;

    /** 장애 문의 1행 저장. 뽑은 번호를 dto.no에 채운다 */
    public int insertInquiry(SupportInquirySaveDTO dto) {
        return mapper.insertInquiry(dto);
    }

    /** 그 직원이 보낸 문의, 최신순 */
    public List<SupportInquiryDTO> listMyInquiries(Long companyId, Long employeeId) {
        return mapper.listMyInquiries(companyId, employeeId);
    }

    /** 그 직원이 보낸 문의에 달린 답변, 먼저 쓴 순 */
    public List<SupportReplyDTO> listMyReplies(Long companyId, Long employeeId) {
        return mapper.listMyReplies(companyId, employeeId);
    }

    /** 업체 관리자: 그 업체 직원들이 보낸 문의와 보낸 사람 이름, 최신순 */
    public List<SupportInquiryDTO> listCompanyInquiries(Long companyId) {
        return mapper.listCompanyInquiries(companyId);
    }

    /** 업체 관리자: 그 업체 문의에 달린 답변, 먼저 쓴 순 */
    public List<SupportReplyDTO> listCompanyReplies(Long companyId) {
        return mapper.listCompanyReplies(companyId);
    }

    /** 운영자: 모든 업체의 문의, 최신순 */
    public List<SupportInquiryDTO> listAllInquiries() {
        return mapper.listAllInquiries();
    }

    /** 운영자: 문의 한 건. 없으면 null */
    public SupportInquiryDTO getInquiry(Long no) {
        return mapper.getInquiry(no);
    }

    /** 운영자: 모든 문의의 답변, 먼저 쓴 순 */
    public List<SupportReplyDTO> listAllReplies() {
        return mapper.listReplies(null);
    }

    /** 운영자: 그 문의의 답변, 먼저 쓴 순 */
    public List<SupportReplyDTO> listReplies(Long no) {
        return mapper.listReplies(no);
    }

    /** 운영자: 답변 1행 저장. 뽑은 번호를 dto.replyId에 채운다 */
    public int insertReply(SupportReplySaveDTO dto) {
        return mapper.insertReply(dto);
    }

    /** 운영자: 그 문의의 그 답변 내용을 고친다. 바뀐 행 수를 돌려준다 */
    public int updateReply(Long no, Long replyId, String body) {
        return mapper.updateReply(no, replyId, body);
    }

    /** 운영자: 문의가 from 상태일 때만 to로 바꾸고 담당 운영자를 적는다 */
    public int changeInquiryStatus(Long no, Long operatorAccountId, SupportInquiryStatus from, SupportInquiryStatus to) {
        return mapper.changeInquiryStatus(no, operatorAccountId, from.name(), to.name());
    }
}
