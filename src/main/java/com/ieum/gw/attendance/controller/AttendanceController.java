package com.ieum.gw.attendance.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.attendance.service.AttendanceException;
import com.ieum.gw.attendance.service.AttendanceService;
import com.ieum.gw.common.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 근무 > 근태·연차 화면과, 로그인한 본인의 근태 API (/attendance/api/**).
 * 업체·직원은 로그인 정보에서만 정한다. 다른 업체·직원의 자료는 주소로 고를 수 없다.
 */
@Controller
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;

    // ===== 화면 =====

    @GetMapping("/attendance/my")
    public String my() {
        return "attendance/my";
    }

    @GetMapping("/attendance/leave")
    public String leave() {
        return "attendance/leave";
    }

    /** 지문 단말기 시뮬레이터 (로그인 없이 연다. 기록은 단말기 키로 /api/attendance/** 에 보낸다) */
    @GetMapping("/attendance/simulator")
    public String simulator() {
        return "attendance/simulator";
    }

    // ===== 본인 근태 API =====

    @GetMapping("/attendance/api/records")
    @ResponseBody
    public Map<String, Object> records(@AuthenticationPrincipal LoginUser user, @RequestParam String month) {
        return service.records(user.getCompanyId(), user.getEmployeeId(), month);
    }

    @PostMapping("/attendance/api/device-link")
    @ResponseBody
    public Map<String, Object> link(@AuthenticationPrincipal LoginUser user, @RequestBody Map<String, String> body) {
        return service.linkDevice(user.getCompanyId(), user.getEmployeeId(), body.get("deviceId"));
    }

    @PostMapping("/attendance/api/device-link/delete")
    @ResponseBody
    public Map<String, Object> unlink(@AuthenticationPrincipal LoginUser user) {
        return service.unlinkDevice(user.getCompanyId(), user.getEmployeeId());
    }

    /** 거절은 상태 코드 + { message } (화면은 message 를 그대로 보여 준다) */
    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<Map<String, String>> rejected(AttendanceException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
