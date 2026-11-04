package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.dao.EmployeeDAO;
import com.ieum.gw.dao.JoinDAO;
import com.ieum.gw.dto.CompanyUsageDTO;
import com.ieum.gw.dto.JoinCompanyDTO;
import com.ieum.gw.dto.JoinOpeningDTO;

/** 직원 가입의 업체 코드 [확인]: 이메일 인증 전 업체는 받지 않는다(ACC-04). 메일을 끈 환경은 인증할 길이 없어 막지 않는다 */
class JoinCompanyVerifyTest {

    static class FakeDao extends JoinDAO {
        FakeDao() {
            super(null);
        }

        @Override
        public JoinCompanyDTO findCompanyByCode(String code) {
            JoinCompanyDTO company = new JoinCompanyDTO();
            company.setCompanyId(42L);
            return company;
        }

        @Override
        public List<JoinOpeningDTO> listOpenings(Long companyId) {
            JoinOpeningDTO opening = new JoinOpeningDTO();
            opening.setDepartmentName("개발팀");
            return List.of(opening);
        }

        @Override
        public List<String> listGradeNames(Long companyId) {
            return List.of("사원");
        }
    }

    private static JoinService service(boolean companyVerified, boolean verificationRequired) {
        SubscriptionService subscription = new SubscriptionService(null, null) {
            @Override
            public CompanyUsageDTO getCompanyUsage(Long companyId) {
                CompanyUsageDTO usage = new CompanyUsageDTO();
                usage.setEmailVerified(companyVerified);
                return usage;
            }
        };
        MailSettings settings = new MailSettings() {
            @Override
            public boolean isVerificationRequired() {
                return verificationRequired;
            }
        };
        return new JoinService(new FakeDao(), subscription, null, null, settings);
    }

    @Test
    @DisplayName("인증을 요구하면 인증 전 업체를 받지 않고, 인증을 마친 업체는 받는다")
    void required() {
        assertNull(service(false, true).checkCompany("ieum4827"));
        assertNotNull(service(true, true).checkCompany("ieum4827"));
    }

    @Test
    @DisplayName("메일을 꺼서 인증을 요구하지 않으면 인증 전 업체도 받는다")
    void notRequired() {
        assertNotNull(service(false, false).checkCompany("ieum4827"));
    }
}
