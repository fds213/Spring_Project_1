package com.ieum.gw.notification.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.notification.service.NotificationNotFoundException;
import com.ieum.gw.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * 알림 목록과 읽음 처리. 본인 알림만 다루고, 업체·계정은 로그인 정보에서만 꺼낸다(AGENTS.md).
 * 화면 JSP·JS는 프론트가 만들고, 주소·요청·응답은 여기서 정한다(AGENTS.md '수정 범위').
 * 헤더 배지 수는 /badges(common)가 NotificationService.countUnread로 받는다.
 * 만료 업체의 읽음 처리는 공통 만료 읽기 전용 검사가 막는다(AGENTS.md '권한·보안').
 */
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    // 알림 목록 화면
    @GetMapping("/notification/list")
    public String list() {
        return "notification/list";
    }

    // 성공 200 {"success": true, "data": {"page", "pages", "total", "items": [NotificationDTO]}}
    @GetMapping("/notification/api/list")
    @ResponseBody
    public Map<String, Object> items(@AuthenticationPrincipal LoginUser user,
                                     @RequestParam(defaultValue = "1") int page) {
        return Map.of("success", true,
                "data", notificationService.list(user.getCompanyId(), user.getUserAccountId(), page));
    }

    // 읽음 처리. 성공 200 {"success": true}, 없거나 남의 알림 404 {"message"}
    @PostMapping("/notification/api/{notificationId}/read")
    @ResponseBody
    public Map<String, Object> read(@AuthenticationPrincipal LoginUser user, @PathVariable Long notificationId) {
        notificationService.markRead(user.getCompanyId(), user.getUserAccountId(), notificationId);
        return Map.of("success", true);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    // 페이지·알림 번호에 숫자가 아닌 값이 왔다
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, String>> badValue(TypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청 값을 읽을 수 없습니다."));
    }

    // 그 밖의 오류는 원인을 로그에만 남기고 화면에는 같은 문구를 보인다(DB 오류 내용이 드러나지 않게)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> failed(RuntimeException e) {
        logger.error("알림 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "알림을 처리하지 못했습니다. 잠시 뒤 다시 해 주세요."));
    }
}
