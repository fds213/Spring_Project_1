package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.MapMarkerDTO;
import com.ieum.gw.dto.MapMarkerSaveDTO;

// 지도 SQL을 부르는 창구. 메서드 이름 = WorkMapMapper.xml 의 id
// (지점 담당의 Branch… 파일과 겹치지 않게 지도는 WorkMap… 이름을 쓴다)
@Mapper
public interface WorkMapMapper {

    // 우리 업체의 공통 지도 번호 (WORK_MAP에 공통 지도 줄이 없으면 null)
    Long selectCommonMapId(@Param("companyId") Long companyId);

    // 우리 업체의 공통 지도 줄 만들기 (첫 마커를 저장할 때). 만든 사람을 소유자로 적는다
    int insertCommonMap(@Param("companyId") Long companyId,
                        @Param("ownerId") Long ownerId);

    // 공통 지도의 마커 전체
    List<MapMarkerDTO> selectCommonMarkers(@Param("companyId") Long companyId);

    // 새 마커 번호 하나 뽑기 (시퀀스 SQ_MAP_MARKER). 저장 뒤 화면이 그 마커를 고르려고 먼저 뽑는다
    Long selectNextMarkerId();

    // 마커 저장
    int insertMarker(@Param("markerId") Long markerId,
                     @Param("companyId") Long companyId,
                     @Param("mapId") Long mapId,
                     @Param("m") MapMarkerSaveDTO marker);

    // 마커 고치기: 우리 업체 공통 지도의 마커만 고쳐진다 (고친 줄 수를 돌려준다)
    int updateMarker(@Param("companyId") Long companyId,
                     @Param("markerId") Long markerId,
                     @Param("m") MapMarkerSaveDTO marker);

    // 마커 지우기: 우리 업체 공통 지도의 마커만 지워진다 (지운 줄 수를 돌려준다)
    int deleteMarker(@Param("companyId") Long companyId,
                     @Param("markerId") Long markerId);

    // ---------- 개인 지도: 소유자 본인 것만 ----------

    // 내 개인 지도 번호 (WORK_MAP에 내 개인 지도 줄이 없으면 null)
    Long selectPersonalMapId(@Param("companyId") Long companyId,
                             @Param("employeeId") Long employeeId);

    // 내 개인 지도 줄 만들기 (내 첫 마커를 저장할 때)
    int insertPersonalMap(@Param("companyId") Long companyId,
                          @Param("employeeId") Long employeeId);

    // 내 개인 지도의 마커 전체
    List<MapMarkerDTO> selectPersonalMarkers(@Param("companyId") Long companyId,
                                             @Param("employeeId") Long employeeId);

    // 마커 고치기: 내 개인 지도의 마커만 고쳐진다 (고친 줄 수를 돌려준다)
    int updatePersonalMarker(@Param("companyId") Long companyId,
                             @Param("employeeId") Long employeeId,
                             @Param("markerId") Long markerId,
                             @Param("m") MapMarkerSaveDTO marker);

    // 마커 지우기: 내 개인 지도의 마커만 지워진다 (지운 줄 수를 돌려준다)
    int deletePersonalMarker(@Param("companyId") Long companyId,
                             @Param("employeeId") Long employeeId,
                             @Param("markerId") Long markerId);
}
