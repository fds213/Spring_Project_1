package com.ieum.gw.location.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.location.dao.WorkMapDAO;
import com.ieum.gw.location.dto.MapMarkerDTO;
import com.ieum.gw.location.dto.MapMarkerSaveDTO;

import lombok.RequiredArgsConstructor;

/**
 * 공통 지도 규칙을 맡는 Service (요구분석서 3.4, 계획서 6장 '지도').
 * - 공통 지도는 업체마다 하나. 업체의 첫 마커를 저장할 때 지도 줄(WORK_MAP)을 같은 트랜잭션에서 만든다.
 * - 권한(계획서 6장 TODO를 지도 담당이 정함): 마커 등록은 우리 업체 직원 누구나, 수정·삭제는 업체 관리자·인사 담당.
 *   (MAP_MARKER에 작성자 칸이 없어 '쓴 사람'으로 나누지 않는다. 내 마커는 개인 지도가 생기면 거기서 다룬다)
 * - 업체는 로그인 정보에서만 꺼낸다. 화면이 보낸 값으로 업체·지도를 정하지 않는다 (AGENTS.md)
 * - 카카오 지도 JavaScript 키는 저장소에 올리지 않는 config/kakao.properties에서 읽는다(kakao.properties.example 참고).
 *   파일이 없어도 서버는 뜨고, 화면은 지도 대신 안내와 마커 목록만 보여 준다.
 */
@Service
@RequiredArgsConstructor
@PropertySource(value = "classpath:config/kakao.properties", ignoreResourceNotFound = true)
public class WorkMapService {

    private static final int TITLE_MAX = 100;        // 제목 글자 수 (map.js와 같게)
    private static final int ADDRESS_MAX = 500;      // 주소 글자 수 (DB 칸 크기)
    private static final int DETAIL_MAX = 200;       // 상세주소 글자 수 (DB 칸 크기)
    private static final int DESCRIPTION_MAX = 1000; // 설명 글자 수 (map.js와 같게)
    private static final String NOT_FOUND = "없는 마커이거나 이미 지운 마커입니다";

    private final WorkMapDAO dao;

    @Value("${kakao.js-key:}")
    private String kakaoKey; // 카카오 지도 JavaScript 키 (없으면 빈 글자)

    // 화면에 넣을 카카오 지도 키 (Controller가 JSP로 넘긴다)
    public String getKakaoKey() {
        return kakaoKey == null ? "" : kakaoKey.trim();
    }

    // ---------- 조회 ----------

    // 공통 지도: { markers, canEdit } (map.js가 읽는 모양). 지도 줄이 아직 없으면 마커는 빈 목록
    public Map<String, Object> getCommonMap(LoginUser user) {
        List<MapMarkerDTO> markers = dao.selectCommonMarkers(user.getCompanyId());

        Map<String, Object> result = new HashMap<>();
        result.put("markers", markers);
        result.put("canEdit", isManager(user)); // 수정·삭제 버튼: 업체 관리자·인사 담당
        return result;
    }

    // ---------- 등록 · 수정 · 삭제 ----------

    // 마커 등록: 우리 업체 직원 누구나. 성공하면 { id } → 화면이 그 마커를 골라 보여 준다
    @Transactional
    public Map<String, Object> addMarker(LoginUser user, MapMarkerSaveDTO form) {
        MapMarkerSaveDTO m = clean(form); // 틀리면 400

        Long companyId = user.getCompanyId();
        Long mapId = dao.selectCommonMapId(companyId);
        if (mapId == null) { // 업체의 첫 마커: 공통 지도 줄을 만든다 (같은 트랜잭션)
            dao.insertCommonMap(companyId, user.getEmployeeId());
            mapId = dao.selectCommonMapId(companyId);
        }

        Long id = dao.selectNextMarkerId();
        dao.insertMarker(id, companyId, mapId, m);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    // 마커 수정: 업체 관리자·인사 담당. 우리 업체 공통 지도의 마커가 아니면 404
    @Transactional
    public Map<String, Object> updateMarker(LoginUser user, Long id, MapMarkerSaveDTO form) {
        checkManager(user);
        MapMarkerSaveDTO m = clean(form);

        int changed = (id == null) ? 0 : dao.updateMarker(user.getCompanyId(), id, m);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    // 마커 삭제: 업체 관리자·인사 담당. 우리 업체 공통 지도의 마커가 아니면 404
    @Transactional
    public Map<String, Object> deleteMarker(LoginUser user, Long id) {
        checkManager(user);

        int deleted = (id == null) ? 0 : dao.deleteMarker(user.getCompanyId(), id);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    // ---------- 같이 쓰는 도구 ----------

    // 값 검사 + 앞뒤 빈칸 정리. 비어 있어도 되는 칸(상세주소·설명)은 빈 글자를 null로 바꾼다. 틀리면 400
    private MapMarkerSaveDTO clean(MapMarkerSaveDTO form) {
        if (form == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "마커 정보를 보내 주세요");
        }
        String title = trim(form.getTitle());
        String address = trim(form.getAddress());
        String detail = trim(form.getDetailAddress());
        String description = trim(form.getDescription());
        Double lat = form.getLat();
        Double lng = form.getLng();

        String message = null;
        if (title.isEmpty())                               message = "제목을 입력해 주세요";
        else if (title.length() > TITLE_MAX)               message = "제목은 " + TITLE_MAX + "자까지 쓸 수 있습니다";
        else if (address.isEmpty())                        message = "주소나 장소를 검색해 위치를 골라 주세요";
        else if (address.length() > ADDRESS_MAX)           message = "주소가 너무 깁니다";
        else if (detail.length() > DETAIL_MAX)             message = "상세주소는 " + DETAIL_MAX + "자까지 쓸 수 있습니다";
        else if (description.length() > DESCRIPTION_MAX)   message = "설명은 " + DESCRIPTION_MAX + "자까지 쓸 수 있습니다";
        else if (lat == null || lng == null || lat.isNaN() || lng.isNaN()
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) message = "위치가 올바르지 않습니다. 다시 검색해 골라 주세요";
        if (message != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        MapMarkerSaveDTO m = new MapMarkerSaveDTO();
        m.setTitle(title);
        m.setAddress(address);
        m.setDetailAddress(detail.isEmpty() ? null : detail);
        m.setDescription(description.isEmpty() ? null : description);
        m.setLat(lat);
        m.setLng(lng);
        return m;
    }

    private String trim(String s) {
        return (s == null) ? "" : s.trim();
    }

    // 관리자·인사 담당이 아니면 403
    private void checkManager(LoginUser user) {
        if (!isManager(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "마커 수정·삭제는 업체 관리자와 인사 담당만 할 수 있습니다");
        }
    }

    // 업체 관리자 또는 인사 담당인가
    private boolean isManager(LoginUser user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
    }
}
