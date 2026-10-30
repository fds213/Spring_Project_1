package com.ieum.gw.chat.mapper;

import com.ieum.gw.chat.dto.ChatEmpDto;
import com.ieum.gw.chat.dto.ChatMessageDTO;
import com.ieum.gw.chat.dto.ChatRoomDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {
    int isMember(@Param("roomId") long rId, @Param("empnum") String empnum);

    void insertMessage(ChatMessageDTO dto);

    List<ChatRoomDTO> getRoomList(String empnum);

    List<ChatEmpDto> getEmpList(String empnum);
}
