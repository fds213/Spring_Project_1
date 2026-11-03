package com.ieum.gw.asset.dto;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetDateTimeSerializerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("자산 지급·회수 시각은 초가 0이거나 소수 초가 있어도 초 단위 고정 문자열이다")
    void serializesAllAssetTimestamps() throws Exception {
        for (String input : new String[] {"2026-11-03T09:00:00", "2026-11-03T09:00:00.123456", "2026-11-03T09:00:00.999999999"}) {
            LocalDateTime time = LocalDateTime.parse(input);
            AssetDTO asset = new AssetDTO();
            asset.setIssuedAt(time);
            AssetAssignmentDTO assignment = new AssetAssignmentDTO();
            assignment.setIssuedAt(time);
            assignment.setReturnedAt(time);
            var assetJson = json.readTree(json.writeValueAsString(asset));
            var assignmentJson = json.readTree(json.writeValueAsString(assignment));
            assertEquals("2026-11-03T09:00:00", assetJson.get("issuedAt").asText());
            assertEquals("2026-11-03T09:00:00", assignmentJson.get("issuedAt").asText());
            assertEquals("2026-11-03T09:00:00", assignmentJson.get("returnedAt").asText());
        }
    }

    @Test
    @DisplayName("회수하지 않은 자산의 회수 일시는 null로 유지한다")
    void preservesNullReturnTime() throws Exception {
        var result = json.readTree(json.writeValueAsString(new AssetAssignmentDTO()));
        assertTrue(result.get("returnedAt").isNull());
    }
}
