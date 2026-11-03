package com.ieum.gw.asset.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.asset.dao.AssetDAO;
import com.ieum.gw.asset.dto.AssetDTO;
import com.ieum.gw.asset.dto.AssetAssignmentDTO;
import com.ieum.gw.common.dto.LoginUser;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {
    private final AssetDAO dao;

    /** 본인에게 지급되어 아직 회수하지 않은 사용 중 자산만 조회한다. */
    public List<AssetDTO> getMyAssets(LoginUser user) {
        requireLogin(user);
        return dao.selectMyAssets(user.getCompanyId(), user.getEmployeeId());
    }

    /** 회수된 자산도 본인에게 지급된 이력은 조회할 수 있다. 다른 수령인의 건은 제외한다. */
    public List<AssetAssignmentDTO> getMyAssignments(LoginUser user, Long assetId) {
        requireLogin(user);
        if (assetId == null || assetId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자산 번호가 올바르지 않습니다.");
        }
        List<AssetAssignmentDTO> assignments = dao.selectMyAssignments(user.getCompanyId(), user.getEmployeeId(), assetId);
        if (assignments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자산 지급 이력을 찾을 수 없습니다.");
        }
        return assignments;
    }

    private static void requireLogin(LoginUser user) {
        if (user == null || user.getCompanyId() == null || user.getCompanyId() <= 0
                || user.getEmployeeId() == null || user.getEmployeeId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    /** 홈 담당은 인증 정보에서 얻은 업체·사원 ID로 호출한다. 목록과 같은 조건으로 센다. */
    public int countMyAssets(Long companyId, Long employeeId) {
        if (companyId == null || companyId <= 0 || employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("업체·사원 정보가 필요합니다.");
        }
        return dao.countMyAssets(companyId, employeeId);
    }
}
