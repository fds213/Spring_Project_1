package com.ieum.gw.dao;

import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.ChatRoomDTO;
import com.ieum.gw.mapper.ChatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatDAO {
    private final ChatMapper mapper;

    public int isMember(long roomId, Long empNum) {
        return mapper.isMember(roomId, empNum);
    }

    public void insertMessage(ChatMessageDTO dto) {
        mapper.insertMessage(dto);
    }

    public List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId) {
        return mapper.getRoomList(companyId, employeeId);
    }

}
