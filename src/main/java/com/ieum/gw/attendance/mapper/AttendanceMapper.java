package com.ieum.gw.attendance.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.attendance.dto.AttendanceDTO;
import com.ieum.gw.attendance.dto.AttendanceDeviceDTO;
import com.ieum.gw.attendance.dto.DevicePersonDTO;
import com.ieum.gw.attendance.dto.HolidayDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.dto.PunchDTO;

@Mapper
public interface AttendanceMapper {

    // 서울 기준 오늘 (YYYY-MM-DD)
    String selectToday();

    // 단말기
    AttendanceDeviceDTO selectDeviceByKeyHash(String keyHash);

    AttendanceDeviceDTO selectCompanyDevice(Long companyId);

    int isCompanyExpired(Long companyId);

    List<DevicePersonDTO> selectDevicePeople(Long attendanceDeviceId);

    DevicePersonDTO selectDevicePerson(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("deviceUserNo") String deviceUserNo);

    // 출퇴근 기록
    int insertCheckIn(PunchDTO dto);

    int updateCheckOut(PunchDTO dto);

    int insertEvent(PunchDTO dto);

    AttendanceDTO selectTodayAttendance(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    List<AttendanceDTO> selectMonthAttendance(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("month") String month);

    List<HolidayDTO> selectMonthHolidays(@Param("companyId") Long companyId, @Param("month") String month);

    List<LeaveDayDTO> selectMonthLeaveDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("month") String month);

    // 본인과 단말기 사용자 번호 연결
    String selectMyDeviceUserNoInCompany(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    String selectMyDeviceUserNo(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("employeeId") Long employeeId);

    int countDeviceUserNoOfOthers(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("deviceUserNo") String deviceUserNo, @Param("employeeId") Long employeeId);

    int deleteMyDeviceLink(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("employeeId") Long employeeId);

    int insertDeviceLink(@Param("companyId") Long companyId, @Param("attendanceDeviceId") Long attendanceDeviceId,
                         @Param("deviceUserNo") String deviceUserNo, @Param("employeeId") Long employeeId);
}
