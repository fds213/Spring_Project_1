package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.ScheduleSaveDTO;
import com.ieum.gw.service.ScheduleService;

import lombok.RequiredArgsConstructor;

// 일정 Controller. 화면 주소 → JSP는 docs/front-screen-api.md 1장, 데이터 주소는 calendar.js가 부르는 /schedule/api/... 를 따른다.
@Controller
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // ---------- 화면 ----------

    // 일정 달력: WEB-INF/views/schedule/calendar.jsp
    @GetMapping("/calendar")
    public String calendar() {
        return "schedule/calendar";
    }

    // ---------- 데이터 (calendar.js가 부른다) ----------

    // 한 달 치: GET /schedule/api/schedules?month=2026-11
    @GetMapping("/api/schedules")
    @ResponseBody
    public Map<String, Object> month(@AuthenticationPrincipal LoginUser loginUser,
                                     @RequestParam(required = false) String month) {
        return scheduleService.getMonth(loginUser, month);
    }

    // 일정 추가: POST /schedule/api/schedules  (본문 JSON: { scope, date, title, memo })
    @PostMapping("/api/schedules")
    @ResponseBody
    public Map<String, Object> add(@AuthenticationPrincipal LoginUser loginUser,
                                   @RequestBody ScheduleSaveDTO form) {
        return scheduleService.addSchedule(loginUser, form.getScope(), form.getDate(), form.getTitle(), form.getMemo());
    }

    // 일정 수정: POST /schedule/api/schedules/3  (본문 JSON: { date, title, memo }. 구분은 바꾸지 않는다)
    @PostMapping("/api/schedules/{id}")
    @ResponseBody
    public Map<String, Object> update(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable Long id,
                                      @RequestBody ScheduleSaveDTO form) {
        return scheduleService.updateSchedule(loginUser, id, form.getDate(), form.getTitle(), form.getMemo());
    }

    // 일정 삭제: POST /schedule/api/schedules/3/delete
    @PostMapping("/api/schedules/{id}/delete")
    @ResponseBody
    public Map<String, Object> delete(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable Long id) {
        return scheduleService.deleteSchedule(loginUser, id);
    }

    // ---------- 오류 응답 ----------

    // Service가 던진 400·403·404를 화면이 읽는 모양 { "message": "안내 문구" }으로 바꾼다 (이 Controller 안에서만)
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        String message = (e.getReason() != null) ? e.getReason() : "요청을 처리하지 못했습니다";
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", message));
    }

    // 값이 잘못됨 → 400
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleBadValue(IllegalArgumentException e) {
        String message = (e.getMessage() != null) ? e.getMessage() : "값이 잘못되었습니다";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
