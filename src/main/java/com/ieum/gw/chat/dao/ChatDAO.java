package com.ieum.gw.chat.dao;

import com.ieum.gw.chat.dto.ChatMessageDTO;
import com.ieum.gw.chat.mapper.ChatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatDAO {
    private final ChatMapper mapper;

    public int isMember(long rId, String empnum) {
        return mapper.isMember(rId, empnum);
    }

    public void insertMessage(ChatMessageDTO dto) {
        mapper.insertMessage(dto);
    }
}
