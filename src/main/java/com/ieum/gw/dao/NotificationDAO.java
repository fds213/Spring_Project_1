package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.NotificationDTO;
import com.ieum.gw.dto.NotificationSaveDTO;
import com.ieum.gw.dto.NotificationUnreadCountDTO;
import com.ieum.gw.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationDAO {

    private final NotificationMapper mapper;

    /** 받는 사원의 계정 번호. 이 업체에 그 사원의 계정이 없으면 null */
    public Long findAccountId(Long companyId, Long employeeId) {
        return mapper.findAccountId(companyId, employeeId);
    }

    /** 알림 1행 기록. 같은 받는 계정에 같은 중복 방지 키면 DuplicateKeyException */
    public int insert(NotificationSaveDTO dto) {
        return mapper.insertNotification(dto);
    }

    /** 본인 알림 한 페이지 (최신 순) */
    public List<NotificationDTO> list(Long companyId, Long userAccountId, int offset, int size) {
        return mapper.listNotifications(companyId, userAccountId, offset, size);
    }

    /** 본인 알림 전체 수 */
    public int count(Long companyId, Long userAccountId) {
        return mapper.countNotifications(companyId, userAccountId);
    }

    /** 본인의 안 읽은 알림 수 */
    public int countUnread(Long companyId, Long userAccountId) {
        return mapper.countUnread(companyId, userAccountId);
    }

    /** 안 읽은 본인 알림을 읽음으로 표시. 바뀐 행 수(이미 읽었거나 본인 것이 아니면 0) */
    public int markRead(Long companyId, Long userAccountId, Long notificationId) {
        return mapper.markRead(companyId, userAccountId, notificationId);
    }

    /** 본인의 안 읽은 이음 문의 답변 알림 수, 문의별 */
    public List<NotificationUnreadCountDTO> countUnreadSupportReplies(Long companyId, Long userAccountId) {
        return mapper.countUnreadSupportReplies(companyId, userAccountId);
    }

    /** 한 문의의 본인 답변 알림을 upToId 번호까지 읽음으로 표시. 바뀐 행 수 */
    public int markSupportRepliesRead(Long companyId, Long userAccountId, Long supportInquiryId, Long upToId) {
        return mapper.markSupportRepliesRead(companyId, userAccountId, supportInquiryId, upToId);
    }

    /** 이 번호가 본인 알림인지 */
    public boolean isOwned(Long companyId, Long userAccountId, Long notificationId) {
        return mapper.countOwned(companyId, userAccountId, notificationId) > 0;
    }
}
