package com.ieum.gw.mapper;

import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.ChatRoomDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMapper {
    int isMember(long roomId, Long employeeId);

    void insertMessage(ChatMessageDTO dto);

    List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId);
}
