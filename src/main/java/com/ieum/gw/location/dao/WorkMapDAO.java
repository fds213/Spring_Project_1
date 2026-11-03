package com.ieum.gw.location.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.location.dto.MapMarkerDTO;
import com.ieum.gw.location.dto.MapMarkerSaveDTO;
import com.ieum.gw.location.mapper.WorkMapMapper;

import lombok.RequiredArgsConstructor;

// 지도 DAO: WorkMapMapper를 감싸서 Service에 건네준다.
// Service는 Mapper를 직접 부르지 않고 이 DAO만 부른다 (AGENTS.md)
@Repository
@RequiredArgsConstructor
public class WorkMapDAO {

    private final WorkMapMapper mapper;

    // 우리 업체의 공통 지도 번호 (없으면 null)
    public Long selectCommonMapId(Long companyId) {
        return mapper.selectCommonMapId(companyId);
    }

    // 공통 지도 줄 만들기
    public int insertCommonMap(Long companyId, Long ownerId) {
        return mapper.insertCommonMap(companyId, ownerId);
    }

    // 공통 지도의 마커 전체
    public List<MapMarkerDTO> selectCommonMarkers(Long companyId) {
        return mapper.selectCommonMarkers(companyId);
    }

    // 새 마커 번호
    public Long selectNextMarkerId() {
        return mapper.selectNextMarkerId();
    }

    // 마커 저장
    public int insertMarker(Long markerId, Long companyId, Long mapId, MapMarkerSaveDTO marker) {
        return mapper.insertMarker(markerId, companyId, mapId, marker);
    }

    // 마커 고치기 (고친 줄 수: 없는 마커면 0)
    public int updateMarker(Long companyId, Long markerId, MapMarkerSaveDTO marker) {
        return mapper.updateMarker(companyId, markerId, marker);
    }

    // 마커 지우기 (지운 줄 수: 없는 마커면 0)
    public int deleteMarker(Long companyId, Long markerId) {
        return mapper.deleteMarker(companyId, markerId);
    }
}
