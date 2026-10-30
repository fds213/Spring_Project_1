package com.ieum.gw.subscription.service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanyCodeGeneratorTest {

    @Test
    @DisplayName("업체 코드는 서버 로캘과 관계없이 ieum + 아라비아 숫자 4자리이고 ieum0000(이음 운영 업체)은 나오지 않는다")
    void formatIgnoresLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG")); // 기본 로캘이면 ٠٨٩٤ 같은 숫자가 찍히는 로캘
            CompanyCodeGenerator generator = new CompanyCodeGenerator();
            for (int i = 0; i < 1000; i++) {
                String code = generator.next();
                assertTrue(code.matches("ieum[0-9]{4}"), code);
                assertNotEquals("ieum0000", code);
            }
        } finally {
            Locale.setDefault(saved);
        }
    }
}
