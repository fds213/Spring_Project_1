package com.ieum.gw.location.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ieum.gw.location.dao.BranchDAO;
import com.ieum.gw.location.dto.BranchDTO;

class BranchServiceTest {
    @Test
    @DisplayName("업체 정보가 없거나 잘못되면 전체 지점을 조회하지 않는다")
    void rejectsInvalidCompany() {
        BranchService service = new BranchService(null);
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class, () -> service.getBranches(id));
        }
    }

    @Test
    @DisplayName("전달받은 업체로 조회하고 지점이 없으면 빈 목록을 돌려준다")
    void passesCompanyAndReturnsEmptyList() {
        BranchDAO dao = new BranchDAO(null) {
            @Override
            public List<BranchDTO> selectActiveBranches(Long companyId) {
                assertEquals(7L, companyId);
                return List.of();
            }
        };
        assertTrue(new BranchService(dao).getBranches(7L).isEmpty());
    }

    @Test
    @DisplayName("실제 Mapper SQL은 업체 조건과 사용 중 조건을 바인딩하고 이름순으로 정렬한다")
    void mapperRestrictsCompanyAndActiveBranches() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/BranchMapper.xml")) {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, "mapper/BranchMapper.xml", config.getSqlFragments()).parse();
        }
        var sql = config.getMappedStatement("com.ieum.gw.location.mapper.BranchMapper.selectActiveBranches")
                .getBoundSql(Map.of("companyId", 7L));
        String text = sql.getSql().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(text.contains("where company_id = ?"));
        assertTrue(text.contains("use_yn = 'y'"));
        assertTrue(text.contains("order by branch_name, branch_id"));
        assertEquals("companyId", sql.getParameterMappings().get(0).getProperty());
    }
}
