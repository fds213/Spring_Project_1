package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ieum.gw.dao.BranchDAO;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.dto.BranchDetailDTO;

class BranchServiceTest {
    @Test
    @DisplayName("업체 정보가 없거나 잘못되면 전체 지점을 조회하지 않는다")
    void rejectsInvalidCompany() {
        BranchService service = new BranchService(null);
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class, () -> service.getBranches(id));
            assertThrows(IllegalArgumentException.class, () -> service.getBranchDetails(id));
        }
    }

    @Test
    @DisplayName("기존 자료 표시용 지점 정보에는 사용 중지 지점도 남긴다")
    void returnsHistoricalBranchLabels() {
        BranchDetailDTO branch = new BranchDetailDTO();
        branch.setBranchId(13L);
        branch.setBranchName("이전 지점");
        branch.setUseYn("N");
        BranchDAO dao = new BranchDAO(null) {
            @Override
            public List<BranchDetailDTO> selectBranchDetails(Long companyId) {
                assertEquals(7L, companyId);
                return List.of(branch);
            }
        };
        var result = new BranchService(dao).getBranchDetails(7L);
        assertEquals("N", result.get(0).getUseYn());
        assertEquals("이전 지점", result.get(0).getBranchName());
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
        var sql = config.getMappedStatement("com.ieum.gw.mapper.BranchMapper.selectActiveBranches")
                .getBoundSql(Map.of("companyId", 7L));
        String text = sql.getSql().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(text.contains("where company_id = ?"));
        assertTrue(text.contains("use_yn = 'y'"));
        assertTrue(text.contains("order by branch_name, branch_id"));
        assertEquals("companyId", sql.getParameterMappings().get(0).getProperty());

        var details = config.getMappedStatement("com.ieum.gw.mapper.BranchMapper.selectBranchDetails")
                .getBoundSql(Map.of("companyId", 7L));
        String detailSql = details.getSql().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(detailSql.contains("where company_id = ?"));
        assertFalse(detailSql.contains("use_yn = 'y'"));
        assertTrue(detailSql.contains("detail_address as detailaddress"));
        assertEquals("companyId", details.getParameterMappings().get(0).getProperty());

        var update = config.getMappedStatement("com.ieum.gw.mapper.BranchMapper.updateBranch")
                .getBoundSql(Map.of("companyId", 7L, "branchId", 13L));
        String updateSql = update.getSql().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(updateSql.contains("where company_id = ? and branch_id = ?"));
        assertEquals(List.of("companyId", "branchId"), update.getParameterMappings().stream()
                .skip(3).map(ParameterMapping::getProperty).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("지점 등록 SQL은 지점명을 바인딩하고 선택 상세 주소를 VARCHAR로 지정한다")
    void mapperBindsBranchRegistration() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/BranchMapper.xml")) {
            new XMLMapperBuilder(in, config, "mapper/BranchMapper.xml", config.getSqlFragments()).parse();
        }

        var mappings = config.getMappedStatement("com.ieum.gw.mapper.BranchMapper.insertBranch")
                .getBoundSql(Map.of("companyId", 7L, "branchName", "판교지점", "address", "판교역로"))
                .getParameterMappings();
        assertEquals(List.of("companyId", "branchName", "address", "detailAddress"),
                mappings.stream().map(ParameterMapping::getProperty).collect(Collectors.toList()));
        assertEquals(JdbcType.VARCHAR, mappings.get(3).getJdbcType());
    }

    @Test
    @DisplayName("지점 중지 SQL은 업체·지점을 제한하고 지급 완료 전 장비 티켓만 집계한다")
    void mapperScopesBranchDisableAndPendingTickets() throws Exception {
        Configuration config = new Configuration();
        try (InputStream in = getClass().getResourceAsStream("/mapper/BranchMapper.xml")) {
            new XMLMapperBuilder(in, config, "mapper/BranchMapper.xml", config.getSqlFragments()).parse();
        }
        String namespace = "com.ieum.gw.mapper.BranchMapper.";
        Map<String, Long> branch = Map.of("companyId", 7L, "branchId", 13L);

        var lock = config.getMappedStatement(namespace + "lockBranch").getBoundSql(branch);
        assertTrue(lock.getSql().replaceAll("\\s+", " ").contains("WHERE company_id = ? AND branch_id = ? FOR UPDATE"));
        var disable = config.getMappedStatement(namespace + "disableBranch").getBoundSql(branch);
        assertTrue(disable.getSql().replaceAll("\\s+", " ").contains("WHERE company_id = ? AND branch_id = ? AND use_yn = 'Y'"));

        var pending = config.getMappedStatement(namespace + "countPendingEquipmentTickets").getBoundSql(branch);
        String sql = pending.getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("t.company_id = b.company_id AND t.branch_id = b.branch_id"));
        assertTrue(sql.contains("WHERE b.company_id = ? AND b.branch_id = ?"));
        assertTrue(sql.contains("t.ticket_type = ? AND t.status_code IN (?, ?, ?)"));
        assertEquals("TK_EQUIPMENT", pending.getAdditionalParameter("equipment"));
        assertEquals("RECEIVED", pending.getAdditionalParameter("received"));
        assertEquals("ASSIGNED", pending.getAdditionalParameter("assigned"));
        assertEquals("IN_PROGRESS", pending.getAdditionalParameter("inProgress"));
    }
}
