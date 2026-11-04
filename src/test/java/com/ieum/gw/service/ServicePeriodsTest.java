package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.dto.CompanyBillingDTO;
import com.ieum.gw.dto.CompanyPeriodDTO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.ServicePeriodDTO;

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
    @DisplayName("연장 만료일: 기준일이 없는 달은 말일로 맞추고 다음 달에는 기준일로 돌아온다")
    void extendedEndsAtKeepsAnchorDay() {
        LocalDate anchor = LocalDate.of(2027, 1, 31);
        assertEquals(at("2027-03-31T00:00"), ServicePeriods.extendedEndsAt(LocalDate.of(2027, 2, 28), anchor, 1));
        assertEquals(at("2027-04-30T00:00"), ServicePeriods.extendedEndsAt(LocalDate.of(2027, 3, 31), anchor, 1));
        assertEquals(at("2027-05-31T00:00"), ServicePeriods.extendedEndsAt(LocalDate.of(2027, 4, 30), anchor, 1));
        assertEquals(at("2028-02-29T00:00"), ServicePeriods.extendedEndsAt(LocalDate.of(2027, 2, 28), anchor, 12));
    }

    @Test
    @DisplayName("연장 만료일: 2/29 기준 연간은 평년에 2/28이고 윤년에 2/29로 돌아온다")
    void extendedEndsAtLeapDay() {
        LocalDate anchor = LocalDate.of(2028, 2, 29);
        assertEquals(at("2030-02-28T00:00"), ServicePeriods.extendedEndsAt(LocalDate.of(2029, 2, 28), anchor, 12));
        assertEquals(at("2032-02-29T00:00"), ServicePeriods.extendedEndsAt(LocalDate.of(2031, 2, 28), anchor, 12));
    }

    @Test
    @DisplayName("바로 이어지는 유료 기간이 있으면 그 끝까지를 만료로 본다. 원본은 바꾸지 않는다")
    void pickThroughRenewals() {
        ServicePeriodDTO first = period(false, "2026-11-02T14:00", "2026-12-02T00:00", 5);
        ServicePeriodDTO second = period(false, "2026-12-02T00:00", "2027-01-02T00:00", 5);
        ServicePeriodDTO third = period(false, "2027-01-02T00:00", "2027-02-02T00:00", 5);
        first.setBillingAnchorDate(LocalDate.of(2026, 11, 2));
        first.setPlan("MONTHLY");

        ServicePeriodDTO picked = ServicePeriods.pick(List.of(third, first, second), at("2026-11-20T10:00"));

        assertEquals(at("2027-02-02T00:00"), picked.getEndsAt());
        assertEquals(at("2026-11-02T14:00"), picked.getStartsAt());
        assertEquals(5, picked.getPurchasedSeats());
        assertEquals(LocalDate.of(2026, 11, 2), picked.getBillingAnchorDate());
        assertEquals("MONTHLY", picked.getPlan());
        assertEquals(at("2026-12-02T00:00"), first.getEndsAt());
    }

    @Test
    @DisplayName("연장해 뒀으면 앞 기간의 마지막 날에도 유료이고, 경계에서 끊기지 않는다")
    void renewalBoundary() {
        List<ServicePeriodDTO> periods = List.of(
                period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5),
                period(false, "2026-11-02T14:00", "2026-12-02T00:00", 5),
                period(false, "2026-12-02T00:00", "2027-01-02T00:00", 5));

        for (String now : List.of("2026-12-01T23:59:59", "2026-12-02T00:00", "2027-01-01T23:59:59")) {
            ServicePeriodDTO picked = ServicePeriods.pick(periods, at(now));
            assertEquals("PAID", ServicePeriods.statusOf(picked, at(now)), now);
            assertEquals(at("2027-01-02T00:00"), picked.getEndsAt(), now);
            assertFalse(ServicePeriods.toUsage(picked, null, at(now)).isExpired(), now);
        }
        assertEquals("2027.01.01", ServicePeriods.toBilling(ServicePeriods.pick(periods, at("2026-11-20T10:00")),
                at("2026-11-20T10:00")).getEndDate());
        assertEquals(42L, ServicePeriods.daysLeft(ServicePeriods.pick(periods, at("2026-11-20T10:00")), at("2026-11-20T10:00")));

        ServicePeriodDTO after = ServicePeriods.pick(periods, at("2027-01-02T00:00"));
        assertEquals("EXPIRED", ServicePeriods.statusOf(after, at("2027-01-02T00:00")));
    }

    @Test
    @DisplayName("끊어진 기간은 잇지 않고, 체험은 늘리지 않으며, 연장이 없으면 그 기간 그대로다")
    void pickDoesNotChain() {
        ServicePeriodDTO paid = period(false, "2026-11-02T14:00", "2026-12-02T00:00", 5);
        ServicePeriodDTO later = period(false, "2026-12-05T10:00", "2027-01-05T00:00", 5);
        assertSame(paid, ServicePeriods.pick(List.of(paid, later), at("2026-11-20T10:00")));

        ServicePeriodDTO trial = period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5);
        ServicePeriodDTO afterTrial = period(false, "2026-11-13T00:00", "2026-12-13T00:00", 5);
        assertSame(trial, ServicePeriods.pick(List.of(trial, afterTrial), at("2026-11-01T10:00")));

        assertSame(paid, ServicePeriods.pick(List.of(paid), at("2026-11-20T10:00")));
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

    @Test
    @DisplayName("유료 만료는 승인일을 첫날로 달력 1개월 뒤 0시다: 11/2 승인 → 12/2 0시 만료, 마지막 날 12/1")
    void paidEndsAt() {
        LocalDateTime endsAt = ServicePeriods.paidEndsAt(LocalDate.of(2026, 11, 2), 1);

        assertEquals(LocalDateTime.of(2026, 12, 2, 0, 0), endsAt);
        assertEquals(LocalDate.of(2026, 12, 1), ServicePeriods.lastDay(endsAt));
    }

    @Test
    @DisplayName("같은 날이 없는 달은 말일로 맞춘다: 1/31 → 2/28, 윤년 1/31 → 2/29, 연간 2/29 → 다음 해 2/28")
    void paidEndsAtMonthEnd() {
        assertEquals(LocalDateTime.of(2027, 2, 28, 0, 0), ServicePeriods.paidEndsAt(LocalDate.of(2027, 1, 31), 1));
        assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0), ServicePeriods.paidEndsAt(LocalDate.of(2028, 1, 31), 1));
        assertEquals(LocalDateTime.of(2029, 2, 28, 0, 0), ServicePeriods.paidEndsAt(LocalDate.of(2028, 2, 29), 12));
    }

    @Test
    @DisplayName("체험 중 결제하면 유료 기간이 체험 위에 겹치고, 유료로 본다")
    void pickPaidOverTrial() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);
        ServicePeriodDTO trial = new ServicePeriodDTO();
        trial.setTrial(true);
        trial.setStartsAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        trial.setEndsAt(LocalDateTime.of(2026, 11, 15, 0, 0));
        ServicePeriodDTO paid = new ServicePeriodDTO();
        paid.setStartsAt(LocalDateTime.of(2026, 11, 5, 10, 0));
        paid.setEndsAt(ServicePeriods.paidEndsAt(LocalDate.of(2026, 11, 5), 1));

        assertEquals(paid, ServicePeriods.pick(List.of(trial, paid), now));
        assertEquals(paid, ServicePeriods.pick(List.of(trial, paid), LocalDateTime.of(2026, 12, 4, 23, 59)));
        assertEquals(paid, ServicePeriods.pick(List.of(trial, paid), LocalDateTime.of(2026, 12, 5, 0, 0)), "만료 뒤에도 마지막 기간은 유료");
    }

    @Test
    @DisplayName("헤더 값: 체험 11/13 0시 만료면 마지막 날은 11/12이고, 그날은 0일 남음, 11/13 0시부터 만료")
    void billingTrialBoundary() {
        ServicePeriodDTO trial = period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5);

        CompanyBillingDTO fourDaysLeft = ServicePeriods.toBilling(trial, at("2026-11-08T09:00"));
        assertEquals("TRIAL", fourDaysLeft.getStatus());
        assertEquals("2026.11.12", fourDaysLeft.getEndDate());
        assertEquals(4, fourDaysLeft.getDaysLeft());

        CompanyBillingDTO lastDay = ServicePeriods.toBilling(trial, at("2026-11-12T23:59:59"));
        assertEquals("TRIAL", lastDay.getStatus());
        assertEquals(0, lastDay.getDaysLeft());

        CompanyBillingDTO expired = ServicePeriods.toBilling(trial, at("2026-11-13T00:00"));
        assertEquals("EXPIRED", expired.getStatus());
        assertEquals("2026.11.12", expired.getEndDate());
        assertEquals(0, expired.getDaysLeft());
    }

    @Test
    @DisplayName("헤더 값: 유료 중이면 PAID, 유료 기간이 끝나면 체험과 같이 EXPIRED")
    void billingPaid() {
        ServicePeriodDTO paid = period(false, "2026-11-02T14:00", "2026-12-02T00:00", 12);

        CompanyBillingDTO using = ServicePeriods.toBilling(paid, at("2026-12-01T23:59"));
        assertEquals("PAID", using.getStatus());
        assertEquals("2026.12.01", using.getEndDate());
        assertEquals(0, using.getDaysLeft());

        assertEquals("EXPIRED", ServicePeriods.toBilling(paid, at("2026-12-02T00:00")).getStatus());
    }

    @Test
    @DisplayName("헤더 값: 만료의 체험과 유료는 상태가 같아 paid로 가른다")
    void billingExpiredTrialOrPaid() {
        ServicePeriodDTO trial = period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5);
        ServicePeriodDTO paid = period(false, "2026-11-02T14:00", "2026-12-02T00:00", 12);

        CompanyBillingDTO trialExpired = ServicePeriods.toBilling(trial, at("2026-11-13T00:00"));
        assertEquals("EXPIRED", trialExpired.getStatus());
        assertFalse(trialExpired.isPaid());

        // 체험 중 결제한 업체의 유료 기간이 끝나면 체험이 아니라 유료 기간을 본다
        LocalDateTime afterPaid = at("2026-12-02T00:00");
        CompanyBillingDTO paidExpired = ServicePeriods.toBilling(ServicePeriods.pick(List.of(trial, paid), afterPaid), afterPaid);
        assertEquals("EXPIRED", paidExpired.getStatus());
        assertTrue(paidExpired.isPaid());
        assertEquals("2026.12.01", paidExpired.getEndDate());
        assertEquals(0, paidExpired.getDaysLeft());
    }

    @Test
    @DisplayName("헤더 값: 체험 중은 paid가 false, 유료 중은 true")
    void billingPaidFlagWhileUsing() {
        assertFalse(ServicePeriods.toBilling(
                period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5), at("2026-11-08T09:00")).isPaid());
        assertTrue(ServicePeriods.toBilling(
                period(false, "2026-11-02T14:00", "2026-12-02T00:00", 12), at("2026-11-08T09:00")).isPaid());
    }

    @Test
    @DisplayName("운영자 요약: 체험 중 결제하면 유료 기간을 보고, 체험 마지막 날은 그대로 남는다")
    void companyPeriodPaidOverTrial() {
        CompanyPeriodDTO summary = ServicePeriods.toCompanyPeriod(List.of(
                period(true, "2026-11-01T09:00", "2026-11-15T00:00", 5),
                period(false, "2026-11-02T14:00", "2026-12-02T00:00", 12)), at("2026-11-03T12:00"));
        assertEquals("PAID", summary.getStatus());
        assertTrue(summary.isPaid());
        assertEquals(12, summary.getSeats());
        assertEquals(LocalDate.parse("2026-11-02"), summary.getFirstDay());
        assertEquals(LocalDate.parse("2026-12-01"), summary.getLastDay());
        assertEquals(28, summary.getDaysLeft());
        assertEquals(LocalDate.parse("2026-11-14"), summary.getTrialLastDay());
    }

    @Test
    @DisplayName("운영자 요약: 연장한 업체는 요금제와 연장분까지의 마지막 날·남은 일수를 준다")
    void companyPeriodThroughRenewal() {
        ServicePeriodDTO paid = period(false, "2026-11-02T14:00", "2026-12-02T00:00", 5);
        paid.setPlan("ANNUAL");
        ServicePeriodDTO renewed = period(false, "2026-12-02T00:00", "2027-01-02T00:00", 5);
        renewed.setPlan("ANNUAL");

        CompanyPeriodDTO summary = ServicePeriods.toCompanyPeriod(
                List.of(period(true, "2026-10-30T15:20", "2026-11-13T00:00", 5), paid, renewed), at("2026-11-20T10:00"));

        assertEquals("PAID", summary.getStatus());
        assertEquals("ANNUAL", summary.getPlan());
        assertEquals(LocalDate.parse("2027-01-01"), summary.getLastDay());
        assertEquals(42, summary.getDaysLeft());
    }

    @Test
    @DisplayName("운영자 요약: 끝난 체험은 만료이고 유료가 아니다. 기간이 없으면 null이다")
    void companyPeriodExpiredTrial() {
        CompanyPeriodDTO summary = ServicePeriods.toCompanyPeriod(
                List.of(period(true, "2026-10-01T09:00", "2026-10-15T00:00", 5)), at("2026-11-03T12:00"));
        assertEquals("EXPIRED", summary.getStatus());
        assertFalse(summary.isPaid());
        assertEquals(0, summary.getDaysLeft());
        assertNull(ServicePeriods.toCompanyPeriod(List.of(), at("2026-11-03T12:00")));
    }
}
