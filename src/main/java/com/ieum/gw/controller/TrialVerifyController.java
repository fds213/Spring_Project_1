package com.ieum.gw.controller;

import java.util.Map;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.service.TrialVerifyException;
import com.ieum.gw.service.TrialVerifyService;

import lombok.RequiredArgsConstructor;

/**
 * 체험 신청 이메일 인증(ACC-06, 로그인 없이 여는 /trial 아래). 링크를 열면 화면만 보이고, 버튼이 POST로 인증한다(GET으로 바꾸지 않는다).
 * 실패 응답은 {"message"}이고 상태는 TrialVerifyException을 따른다.
 */
@Controller
@RequiredArgsConstructor
public class TrialVerifyController {

    private static final Logger logger = LoggerFactory.getLogger(TrialVerifyController.class);

    private final TrialVerifyService verifyService;

    // 인증 링크의 확인 화면. 링크 값은 # 뒤에 있어 서버로 오지 않고 화면 JS가 읽는다
    @GetMapping("/trial/verify")
    public String page() {
        return "site/verify";
    }

    /** 요청 {"token"} → 200 {"message", "alreadyVerified"} */
    @PostMapping("/trial/api/verify")
    @ResponseBody
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        boolean verified = verifyService.verify(body.get("token"));
        return Map.of("alreadyVerified", !verified,
                "message", verified ? "이메일을 인증했습니다. 이제 로그인할 수 있습니다." : "이미 인증을 마쳤습니다. 로그인해 주세요.");
    }

    /**
     * 인증 메일 다시 보내기. 체험을 신청한 세션이나 인증 전 업체의 비밀번호를 맞힌 세션의 업체에만 보낸다.
     * 200 {"message", "alreadyVerified"}, 그 밖은 {"message"}.
     */
    @PostMapping("/trial/api/verify/resend")
    @ResponseBody
    public Map<String, Object> resend(HttpSession session) {
        Object companyId = session.getAttribute(TrialVerifyService.SESSION_COMPANY_ID);
        if (!(companyId instanceof Long)) {
            throw new TrialVerifyException(400, "로그인 화면에서 업체 코드·사번·비밀번호를 입력한 뒤 다시 눌러 주세요.");
        }
        boolean sent = verifyService.resend((Long) companyId);
        return Map.of("alreadyVerified", !sent,
                "message", sent ? "인증 메일을 다시 보냈습니다. 받은 편지함과 스팸함을 확인해 주세요." : "이미 인증을 마쳤습니다. 로그인해 주세요.");
    }

    @ExceptionHandler(TrialVerifyException.class)
    public ResponseEntity<Map<String, String>> rejected(TrialVerifyException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "인증 링크가 올바르지 않습니다. 메일의 링크를 다시 열어 주세요."));
    }

    // 원인은 로그에만 남긴다
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> failed(RuntimeException e) {
        logger.error("이메일 인증 처리 실패", e);
        return ResponseEntity.internalServerError().body(Map.of("message", "처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
