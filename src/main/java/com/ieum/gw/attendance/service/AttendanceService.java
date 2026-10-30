package com.ieum.gw.attendance.service;

import com.ieum.gw.attendance.dto.HolidayDTO;
import com.ieum.gw.attendance.dto.LeaveDayDTO;
import com.ieum.gw.attendance.dto.TodayAttendanceDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 근태가 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 * 결재가 부르는 메서드는 결재 트랜잭션 안에서 불린다. 따로 트랜잭션을 열지 않고(REQUIRES_NEW 금지),
 * 안 되면 예외를 던져 결재까지 함께 취소되게 한다.
 */
@Service
public class AttendanceService {

    /**
     * 휴가 기간을 하루씩 나눈다. 주말과 휴일(HOLIDAY)은 뺀다. 결재가 상신할 때 부른다.
     * 반차는 fromDate와 toDate가 같은 날이어야 한다. 휴일 초기 데이터가 없는 기간이 끼어 있거나
     * 남는 날이 없으면 IllegalArgumentException(계획서 4장 '근태·휴가의 시연 기준').
     *
     * @param leaveType LV_FULL(종일 연차)·LV_AM(오전 반차)·LV_PM(오후 반차)
     * @return 날짜순. leaveRequestDayId는 비어 있다(결재가 저장한 뒤 채운다)
     */
    public List<LeaveDayDTO> calculateLeaveDays(Long companyId, String leaveType, LocalDate fromDate, LocalDate toDate) {
        // TODO(홍동국): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 홍동국");
    }

    /**
     * 오늘이 속한 연차 주기의 남은 일수(부여 일수 + 사용·복원 내역 합). 주기가 없으면 0.
     * 승인 대기 중인 휴가는 결재 쪽 자료라 여기서 빼지 않는다.
     */
    public BigDecimal getRemainingLeaveDays(Long companyId, Long employeeId) {
        // TODO(홍동국): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 홍동국");
    }

    /**
     * 이미 승인된 휴가와 날짜·시간대가 겹치는지 본다. 종일 연차는 오전·오후를 모두 쓴 것으로 본다.
     * 예: 승인된 오전 반차가 있는 날에 종일 연차나 오전 반차를 내면 true, 오후 반차면 false.
     * 결재가 상신할 때 부른다. 아직 승인되지 않은 문서끼리의 겹침은 보지 않는다(결재 쪽 자료).
     */
    public boolean hasApprovedLeaveOverlap(Long companyId, Long employeeId, List<LeaveDayDTO> days) {
        // TODO(홍동국): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 홍동국");
    }

    /**
     * 휴가 최종 승인 때 연차를 차감한다. 결재 최종 승인 트랜잭션 안에서 부른다.
     * 연차 주기 행(LEAVE_CYCLE)을 SELECT ... FOR UPDATE로 잠근 뒤 남은 일수와 날짜 겹침을 다시 확인하고,
     * 하루마다 시간대(LEAVE_SLOT)와 사용 내역(LEAVE_LEDGER, LC_USE)을 저장한다.
     * 남은 일수가 모자라거나 겹치면 IllegalStateException(결재도 함께 취소된다).
     *
     * @param employeeId     휴가를 쓰는 사람(기안자)
     * @param actorAccountId 최종 승인한 사람의 계정 ID(LoginUser.getUserAccountId())
     * @param days           결재가 저장한 휴가 날짜 행(leaveRequestDayId가 채워져 있어야 한다)
     */
    public void useLeave(Long companyId, Long employeeId, Long actorAccountId, List<LeaveDayDTO> days) {
        // TODO(홍동국): 구현. 구현 전에는 아무것도 하지 않는다(결재는 끝나고 연차만 줄지 않는다).
    }

    /**
     * 새 사원에게 첫 연차 주기와 기본 15일을 준다(요구분석서 3.9 '최초 부여').
     * 사원을 만드는 쪽(직원 가입 승인, 체험 신청의 관리자 계정)이 같은 트랜잭션 안에서 부른다.
     *
     * @param hireDate 입사일. 이 날부터 첫 연차 주기를 센다
     */
    public void grantInitialLeave(Long companyId, Long employeeId, LocalDate hireDate) {
        // TODO(홍동국): 구현. 구현 전에는 아무것도 하지 않는다(연차 주기가 생기지 않는다).
    }

    /**
     * 기간 안의 휴일 목록(날짜순). 일정 달력이 부른다.
     */
    public List<HolidayDTO> getHolidays(Long companyId, LocalDate fromDate, LocalDate toDate) {
        // TODO(홍동국): 구현. 구현 전에는 빈 목록을 돌려준다.
        return List.of();
    }

    /**
     * 오늘(서버 날짜) 출퇴근 기록. 출근 기록이 없으면 null. 헤더의 출퇴근 표시가 요청마다 부른다(AGENTS.md 72줄).
     */
    public TodayAttendanceDTO getTodayAttendance(Long companyId, Long employeeId) {
        // TODO(홍동국): 구현. 구현 전에는 null(출근 기록 없음)을 돌려준다.
        return null;
    }
}
