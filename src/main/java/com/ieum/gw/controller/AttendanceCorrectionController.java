package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.AttendanceCorrectionService;
import com.ieum.gw.service.AttendanceException;

import lombok.RequiredArgsConstructor;

/**
 * 근태 정정 요청 API (/attendance/api/corrections/**). 로그인한 직원 누구나 부른다.
 * 처리(승인·반려)할 수 있는지는 Service가 요청마다 본다: 요청자 부서장, 없거나 부서장 본인이면 업체 관리자.
 * 업체·직원은 로그인 정보에서만 정한다.
 */
@Controller
@RequiredArgsConstructor
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService service;

    /** 내 그 달 정정 요청 (month = YYYY-MM) */
    @GetMapping("/attendance/api/corrections")
    @ResponseBody
    public Map<String, Object> mine(@AuthenticationPrincipal LoginUser user, @RequestParam String month) {
        return service.myCorrections(user, month);
    }

    /** 정정 요청 { date, inTime(결근만), outTime, reason }. 근무일로부터 7일까지 */
    @PostMapping("/attendance/api/corrections")
    @ResponseBody
    public Map<String, Object> request(@AuthenticationPrincipal LoginUser user, @RequestBody Map<String, Object> body) {
        return service.request(user, str(body.get("date")), str(body.get("inTime")), str(body.get("outTime")), str(body.get("reason")));
    }

    /** 처리 전인 내 요청 취소 */
    @PostMapping("/attendance/api/corrections/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancel(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return service.cancel(user, id);
    }

    /** 내가 처리할 대기 요청 (부서장·업체 관리자) */
    @GetMapping("/attendance/api/corrections/inbox")
    @ResponseBody
    public Map<String, Object> inbox(@AuthenticationPrincipal LoginUser user) {
        return service.inbox(user);
    }

    /** 승인: 요청한 퇴근 시각을 그날 근태에 반영 */
    @PostMapping("/attendance/api/corrections/{id}/approve")
    @ResponseBody
    public Map<String, Object> approve(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return service.approve(user, id);
    }

    /** 반려 { reason } */
    @PostMapping("/attendance/api/corrections/{id}/reject")
    @ResponseBody
    public Map<String, Object> reject(@AuthenticationPrincipal LoginUser user, @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        return service.reject(user, id, str(body.get("reason")));
    }

    /** 요청 본문 값을 글자로 (없으면 null) */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 거절은 상태 코드 + { message } (화면은 message 를 그대로 보여 준다) */
    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<Map<String, String>> rejected(AttendanceException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
