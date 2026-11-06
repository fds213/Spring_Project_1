package com.ieum.gw.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.JoinCompanyDTO;
import com.ieum.gw.dto.JoinOpeningDTO;
import com.ieum.gw.dto.JoinRequestDTO;
import com.ieum.gw.dto.JoinRequestViewDTO;
import com.ieum.gw.mapper.JoinMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JoinDAO {

    private final JoinMapper mapper;

    /** ❶ 업체 코드 */
    public String getCompanyCode(Long companyId) {
        return mapper.getCompanyCode(companyId);
    }

    /** ❷ 내가 부서장인 부서 이름. 아니면 null */
    public String getHeadDeptName(Long companyId, Long employeeId) {
        return mapper.getHeadDeptName(companyId, employeeId);
    }

    /** ❸ 부서 목록 */
    public List<String> listDeptNames(Long companyId) {
        return mapper.listDeptNames(companyId);
    }

    /** ❹ 지금 가입이 열린 부서 */
    public List<JoinOpeningDTO> listOpenings(Long companyId) {
        return mapper.listOpenings(companyId);
    }

    /** ❺ 번호표 기계의 마지막 번호. 기계가 없으면 null */
    public Integer peekLastSerial(Long companyId) {
        return mapper.peekLastSerial(companyId);
    }

    /** 가입 열기. 바뀐 줄 수 (0이면 없는 부서) */
    public int openDept(Long companyId, String deptName, LocalDateTime until, Long openedBy) {
        return mapper.openDept(companyId, deptName, until, openedBy);
    }

    /** 한 부서 끄기 */
    public int closeDept(Long companyId, String deptName) {
        return mapper.closeDept(companyId, deptName);
    }

    /** 모두 끄기 */
    public int closeAll(Long companyId) {
        return mapper.closeAll(companyId);
    }

    /** 업체 코드로 업체 찾기 (계정 만들기 화면). 없으면 null */
    public JoinCompanyDTO findCompanyByCode(String companyCode) {
        return mapper.findCompanyByCode(companyCode);
    }

    /** 직급 목록 */
    public List<String> listGradeNames(Long companyId) {
        return mapper.listGradeNames(companyId);
    }

    /** 지금 가입이 열려 있는 부서의 ID (이름으로). 닫혔거나 없으면 null */
    public Long findOpenDeptId(Long companyId, String deptName) {
        return mapper.findOpenDeptId(companyId, deptName);
    }

    /** 직급 ID (이름으로). 없으면 null */
    public Long findGradeId(Long companyId, String gradeName) {
        return mapper.findGradeId(companyId, gradeName);
    }

    /** 같은 업체에서 이 이메일로 대기 중이거나 쓰는 계정 수 (반려·취소 제외) */
    public int countLiveEmail(Long companyId, String email) {
        return mapper.countLiveEmail(companyId, email);
    }

    /** 같은 업체에서 이 이메일의 비활성(RETIRED) 계정 수 */
    public int countRetiredEmail(Long companyId, String email) {
        return mapper.countRetiredEmail(companyId, email);
    }

    /** 가입 요청 1줄 저장. 성공하면 dto.accountRequestId에 새 번호 */
    public int insertRequest(JoinRequestDTO dto) {
        return mapper.insertRequest(dto);
    }

    /** 요청 1건 보기 (업체 코드와 요청 키 해시가 둘 다 맞아야). 없으면 null */
    public JoinRequestViewDTO getRequestView(String companyCode, String keyHash) {
        return mapper.getRequestView(companyCode, keyHash);
    }

    /** 요청 취소 (대기 중일 때만). 0이면 이미 처리된 요청 */
    public int cancelRequest(Long requestId, Long companyId) {
        return mapper.cancelRequest(requestId, companyId);
    }

    /** 그 요청의 계정도 취소 (대기 중일 때만) */
    public int cancelAccount(Long userAccountId, Long companyId) {
        return mapper.cancelAccount(userAccountId, companyId);
    }

    /** 가입 승인 화면: 승인 대기 목록 */
    public List<JoinRequestViewDTO> listWaitingRequests(Long companyId) {
        return mapper.listWaitingRequests(companyId);
    }

    /** 승인·반려 전에 요청 행 잠금 (동시에 두 번 처리 못 하게) */
    public JoinRequestViewDTO lockRequest(Long companyId, Long requestId) {
        return mapper.lockRequest(companyId, requestId);
    }

    /** 계정에 사원을 연결하고 사용 시작(ACTIVE) */
    public int activateAccount(Long companyId, Long userAccountId, Long employeeId) {
        return mapper.activateAccount(companyId, userAccountId, employeeId);
    }

    /** 요청 승인 완료 처리 */
    public int approveRequest(Long companyId, Long requestId, Long reviewerId) {
        return mapper.approveRequest(companyId, requestId, reviewerId);
    }

    /** 요청 반려 (대기 중일 때만) */
    public int rejectRequest(Long companyId, Long requestId, Long reviewerId) {
        return mapper.rejectRequest(companyId, requestId, reviewerId);
    }

    /** 반려된 요청의 계정도 반려 (대기 중일 때만) */
    public int rejectAccount(Long companyId, Long userAccountId) {
        return mapper.rejectAccount(companyId, userAccountId);
    }
}