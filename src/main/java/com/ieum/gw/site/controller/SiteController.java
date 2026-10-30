package com.ieum.gw.site.controller;

import com.ieum.gw.site.dto.TrialApplyDTO;
import com.ieum.gw.site.dto.TrialResultDTO;
import com.ieum.gw.site.service.SiteService;
import com.ieum.gw.site.service.TrialFormException;
import com.ieum.gw.subscription.service.CompanyCodeExhaustedException;
import com.ieum.gw.subscription.service.TrialEmailUsedException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 상품 홈페이지(로그인 없이 여는 주소: '/'는 그 주소만, '/trial'은 하위 주소까지, security-context.xml).
 * 화면 JSP는 프론트가 만들었고, 화면 JS가 부르는 주소·요청·응답은 여기서 정한다(AGENTS.md '수정 범위').
 */
@Controller
@RequiredArgsConstructor
public class SiteController {

    private static final Logger logger = LoggerFactory.getLogger(SiteController.class);

    private final SiteService siteService;

    // 상품 소개 (WEB-01)
    @GetMapping("/")
    public String intro() {
        return "site/intro";
    }

    // 무료 체험 신청 화면 (ACC-01)
    @GetMapping("/trial")
    public String trial() {
        return "site/trial";
    }

    /**
     * 무료 체험 신청. 화면 JS가 JSON 본문으로 보낸다(docs/front-screen-api.md 2장).
     * 성공 200 {"success": true, "message", "data": TrialResultDTO}.
     * 실패 400 입력 오류 {"message", "field"}, 409 이미 쓴 이메일, 503 업체 코드 소진, 500 그 밖 {"message"}.
     */
    @PostMapping("/trial/api/apply")
    @ResponseBody
    public Map<String, Object> apply(@RequestBody TrialApplyDTO form) {
        TrialResultDTO result = siteService.applyTrial(form);
        logger.info("체험 신청: companyCode={}, employeeNo={}", result.getCompanyCode(), result.getEmployeeNo());
        return Map.of("success", true, "message", "무료 체험을 시작했습니다", "data", result);
    }

    @ExceptionHandler(TrialFormException.class)
    public ResponseEntity<Map<String, String>> badForm(TrialFormException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "field", e.getField()));
    }

    // JSON이 깨졌거나 인원 칸에 숫자가 아닌 값이 왔다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "신청 내용을 읽을 수 없습니다. 입력한 값을 확인해 주세요."));
    }

    @ExceptionHandler(TrialEmailUsedException.class)
    public ResponseEntity<Map<String, String>> emailUsed(TrialEmailUsedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage(), "field", "email"));
    }

    @ExceptionHandler(CompanyCodeExhaustedException.class)
    public ResponseEntity<Map<String, String>> codeExhausted(CompanyCodeExhaustedException e) {
        logger.error("업체 코드 소진: 10번 뽑아 모두 겹침");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }

    // 그 밖의 오류는 원인을 로그에만 남기고 화면에는 같은 문구를 보인다(DB 오류 내용이 드러나지 않게)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> failed(RuntimeException e) {
        logger.error("체험 신청 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "신청을 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
