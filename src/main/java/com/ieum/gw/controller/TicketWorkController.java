package com.ieum.gw.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.TicketRequestDTO;
import com.ieum.gw.service.TicketWorkService;
import lombok.RequiredArgsConstructor;

/** form-options와 기존 원시 댓글·이력 조회 경로는 다른 Controller에서 유지한다. */
@Controller
@RequiredArgsConstructor
public class TicketWorkController {
    private static final Logger logger = LoggerFactory.getLogger(TicketWorkController.class);
    private final TicketWorkService service;

    @GetMapping("/asset/api/assets")
    @ResponseBody
    public Map<String, Object> myEquipment(@AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.myEquipment(user));
    }

    @GetMapping("/ticket/admin/list")
    public String adminList() { return "ticket/admin/list"; }

    /** 관리자 티켓 목록을 기존 방식 또는 검색·페이징 방식으로 조회한다. */
    @GetMapping("/ticket/admin/api/tickets")
    @ResponseBody
    public Map<String, Object> adminList(@AuthenticationPrincipal LoginUser user, 
    										@RequestParam(name = "filter", required = false) String filter,
    										@RequestParam(name = "keyword", required = false) String keyword,
    										@RequestParam(name = "page", required = false) Long page) {
    	Map<String, Object> data = keyword == null && page == null 
    										? service.listAdmin(user, filter) : service.searchAdmin(user, filter, keyword, page);
    	
        return Map.of("success", true, "data", data);
    }

    @GetMapping("/ticket/admin/api/tickets/{id}")
    @ResponseBody
    public Map<String, Object> adminDetail(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return Map.of("success", true, "data", service.detailAdmin(user, id));
    }

    @GetMapping("/ticket/api/tickets")
    @ResponseBody
    public Map<String, Object> mine(@AuthenticationPrincipal LoginUser user) {
        return Map.of("success", true, "data", service.listMine(user));
    }

    @PostMapping("/ticket/api/tickets")
    @ResponseBody
    public Map<String, Object> create(@AuthenticationPrincipal LoginUser user, @RequestBody TicketRequestDTO form) {
        return Map.of("success", true, "data", Map.of("id", service.create(user, form)));
    }
    
    /** 장애 접수에 사용할 사진을 업로드한다. */
    @PostMapping(value = "/ticket/api/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> uploadPhoto(@AuthenticationPrincipal LoginUser user, @RequestParam("photo") MultipartFile photo) {
    	Long attachmentId = service.uploadPhoto(user, photo);
    	
    	return Map.of("success", true, "data", Map.of("attachmentId", attachmentId));
    }
    
    /** 요청자가 본인 티켓에 연결된 사진을 연다. */
    @GetMapping("/ticket/api/tickets/{id}/photos/{attachmentId}")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @PathVariable Long attachmentId) {
    	return photoResponse(service.loadPhoto(user, id, attachmentId, false));
    }
    
    /** IT 담당자 또는 관리자가 같은 업체 티켓의 사진을 연다. */
    @GetMapping("/ticket/admin/api/tickets/{id}/photos/{attachmentId}")
    public ResponseEntity<Resource> adminPhoto(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @PathVariable Long attachmentId) {
    	return photoResponse(service.loadPhoto(user, id, attachmentId, true));
    }
    
    /** 사진을 브라우저가 표시할 수 있는 형식으로 응답한다. */
    private ResponseEntity<Resource> photoResponse(Resource photo) {
    	MediaType contentType = MediaTypeFactory.getMediaType(photo).orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok().contentType(contentType).cacheControl(CacheControl.noStore()).body(photo);
    }

    @PostMapping("/ticket/api/tickets/{id}/comments")
    @ResponseBody
    public Map<String, Object> comment(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.comment(user, id, form, false);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/api/tickets/{id}/close")
    @ResponseBody
    public Map<String, Object> close(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.close(user, id, form);
        return Map.of("success", true);
    }

    /** 로그인한 요청자의 장애·문의 티켓 취소를 처리한다. */
    @PostMapping("/ticket/api/tickets/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancel(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
    	service.cancel(user, id, form);
    	
    	return Map.of("success", true);
    }
    
    @PostMapping("/ticket/admin/api/tickets/{id}/assign")
    @ResponseBody
    public Map<String, Object> assign(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.assign(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/start")
    @ResponseBody
    public Map<String, Object> start(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.start(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/resolve")
    @ResponseBody
    public Map<String, Object> resolve(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.resolve(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/reject")
    @ResponseBody
    public Map<String, Object> reject(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.reject(user, id, form);
        return Map.of("success", true);
    }

    @PostMapping("/ticket/admin/api/tickets/{id}/comments")
    @ResponseBody
    public Map<String, Object> adminComment(@AuthenticationPrincipal LoginUser user, @PathVariable Long id, @RequestBody TicketRequestDTO form) {
        service.comment(user, id, form, true);
        return Map.of("success", true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rejected(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("success", false, "message", e.getReason()));
    }

    @ExceptionHandler({TypeMismatchException.class, HttpMessageNotReadableException.class})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "요청 값의 형식을 확인해 주세요."));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> failed(RuntimeException e) {
        logger.error("티켓 업무 처리 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
