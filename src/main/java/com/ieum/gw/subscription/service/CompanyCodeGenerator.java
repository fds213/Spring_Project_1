package com.ieum.gw.subscription.service;

import java.security.SecureRandom;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 업체 코드 후보를 하나 뽑는다: ieum0001~ieum9999 중 무작위(ieum0000은 이음 운영 업체).
 * 겹치는지는 여기서 보지 않고 저장할 때 고유 제약으로 안다. 테스트에서 '계속 겹침'을 만들려고 따로 뗐다(AGENTS.md 'DB·MyBatis').
 */
@Component
public class CompanyCodeGenerator {

    private final SecureRandom random = new SecureRandom(); // 남의 업체 코드를 짐작하기 어렵게(요구분석서 3.11)

    public String next() {
        // Locale.ROOT: 서버 로캘에 따라 아라비아 숫자가 아닌 숫자로 찍히면 DB 검사 제약([0-9]{4})에 걸린다
        return String.format(Locale.ROOT, "ieum%04d", random.nextInt(9999) + 1);
    }
}
