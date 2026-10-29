package com.ieum.gw.test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// CI 실패 표시 확인용(test/common-ci-fail-jw). 병합하지 않고 PR을 닫은 뒤 브랜치를 지운다.
public class CiFailCheckTest {

    @Test
    @DisplayName("CI 실패 표시 확인: 틀린 값을 기대해 일부러 실패한다")
    public void failOnPurpose() {
        assertEquals(3, 1 + 1, "일부러 실패시키는 테스트");   // 기대 3, 실제 2
    }
}
