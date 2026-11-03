package com.ieum.gw.asset.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.asset.dao.AssetDAO;
import com.ieum.gw.asset.dto.AssetDTO;
import com.ieum.gw.common.dto.AuthEmpDTO;
import com.ieum.gw.common.dto.LoginUser;

class AssetServiceTest {
    @Test
    @DisplayName("로그인 정보가 없으면 자산 목록을 읽지 않는다")
    void rejectsMissingLogin() {
        var service = new AssetService(null);
        var error = assertThrows(ResponseStatusException.class, () -> service.getMyAssets(null));
        assertEquals(401, error.getRawStatusCode());
    }

    @Test
    @DisplayName("인증 정보의 업체와 사원으로만 본인 자산을 조회한다")
    void usesAuthenticatedOwner() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(7L);
        emp.setEmployeeId(9L);
        AssetDAO dao = new AssetDAO(null) {
            @Override
            public List<AssetDTO> selectMyAssets(Long companyId, Long employeeId) {
                assertEquals(7L, companyId);
                assertEquals(9L, employeeId);
                return List.of();
            }
        };
        assertTrue(new AssetService(dao).getMyAssets(new LoginUser(emp)).isEmpty());
    }

    @Test
    @DisplayName("홈 자산 건수는 전달받은 업체와 사원으로 조회한다")
    void countsMyAssets() {
        AssetDAO dao = new AssetDAO(null) {
            @Override
            public int countMyAssets(Long companyId, Long employeeId) {
                assertEquals(7L, companyId);
                assertEquals(9L, employeeId);
                return 2;
            }
        };
        assertEquals(2, new AssetService(dao).countMyAssets(7L, 9L));
    }

    @Test
    @DisplayName("업체나 사원 정보가 잘못된 건수 요청은 조회 전에 거절한다")
    void rejectsMissingOwner() {
        var service = new AssetService(null);
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class, () -> service.countMyAssets(id, 9L));
            assertThrows(IllegalArgumentException.class, () -> service.countMyAssets(7L, id));
        }
    }

    @Test
    @DisplayName("지급 이력 조회에 자산 번호가 없으면 DB를 읽기 전에 거절한다")
    void rejectsMissingAssetId() {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(7L);
        emp.setEmployeeId(9L);
        var service = new AssetService(null);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.getMyAssignments(new LoginUser(emp), null));
        assertEquals(400, error.getRawStatusCode());
    }

    @Test
    @DisplayName("목록과 건수 SQL은 같은 업체·사원·미회수·사용 중 조건을 쓴다")
    void mapperUsesIdenticalOwnerScope() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/AssetMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/AssetMapper.xml", config.getSqlFragments()).parse();
        }
        String[] names = {"selectMyAssets", "countMyAssets"};
        String[] scopes = new String[2];
        for (int i = 0; i < names.length; i++) {
            var bound = config.getMappedStatement("com.ieum.gw.asset.dao.AssetMapper." + names[i])
                    .getBoundSql(Map.of("companyId", 7L, "employeeId", 9L));
            String sql = bound.getSql().replaceAll("\\s+", " ").toLowerCase();
            assertTrue(sql.contains("aa.company_id = a.company_id"));
            assertTrue(sql.contains("where a.company_id = ?"));
            assertTrue(sql.contains("aa.employee_id = ?"));
            assertTrue(sql.contains("aa.returned_at is null"));
            assertTrue(sql.contains("a.status_code = 'as_in_use'"));
            assertEquals("companyId", bound.getParameterMappings().get(0).getProperty());
            assertEquals("employeeId", bound.getParameterMappings().get(1).getProperty());
            scopes[i] = sql.substring(sql.indexOf("from asset")).split(" order by ")[0].trim();
        }
        assertEquals(scopes[0], scopes[1]);

        var history = config.getMappedStatement("com.ieum.gw.asset.dao.AssetMapper.selectMyAssignments")
                .getBoundSql(Map.of("companyId", 7L, "employeeId", 9L, "assetId", 13L));
        String historySql = history.getSql().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(historySql.contains("where company_id = ? and employee_id = ? and asset_id = ?"));
        assertFalse(historySql.contains("returned_at is null"));
        assertTrue(historySql.contains("order by issued_at desc, asset_assignment_id desc"));
        assertEquals(List.of("companyId", "employeeId", "assetId"), history.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty()).collect(java.util.stream.Collectors.toList()));
    }
}
