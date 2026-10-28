package com.ieum.gw.chat.mapper;

import com.ieum.gw.chat.dto.ChatMessageDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMapper {
    int isMember(@Param("roomId") long rId, @Param("empnum") String empnum);

    void insertMessage(ChatMessageDTO dto);
}
