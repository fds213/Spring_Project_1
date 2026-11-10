package com.ieum.gw.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.SupportInquiryDTO;
import com.ieum.gw.dto.SupportInquiryFormDTO;
import com.ieum.gw.dto.SupportReplyFormDTO;
import com.ieum.gw.service.SupportFormException;
import com.ieum.gw.service.SupportNotFoundException;
import com.ieum.gw.service.SupportService;

import lombok.RequiredArgsConstructor;

/**
 * 이음 장애 문의 최소판(OPS-04 중 문의와 답변만). /support/**는 로그인한 직원, /support/admin/**는 업체 관리자, /ops/**는 운영자만 security-context.xml이 통과시킨다.
 * 업체·작성자는 로그인 정보에서만 꺼낸다. 만료 업체의 등록은 공통 만료 읽기 전용 검사가 막는다.
 * 성공 200 {"success": true, "data": ...}, 실패는 상태 코드와 {"message"}.
 */
@Controller
@RequiredArgsConstructor
public class SupportController {

    private static final Logger logger = LoggerFactory.getLogger(SupportController.class);

    private final SupportService supportService;

    /** 직원: 문의 쓰기와 '내가 보낸 문의' 화면 */
    @GetMapping("/support/inquiry")
    public String inquiry() {
        return "support/inquiry";
    }

    /** 운영자: 회원 접수 화면 */
    @GetMapping("/ops/inquiry-member")
    public String inquiryMember() {
        return "ops/inquiry-member";
    }

    /** 직원: 내가 보낸 문의와 답변 */
    @GetMapping("/support/api/inquiries")
    @ResponseBody
    public Map<String, Object> myInquiries(@AuthenticationPrincipal LoginUser user) {
        List<SupportInquiryDTO> inquiries = supportService.listMine(user.getCompanyId(), user.getEmployeeId());
        return ok(inquiries);
    }

    /** 직원: 문의를 보낸다. data는 {"no": 접수번호} */
    @PostMapping("/support/api/inquiries")
    @ResponseBody
    public Map<String, Object> create(@AuthenticationPrincipal LoginUser user, @RequestBody SupportInquiryFormDTO form) {
        Long no = supportService.createInquiry(user.getCompanyId(), user.getEmployeeId(), form);
        return ok(Map.of("no", no));
    }

    /** 업체 관리자: 우리 회사 직원들이 보낸 문의와 답변 */
    @GetMapping("/support/admin/api/inquiries")
    @ResponseBody
    public Map<String, Object> companyInquiries(@AuthenticationPrincipal LoginUser user) {
        List<SupportInquiryDTO> inquiries = supportService.listCompany(user.getCompanyId());
        return ok(inquiries);
    }

    /** 운영자: 모든 업체의 문의와 답변 */
    @GetMapping("/ops/api/member-inquiries")
    @ResponseBody
    public Map<String, Object> memberInquiries() {
        List<SupportInquiryDTO> inquiries = supportService.listAll();
        return ok(inquiries);
    }

    /** 운영자: 답변을 쓴다. data는 답변이 반영된 문의 */
    @PostMapping("/ops/api/member-inquiries/{no}/replies")
    @ResponseBody
    public Map<String, Object> reply(@AuthenticationPrincipal LoginUser user, @PathVariable Long no,
                                     @RequestBody SupportReplyFormDTO form) {
        SupportInquiryDTO inquiry = supportService.reply(no, form, user.getUserAccountId());
        return ok(inquiry);
    }

    /** 운영자: 쓴 답변을 고친다. data는 답변이 반영된 문의 */
    @PostMapping("/ops/api/member-inquiries/{no}/replies/{replyId}")
    @ResponseBody
    public Map<String, Object> editReply(@PathVariable Long no, @PathVariable Long replyId,
                                         @RequestBody SupportReplyFormDTO form) {
        SupportInquiryDTO inquiry = supportService.editReply(no, replyId, form);
        return ok(inquiry);
    }

    /** 성공 응답으로 감싼다 */
    private static Map<String, Object> ok(Object data) {
        return Map.of("success", true, "data", data);
    }

    /** 입력 규칙에 맞지 않을 때 400 */
    @ExceptionHandler(SupportFormException.class)
    public ResponseEntity<Map<String, String>> badForm(SupportFormException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** 없는 접수번호일 때 404 */
    @ExceptionHandler(SupportNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(SupportNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    /** JSON이 깨졌거나 번호 칸에 글자가 왔을 때 400 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청을 읽을 수 없습니다. 화면을 새로 고쳐 다시 시도해 주세요."));
    }

    /** 그 밖의 오류는 원인을 로그에만 남긴다(DB 오류 내용이 드러나지 않게) */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> failed(RuntimeException e) {
        logger.error("이음 장애 문의 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
