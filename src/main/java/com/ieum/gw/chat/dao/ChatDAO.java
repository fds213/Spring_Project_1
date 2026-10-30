package com.ieum.gw.chat.dao;

import com.ieum.gw.chat.dto.ChatMessageDTO;
import com.ieum.gw.chat.dto.ChatRoomDTO;
import com.ieum.gw.chat.mapper.ChatMapper;
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
