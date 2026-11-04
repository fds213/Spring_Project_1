package com.ieum.gw.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.dto.OpsApplicationDTO;
import com.ieum.gw.dto.OpsCompanyDTO;
import com.ieum.gw.dto.OpsCompanyDetailDTO;
import com.ieum.gw.dto.OpsGuestInquiryDTO;
import com.ieum.gw.service.OpsNotFoundException;
import com.ieum.gw.service.OpsService;

import lombok.RequiredArgsConstructor;

/**
 * 운영자 화면. /ops/**는 security-context.xml이 ROLE_OPS만 통과시킨다.
 * 화면 JSP·JS는 프론트가 만들었고, 화면 JS가 부르는 주소·응답은 여기서 정한다(AGENTS.md '수정 범위').
 * 성공 200 {"success": true, "data": ...}, 실패는 상태 코드와 {"message"}.
 */
@Controller
@RequiredArgsConstructor
public class OpsController {

    private static final Logger logger = LoggerFactory.getLogger(OpsController.class);

    private final OpsService opsService;

    // 운영자가 로그인하면 처음 보는 화면(LoginSuccessHandler)
    @GetMapping("/ops/company")
    public String company() {
        return "ops/company";
    }

    @GetMapping("/ops/application-status")
    public String applicationStatus() {
        return "ops/application";
    }

    @GetMapping("/ops/inquiry-guest")
    public String inquiryGuest() {
        return "ops/inquiry-guest";
    }

    @GetMapping("/ops/api/companies")
    @ResponseBody
    public Map<String, Object> companies() {
        List<OpsCompanyDTO> companies = opsService.listCompanies();
        return ok(companies);
    }

    @GetMapping("/ops/api/companies/{code}")
    @ResponseBody
    public Map<String, Object> company(@PathVariable String code) {
        OpsCompanyDetailDTO company = opsService.getCompany(code);
        return ok(company);
    }

    @GetMapping("/ops/api/applications")
    @ResponseBody
    public Map<String, Object> applications() {
        List<OpsApplicationDTO> applications = opsService.listApplications();
        return ok(applications);
    }

    @GetMapping("/ops/api/guest-inquiries")
    @ResponseBody
    public Map<String, Object> guestInquiries() {
        List<OpsGuestInquiryDTO> inquiries = opsService.listGuestInquiries();
        return ok(inquiries);
    }

    private static Map<String, Object> ok(Object data) {
        return Map.of("success", true, "data", data);
    }

    @ExceptionHandler(OpsNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(OpsNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    // 원인은 로그에만 남긴다(DB 오류 내용이 드러나지 않게)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> failed(RuntimeException e) {
        logger.error("운영자 화면 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
