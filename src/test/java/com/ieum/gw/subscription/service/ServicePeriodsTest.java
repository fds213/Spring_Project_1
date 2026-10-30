package com.ieum.gw.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.subscription.dto.CompanyUsageDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;

class ServicePeriodsTest {

    private static LocalDateTime at(String iso) {
        return LocalDateTime.parse(iso);
    }

    private static ServicePeriodDTO period(boolean trial, String startsAt, String endsAt, int seats) {
        ServicePeriodDTO p = new ServicePeriodDTO();
        p.setTrial(trial);
        p.setStartsAt(at(startsAt));
        p.setEndsAt(at(endsAt));
        p.setPurchasedSeats(seats);
        return p;
    }

    @Test
    @DisplayName("체험 만료는 날짜 기준: 10/30 15:20 신청이면 11/13 0시에 만료(마지막 날 11/12)")
    void trialEndsAtNextMidnightOfDay14() {
        assertEquals(at("2026-11-13T00:00"), ServicePeriods.trialEndsAt(at("2026-10-30T15:20")));
        assertEquals(at("2026-11-13T00:00"), ServicePeriods.trialEndsAt(at("2026-10-30T00:00")));
        assertEquals(at("2026-11-13T00:00"), ServicePeriods.trialEndsAt(at("2026-10-30T23:59:59")));
    }

    @Test
    @DisplayName("체험 만료는 달·해가 바뀌어도 신청일부터 14일, 윤년 2월은 29일까지 센다")
    void trialEndsAtAcrossMonthYearAndLeapDay() {
        assertEquals(at("2027-01-08T00:00"), ServicePeriods.trialEndsAt(at("2026-12-25T10:00")));
        assertEquals(at("2028-03-05T00:00"), ServicePeriods.trialEndsAt(at("2028-02-20T09:00")));
    }

    @Test
    @DisplayName("체험 마지막 날 23:59는 체험 중, 다음 날 0시부터 체험 만료")
    void trialBoundary() {
        ServicePeriodDTO trial = period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5);

        CompanyUsageDTO lastMinute = ServicePeriods.toUsage(
                ServicePeriods.pick(List.of(trial), at("2026-11-12T23:59:59")), null, at("2026-11-12T23:59:59"));
        assertTrue(lastMinute.isTrial());
        assertFalse(lastMinute.isExpired());

        CompanyUsageDTO expired = ServicePeriods.toUsage(
                ServicePeriods.pick(List.of(trial), at("2026-11-13T00:00")), null, at("2026-11-13T00:00"));
        assertTrue(expired.isTrial());
        assertTrue(expired.isExpired());
        assertEquals(at("2026-11-13T00:00"), expired.getEndsAt());
    }

    @Test
    @DisplayName("체험 중 결제하면 유료 기간이 겹쳐도 유료로 본다(PAY-02: 승인일부터 유료)")
    void paidDuringTrialWins() {
        ServicePeriodDTO trial = period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5);
        ServicePeriodDTO paid = period(false, "2026-11-05T10:00", "2026-12-05T10:00", 20);

        assertSame(paid, ServicePeriods.pick(List.of(trial, paid), at("2026-11-06T09:00")));
    }

    @Test
    @DisplayName("모든 기간이 끝났으면 가장 늦게 끝난 기간으로 만료 상태를 본다(유료 만료)")
    void allEndedPicksLatestEnd() {
        ServicePeriodDTO trial = period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5);
        ServicePeriodDTO paid = period(false, "2026-11-05T10:00", "2026-12-05T10:00", 20);
        LocalDateTime now = at("2026-12-10T09:00");

        CompanyUsageDTO usage = ServicePeriods.toUsage(ServicePeriods.pick(List.of(paid, trial), now), at("2026-10-30T16:00"), now);
        assertFalse(usage.isTrial());
        assertTrue(usage.isExpired());
        assertTrue(usage.isEmailVerified());
        assertEquals(at("2026-12-05T10:00"), usage.getEndsAt());
    }

    @Test
    @DisplayName("이용 기간이 없거나 아직 시작하지 않은 기간뿐이면 고를 기간이 없다")
    void noPeriod() {
        assertNull(ServicePeriods.pick(List.of(), at("2026-10-30T09:00")));
        assertNull(ServicePeriods.pick(
                List.of(period(false, "2026-11-01T00:00", "2026-12-01T00:00", 10)), at("2026-10-30T09:00")));
    }

    @Test
    @DisplayName("인원 한도: 체험은 신청 인원과 관계없이 10명, 유료는 구매 인원")
    void seatLimit() {
        assertEquals(10, ServicePeriods.seatLimitOf(period(true, "2026-10-30T15:20", "2026-11-13T00:00", 3)));
        assertEquals(25, ServicePeriods.seatLimitOf(period(false, "2026-11-05T10:00", "2026-12-05T10:00", 25)));
    }
}
