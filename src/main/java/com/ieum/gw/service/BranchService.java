package com.ieum.gw.service;

import com.ieum.gw.dto.BranchDTO;
import com.ieum.gw.dto.BranchDetailDTO;
import com.ieum.gw.dto.BranchSaveRequestDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dao.BranchDAO;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 지점이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 지도도 location 모듈이지만 담당이 달라 지점은 이 클래스로 나눈다.
 */
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchDAO dao;

    /**
     * 사용 중(USE_YN = 'Y')인 지점 목록(이름순). 장비 신청의 수령 지점, 티켓의 접수 지점을 고를 때 쓴다.
     */
    @Transactional(readOnly = true)
    public List<BranchDTO> getBranches(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("업체 정보가 필요합니다.");
        }
        return dao.selectActiveBranches(companyId);
    }

    /**
     * 기존 자산·티켓의 이름과 주소를 표시할 때 호출한다. 사용 중지한 지점도 포함한다.
     * 입력 업체 ID는 호출한 모듈이 인증 정보에서 가져오며, SQL에서도 같은 업체만 읽는다.
     * 새 접수·장비 신청 선택 목록에는 기존 getBranches를 사용한다.
     */
    @Transactional(readOnly = true)
    public List<BranchDetailDTO> getBranchDetails(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("업체 정보가 필요합니다.");
        }
        return dao.selectBranchDetails(companyId);
    }
    
    /** 업체 관리자가 같은 업체의 사용 중·사용 중지 지점을 조회한다. */
    @Transactional(readOnly = true)
    public List<BranchDetailDTO> getAdminBranches(LoginUser user) {
    	return dao.selectBranchDetails(requireBranchAdmin(user));
    }
    
    /** 업체 관리자가 소속 업체에 새 지점을 등록한다. */
    @Transactional
    public void createBranch(LoginUser user, BranchSaveRequestDTO request) {
    	Long companyId = requireBranchAdmin(user);
    	String branchName = branchText(request.getBranchName(), "지점명", 100, true);
    	String address = branchText(request.getAddress(), "주소", 500, true);
    	String detailAddress = branchText(request.getDetailAddress(), "상세 주소", 200, false);
    	
    	try {
			if (dao.insertBranch(companyId, branchName, address, detailAddress) != 1) {
				throw new IllegalStateException("지점 등록 결과를 확인할 수 없습니다.");
			}
		} catch (DuplicateKeyException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 이름의 지점이 이미 있습니다.");
		}
    }
    
    /** 업체 관리자가 소속 업체의 지점명과 주소를 수정한다. */
    @Transactional
    public void updateBranch(LoginUser user, Long branchId, BranchSaveRequestDTO request) {
    	Long companyId = requireBranchAdmin(user);
    	
    	if (branchId == null || branchId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지점 번호를 확인해 주세요.");
		}
    	
    	String branchName = branchText(request.getBranchName(), "지점명", 100, true);
    	String address = branchText(request.getAddress(), "주소", 500, true);
    	String detailAddress = branchText(request.getDetailAddress(), "상세 주소", 200, false);
    	
    	try {
            if (dao.updateBranch(companyId, branchId, branchName, address, detailAddress) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지점을 찾을 수 없습니다.");
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 이름의 지점이 이미 있습니다.");
        }
    }
    
    /** 업체 관리자 권한을 확인하고 로그인한 업체 ID를 반환한다. */
    private Long requireBranchAdmin(LoginUser user) {
    	if (user == null || user.getCompanyId() == null || user.getCompanyId() <= 0) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
    	
    	if (!"ROLE_ADMIN".equals(user.getRole())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "업체 관리자만 지점을 관리할 수 있습니다.");
		}
    	
    	return user.getCompanyId();
    }
    
    /** 입력값의 앞뒤 공백을 제거하고 필수 여부와 최대 길이를 검사한다. */
    private String branchText(String value, String fieldName, int maxLength, boolean required) {
    	String text = value == null ? "" : value.strip();
    	
    	if (text.isEmpty()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "을 입력해 주세요.");
            }
            
            return null;
        }
    	
        if (text.codePointCount(0, text.length()) > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "은 " + maxLength + "자 이하로 입력해 주세요.");
        }
    	
    	return text;
    }
    
    /** 미지급 장비가 없는 지점을 사용 중지한다. */
    @Transactional
    public void disableBranch(LoginUser user, Long branchId) {
    	Long companyId = requireBranchAdmin(user);
    	
    	if (branchId == null || branchId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지점 번호를 확인해 주세요.");
        }
        if (dao.lockBranch(companyId, branchId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지점을 찾을 수 없습니다.");
        }
        if ("N".equals(dao.selectBranchUseYn(companyId, branchId))) {
            return;
        }

        int pending = dao.countPendingEquipmentTickets(companyId, branchId);
        
        if (pending > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "미지급 장비 요청이 " + pending + "건 있습니다. 지급을 완료한 뒤 사용 중지해 주세요.");
        }
        if (dao.disableBranch(companyId, branchId) != 1) {
            throw new IllegalStateException("지점 사용 중지 결과를 확인할 수 없습니다.");
        }
    }
    
    /** 지급 티켓 생성 트랜잭션에서 수령 지점을 잠그고 사용 상태를 확인한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveBranchForEquipmentTicket(Long companyId, Long branchId) {
        return dao.lockBranch(companyId, branchId) != null && "Y".equals(dao.selectBranchUseYn(companyId, branchId));
    }
}
