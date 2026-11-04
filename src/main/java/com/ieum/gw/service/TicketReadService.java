package com.ieum.gw.service;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.service.AssetService;
import com.ieum.gw.service.EmployeeService;
import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.service.BranchService;
import com.ieum.gw.dao.TicketReadDAO;
import com.ieum.gw.dto.TicketCommentDTO;
import com.ieum.gw.dto.TicketHistoryDTO;
import com.ieum.gw.dto.TicketFormOptionsDTO;
import lombok.RequiredArgsConstructor;

/** 내 티켓의 읽기 기능. 배정·처리·종료 같은 상태 변경은 이 클래스에서 하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketReadService {
    private final TicketReadDAO dao;
    private final AssetService assetService;
    private final BranchService branchService;
    private final EmployeeService employeeService;

    /** 접수 화면의 선택 목록을 모은다. 업체·사원 ID는 인증 정보만 사용한다. */
    public TicketFormOptionsDTO getFormOptions(LoginUser user) {
        requireLogin(user);
        Long companyId = user.getCompanyId();
        List<AssetDTO> assets = assetService.getMyAssets(user);
        List<BranchDTO> branches = branchService.getBranches(companyId);
        Long workBranchId = employeeService.getWorkBranchId(companyId, user.getEmployeeId());

        // 근무 지점이 없거나 사용 중 목록에서 빠졌으면 다른 지점으로 대체하지 않는다.
        Long defaultBranchId = branches.stream()
                .anyMatch(branch -> branch.getBranchId().equals(workBranchId)) ? workBranchId : null;

        List<TicketFormOptionsDTO.AssetOption> assetOptions = assets.stream()
                .map(asset -> new TicketFormOptionsDTO.AssetOption(asset.getAssetId(), asset.getAssetName(),
                        asset.getSerialNo(), asset.getBranchId()))
                .collect(Collectors.toList());
        List<TicketFormOptionsDTO.BranchOption> branchOptions = branches.stream()
                .map(branch -> new TicketFormOptionsDTO.BranchOption(branch.getBranchId(), branch.getBranchName()))
                .collect(Collectors.toList());
        return new TicketFormOptionsDTO(assetOptions, branchOptions, defaultBranchId);
    }

    public List<TicketCommentDTO> getMyComments(LoginUser user, Long itTicketId) {
        requireMyTicket(user, itTicketId);
        return dao.selectMyComments(user.getCompanyId(), user.getEmployeeId(), itTicketId);
    }

    public List<TicketHistoryDTO> getMyHistory(LoginUser user, Long itTicketId) {
        requireMyTicket(user, itTicketId);
        return dao.selectMyHistory(user.getCompanyId(), user.getEmployeeId(), itTicketId);
    }

    private void requireMyTicket(LoginUser user, Long itTicketId) {
        requireLogin(user);
        if (itTicketId == null || itTicketId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "티켓 번호가 올바르지 않습니다.");
        }
        if (dao.countMyTicket(user.getCompanyId(), user.getEmployeeId(), itTicketId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "티켓을 찾을 수 없습니다.");
        }
    }

    private static void requireLogin(LoginUser user) {
        if (user == null || user.getCompanyId() == null || user.getCompanyId() <= 0
                || user.getEmployeeId() == null || user.getEmployeeId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }
}
