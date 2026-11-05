package com.ieum.gw.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketFormOptionsDTOTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("접수 선택 목록은 화면이 사용하는 필드명과 숫자 ID로 JSON을 반환한다")
    void serializesFormOptions() throws Exception {
        TicketFormOptionsDTO options = new TicketFormOptionsDTO(
                List.of(new TicketFormOptionsDTO.AssetOption(1L, "업무용 노트북", "SN-001", 10L)),
                List.of(new TicketFormOptionsDTO.BranchOption(10L, "본사")), 10L);

        assertEquals(json.readTree("{\"assets\":[{\"id\":1,\"name\":\"업무용 노트북\","
                + "\"serial\":\"SN-001\",\"branchId\":10}],"
                + "\"branches\":[{\"id\":10,\"name\":\"본사\"}],\"defaultBranchId\":10}"),
                json.readTree(json.writeValueAsString(options)));
    }

    @Test
    @DisplayName("선택 목록이 비면 빈 배열을 유지하고 기본 지점과 일련번호가 없으면 null을 유지한다")
    void preservesEmptyListsAndNulls() throws Exception {
        TicketFormOptionsDTO empty = new TicketFormOptionsDTO(List.of(), List.of(), null);
        assertEquals(json.readTree("{\"assets\":[],\"branches\":[],\"defaultBranchId\":null}"),
                json.readTree(json.writeValueAsString(empty)));

        TicketFormOptionsDTO noSerial = new TicketFormOptionsDTO(
                List.of(new TicketFormOptionsDTO.AssetOption(1L, "업무용 노트북", null, 10L)),
                List.of(new TicketFormOptionsDTO.BranchOption(10L, "본사")), null);
        assertEquals(json.readTree("{\"assets\":[{\"id\":1,\"name\":\"업무용 노트북\","
                + "\"serial\":null,\"branchId\":10}],"
                + "\"branches\":[{\"id\":10,\"name\":\"본사\"}],\"defaultBranchId\":null}"),
                json.readTree(json.writeValueAsString(noSerial)));
    }
}
