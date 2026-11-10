package com.ieum.gw.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.code.SupportInquiryStatus;
import com.ieum.gw.dao.SupportDAO;
import com.ieum.gw.dto.SupportInquiryDTO;
import com.ieum.gw.dto.SupportInquiryFormDTO;
import com.ieum.gw.dto.SupportInquirySaveDTO;
import com.ieum.gw.dto.SupportReplyDTO;
import com.ieum.gw.dto.SupportReplyFormDTO;
import com.ieum.gw.dto.SupportReplySaveDTO;

import lombok.RequiredArgsConstructor;

/**
 * 이음 장애 문의 최소판(요구분석서 3장 '추가 구현 범위', OPS-04 중 문의와 답변만).
 * 직원은 자기가 보낸 문의만, 업체 관리자는 같은 업체의 문의까지 본다. 운영자는 문의 표만 모든 업체 것을 읽고 답변을 쓰며, 원문·작성자·업체는 바꾸지 못한다(기능명세서 1.6).
 * 만료 업체의 등록은 공통 만료 읽기 전용 검사(ReadOnlyInterceptor)가 막는다.
 * 운영자가 답변하면 문의한 직원에게 알림(NT_SUPPORT_REPLY)을 같은 트랜잭션으로 저장한다. '내가 보낸 문의'의 새 답변 표시는
 * 그 알림의 읽음 여부를 쓰고, 직원이 문의를 펼치면 그 문의의 답변 알림을 읽음으로 바꾼다(칸을 따로 두지 않는다, db/README.md).
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    static final int MAX_TITLE = 100;   // 화면 support/inquiry.jsp와 같은 값
    static final int MAX_BODY = 4000;   // 화면과 같은 값. 내용은 CLOB이라 여기서 막아야 길이가 정해진다

    private final SupportDAO supportDAO;

    private final NotificationService notificationService;

    /**
     * 직원이 장애 문의를 등록한다. 업체·작성자는 로그인 정보로만 받는다.
     *
     * @return 접수번호
     * @throws SupportFormException 제목·내용이 비었거나 너무 길 때
     */
    public Long createInquiry(Long companyId, Long employeeId, SupportInquiryFormDTO form) {
        SupportInquirySaveDTO inquiry = new SupportInquirySaveDTO();
        inquiry.setCompanyId(companyId);
        inquiry.setEmployeeId(employeeId);
        inquiry.setTitle(required(form.getTitle(), "제목", MAX_TITLE));
        inquiry.setBody(required(form.getBody(), "내용", MAX_BODY));
        supportDAO.insertInquiry(inquiry);
        return inquiry.getNo();
    }

    /** 내가 보낸 문의와 그 답변, 최신 문의 순. 문의마다 안 읽은 답변 알림 수(unread)를 붙인다 */
    public List<SupportInquiryDTO> listMine(Long companyId, Long employeeId, Long userAccountId) {
        List<SupportInquiryDTO> inquiries = withReplies(supportDAO.listMyInquiries(companyId, employeeId),
                supportDAO.listMyReplies(companyId, employeeId));
        Map<Long, Integer> unread = notificationService.countUnreadSupportReplies(companyId, userAccountId);
        for (SupportInquiryDTO inquiry : inquiries) {
            inquiry.setUnread(unread.getOrDefault(inquiry.getNo(), 0));
        }
        return inquiries;
    }

    /**
     * 직원이 내가 보낸 문의를 펼쳤을 때 그 문의의 답변 알림을 읽음으로 바꾼다. 업체·계정은 로그인 정보로만 받는다.
     * 남의 문의 번호면 바뀌는 것이 없다(알림의 받는 계정 조건). 바뀐 알림 수를 돌려준다.
     */
    public int markRepliesRead(Long companyId, Long userAccountId, Long no) {
        return notificationService.markSupportRepliesRead(companyId, userAccountId, no);
    }

    /** 업체 관리자: 우리 회사 직원들이 보낸 문의와 그 답변, 최신 문의 순. 업체는 로그인 정보로만 받는다 */
    public List<SupportInquiryDTO> listCompany(Long companyId) {
        return withReplies(supportDAO.listCompanyInquiries(companyId), supportDAO.listCompanyReplies(companyId));
    }

    /** 운영자: 모든 업체의 문의와 그 답변, 최신 문의 순 */
    public List<SupportInquiryDTO> listAll() {
        return withReplies(supportDAO.listAllInquiries(), supportDAO.listAllReplies());
    }

    /**
     * 운영자가 답변을 쓴다. 답변은 문의한 업체의 것으로 저장하고, 첫 답변이면 문의를 지원 중으로 바꾼다.
     * 문의한 직원에게 답변 알림을 같은 트랜잭션으로 저장한다(알림 저장이 실패하면 답변도 취소된다).
     *
     * @return 답변이 반영된 문의
     * @throws SupportFormException 답변이 비었거나 너무 길 때
     * @throws SupportNotFoundException 없는 접수번호일 때
     */
    @Transactional
    public SupportInquiryDTO reply(Long no, SupportReplyFormDTO form, Long operatorAccountId) {
        String body = required(form.getBody(), "답변", MAX_BODY);
        SupportInquiryDTO inquiry = requireInquiry(no);
        SupportReplySaveDTO reply = new SupportReplySaveDTO();
        reply.setCompanyId(inquiry.getCompanyId());
        reply.setNo(no);
        reply.setAuthorAccountId(operatorAccountId);
        reply.setBody(body);
        supportDAO.insertReply(reply);
        supportDAO.changeInquiryStatus(no, operatorAccountId, SupportInquiryStatus.SUP_RECEIVED, SupportInquiryStatus.SUP_PROCESSING);
        notificationService.notifySupportReply(inquiry.getCompanyId(), inquiry.getRequesterEmployeeId(), no,
                reply.getReplyId(), inquiry.getTitle());
        return inquiryWithReplies(no);
    }

    /**
     * 운영자가 이미 쓴 답변을 고친다. 답변은 운영팀 이름으로 보이므로 운영자면 누구나 고칠 수 있다.
     *
     * @return 답변이 반영된 문의
     * @throws SupportFormException 답변이 비었거나 너무 길 때
     * @throws SupportNotFoundException 그 문의에 그 답변이 없을 때
     */
    public SupportInquiryDTO editReply(Long no, Long replyId, SupportReplyFormDTO form) {
        String body = required(form.getBody(), "답변", MAX_BODY);
        if (supportDAO.updateReply(no, replyId, body) == 0) {
            throw new SupportNotFoundException("답변을 찾을 수 없습니다.");
        }
        return inquiryWithReplies(no);
    }

    /** 문의 한 건과 그 답변 */
    private SupportInquiryDTO inquiryWithReplies(Long no) {
        SupportInquiryDTO inquiry = requireInquiry(no);
        inquiry.setReplies(supportDAO.listReplies(no));
        return inquiry;
    }

    /** 없는 접수번호면 404로 거절한다 */
    private SupportInquiryDTO requireInquiry(Long no) {
        SupportInquiryDTO inquiry = supportDAO.getInquiry(no);
        if (inquiry == null) {
            throw new SupportNotFoundException("문의를 찾을 수 없습니다.");
        }
        return inquiry;
    }

    /** 답변을 문의별로 나눠 붙인다. 답변이 없는 문의는 빈 목록이다 */
    private static List<SupportInquiryDTO> withReplies(List<SupportInquiryDTO> inquiries, List<SupportReplyDTO> replies) {
        Map<Long, List<SupportReplyDTO>> byInquiry = new HashMap<>();
        for (SupportReplyDTO reply : replies) {
            byInquiry.computeIfAbsent(reply.getInquiryNo(), no -> new ArrayList<>()).add(reply);
        }
        for (SupportInquiryDTO inquiry : inquiries) {
            inquiry.setReplies(byInquiry.getOrDefault(inquiry.getNo(), List.of()));
        }
        return inquiries;
    }

    /** 앞뒤 공백을 뗀 값. 비었거나 max자를 넘으면 화면에 보일 문구로 거절한다 */
    private static String required(String value, String label, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new SupportFormException(label + "을 입력해 주세요.");
        }
        if (text.length() > max) {
            throw new SupportFormException(label + "은 " + max + "자까지 쓸 수 있습니다.");
        }
        return text;
    }
}
