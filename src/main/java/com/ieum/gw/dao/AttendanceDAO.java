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

    public String selectToday() {
        return mapper.selectToday();
    }

    public AttendanceDeviceDTO selectDeviceByKeyHash(String keyHash) {
        return mapper.selectDeviceByKeyHash(keyHash);
    }

    public AttendanceDeviceDTO selectCompanyDevice(Long companyId) {
        return mapper.selectCompanyDevice(companyId);
    }

    public boolean isCompanyExpired(Long companyId) {
        return mapper.isCompanyExpired(companyId) == 1;
    }

    public int insertDevice(Long companyId, String deviceName, String keyHash) {
        return mapper.insertDevice(companyId, deviceName, keyHash);
    }

    public int updateDeviceKey(Long attendanceDeviceId, Long companyId, String keyHash) {
        return mapper.updateDeviceKey(attendanceDeviceId, companyId, keyHash);
    }

    public List<DevicePersonDTO> selectCompanyEmployees(Long companyId) {
        return mapper.selectCompanyEmployees(companyId);
    }

    public String selectEmploymentStatus(Long companyId, Long employeeId) {
        return mapper.selectEmploymentStatus(companyId, employeeId);
    }

    public List<DevicePersonDTO> selectDevicePeople(Long attendanceDeviceId) {
        return mapper.selectDevicePeople(attendanceDeviceId);
    }

    public DevicePersonDTO selectDevicePerson(Long attendanceDeviceId, String deviceUserNo) {
        return mapper.selectDevicePerson(attendanceDeviceId, deviceUserNo);
    }

    public int insertCheckIn(PunchDTO dto) {
        return mapper.insertCheckIn(dto);
    }

    public int updateCheckOut(PunchDTO dto) {
        return mapper.updateCheckOut(dto);
    }

    public int insertEvent(PunchDTO dto) {
        return mapper.insertEvent(dto);
    }

    public AttendanceDTO selectTodayAttendance(Long companyId, Long employeeId) {
        return mapper.selectTodayAttendance(companyId, employeeId);
    }

    public List<AttendanceDTO> selectMonthAttendance(Long companyId, Long employeeId, String month) {
        return mapper.selectMonthAttendance(companyId, employeeId, month);
    }

    public TodayAttendanceDTO selectTodayTimes(Long companyId, Long employeeId) {
        return mapper.selectTodayTimes(companyId, employeeId);
    }

    public List<HolidayDTO> selectHolidays(Long companyId, String from, String to) {
        return mapper.selectHolidays(companyId, from, to);
    }

    public List<Integer> selectHolidayYears(Long companyId, int fromYear, int toYear) {
        return mapper.selectHolidayYears(companyId, fromYear, toYear);
    }

    public int insertHoliday(Long companyId, String holidayDate, String holidayName) {
        return mapper.insertHoliday(companyId, holidayDate, holidayName);
    }

    public int deleteHoliday(Long companyId, Long holidayId) {
        return mapper.deleteHoliday(companyId, holidayId);
    }

    public List<AttendanceSummaryDTO> selectMonthSummary(Long companyId, String month, String lateAfter, String earlyBefore) {
        return mapper.selectMonthSummary(companyId, month, lateAfter, earlyBefore);
    }

    public List<LeaveDayDTO> selectMonthLeaveDays(Long companyId, Long employeeId, String month) {
        return mapper.selectMonthLeaveDays(companyId, employeeId, month);
    }

    public String selectMyDeviceUserNoInCompany(Long companyId, Long employeeId) {
        return mapper.selectMyDeviceUserNoInCompany(companyId, employeeId);
    }

    public int countDeviceUserNoOfOthers(Long attendanceDeviceId, String deviceUserNo, Long employeeId) {
        return mapper.countDeviceUserNoOfOthers(attendanceDeviceId, deviceUserNo, employeeId);
    }

    public int deleteMyDeviceLink(Long attendanceDeviceId, Long employeeId) {
        return mapper.deleteMyDeviceLink(attendanceDeviceId, employeeId);
    }

    public int insertDeviceLink(Long companyId, Long attendanceDeviceId, String deviceUserNo, Long employeeId) {
        return mapper.insertDeviceLink(companyId, attendanceDeviceId, deviceUserNo, employeeId);
    }
}
