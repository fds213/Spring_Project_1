package com.ieum.gw.controller;

import java.math.BigDecimal;
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

import com.ieum.gw.service.AttendanceException;
import com.ieum.gw.service.AttendanceService;
import com.ieum.gw.service.HolidayService;
import com.ieum.gw.service.LeaveService;
import com.ieum.gw.dto.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 근무 > 근태·연차 화면과 로그인한 본인의 근태 API (/attendance/api/**),
 * 업체 관리자·인사 담당의 근태 단말기·근태 관리·휴일 관리 (/attendance/admin/**).
 * 업체·직원은 로그인 정보에서만 정한다. 다른 업체·직원의 자료는 주소로 고를 수 없다.
 */
@Controller
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;
    private final LeaveService leaveService;
    private final HolidayService holidayService;

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

    /** 연차 화면: 연차 현황(15일 기준)과 휴가 신청 내역 */
    @GetMapping("/attendance/api/leaves")
    @ResponseBody
    public Map<String, Object> leaves(@AuthenticationPrincipal LoginUser user) {
        return leaveService.leaves(user.getCompanyId(), user.getEmployeeId());
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

    // ===== 근무 > 근태 단말기 (Security: /attendance/admin/** 는 업체 관리자·인사 담당만) =====

    @GetMapping("/attendance/admin/device")
    public String deviceAdminPage() {
        return "attendance/admin/device";
    }

    @GetMapping("/attendance/admin/api/device")
    @ResponseBody
    public Map<String, Object> deviceAdmin(@AuthenticationPrincipal LoginUser user) {
        return service.deviceAdmin(user.getCompanyId());
    }

    /** 단말기 키 발급·다시 발급 (키 원문은 이 응답에서 한 번만 보인다) */
    @PostMapping("/attendance/admin/api/device/key")
    @ResponseBody
    public Map<String, Object> issueKey(@AuthenticationPrincipal LoginUser user) {
        return service.issueDeviceKey(user.getCompanyId());
    }

    @PostMapping("/attendance/admin/api/device/links")
    @ResponseBody
    public Map<String, Object> adminLink(@AuthenticationPrincipal LoginUser user, @RequestBody Map<String, Object> body) {
        return service.adminLinkDevice(user.getCompanyId(), toLong(body.get("employeeId")), (String) body.get("userNo"));
    }

    @PostMapping("/attendance/admin/api/device/links/delete")
    @ResponseBody
    public Map<String, Object> adminUnlink(@AuthenticationPrincipal LoginUser user, @RequestBody Map<String, Object> body) {
        return service.adminUnlinkDevice(user.getCompanyId(), toLong(body.get("employeeId")));
    }

    // ===== 인사 관리 > 근태·연차 관리, 휴일 관리 (업체 관리자·인사 담당) =====

    @GetMapping("/attendance/admin/list")
    public String overviewPage() {
        return "attendance/admin/list";
    }

    /** 직원별 오늘 출퇴근, 그 달 출근일·지각·조퇴, 남은 연차 */
    @GetMapping("/attendance/admin/api/overview")
    @ResponseBody
    public Map<String, Object> overview(@AuthenticationPrincipal LoginUser user, @RequestParam String month) {
        return service.overview(user.getCompanyId(), month);
    }

    /** 직원 한 명의 연차 보유·남은 일수와 추가 부여 내역 (최근 순) */
    @GetMapping("/attendance/admin/api/leaves/{employeeId}/grants")
    @ResponseBody
    public Map<String, Object> leaveGrants(@AuthenticationPrincipal LoginUser user, @PathVariable Long employeeId) {
        return leaveService.grantHistory(user.getCompanyId(), employeeId);
    }

    /** 연차 추가 부여(+)·되돌리기(-) { days, note }. 업체 관리자·인사 담당, 본인 연차는 안 된다 */
    @PostMapping("/attendance/admin/api/leaves/{employeeId}/grants")
    @ResponseBody
    public Map<String, Object> grantLeave(@AuthenticationPrincipal LoginUser user, @PathVariable Long employeeId,
                                          @RequestBody Map<String, Object> body) {
        Object note = body.get("note");
        return leaveService.grantExtraLeave(user, employeeId, toDays(body.get("days")), note == null ? null : String.valueOf(note));
    }

    @GetMapping("/attendance/admin/holiday")
    public String holidayPage() {
        return "attendance/admin/holiday";
    }

    @GetMapping("/attendance/admin/api/holidays")
    @ResponseBody
    public Map<String, Object> holidays(@AuthenticationPrincipal LoginUser user, @RequestParam int year) {
        return holidayService.list(user.getCompanyId(), year);
    }

    @PostMapping("/attendance/admin/api/holidays")
    @ResponseBody
    public Map<String, Object> addHoliday(@AuthenticationPrincipal LoginUser user, @RequestBody Map<String, String> body) {
        return holidayService.add(user.getCompanyId(), body.get("date"), body.get("name"));
    }

    @PostMapping("/attendance/admin/api/holidays/{id}/delete")
    @ResponseBody
    public Map<String, Object> deleteHoliday(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return holidayService.delete(user.getCompanyId(), id);
    }

    /** 그 해 법정 공휴일을 한 번에 넣는다 (이미 있는 날은 건너뛴다) */
    @PostMapping("/attendance/admin/api/holidays/preset")
    @ResponseBody
    public Map<String, Object> addPresetHolidays(@AuthenticationPrincipal LoginUser user, @RequestBody Map<String, Object> body) {
        Long year = toLong(body.get("year"));
        return holidayService.addPreset(user.getCompanyId(), year == null ? null : year.intValue());
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new AttendanceException(400, "값이 올바르지 않습니다");
        }
    }

    /** 일수 값 (숫자·문자 모두 받는다). 비었으면 null, 숫자가 아니면 400 */
    private static BigDecimal toDays(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new AttendanceException(400, "일수는 숫자로 넣어 주세요");
        }
    }

    /** 거절은 상태 코드 + { message } (화면은 message 를 그대로 보여 준다) */
    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<Map<String, String>> rejected(AttendanceException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
