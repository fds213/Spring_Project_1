package com.ieum.gw.attendance.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.attendance.dto.AttendanceDTO;
import com.ieum.gw.attendance.dto.AttendanceSummaryDTO;
import com.ieum.gw.attendance.dto.AttendanceDeviceDTO;
import com.ieum.gw.attendance.dto.DevicePersonDTO;
import com.ieum.gw.attendance.dto.HolidayDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.dto.PunchDTO;
import com.ieum.gw.attendance.dto.TodayAttendanceDTO;

@Mapper
public interface AttendanceMapper {

    // 서울 기준 오늘 (YYYY-MM-DD)
    String selectToday();

    // 단말기
    AttendanceDeviceDTO selectDeviceByKeyHash(String keyHash);

    AttendanceDeviceDTO selectCompanyDevice(Long companyId);

    int isCompanyExpired(Long companyId);

    int insertDevice(@Param("companyId") Long companyId, @Param("deviceName") String deviceName, @Param("keyHash") String keyHash);

    int updateDeviceKey(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("companyId") Long companyId, @Param("keyHash") String keyHash);

    List<DevicePersonDTO> selectCompanyEmployees(Long companyId);

    String selectEmploymentStatus(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    List<DevicePersonDTO> selectDevicePeople(Long attendanceDeviceId);

    DevicePersonDTO selectDevicePerson(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("deviceUserNo") String deviceUserNo);

    // 출퇴근 기록
    int insertCheckIn(PunchDTO dto);

    int updateCheckOut(PunchDTO dto);

    int insertEvent(PunchDTO dto);

    AttendanceDTO selectTodayAttendance(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    List<AttendanceDTO> selectMonthAttendance(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("month") String month);

    TodayAttendanceDTO selectTodayTimes(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    // 휴일 (from·to 는 YYYY-MM-DD, 둘 다 포함)
    List<HolidayDTO> selectHolidays(@Param("companyId") Long companyId, @Param("from") String from, @Param("to") String to);

    List<Integer> selectHolidayYears(@Param("companyId") Long companyId, @Param("fromYear") int fromYear, @Param("toYear") int toYear);

    int insertHoliday(@Param("companyId") Long companyId, @Param("holidayDate") String holidayDate, @Param("holidayName") String holidayName);

    int deleteHoliday(@Param("companyId") Long companyId, @Param("holidayId") Long holidayId);

    // 근태·연차 관리: 업체 직원별 한 달 요약. lateAfter 보다 늦은 출근은 지각, earlyBefore 보다 이른 퇴근은 조퇴 (HH24:MI:SS)
    List<AttendanceSummaryDTO> selectMonthSummary(@Param("companyId") Long companyId, @Param("month") String month,
                                                  @Param("lateAfter") String lateAfter, @Param("earlyBefore") String earlyBefore);

    List<LeaveDayDTO> selectMonthLeaveDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("month") String month);

    // 본인과 단말기 사용자 번호 연결
    String selectMyDeviceUserNoInCompany(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    int countDeviceUserNoOfOthers(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("deviceUserNo") String deviceUserNo, @Param("employeeId") Long employeeId);

    int deleteMyDeviceLink(@Param("attendanceDeviceId") Long attendanceDeviceId, @Param("employeeId") Long employeeId);

    int insertDeviceLink(@Param("companyId") Long companyId, @Param("attendanceDeviceId") Long attendanceDeviceId,
                         @Param("deviceUserNo") String deviceUserNo, @Param("employeeId") Long employeeId);
}
