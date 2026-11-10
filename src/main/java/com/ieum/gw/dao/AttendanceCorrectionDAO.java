package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.AttendanceCorrectionDTO;
import com.ieum.gw.dto.AttendanceDTO;
import com.ieum.gw.mapper.AttendanceCorrectionMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AttendanceCorrectionDAO {

    private final AttendanceCorrectionMapper mapper;

    /** 직원의 그날 근태 1건을 잠그며 읽는다 */
    public AttendanceDTO selectAttendanceOnForUpdate(Long companyId, Long employeeId, String workDate) { return mapper.selectAttendanceOnForUpdate(companyId, employeeId, workDate); }

    /** 정정 요청 1건을 넣는다(원래 출퇴근은 근태 행에서 복사) */
    public int insertCorrection(AttendanceCorrectionDTO dto) { return mapper.insertCorrection(dto); }

    /** 결근 정정 요청 1건을 넣는다(요청한 출근·퇴근만) */
    public int insertAbsenceCorrection(AttendanceCorrectionDTO dto) { return mapper.insertAbsenceCorrection(dto); }

    /** 직원 입사일을 읽는다(비었으면 null) */
    public String selectHireDate(Long companyId, Long employeeId) { return mapper.selectHireDate(companyId, employeeId); }

    /** 직원 본인의 그 달 정정 요청을 읽는다 */
    public List<AttendanceCorrectionDTO> selectMyCorrections(Long companyId, Long employeeId, String month) { return mapper.selectMyCorrections(companyId, employeeId, month); }

    /** 정정 요청 1건과 요청자 부서장을 잠그며 읽는다 */
    public AttendanceCorrectionDTO selectCorrectionForUpdate(Long companyId, Long id) { return mapper.selectCorrectionForUpdate(companyId, id); }

    /** 이 사람이 처리할 대기 요청을 읽는다 */
    public List<AttendanceCorrectionDTO> selectInbox(Long companyId, Long approverId, boolean admin) { return mapper.selectInbox(companyId, approverId, admin); }

    /** 정정 요청 알림을 받을 업체 관리자 사원 번호를 읽는다(요청자 본인 제외) */
    public List<Long> selectAdminEmployeeIds(Long companyId, Long exceptEmployeeId) { return mapper.selectAdminEmployeeIds(companyId, exceptEmployeeId); }

    /** 대기 중인 요청을 요청자 본인이 취소한다 */
    public int cancelCorrection(Long companyId, Long id, Long employeeId) { return mapper.cancelCorrection(companyId, id, employeeId); }

    /** 대기 중인 요청을 승인·반려로 바꾼다 */
    public int decideCorrection(Long companyId, Long id, String status, Long approverId, String rejectReason) { return mapper.decideCorrection(companyId, id, status, approverId, rejectReason); }

    /** 승인한 요청의 퇴근 시각을 그날 근태에 넣는다 */
    public int applyCheckOut(Long companyId, Long id) { return mapper.applyCheckOut(companyId, id); }

    /** 결근 정정을 승인해 그날 근태를 만든다 */
    public int insertAttendanceFromCorrection(Long companyId, Long id) { return mapper.insertAttendanceFromCorrection(companyId, id); }
}
