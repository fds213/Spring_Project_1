package com.ieum.gw.employee.service;

import com.ieum.gw.employee.dto.AdminAccountDTO;
import org.springframework.stereotype.Service;

/**
 * 사원이 다른 모듈에 주는 메서드(연동 계약, 10/30). 이름·인자·반환값을 바꿀 때는 부르는 담당과 먼저 맞춘다.
 */
@Service
public class EmployeeService {

    /**
     * 같은 업체의 재직자인지 본다. 재직자는 사원이 재직(ST_SERVING)이고 계정이 사용 중(ACTIVE)인 사람이다.
     * 결재가 결재자를 고를 때와 결재할 때 부른다.
     */
    public boolean isActiveEmployee(Long companyId, Long employeeId) {
        // TODO(김상준): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 김상준");
    }

    /**
     * 같은 업체 사원의 역할이 roleCodes 중 하나인지 본다. 다른 사람의 역할을 볼 때 쓴다
     * (예: 티켓을 배정받을 사람이 ROLE_IT·ROLE_ADMIN인지). 로그인한 본인의 역할은 LoginUser에서 꺼낸다.
     *
     * @param roleCodes ROLE_EMP·ROLE_IT·ROLE_HR·ROLE_ADMIN 중 하나 이상
     */
    public boolean hasAnyRole(Long companyId, Long employeeId, String... roleCodes) {
        // TODO(김상준): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 김상준");
    }

    /**
     * 체험 신청 때 업체 관리자(ROLE_ADMIN) 사원과 계정을 만든다. 체험 신청 트랜잭션 안에서 부른다(AGENTS.md 52줄).
     * 인증 메일을 붙이기 전이라 바로 로그인할 수 있게(ACTIVE) 만든다(요구분석서 3장 범위 표 아래).
     * 사번 발급(업체별 다음 번호, AGENTS.md 179줄)과 첫 연차(AttendanceService.grantInitialLeave)도 이 안에서 한다.
     *
     * @param rawPassword 평문 비밀번호. 여기서 BCrypt로 해시하고 로그에 남기지 않는다
     * @return 만든 사원 ID·계정 ID·사번
     */
    public AdminAccountDTO createCompanyAdmin(Long companyId, String adminName, String email, String rawPassword) {
        // TODO(김상준): 구현. 구현 전에는 예외를 던진다.
        throw new UnsupportedOperationException("구현 전: 김상준");
    }
}
