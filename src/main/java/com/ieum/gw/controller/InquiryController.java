package com.ieum.gw.controller;

import com.ieum.gw.dto.InquiryResultDTO;
import com.ieum.gw.dto.InquirySubmitDTO;
import com.ieum.gw.service.InquiryFormException;
import com.ieum.gw.service.InquiryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 도입 문의 접수(WEB-02, 로그인 없이 부른다). 화면 주소 GET /inquiry는 SiteController에 있고,
 * 주소·요청 값은 화면 JS(site/inquiry.js)가 이미 부르는 대로다.
 */
@Controller
@RequiredArgsConstructor
public class InquiryController {

    private static final Logger logger = LoggerFactory.getLogger(InquiryController.class);

    private final InquiryService inquiryService;

    /**
     * 성공 200 {"success": true, "message", "data": {"no", "email"}}.
     * 실패 400 입력 오류 {"message", "field"}, 500 그 밖 {"message"}.
     */
    @PostMapping("/inquiry/api/submit")
    @ResponseBody
    public Map<String, Object> submit(@RequestBody InquirySubmitDTO form) {
        InquiryResultDTO result = inquiryService.submit(form);
        // no=null이면 봇 막기 칸에 걸려 저장하지 않은 요청이다
        logger.info("도입 문의 접수: no={}", result.getNo());
        return Map.of("success", true, "message", "문의를 접수했습니다", "data", result);
    }

    @ExceptionHandler(InquiryFormException.class)
    public ResponseEntity<Map<String, String>> badForm(InquiryFormException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "field", e.getField()));
    }

    // JSON이 깨졌거나 글자 칸에 객체·배열이 왔다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "문의 내용을 읽을 수 없습니다. 입력한 값을 확인해 주세요."));
    }

    // 그 밖의 오류는 원인을 로그에만 남기고 화면에는 같은 문구를 보인다(DB 오류 내용이 드러나지 않게)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> failed(RuntimeException e) {
        logger.error("도입 문의 접수 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "문의를 접수하지 못했습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
