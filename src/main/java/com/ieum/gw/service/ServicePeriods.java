package com.ieum.gw.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.ieum.gw.dto.CompanyBillingDTO;
import com.ieum.gw.dto.CompanyPeriodDTO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.ServicePeriodDTO;

/**
 * 이용 기간 계산. DB 없이 테스트하려고 SubscriptionService에서 뗐다.
 */
final class ServicePeriods {

    static final int TRIAL_DAYS = 14;
    static final int TRIAL_SEAT_LIMIT = 10; // 체험은 신청 인원과 관계없이 관리자 포함 10명(요구분석서 3장 단순화 ②)

    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private ServicePeriods() {
    }

    /**
     * 체험 만료 시각. 신청일을 첫날로 14일째 날이 끝나는 때다(날짜 기준, 10/30 결정).
     * 예: 10/30 15:20 신청 → 11/13 00:00 만료, 마지막으로 쓰는 날은 11/12.
     */
    static LocalDateTime trialEndsAt(LocalDateTime appliedAt) {
        return appliedAt.toLocalDate().plusDays(TRIAL_DAYS).atStartOfDay();
    }

    /**
     * 유료 기간 만료 시각. 체험 만료(10/30 결정)와 같은 방식으로 날짜로 센다: 승인일을 첫날로 달력 개월 수만큼 쓰고 0시에 만료한다.
     * 그 달에 같은 날이 없으면 말일로 맞춘다(요구분석서 3.6).
     * 예: 11/2 14:00 승인 → 12/2 00:00 만료(마지막으로 쓰는 날 12/1), 1/31 승인 → 2/28 00:00 만료.
     */
    static LocalDateTime paidEndsAt(LocalDate approvedDate, int months) {
        return approvedDate.plusMonths(months).atStartOfDay();
    }

    /**
     * 연장한 기간의 만료 시각. 지금 만료일에 달력 개월 수를 더하되, 날짜는 최초 기준일에 맞춘다: 그 달에 기준일이 없으면 말일이고
     * 다음 달에는 기준일로 돌아온다(요구분석서 3.6). 예: 1/31 시작 → 2/28 만료 → 연장 3/31 만료 → 연장 4/30 만료.
     *
     * @param from 지금 만료일(새 기간이 시작하는 날)
     */
    static LocalDateTime extendedEndsAt(LocalDate from, LocalDate anchor, int months) {
        YearMonth month = YearMonth.from(from).plusMonths(months);
        return month.atDay(Math.min(anchor.getDayOfMonth(), month.lengthOfMonth())).atStartOfDay();
    }

    /** 마지막으로 쓰는 날. 만료 시각이 0시이므로 그 전날이다 */
    static LocalDate lastDay(LocalDateTime endsAt) {
        return endsAt.minusNanos(1).toLocalDate();
    }

    /**
     * 지금 기간(시작 ≤ now < 만료)을 고른다. 겹치면 나중에 시작한 것이다(체험 중 결제하면 유료 기간이 체험 기간 위에 생긴다, PAY-02).
     * 지금 기간이 없으면 이미 끝난 기간 중 가장 늦게 끝난 것, 그것도 없으면 null.
     * 연장은 새 기간 행을 만드므로, 지금 기간에 바로 이어지는 유료 기간이 있으면 그 끝까지를 endsAt으로 돌려준다(SUB-03).
     * 그래서 만료 여부·마지막 날·남은 일수는 이 값으로 계산하면 연장분까지 맞다.
     */
    static ServicePeriodDTO pick(List<ServicePeriodDTO> periods, LocalDateTime now) {
        ServicePeriodDTO current = null;
        ServicePeriodDTO last = null;
        for (ServicePeriodDTO p : periods) {
            if (!p.getStartsAt().isAfter(now) && now.isBefore(p.getEndsAt())) {
                if (current == null || p.getStartsAt().isAfter(current.getStartsAt())) {
                    current = p;
                }
            } else if (!p.getEndsAt().isAfter(now)) {
                if (last == null || p.getEndsAt().isAfter(last.getEndsAt())) {
                    last = p;
                }
            }
        }
        return current != null ? throughRenewals(current, periods) : last;
    }

    // 지금 기간에 바로 이어지는 유료 기간의 끝까지 늘린 사본. 이어지는 기간이 없으면 지금 기간 그대로다
    private static ServicePeriodDTO throughRenewals(ServicePeriodDTO current, List<ServicePeriodDTO> periods) {
        if (current.isTrial()) {
            return current;
        }
        LocalDateTime end = current.getEndsAt();
        List<ServicePeriodDTO> byStart = new ArrayList<>(periods);
        byStart.sort(Comparator.comparing(ServicePeriodDTO::getStartsAt));
        for (ServicePeriodDTO p : byStart) {
            if (!p.isTrial() && !p.getStartsAt().isAfter(end) && p.getEndsAt().isAfter(end)) {
                end = p.getEndsAt();
            }
        }
        if (end.equals(current.getEndsAt())) {
            return current;
        }
        ServicePeriodDTO through = new ServicePeriodDTO();
        through.setServicePeriodId(current.getServicePeriodId());
        through.setCompanyId(current.getCompanyId());
        through.setTrial(false);
        through.setStartsAt(current.getStartsAt());
        through.setEndsAt(end);
        through.setPurchasedSeats(current.getPurchasedSeats());
        through.setBillingAnchorDate(current.getBillingAnchorDate());
        through.setPlan(current.getPlan());
        return through;
    }

    static CompanyUsageDTO toUsage(ServicePeriodDTO period, LocalDateTime emailVerifiedAt, LocalDateTime now) {
        CompanyUsageDTO usage = new CompanyUsageDTO();
        usage.setTrial(period.isTrial());
        usage.setExpired(!now.isBefore(period.getEndsAt()));
        usage.setEndsAt(period.getEndsAt());
        usage.setEmailVerified(emailVerifiedAt != null);
        return usage;
    }

    /** 이용 상태. 끝난 기간은 체험·유료를 가리지 않고 EXPIRED다 */
    static String statusOf(ServicePeriodDTO period, LocalDateTime now) {
        if (!now.isBefore(period.getEndsAt())) {
            return "EXPIRED";
        }
        return period.isTrial() ? "TRIAL" : "PAID";
    }

    /** 마지막 날까지 남은 일수. 0이면 마지막 날이거나 이미 지났다 */
    static long daysLeft(ServicePeriodDTO period, LocalDateTime now) {
        return Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), lastDay(period.getEndsAt())));
    }

    static CompanyBillingDTO toBilling(ServicePeriodDTO period, LocalDateTime now) {
        CompanyBillingDTO billing = new CompanyBillingDTO();
        billing.setStatus(statusOf(period, now));
        billing.setPaid(!period.isTrial());
        billing.setEndDate(lastDay(period.getEndsAt()).format(HEADER_DATE));
        billing.setDaysLeft(daysLeft(period, now));
        return billing;
    }

    /** 운영자 화면의 이용 기간 요약. 이용 기간이 하나도 없으면 null */
    static CompanyPeriodDTO toCompanyPeriod(List<ServicePeriodDTO> periods, LocalDateTime now) {
        ServicePeriodDTO period = pick(periods, now);
        if (period == null) {
            return null;
        }
        CompanyPeriodDTO summary = new CompanyPeriodDTO();
        summary.setStatus(statusOf(period, now));
        summary.setPaid(!period.isTrial());
        summary.setPlan(period.getPlan());
        summary.setSeats(period.getPurchasedSeats());
        summary.setFirstDay(period.getStartsAt().toLocalDate());
        summary.setLastDay(lastDay(period.getEndsAt()));
        summary.setDaysLeft(daysLeft(period, now));
        for (ServicePeriodDTO p : periods) {
            if (p.isTrial()) {
                summary.setTrialLastDay(lastDay(p.getEndsAt()));
            }
        }
        return summary;
    }

    static int seatLimitOf(ServicePeriodDTO period) {
        return period.isTrial() ? TRIAL_SEAT_LIMIT : period.getPurchasedSeats();
    }
}
