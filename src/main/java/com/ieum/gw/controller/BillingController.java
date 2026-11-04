package com.ieum.gw.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.ConfirmFormDTO;
import com.ieum.gw.dto.FailFormDTO;
import com.ieum.gw.dto.OrderFormDTO;
import com.ieum.gw.service.BillingException;
import com.ieum.gw.service.PaymentService;

import lombok.RequiredArgsConstructor;

/**
 * 이용 결제. 역할만으로 정해지지 않는 기능이라 관리 화면 주소 밖에 두고, Service가 요청마다 사람을 확인한다(AGENTS.md 'URL·요청 방식').
 * 성공 200 {"success": true, "data": ...}, 실패는 상태 코드와 {"message", "retry"}. retry가 true면 같은 요청을 다시 보내도 된다.
 */
@Controller
@RequiredArgsConstructor
public class BillingController {

    private static final Logger logger = LoggerFactory.getLogger(BillingController.class);

    private final PaymentService paymentService;

    // 이용 결제 화면. 토스 결제창이 돌아오는 두 주소도 같은 화면이고, 아무것도 바꾸지 않는다:
    // 화면 JS가 돌아온 값을 POST로 보내야 처리한다(GET은 아무것도 바꾸지 않는다, AGENTS.md)
    @GetMapping({"/subscription/billing", "/subscription/billing/success", "/subscription/billing/fail"})
    public String billing() {
        return "subscription/billing";
    }

    @GetMapping("/subscription/api/billing")
    @ResponseBody
    public Map<String, Object> state(@AuthenticationPrincipal LoginUser user) {
        return ok(paymentService.getBilling(user));
    }

    @GetMapping("/subscription/api/quote")
    @ResponseBody
    public Map<String, Object> quote(@AuthenticationPrincipal LoginUser user,
                                     @RequestParam String plan, @RequestParam int users) {
        return ok(paymentService.quote(user, plan, users));
    }

    // 주문 만들기: 서버가 금액을 정해 저장하고, 화면 JS는 받은 값으로 토스 결제창을 연다
    @PostMapping("/subscription/api/orders")
    @ResponseBody
    public Map<String, Object> createOrder(@AuthenticationPrincipal LoginUser user, @RequestBody OrderFormDTO form) {
        return ok(paymentService.createOrder(user, form));
    }

    // 결제창이 successUrl로 돌아온 뒤 승인 확인·반영
    @PostMapping("/subscription/api/orders/confirm")
    @ResponseBody
    public Map<String, Object> confirm(@AuthenticationPrincipal LoginUser user, @RequestBody ConfirmFormDTO form) {
        return ok(paymentService.confirm(user, form));
    }

    // 결제창이 failUrl로 돌아왔을 때 실패 기록
    @PostMapping("/subscription/api/orders/fail")
    @ResponseBody
    public Map<String, Object> fail(@AuthenticationPrincipal LoginUser user, @RequestBody FailFormDTO form) {
        paymentService.reportFailure(user, form);
        return Map.of("success", true);
    }

    private static Map<String, Object> ok(Object data) {
        return Map.of("success", true, "data", data);
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<Map<String, Object>> rejected(BillingException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage(), "retry", e.isRetry()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청 내용을 읽을 수 없습니다. 입력한 값을 확인해 주세요.", "retry", false));
    }

    // 원인은 로그에만 남긴다. 승인 뒤 저장이 실패했을 수 있으니 다시 보내도 된다고 알린다(같은 주문은 한 번만 반영된다)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> failed(RuntimeException e) {
        logger.error("결제 처리 실패", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("message", "결제를 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.", "retry", true));
    }
}
