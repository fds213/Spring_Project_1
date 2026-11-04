package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.service.AssetService;
import com.ieum.gw.dto.AuthEmpDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.EmployeeService;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.service.BranchService;

/** 공개 Service의 결과를 조합하는 테스트. 실제 DB와 Spring 컨테이너는 사용하지 않는다. */
class TicketFormOptionsServiceTest {
    private int reads;

    private LoginUser login(Long companyId, Long employeeId) {
        AuthEmpDTO emp = new AuthEmpDTO();
        emp.setCompanyId(companyId);
        emp.setEmployeeId(employeeId);
        return new LoginUser(emp);
    }

    private TicketReadService service(List<AssetDTO> assets, List<BranchDTO> branches, Long workBranchId) {
        AssetService assetService = new AssetService(null) {
            @Override
            public List<AssetDTO> getMyAssets(LoginUser user) {
                reads++;
                assertEquals(7L, user.getCompanyId());
                assertEquals(9L, user.getEmployeeId());
                return assets;
            }
        };
        BranchService branchService = new BranchService(null) {
            @Override
            public List<BranchDTO> getBranches(Long companyId) {
                reads++;
                assertEquals(7L, companyId);
                return branches;
            }
        };
        EmployeeService employeeService = new EmployeeService() {
            @Override
            public Long getWorkBranchId(Long companyId, Long employeeId) {
                reads++;
                assertEquals(7L, companyId);
                assertEquals(9L, employeeId);
                return workBranchId;
            }
        };
        return new TicketReadService(null, assetService, branchService, employeeService);
    }

    private BranchDTO branch(Long id, String name) {
        BranchDTO branch = new BranchDTO();
        branch.setBranchId(id);
        branch.setBranchName(name);
        return branch;
    }

    @Test
    @DisplayName("인증된 업체·사원으로 조회하고 자산번호 대신 제조 일련번호를 화면 응답에 담는다")
    void combinesPublicServiceResults() {
        AssetDTO asset = new AssetDTO();
        asset.setAssetId(1L);
        asset.setAssetName("업무용 노트북");
        asset.setAssetNo("ASSET-001");
        asset.setSerialNo("SN-001");
        asset.setBranchId(20L);
        var result = service(List.of(asset), List.of(branch(10L, "본사"), branch(20L, "지점")), 10L)
                .getFormOptions(login(7L, 9L));

        assertEquals(3, reads);
        assertEquals(1, result.getAssets().size());
        var option = result.getAssets().get(0);
        assertEquals(1L, option.getId());
        assertEquals("업무용 노트북", option.getName());
        assertEquals("SN-001", option.getSerial());
        assertEquals(20L, option.getBranchId());
        assertEquals(2, result.getBranches().size());
        assertEquals(10L, result.getBranches().get(0).getId());
        assertEquals("본사", result.getBranches().get(0).getName());
        // 자산의 지점과 사원의 근무 지점은 서로 다를 수 있다.
        assertEquals(10L, result.getDefaultBranchId());
    }

    @Test
    @DisplayName("근무 지점이 없거나 사용 중 지점 목록에 없으면 첫 지점으로 대체하지 않는다")
    void doesNotInventDefaultBranch() {
        for (Long workBranchId : new Long[] {null, 99L}) {
            var result = service(List.of(), List.of(branch(10L, "본사")), workBranchId)
                    .getFormOptions(login(7L, 9L));
            assertNull(result.getDefaultBranchId());
            assertTrue(result.getAssets().isEmpty());
            assertEquals(1, result.getBranches().size());
        }
    }

    @Test
    @DisplayName("자산과 사용 중 지점이 없으면 빈 목록과 기본 지점 null을 반환한다")
    void returnsEmptyOptions() {
        var result = service(List.of(), List.of(), 10L).getFormOptions(login(7L, 9L));
        assertTrue(result.getAssets().isEmpty());
        assertTrue(result.getBranches().isEmpty());
        assertNull(result.getDefaultBranchId());
    }

    @Test
    @DisplayName("로그인 정보가 없거나 업체·사원 ID가 잘못되면 다른 Service 조회 전에 401로 거절한다")
    void rejectsInvalidLoginBeforeAnyRead() {
        var service = service(List.of(), List.of(), null);
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.getFormOptions(null)).getRawStatusCode());
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            assertEquals(401, assertThrows(ResponseStatusException.class,
                    () -> service.getFormOptions(login(invalid, 9L))).getRawStatusCode());
            assertEquals(401, assertThrows(ResponseStatusException.class,
                    () -> service.getFormOptions(login(7L, invalid))).getRawStatusCode());
        }
        assertEquals(0, reads);
    }
}
