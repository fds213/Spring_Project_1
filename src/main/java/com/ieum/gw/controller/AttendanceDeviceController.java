package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ieum.gw.service.AttendanceException;
import com.ieum.gw.service.AttendanceService;

import lombok.RequiredArgsConstructor;

/**
 * 지문 단말기(시뮬레이터) 전용 API. 로그인 세션 대신 X-Device-Key 헤더의 단말기 키로 업체를 확인한다.
 * Security 는 /api/** 를 별도 체인(세션·CSRF 없음)으로 통과시키고, 키 확인은 Service 가 요청마다 한다.
 *
 *   GET  /api/attendance/device   → { companyName, deviceName, people: [{ userNo, name, dept, serving }] }
 *   POST /api/attendance/punch    { userNo, type: IN | OUT } → { name, userNo, type, date, time, status }
 *        401 키 없음·틀림, 400 값 오류, 403 만료 업체·재직 아님, 404 등록 안 된 번호, 409 중복·출근 없는 퇴근
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceDeviceController {

    private static final String KEY_HEADER = "X-Device-Key";

    private final AttendanceService service;

    @GetMapping("/device")
    public Map<String, Object> device(@RequestHeader(value = KEY_HEADER, required = false) String key) {
        return service.deviceInfo(key);
    }

    @PostMapping("/punch")
    public Map<String, Object> punch(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                     @RequestBody Map<String, String> body) {
        return service.punch(key, body.get("userNo"), body.get("type"));
    }

    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<Map<String, String>> rejected(AttendanceException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
