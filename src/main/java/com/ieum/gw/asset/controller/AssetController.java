package com.ieum.gw.asset.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.asset.service.AssetService;
import com.ieum.gw.common.dto.LoginUser;
import lombok.RequiredArgsConstructor;

/** 현재 지급 자산과 본인 지급·회수 이력 조회. 업체·사원은 인증 정보에서만 가져온다. */
@Controller
@RequiredArgsConstructor
public class AssetController {
    private static final Logger logger = LoggerFactory.getLogger(AssetController.class);
    private final AssetService assetService;

    // 지급 대기 티켓을 합치는 /asset/api/assets 응답은 티켓 연동 뒤 연결한다.
    @GetMapping("/asset/api/assets/assigned")
    @ResponseBody
    public Map<String, Object> assigned(@AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", assetService.getMyAssets(user));
    }

    @GetMapping("/asset/api/assets/{assetId}/assignments")
    @ResponseBody
    public Map<String, Object> assignments(@AuthenticationPrincipal LoginUser user, @PathVariable Long assetId) {
        return Map.of("success", true, "data", assetService.getMyAssignments(user, assetId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> rejected(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("success", false, "message", e.getReason()));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidId(TypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "자산 번호가 올바르지 않습니다."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> failed(RuntimeException e) {
        logger.error("자산 조회 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "자산 정보를 불러오지 못했습니다. 잠시 뒤 다시 해 주세요."));
    }
}
