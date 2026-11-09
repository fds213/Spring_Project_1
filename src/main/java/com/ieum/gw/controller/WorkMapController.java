package com.ieum.gw.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.MapMarkerSaveDTO;
import com.ieum.gw.service.WorkMapService;

import lombok.RequiredArgsConstructor;

// 공통·개인 지도 Controller. 화면은 /location/map(공통)과 /location/map/personal(개인), 데이터는 /location/api/map/... 를 쓴다.
// (지점 담당의 /location 화면과 겹치지 않게 지도 데이터는 /location/api/map 아래에 둔다)
@Controller
@RequestMapping("/location")
@RequiredArgsConstructor
public class WorkMapController {

    private final WorkMapService workMapService;

    // ---------- 화면 ----------

    // 공통 지도: WEB-INF/views/location/map.jsp. 카카오 지도 키를 화면에 넘긴다 (없으면 빈 글자 → 화면이 안내만 보여 줌)
    @GetMapping("/map")
    public String map(Model model) {
        model.addAttribute("kakaoKey", workMapService.getKakaoKey());
        return "location/map";
    }

    // 개인 지도: 공통 지도와 같은 화면을 mapScope만 바꿔 연다 (map.js가 이 값을 보고 개인 지도 주소를 부른다)
    @GetMapping("/map/personal")
    public String personalMap(Model model) {
        model.addAttribute("kakaoKey", workMapService.getKakaoKey());
        model.addAttribute("mapScope", "personal");
        return "location/map";
    }

    // ---------- 데이터 (map.js가 부른다) ----------

    // 공통 지도의 마커 전체: GET /location/api/map/markers → { markers, canEdit }
    @GetMapping("/api/map/markers")
    @ResponseBody
    public Map<String, Object> markers(@AuthenticationPrincipal LoginUser loginUser) {
        return workMapService.getCommonMap(loginUser);
    }

    // 마커 등록: POST /location/api/map/markers (본문 JSON: { title, address, detailAddress, lat, lng, description })
    @PostMapping("/api/map/markers")
    @ResponseBody
    public Map<String, Object> add(@AuthenticationPrincipal LoginUser loginUser,
                                   @RequestBody MapMarkerSaveDTO form) {
        return workMapService.addMarker(loginUser, form);
    }

    // 마커 수정: POST /location/api/map/markers/3
    @PostMapping("/api/map/markers/{id}")
    @ResponseBody
    public Map<String, Object> update(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable Long id,
                                      @RequestBody MapMarkerSaveDTO form) {
        return workMapService.updateMarker(loginUser, id, form);
    }

    // 마커 삭제: POST /location/api/map/markers/3/delete
    @PostMapping("/api/map/markers/{id}/delete")
    @ResponseBody
    public Map<String, Object> delete(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable Long id) {
        return workMapService.deleteMarker(loginUser, id);
    }

    // 내 개인 지도의 마커 전체: GET /location/api/map/personal/markers → { markers, canEdit }
    @GetMapping("/api/map/personal/markers")
    @ResponseBody
    public Map<String, Object> personalMarkers(@AuthenticationPrincipal LoginUser loginUser) {
        return workMapService.getPersonalMap(loginUser);
    }

    // 개인 지도 마커 등록: POST /location/api/map/personal/markers (본문은 공통 지도와 같다)
    @PostMapping("/api/map/personal/markers")
    @ResponseBody
    public Map<String, Object> addPersonal(@AuthenticationPrincipal LoginUser loginUser,
                                           @RequestBody MapMarkerSaveDTO form) {
        return workMapService.addPersonalMarker(loginUser, form);
    }

    // 개인 지도 마커 수정: POST /location/api/map/personal/markers/3
    @PostMapping("/api/map/personal/markers/{id}")
    @ResponseBody
    public Map<String, Object> updatePersonal(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable Long id,
                                              @RequestBody MapMarkerSaveDTO form) {
        return workMapService.updatePersonalMarker(loginUser, id, form);
    }

    // 개인 지도 마커 삭제: POST /location/api/map/personal/markers/3/delete
    @PostMapping("/api/map/personal/markers/{id}/delete")
    @ResponseBody
    public Map<String, Object> deletePersonal(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable Long id) {
        return workMapService.deletePersonalMarker(loginUser, id);
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
