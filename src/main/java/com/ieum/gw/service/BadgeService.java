package com.ieum.gw.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/** 사이드바 배지 수 (GET /badges, common.js가 15초마다). 숫자는 각 모듈 Service가 세고 여기서는 모으기만 한다 */
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final NotificationService notificationService;  
    private final ApprovalService approvalService;          
    private final JoinService joinService;                  
    private final OpsService opsService;   
    private final ChatService chatService;

    /** 키 이름은 sidebar.jspf의 data-badge와 같다. 볼 수 없는 배지는 키를 넣지 않는다(화면이 0으로 숨김) */
    public Map<String, Integer> getBadges(LoginUser user) {
        Long companyId = user.getCompanyId();
        Map<String, Integer> badges = new LinkedHashMap<>();

        // 업체 직원 (운영자도 이음 운영 업체의 직원 메뉴를 같이 본다)
        badges.put("notification", notificationService.countUnread(companyId, user.getUserAccountId()));
        badges.put("approval", approvalService.countWaitingForMe(companyId, user.getEmployeeId()));
        badges.put("chat", countUnreadChat(user));
        Integer join = joinService.countWaitingForBadge(user);
        if (join != null) {
            badges.put("join", join);
        }

        // 운영자 메뉴
        if ("ROLE_OPS".equals(user.getRole())) {
            badges.put("application", 0); // shortcut: 새 체험 신청 수 메서드가 아직 없어 0, 운영 모듈에 생기면 연결
            badges.put("inquiryGuest", opsService.countWaitingGuestInquiries());
        }
        return badges;
    }
    

    /** 안 읽은 채팅 수. 재직 중인 사용 계정이 아니면(운영자 등) 0: 채팅 쪽 예외로 /badges 전체가 실패하지 않게 */
    private int countUnreadChat(LoginUser user) {
        try {
            return chatService.countUnread(user.getCompanyId(), user.getEmployeeId());
        } catch (AccessDeniedException e) {
            return 0;
        }
    }
}