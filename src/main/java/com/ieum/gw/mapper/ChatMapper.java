package com.ieum.gw.mapper;

import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.ChatRoomDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMapper {
    int isMember(Long companyId, long roomId, Long employeeId);

    void insertMessage(ChatMessageDTO dto);

    Long findImageAttachmentId(Long companyId, Long roomId, Long messageId);

    List<Long> getRoomMemberIds(Long companyId, Long roomId);

    Long lockRoom(Long companyId, Long roomId);

    List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId);

    List<ChatMessageDTO> getMessages(Long companyId, Long roomId, Long beforeMessageId);

    List<ChatMessageDTO> getMessagesAfter(Long companyId, Long roomId, Long afterMessageId);

    int markRead(Long companyId, Long roomId, Long employeeId, Long messageId);

    int countUnread(Long companyId, Long employeeId);

    Long getOtherLastReadId(Long companyId, Long roomId, Long employeeId);

    Long findDirectRoom(ChatRoomDTO room);

    int insertRoom(ChatRoomDTO room);

    int insertMember(Long companyId, Long roomId, Long employeeId);

    ChatMessageDTO findByClientKey(ChatMessageDTO message);
}
