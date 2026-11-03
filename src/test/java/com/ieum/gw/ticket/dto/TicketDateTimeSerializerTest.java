package com.ieum.gw.ticket.dto;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketDateTimeSerializerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("댓글과 처리 이력의 시각은 초 생략·소수 초 없이 같은 형식으로 응답한다")
    void serializesBothTimestamps() throws Exception {
        for (String input : new String[] {"2026-11-03T09:00:00", "2026-11-03T09:00:00.123456", "2026-11-03T09:00:00.999999999"}) {
            TicketCommentDTO comment = new TicketCommentDTO();
            TicketHistoryDTO history = new TicketHistoryDTO();
            comment.setCreatedAt(LocalDateTime.parse(input));
            history.setCreatedAt(LocalDateTime.parse(input));
            assertEquals("2026-11-03T09:00:00", json.readTree(json.writeValueAsString(comment)).get("createdAt").asText());
            assertEquals("2026-11-03T09:00:00", json.readTree(json.writeValueAsString(history)).get("createdAt").asText());
        }
    }

    @Test
    @DisplayName("시각이 비어 있으면 임의 값을 만들지 않고 null을 유지한다")
    void preservesNullTime() throws Exception {
        assertTrue(json.readTree(json.writeValueAsString(new TicketCommentDTO())).get("createdAt").isNull());
    }
}
