package com.ieum.gw.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 WorkMapMapper.xml의 문장에 업체·지도 종류·소유자 조건이 붙어 있는지 본다.
 * 개인 지도는 본인만 보는 보호 자료라, 조건 하나만 빠져도 남의 마커가 보이거나 지워진다.
 */
class WorkMapMapperTest {

    private static final String NS = WorkMapMapper.class.getName() + ".";

    private Configuration config;

    // 실제 XML을 읽어 문장을 등록한다 (DB 없이 SQL 글자만 본다)
    @BeforeEach
    void load() throws Exception {
        config = new Configuration();
        try (var in = getClass().getResourceAsStream("/mapper/WorkMapMapper.xml")) {
            new XMLMapperBuilder(in, config, "mapper/WorkMapMapper.xml", config.getSqlFragments()).parse();
        }
    }

    // 문장을 공백 하나로 고른 SQL로 돌려준다 (줄바꿈·들여쓰기에 검사가 흔들리지 않게)
    private String sql(String id) {
        return config.getMappedStatement(NS + id).getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ");
    }

    // 업체·개인 지도·소유자 조건이 모두 있고 공통 지도는 섞이지 않았는지 본다
    private void assertPersonalOnly(String sql) {
        assertTrue(sql.contains("company_id = ?"), sql);
        assertTrue(sql.contains("map_type = 'MAP_PERSONAL'"), sql);
        assertTrue(sql.contains("owner_employee_id = ?"), sql);
        assertFalse(sql.contains("MAP_COMMON"), sql);
    }

    @Test
    @DisplayName("개인 지도 번호 찾기와 마커 조회는 업체·개인 지도·소유자로 거른다")
    void personalReadsFilterByOwner() {
        assertPersonalOnly(sql("selectPersonalMapId"));
        assertPersonalOnly(sql("selectPersonalMarkers"));
    }

    @Test
    @DisplayName("개인 지도 마커 수정·삭제는 업체·마커 번호에 더해 내 개인 지도 안의 마커만 건드린다")
    void personalWritesFilterByOwner() {
        for (String id : new String[] { "updatePersonalMarker", "deletePersonalMarker" }) {
            String sql = sql(id);
            assertPersonalOnly(sql);
            assertTrue(sql.contains("map_marker_id = ?"), sql);
            assertTrue(sql.contains("work_map_id IN ("), sql);
        }
    }

    @Test
    @DisplayName("개인 지도 줄은 MAP_PERSONAL 종류와 소유자를 넣어 만든다")
    void insertsPersonalMapWithOwner() {
        String sql = sql("insertPersonalMap");
        assertTrue(sql.contains("'MAP_PERSONAL'"), sql);
        assertTrue(sql.contains("owner_employee_id"), sql);
    }

    @Test
    @DisplayName("공통 지도 문장은 공통 지도만 건드린다: 관리자가 공통 지도 주소로 개인 지도 마커를 고치거나 지울 수 없다")
    void commonStatementsNeverTouchPersonalMaps() {
        for (String id : new String[] { "selectCommonMapId", "selectCommonMarkers", "updateMarker", "deleteMarker" }) {
            String sql = sql(id);
            assertTrue(sql.contains("company_id = ?"), sql);
            assertTrue(sql.contains("map_type = 'MAP_COMMON'"), sql);
            assertFalse(sql.contains("MAP_PERSONAL"), sql);
        }
    }
}
