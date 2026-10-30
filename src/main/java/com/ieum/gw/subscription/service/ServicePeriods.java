package com.ieum.gw.subscription.service;

import java.time.LocalDateTime;
import java.util.List;

import com.ieum.gw.subscription.dto.CompanyUsageDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;

/**
 * 이용 기간 계산. DB 없이 테스트하려고 SubscriptionService에서 뗐다.
 */
final class ServicePeriods {

    static final int TRIAL_DAYS = 14;
    static final int TRIAL_SEAT_LIMIT = 10; // 체험은 신청 인원과 관계없이 관리자 포함 10명(요구분석서 3장 단순화 ②)

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
     * 지금 기간(시작 ≤ now < 만료)을 고른다. 겹치면 나중에 시작한 것이다(체험 중 결제하면 유료 기간이 체험 기간 위에 생긴다, PAY-02).
     * 지금 기간이 없으면 이미 끝난 기간 중 가장 늦게 끝난 것, 그것도 없으면 null.
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
        return current != null ? current : last;
    }

    // TODO(최준원): 만료 전 연장(SUB-03, 핵심 뒤 추가)을 만들 때 바로 이어지는 기간의 끝까지 endsAt으로 본다
    static CompanyUsageDTO toUsage(ServicePeriodDTO period, LocalDateTime emailVerifiedAt, LocalDateTime now) {
        CompanyUsageDTO usage = new CompanyUsageDTO();
        usage.setTrial(period.isTrial());
        usage.setExpired(!now.isBefore(period.getEndsAt()));
        usage.setEndsAt(period.getEndsAt());
        usage.setEmailVerified(emailVerifiedAt != null);
        return usage;
    }

    static int seatLimitOf(ServicePeriodDTO period) {
        return period.isTrial() ? TRIAL_SEAT_LIMIT : period.getPurchasedSeats();
    }
}
