package com.ieum.gw.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ieum.gw.dao.AssetAllocationDAO;
import com.ieum.gw.dto.AssetAdminAssignmentDTO;
import com.ieum.gw.dto.AssetAllocationDTO;
import com.ieum.gw.dto.AssetDTO;
import com.ieum.gw.dto.AssetListDTO;
import com.ieum.gw.code.AssetStatus;
import com.ieum.gw.code.AssetType;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.service.BranchService;
import lombok.RequiredArgsConstructor;

/** 자산 지급·회수 업무. 모든 경로는 같은 자산 행을 먼저 잠근다. */
@Service
@RequiredArgsConstructor
public class AssetAllocationService {
    private final AssetAllocationDAO dao;
    private final BranchService branchService;
    private final EmployeeService employeeService;
    
    /** 자산 지급 대상 직원과 기존 자산의 지점 표시용 목록을 제공한다. */
    @Transactional(readOnly = true)
    public Map<String, Object> getFormOptions(LoginUser user) {
    	requireOperator(user);
    	
    	Long companyId = user.getCompanyId();
    	
    	return Map.of("employees", employeeService.listActiveEmp(companyId, null),
    				  "branches", branchService.getBranchDetails(companyId));
    }

    /** 같은 업체 자산의 지급·회수 이력을 관리자에게 제공한다. */
    @Transactional(readOnly = true)
    public List<AssetAdminAssignmentDTO> getAssignments(LoginUser user, Long assetId) {
    	requireOperator(user);
    	requireId(assetId);
    	
    	Long companyId = user.getCompanyId();
    	AssetAllocationDTO asset = dao.detail(companyId, assetId);
    	
    	if (asset == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자산을 찾을 수 없습니다.");
		}
    	
    	return dao.selectAssignments(companyId, assetId);
    }
    
    /** 로그인한 IT 담당자·관리자에게 같은 업체의 전체 자산 목록을 제공한다. */
    @Transactional(readOnly = true)
    public List<AssetListDTO> getAssetList(LoginUser user) {
    	requireOperator(user);
    	
    	return dao.selectList(user.getCompanyId());
    }
    
    /** 자산 ID로 같은 업체의 자산 상세 정보를 조회한다. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> detail(LoginUser operator, Long id) {
        requireOperator(operator);
        requireId(id);
        AssetAllocationDTO row = dao.detail(operator.getCompanyId(), id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자산을 찾을 수 없습니다.");
        }
        List<Long> open = dao.openAssignments(operator.getCompanyId(), id);
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", id);
        data.put("name", row.getAssetName());
        data.put("assetNo", row.getAssetNo());
        data.put("serial", row.getSerialNo());
        data.put("kind", row.getAssetType());
        data.put("status", row.getStatus());
        data.put("branchId", row.getBranchId());
        data.put("version", row.getVersion());
        data.put("actions", java.util.Map.of(
                "assign", AssetStatus.AS_AVAILABLE.name().equals(row.getStatus()) && open.isEmpty(),
                "return", AssetStatus.AS_IN_USE.name().equals(row.getStatus()) && open.size() == 1));
        return data;
    }

    @Transactional
    public void assign(LoginUser operator, Long assetId, Long receiverId, Long version) {
        requireOperator(operator);
        requireId(receiverId);
        requireVersion(version);
        AssetAllocationDTO row = locked(operator, assetId);
        checkVersion(row, version);
        if (!employeeService.isActiveEmployee(operator.getCompanyId(), receiverId)) {
            throw bad("같은 업체의 재직 사원에게만 지급할 수 있습니다.");
        }
        issue(operator, row, receiverId, null, row.getBranchId(), "관리 화면에서 지급");
    }

    @Transactional(readOnly = true)
    public List<AssetDTO> getStock(LoginUser operator,String type) {
        requireOperator(operator);
        AssetType kind=kind(type);
        return dao.stock(operator.getCompanyId(),kind.name(),AssetStatus.AS_AVAILABLE.name());
    }

    /**
     * 티켓 담당 Service가 티켓 잠금·version·담당자와 승인 정보를 검사한 뒤 호출한다.
     * 지급과 티켓 완료가 반드시 같은 트랜잭션에서 성공하도록 독립 호출은 거절한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long issueForTicket(LoginUser operator,Long receiverId,Long ticketId,Long assetId,String approvedType,Long branchId) {
        requireOperator(operator); requireId(receiverId); requireId(ticketId); requireId(branchId);
        AssetType kind=kind(approvedType);
        AssetAllocationDTO row=locked(operator,assetId);
        if (!kind.name().equals(row.getAssetType())) {
            throw bad("승인된 종류와 같은 장비를 선택해 주세요.");
        }
        if (branchService.getBranches(operator.getCompanyId()).stream().noneMatch(b -> branchId.equals(b.getBranchId()))) {
            throw bad("같은 업체의 사용 중인 수령 지점이 필요합니다.");
        }
        return issue(operator, row, receiverId, ticketId, branchId, "장비 지급 (티켓 " + ticketId + ")");
    }

    private Long issue(LoginUser operator, AssetAllocationDTO row, Long receiverId,
                       Long ticketId, Long branchId, String note) {
        if (!AssetStatus.AS_AVAILABLE.name().equals(row.getStatus())
                || !dao.openAssignments(operator.getCompanyId(), row.getAssetId()).isEmpty()) {
            throw conflict("이미 지급되었거나 지급할 수 없는 자산입니다.");
        }
        row.setEmployeeId(receiverId);
        row.setTicketId(ticketId);
        row.setActorAccountId(operator.getUserAccountId());
        row.setTargetBranchId(branchId);
        row.setTargetStatus(AssetStatus.AS_IN_USE.name());
        row.setNote(note);
        if (dao.issue(row) != 1) {
            throw conflict("다른 사람이 먼저 자산을 변경했습니다.");
        }
        if (dao.insertAssignment(row) != 1 || row.getAssignmentId() == null || dao.insertIssueHistory(row) != 1) {
            throw new IllegalStateException("자산 지급 이력을 저장하지 못했습니다.");
        }
        return row.getAssignmentId();
    }

    @Transactional
    public void returnAsset(LoginUser operator, Long assetId, Long version, String reason) {
        requireOperator(operator);
        requireVersion(version);
        if (reason == null || reason.isBlank() || reason.strip().length() > 300) {
            throw bad("회수 사유는 1~300자로 입력해 주세요.");
        }
        AssetAllocationDTO row = locked(operator, assetId);
        checkVersion(row, version);
        List<Long> assignments = dao.openAssignments(operator.getCompanyId(), assetId);
        if (!AssetStatus.AS_IN_USE.name().equals(row.getStatus()) || assignments.size() != 1) {
            throw conflict("현재 지급 이력이 변경되었습니다. 새로 불러와 주세요.");
        }
        row.setAssignmentId(assignments.get(0));
        row.setActorAccountId(operator.getUserAccountId());
        row.setTargetStatus(AssetStatus.AS_AVAILABLE.name());
        row.setTargetBranchId(row.getBranchId());
        row.setNote(reason.strip());
        if (dao.returnAssignment(row) != 1 || dao.release(row) != 1) {
            throw conflict("다른 사람이 먼저 자산을 변경했습니다.");
        }
        if (dao.insertReturnHistory(row) != 1) {
            throw new IllegalStateException("자산 회수 이력을 저장하지 못했습니다.");
        }
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) {
            throw bad("자산 version을 입력해 주세요.");
        }
    }

    private static void checkVersion(AssetAllocationDTO row, Long version) {
        if (!version.equals(row.getVersion())) {
            throw conflict("다른 사람이 먼저 자산을 변경했습니다. 새로 불러와 주세요.");
        }
    }

    private AssetAllocationDTO locked(LoginUser user,Long assetId) {
        requireId(assetId);
        AssetAllocationDTO row=dao.lock(user.getCompanyId(),assetId);
        if(row==null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"자산을 찾을 수 없습니다."); }
        return row;
    }
    
    private static AssetType kind(String type) {
        try { return AssetType.from(type); } catch(IllegalArgumentException e) { throw bad("장비 종류가 올바르지 않습니다."); }
    }
    
    private static void requireId(Long id) { if(id==null || id<=0) { throw bad("번호가 올바르지 않습니다."); } }
    
    private static void requireOperator(LoginUser user) {
        if(user==null || user.getCompanyId()==null || user.getCompanyId()<=0 || user.getEmployeeId()==null
                || user.getEmployeeId()<=0 || user.getUserAccountId()==null || user.getUserAccountId()<=0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
        }
        if(!"ROLE_IT".equals(user.getRole()) && !"ROLE_ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"IT 담당자 또는 관리자만 사용할 수 있습니다.");
        }
    }
    
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
