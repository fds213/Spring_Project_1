package com.ieum.gw.subscription.service;

import com.ieum.gw.subscription.dao.SubscriptionDAO;
import com.ieum.gw.subscription.dto.CompanyDTO;
import com.ieum.gw.subscription.dto.CompanyUsageDTO;
import com.ieum.gw.subscription.dto.ServicePeriodDTO;
import com.ieum.gw.subscription.dto.TrialCompanyDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 구독이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final int MAX_CODE_TRIES = 10; // 기능명세서 1.4

    private final SubscriptionDAO subscriptionDAO;
    private final CompanyCodeGenerator codeGenerator;

    /**
     * 업체 이용 상태. 결제·만료로 로그인 중에도 바뀌므로 세션에 담지 말고 요청마다 한 번 읽어
     * 헤더의 companyBilling(layout.tag)·만료 읽기 전용 검사·남은 일수 팝업에 같이 쓴다(AGENTS.md '수정 범위'·'DB·MyBatis').
     *
     * @return 이용 기간이 하나도 없는 업체는 null(업체를 가리지 않는다). 체험 신청은 늘 기간을 만들므로 정상 데이터에서는
     *         이음 운영 업체(ieum0000)뿐이고, 부르는 쪽은 null을 체험·만료 제한 없음으로 본다.
     *         시험 데이터 업체에 기간 행이 빠져 있으면 데이터를 고친다(DB 담당)
     */
    public CompanyUsageDTO getCompanyUsage(Long companyId) {
        LocalDateTime now = LocalDateTime.now(SEOUL);
        ServicePeriodDTO period = ServicePeriods.pick(subscriptionDAO.listPeriods(companyId), now);
        if (period == null) {
            return null;
        }
        return ServicePeriods.toUsage(period, subscriptionDAO.getEmailVerifiedAt(companyId), now);
    }

    /**
     * 사용 중(ACTIVE) 계정을 둘 수 있는 최대 수(관리자 포함). 체험은 10, 유료는 구매 인원(요구분석서 3장 단순화 ②).
     * 직원 가입 승인 때 부르는 쪽이 업체 행을 잠근 뒤 사용 중 계정 수와 비교한다(AGENTS.md '트랜잭션·동시 처리').
     * 만료된 업체는 마지막 기간의 값이다(만료 중 가입 승인은 만료 읽기 전용 검사가 막는다).
     *
     * @throws IllegalStateException 이용 기간이 하나도 없는 업체
     */
    public int getSeatLimit(Long companyId) {
        ServicePeriodDTO period = ServicePeriods.pick(subscriptionDAO.listPeriods(companyId), LocalDateTime.now(SEOUL));
        if (period == null) {
            throw new IllegalStateException("이용 기간이 없는 업체입니다: companyId=" + companyId);
        }
        return ServicePeriods.seatLimitOf(period);
    }

    /**
     * 체험 신청 때 업체와 14일 체험 기간을 만든다. 체험 신청 트랜잭션(SiteService.applyTrial) 안에서 부른다(AGENTS.md '패키지·이름 규칙').
     *
     * @param trialEmail 소문자로 바꾸고 앞뒤 공백을 뺀 이메일(부르는 쪽이 맞춘다, DB 검사 제약)
     * @param seats      체험 신청 인원 1~10. 기록만 하고 한도는 10이다(getSeatLimit)
     * @throws TrialEmailUsedException       이 이메일로 만든 업체가 이미 있을 때(ACC-01)
     * @throws CompanyCodeExhaustedException 업체 코드를 10번 뽑아 모두 겹쳤을 때(기능명세서 1.4)
     */
    @Transactional
    public TrialCompanyDTO createTrialCompany(String companyName, String trialEmail, int seats) {
        // 미리 보는 것은 안내를 빨리 하려는 것이다. 같은 이메일이 거의 동시에 오면 저장할 때 이메일 고유 제약이 막는다
        if (subscriptionDAO.existsTrialEmail(trialEmail)) {
            throw new TrialEmailUsedException();
        }
        LocalDateTime now = LocalDateTime.now(SEOUL);

        CompanyDTO company = new CompanyDTO();
        company.setCompanyName(companyName);
        company.setTrialEmail(trialEmail);
        company.setRequestedSeats(seats);
        company.setTrialRequestedAt(now);
        insertWithNewCode(company);

        ServicePeriodDTO period = new ServicePeriodDTO();
        period.setCompanyId(company.getCompanyId());
        period.setTrial(true); // 저장은 Mapper XML이 PD_TRIAL로 한다
        period.setStartsAt(now);
        period.setEndsAt(ServicePeriods.trialEndsAt(now));
        period.setPurchasedSeats(seats);
        period.setBillingAnchorDate(now.toLocalDate());
        subscriptionDAO.insertTrialPeriod(period);

        TrialCompanyDTO result = new TrialCompanyDTO();
        result.setCompanyId(company.getCompanyId());
        result.setCompanyCode(company.getCompanyCode());
        result.setStartsAt(period.getStartsAt());
        result.setEndsAt(period.getEndsAt());
        return result;
    }

    // 뽑은 코드로 저장해 보고 고유 제약에 걸리면 다시 뽑는다.
    // '있는지 조회 → 저장'으로 겹침을 보지 않는다: 동시에 신청하면 둘 다 '없음'을 보고 같은 코드를 저장한다(AGENTS.md 'DB·MyBatis')
    private void insertWithNewCode(CompanyDTO company) {
        for (int i = 0; i < MAX_CODE_TRIES; i++) {
            company.setCompanyCode(codeGenerator.next());
            try {
                subscriptionDAO.insertCompany(company);
                return;
            } catch (DuplicateKeyException e) {
                // 업체 코드가 겹쳤거나, 같은 이메일의 다른 신청이 막 저장됐다
                if (subscriptionDAO.existsTrialEmail(company.getTrialEmail())) {
                    throw new TrialEmailUsedException();
                }
            }
        }
        throw new CompanyCodeExhaustedException();
    }
}
