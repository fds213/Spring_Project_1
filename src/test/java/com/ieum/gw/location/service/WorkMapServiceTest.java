package com.ieum.gw.location.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.location.dao.WorkMapDAO;
import com.ieum.gw.location.dto.MapMarkerDTO;
import com.ieum.gw.location.dto.MapMarkerSaveDTO;

/**
 * 공통 지도 규칙: 마커 등록은 누구나(첫 마커 때 공통 지도 줄을 만듦), 수정·삭제는 업체 관리자·인사 담당,
 * 다른 업체의 마커 번호로 고치거나 지우면 404 (업체 분리, AGENTS.md '테스트').
 * 가짜 DAO는 실제 SQL처럼 업체 ID가 맞을 때만 마커를 고치고 지운다.
 */
class WorkMapServiceTest {

    private static final Long MY_COMPANY = 7L;
    private static final Long OTHER_COMPANY = 8L;
    private static final Long MAP_ID = 40L;
    private static final Long MARKER_ID = 1000L; // 업체 7의 마커

    /** 공통 지도 줄과 마커 한 건만 있는 가짜 DB */
    static class FakeDAO extends WorkMapDAO {
        Long mapId;                  // 지금 있는 공통 지도 줄 (없으면 null)
        Long mapOwner;               // 공통 지도 줄을 만든 사람
        int mapInserts;
        Long insertedCompany;
        Long insertedMap;
        MapMarkerSaveDTO inserted;
        final List<String> writes = new ArrayList<>();

        FakeDAO(Long mapId) {
            super(null);
            this.mapId = mapId;
        }

        @Override
        public Long selectCommonMapId(Long companyId) {
            return mapId;
        }

        @Override
        public int insertCommonMap(Long companyId, Long ownerId) {
            mapInserts++;
            mapOwner = ownerId;
            mapId = MAP_ID;
            return 1;
        }

        @Override
        public List<MapMarkerDTO> selectCommonMarkers(Long companyId) {
            return new ArrayList<>();
        }

        @Override
        public Long selectNextMarkerId() {
            return 2000L;
        }

        @Override
        public int insertMarker(Long markerId, Long companyId, Long mapId, MapMarkerSaveDTO marker) {
            insertedCompany = companyId;
            insertedMap = mapId;
            inserted = marker;
            return 1;
        }

        @Override
        public int updateMarker(Long companyId, Long markerId, MapMarkerSaveDTO marker) {
            if (!MY_COMPANY.equals(companyId) || !MARKER_ID.equals(markerId)) return 0;
            writes.add("update");
            return 1;
        }

        @Override
        public int deleteMarker(Long companyId, Long markerId) {
            if (!MY_COMPANY.equals(companyId) || !MARKER_ID.equals(markerId)) return 0;
            writes.add("delete");
            return 1;
        }
    }

    private static LoginUser user(Long companyId, String role) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setRole(role);
        emp.setCompanyId(companyId);
        emp.setEmployeeId(5L);
        emp.setUserAccountId(5L);
        return new LoginUser(emp);
    }

    private static MapMarkerSaveDTO form() {
        MapMarkerSaveDTO f = new MapMarkerSaveDTO();
        f.setTitle("  판교 창고  ");
        f.setAddress("경기 성남시 분당구 판교역로 235");
        f.setDetailAddress("   ");
        f.setLat(37.4020);
        f.setLng(127.1086);
        f.setDescription("");
        return f;
    }

    private static void assertStatus(HttpStatus status, Executable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
        assertEquals(status, e.getStatus());
    }

    @Test
    @DisplayName("직원도 마커를 등록한다: 공통 지도 줄이 없으면 만들고(만든 사람이 소유자) 그 지도에 저장, 빈칸은 정리")
    void employeeAddsFirstMarker() {
        FakeDAO dao = new FakeDAO(null);

        Map<String, Object> result = new WorkMapService(dao).addMarker(user(MY_COMPANY, "ROLE_EMP"), form());

        assertEquals(2000L, result.get("id"));
        assertEquals(1, dao.mapInserts);
        assertEquals(5L, dao.mapOwner);
        assertEquals(MY_COMPANY, dao.insertedCompany);
        assertEquals(MAP_ID, dao.insertedMap);
        assertEquals("판교 창고", dao.inserted.getTitle());
        assertNull(dao.inserted.getDetailAddress());
        assertNull(dao.inserted.getDescription());
    }

    @Test
    @DisplayName("공통 지도 줄이 이미 있으면 새로 만들지 않는다")
    void usesExistingMap() {
        FakeDAO dao = new FakeDAO(11L);

        new WorkMapService(dao).addMarker(user(MY_COMPANY, "ROLE_EMP"), form());

        assertEquals(0, dao.mapInserts);
        assertEquals(11L, dao.insertedMap);
    }

    @Test
    @DisplayName("값이 틀리면 400: 제목 빈칸, 위치 없음, 위도 범위 밖")
    void rejectsBadValues() {
        WorkMapService service = new WorkMapService(new FakeDAO(MAP_ID));

        MapMarkerSaveDTO noTitle = form();
        noTitle.setTitle("  ");
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.addMarker(user(MY_COMPANY, "ROLE_EMP"), noTitle));

        MapMarkerSaveDTO noLat = form();
        noLat.setLat(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.addMarker(user(MY_COMPANY, "ROLE_EMP"), noLat));

        MapMarkerSaveDTO badLat = form();
        badLat.setLat(91.0);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.addMarker(user(MY_COMPANY, "ROLE_EMP"), badLat));
    }

    @Test
    @DisplayName("직원은 마커를 고치거나 지울 수 없다 (403), 관리자·인사 담당은 된다")
    void onlyManagersEditOrDelete() {
        FakeDAO dao = new FakeDAO(MAP_ID);
        WorkMapService service = new WorkMapService(dao);

        assertStatus(HttpStatus.FORBIDDEN, () -> service.updateMarker(user(MY_COMPANY, "ROLE_EMP"), MARKER_ID, form()));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.deleteMarker(user(MY_COMPANY, "ROLE_IT"), MARKER_ID));
        assertEquals(List.of(), dao.writes);

        service.updateMarker(user(MY_COMPANY, "ROLE_HR"), MARKER_ID, form());
        service.deleteMarker(user(MY_COMPANY, "ROLE_ADMIN"), MARKER_ID);
        assertEquals(List.of("update", "delete"), dao.writes);
    }

    @Test
    @DisplayName("다른 업체 관리자가 마커 번호로 고치거나 지우면 404이고 바뀌지 않는다")
    void cannotTouchOtherCompanyMarker() {
        FakeDAO dao = new FakeDAO(MAP_ID);
        WorkMapService service = new WorkMapService(dao);

        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateMarker(user(OTHER_COMPANY, "ROLE_ADMIN"), MARKER_ID, form()));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteMarker(user(OTHER_COMPANY, "ROLE_ADMIN"), MARKER_ID));
        assertEquals(List.of(), dao.writes);
    }

    @Test
    @DisplayName("지도 조회: 수정·삭제 버튼은 관리자·인사 담당에게만 (canEdit)")
    void canEditOnlyForManagers() {
        WorkMapService service = new WorkMapService(new FakeDAO(MAP_ID));

        assertEquals(false, service.getCommonMap(user(MY_COMPANY, "ROLE_EMP")).get("canEdit"));
        assertTrue((Boolean) service.getCommonMap(user(MY_COMPANY, "ROLE_HR")).get("canEdit"));
    }
}
