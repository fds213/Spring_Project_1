package com.ieum.gw.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ieum.gw.dto.AttendanceDTO;
import com.ieum.gw.dto.AttendanceSummaryDTO;
import com.ieum.gw.dto.AttendanceDeviceDTO;
import com.ieum.gw.dto.DevicePersonDTO;
import com.ieum.gw.dto.HolidayDTO;
import com.ieum.gw.dto.LeaveDayDTO;
import com.ieum.gw.dto.PunchDTO;
import com.ieum.gw.dto.TodayAttendanceDTO;
import com.ieum.gw.mapper.AttendanceMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AttendanceDAO {
    private final AttendanceMapper mapper;

    // 오늘 날짜·업체·사원

    /** 서울 기준 오늘 날짜(YYYY-MM-DD)를 DB 시각으로 받는다 */
    public String selectToday() { return mapper.selectToday(); }

    /** 단말기에 번호를 연결할 수 있는 업체 직원 목록을 읽는다 */
    public List<DevicePersonDTO> selectCompanyEmployees(Long companyId) { return mapper.selectCompanyEmployees(companyId); }

    /** 같은 업체 직원의 재직 상태를 읽는다(다른 업체 직원이면 null) */
    public String selectEmploymentStatus(Long companyId, Long employeeId) { return mapper.selectEmploymentStatus(companyId, employeeId); }

    /** 이용 기간이 모두 끝난 업체인지 본다(Mapper가 끝났으면 1을 준다) */
    public boolean isCompanyExpired(Long companyId) {
        return mapper.isCompanyExpired(companyId) == 1;
    }

    // 단말기

    /** 단말기 키 해시로 쓰는 중인 단말기를 찾는다 */
    public AttendanceDeviceDTO selectDeviceByKeyHash(String keyHash) { return mapper.selectDeviceByKeyHash(keyHash); }

    /** 업체의 단말기를 읽는다(여러 대면 먼저 등록한 것) */
    public AttendanceDeviceDTO selectCompanyDevice(Long companyId) { return mapper.selectCompanyDevice(companyId); }

    /** 업체의 첫 단말기를 등록한다 */
    public int insertDevice(Long companyId, String deviceName, String keyHash) { return mapper.insertDevice(companyId, deviceName, keyHash); }

    /** 단말기 키를 다시 발급한다(예전 키는 바로 못 쓴다) */
    public int updateDeviceKey(Long attendanceDeviceId, Long companyId, String keyHash) { return mapper.updateDeviceKey(attendanceDeviceId, companyId, keyHash); }

    /** 단말기에 등록된 사람 목록을 읽는다 */
    public List<DevicePersonDTO> selectDevicePeople(Long attendanceDeviceId) { return mapper.selectDevicePeople(attendanceDeviceId); }

    /** 단말기 사용자 번호로 등록된 사람 한 명을 찾는다 */
    public DevicePersonDTO selectDevicePerson(Long attendanceDeviceId, String deviceUserNo) { return mapper.selectDevicePerson(attendanceDeviceId, deviceUserNo); }

    // 단말기 사용자 번호 연결 (직원 본인)

    /** 업체 단말기에 연결된 내 사용자 번호를 읽는다 */
    public String selectMyDeviceUserNoInCompany(Long companyId, Long employeeId) { return mapper.selectMyDeviceUserNoInCompany(companyId, employeeId); }

    /** 같은 단말기에서 그 번호를 쓰는 다른 직원 수를 센다 */
    public int countDeviceUserNoOfOthers(Long attendanceDeviceId, String deviceUserNo, Long employeeId) { return mapper.countDeviceUserNoOfOthers(attendanceDeviceId, deviceUserNo, employeeId); }

    /** 단말기 사용자 번호를 직원에게 연결한다 */
    public int insertDeviceLink(Long companyId, Long attendanceDeviceId, String deviceUserNo, Long employeeId) { return mapper.insertDeviceLink(companyId, attendanceDeviceId, deviceUserNo, employeeId); }

    /** 본인의 단말기 사용자 번호 연결을 끊는다 */
    public int deleteMyDeviceLink(Long attendanceDeviceId, Long employeeId) { return mapper.deleteMyDeviceLink(attendanceDeviceId, employeeId); }

    // 출퇴근 찍기

    /** 출근을 기록한다(같은 날 두 번째 출근은 고유 제약으로 막힌다) */
    public int insertCheckIn(PunchDTO dto) { return mapper.insertCheckIn(dto); }

    /** 오늘 출근하고 아직 퇴근하지 않은 근태에 퇴근을 기록한다 */
    public int updateCheckOut(PunchDTO dto) { return mapper.updateCheckOut(dto); }

    /** 단말기에서 받은 출퇴근 이력을 남긴다 */
    public int insertEvent(PunchDTO dto) { return mapper.insertEvent(dto); }

    // 근태 조회

    /** 오늘 근태 한 줄을 읽는다 */
    public AttendanceDTO selectTodayAttendance(Long companyId, Long employeeId) { return mapper.selectTodayAttendance(companyId, employeeId); }

    /** 오늘 출퇴근 시각을 읽는다(헤더 표시) */
    public TodayAttendanceDTO selectTodayTimes(Long companyId, Long employeeId) { return mapper.selectTodayTimes(companyId, employeeId); }

    /** 한 달 근태를 읽는다(month = YYYY-MM) */
    public List<AttendanceDTO> selectMonthAttendance(Long companyId, Long employeeId, String month) { return mapper.selectMonthAttendance(companyId, employeeId, month); }

    /** 한 달 동안 최종 승인된 휴가 날짜를 읽는다 */
    public List<LeaveDayDTO> selectMonthLeaveDays(Long companyId, Long employeeId, String month) { return mapper.selectMonthLeaveDays(companyId, employeeId, month); }

    /** 근태·연차 관리용 업체 직원별 한 달 요약을 읽는다 */
    public List<AttendanceSummaryDTO> selectMonthSummary(Long companyId, String month, String lateAfter, String earlyBefore) { return mapper.selectMonthSummary(companyId, month, lateAfter, earlyBefore); }

    // 휴일

    /** 기간 안의 업체 휴일을 읽는다 */
    public List<HolidayDTO> selectHolidays(Long companyId, String from, String to) { return mapper.selectHolidays(companyId, from, to); }

    /** 휴일 자료가 들어 있는 해를 읽는다 */
    public List<Integer> selectHolidayYears(Long companyId, int fromYear, int toYear) { return mapper.selectHolidayYears(companyId, fromYear, toYear); }

    /** 휴일을 하나 넣는다(같은 날은 하나만) */
    public int insertHoliday(Long companyId, String holidayDate, String holidayName) { return mapper.insertHoliday(companyId, holidayDate, holidayName); }

    /** 휴일을 하나 지운다 */
    public int deleteHoliday(Long companyId, Long holidayId) { return mapper.deleteHoliday(companyId, holidayId); }
}
