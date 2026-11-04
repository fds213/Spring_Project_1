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

    public Long findOpenDeptId(Long companyId, String deptName) {
        return mapper.findOpenDeptId(companyId, deptName);
    }

    public Long findGradeId(Long companyId, String gradeName) {
        return mapper.findGradeId(companyId, gradeName);
    }

    public int countLiveEmail(Long companyId, String email) {
        return mapper.countLiveEmail(companyId, email);
    }

    public int countRetiredEmail(Long companyId, String email) {
        return mapper.countRetiredEmail(companyId, email);
    }

    public int insertRequest(JoinRequestDTO dto) {
        return mapper.insertRequest(dto);
    }

    public JoinRequestViewDTO getRequestView(String companyCode, String keyHash) {
        return mapper.getRequestView(companyCode, keyHash);
    }

    public int cancelRequest(Long requestId, Long companyId) {
        return mapper.cancelRequest(requestId, companyId);
    }

    public int cancelAccount(Long userAccountId, Long companyId) {
        return mapper.cancelAccount(userAccountId, companyId);
    }

    public List<JoinRequestViewDTO> listWaitingRequests(Long companyId) {
        return mapper.listWaitingRequests(companyId);
    }

    public JoinRequestViewDTO lockRequest(Long companyId, Long requestId) {
        return mapper.lockRequest(companyId, requestId);
    }

    public int activateAccount(Long companyId, Long userAccountId, Long employeeId) {
        return mapper.activateAccount(companyId, userAccountId, employeeId);
    }

    public int approveRequest(Long companyId, Long requestId, Long reviewerId) {
        return mapper.approveRequest(companyId, requestId, reviewerId);
    }

    public int rejectRequest(Long companyId, Long requestId, Long reviewerId) {
        return mapper.rejectRequest(companyId, requestId, reviewerId);
    }

    public int rejectAccount(Long companyId, Long userAccountId) {
        return mapper.rejectAccount(companyId, userAccountId);
    }
}