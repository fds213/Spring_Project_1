package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.OpsCompanyRowDTO;
import com.ieum.gw.dto.OpsEmployeeDTO;
import com.ieum.gw.dto.OpsGuestInquiryDTO;
import com.ieum.gw.dto.OpsGuestReplyDTO;
import com.ieum.gw.dto.OpsGuestReplySaveDTO;
import com.ieum.gw.dto.OpsPaymentStatDTO;
import com.ieum.gw.mapper.OpsMapper;

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

    /** 비회원 접수 한 건. 없으면 null */
    public OpsGuestInquiryDTO getGuestInquiry(Long no) {
        return mapper.getGuestInquiry(no);
    }

    /** 비회원 접수 한 건을 잠근다(트랜잭션 안에서). 같은 접수에 답변을 동시에 보내도 하나씩 처리한다. 없으면 null */
    public OpsGuestInquiryDTO lockGuestInquiry(Long no) {
        return mapper.lockGuestInquiry(no);
    }

    /** 모든 비회원 접수의 답변, 접수별 먼저 보낸 순 */
    public List<OpsGuestReplyDTO> listGuestReplies(int staleMinutes) {
        return mapper.listGuestReplies(null, staleMinutes);
    }

    /** 한 접수의 답변, 먼저 보낸 순 */
    public List<OpsGuestReplyDTO> listGuestReplies(Long no, int staleMinutes) {
        return mapper.listGuestReplies(no, staleMinutes);
    }

    /** 답변 1행 저장. 뽑은 번호를 dto.replyId에 채운다 */
    public int insertGuestReply(OpsGuestReplySaveDTO dto) {
        return mapper.insertGuestReply(dto);
    }

    /** 답변 메일이 나간 접수를 답변 완료로 바꾼다. 이미 답변 완료면 바뀌지 않는다 */
    public int markGuestInquiryAnswered(Long no) {
        return mapper.markGuestInquiryAnswered(no);
    }
}
