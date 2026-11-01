package com.ieum.gw.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.attendance.service.LeaveService;
import com.ieum.gw.attendance.service.LeaveService.Cycle;

/** 연차 계산 (요구분석서 3.9, AGENTS.md 테스트: 연차 3년 경계) */
class LeaveCycleTest {

    @Test
    @DisplayName("총 경력 3년 미만은 15일, 3년 이상 16일, 6년 이상 17일")
    void grantBoundary() {
        assertEquals(BigDecimal.valueOf(15), LeaveService.grantFor(35));
        assertEquals(BigDecimal.valueOf(16), LeaveService.grantFor(36));
        assertEquals(BigDecimal.valueOf(16), LeaveService.grantFor(71));
        assertEquals(BigDecimal.valueOf(17), LeaveService.grantFor(72));
    }

    @Test
    @DisplayName("주기는 가장 최근 입사기념일부터 다음 입사기념일 전까지, 경력은 그 시작일 기준")
    void cycleFromAnniversary() {
        // 2024-03-04 입사, 이전 경력 48개월 → 2026-03-04 주기: 경력 48 + 24 = 72개월(6년) → 17일
        Cycle c = LeaveService.cycleFor(LocalDate.of(2024, 3, 4), 48, LocalDate.of(2026, 10, 30));
        assertEquals(LocalDate.of(2026, 3, 4), c.start);
        assertEquals(LocalDate.of(2027, 3, 4), c.end);
        assertEquals(72, c.careerMonths);
        assertEquals(BigDecimal.valueOf(17), c.grant);
    }

    @Test
    @DisplayName("올해 입사기념일 전이면 작년 기념일부터가 이번 주기다")
    void beforeAnniversary() {
        Cycle c = LeaveService.cycleFor(LocalDate.of(2025, 11, 15), 0, LocalDate.of(2026, 10, 30));
        assertEquals(LocalDate.of(2025, 11, 15), c.start);
        assertEquals(LocalDate.of(2026, 11, 15), c.end);
        assertEquals(BigDecimal.valueOf(15), c.grant);
    }

    @Test
    @DisplayName("2월 29일 입사자는 평년에 2월 28일을 기념일로 쓴다")
    void leapDayHire() {
        Cycle c = LeaveService.cycleFor(LocalDate.of(2024, 2, 29), 0, LocalDate.of(2026, 10, 30));
        assertEquals(LocalDate.of(2026, 2, 28), c.start);
        assertEquals(LocalDate.of(2027, 2, 28), c.end);
        assertEquals(24, c.careerMonths);
    }

    @Test
    @DisplayName("2월 29일 입사자도 3년째 기념일(2월 28일)부터 16일이다 (경력을 한 달 모자라게 세지 않는다)")
    void leapDayHireThirdYear() {
        Cycle c = LeaveService.cycleFor(LocalDate.of(2024, 2, 29), 0, LocalDate.of(2027, 6, 1));
        assertEquals(LocalDate.of(2027, 2, 28), c.start);
        assertEquals(36, c.careerMonths);
        assertEquals(BigDecimal.valueOf(16), c.grant);
    }

    @Test
    @DisplayName("입사일이 아직 오지 않았으면 입사일부터가 첫 주기이고 경력은 이전 경력뿐이다")
    void beforeHire() {
        Cycle c = LeaveService.cycleFor(LocalDate.of(2026, 12, 1), 6, LocalDate.of(2026, 10, 30));
        assertEquals(LocalDate.of(2026, 12, 1), c.start);
        assertEquals(LocalDate.of(2027, 12, 1), c.end);
        assertEquals(6, c.careerMonths);
    }
}
