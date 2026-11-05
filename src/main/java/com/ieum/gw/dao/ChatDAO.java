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

    public int isMember(Long companyId, long roomId, Long empNum) {
        return mapper.isMember(companyId, roomId, empNum);
    }

    public void insertMessage(ChatMessageDTO dto) {
        mapper.insertMessage(dto);
    }

    public Long findImageAttachmentId(Long companyId, Long roomId, Long messageId) {
        return mapper.findImageAttachmentId(companyId, roomId, messageId);
    }

    public List<Long> getRoomMemberIds(Long companyId, Long roomId) {
        return mapper.getRoomMemberIds(companyId, roomId);
    }

    public Long lockRoom(Long companyId, Long roomId) {
        return mapper.lockRoom(companyId, roomId);
    }

    public List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId) {
        return mapper.getRoomList(companyId, employeeId);
    }

    public List<ChatMessageDTO> getMessages(Long companyId, Long roomId, Long beforeMessageId) {
        return mapper.getMessages(companyId, roomId, beforeMessageId);
    }

    public List<ChatMessageDTO> getMessagesAfter(Long companyId, Long roomId, Long afterMessageId) {
        return mapper.getMessagesAfter(companyId, roomId, afterMessageId);
    }

    public int markRead(Long companyId, Long roomId, Long employeeId, Long messageId) {
        return mapper.markRead(companyId, roomId, employeeId, messageId);
    }

    public int countUnread(Long companyId, Long employeeId) {
        return mapper.countUnread(companyId, employeeId);
    }

    public Long getOtherLastReadId(Long companyId, Long roomId, Long employeeId) {
        return mapper.getOtherLastReadId(companyId, roomId, employeeId);
    }

    public Long findDirectRoom(ChatRoomDTO room) {
        return mapper.findDirectRoom(room);
    }

    public int insertRoom(ChatRoomDTO room) {
        return mapper.insertRoom(room);
    }

    public int insertMember(Long companyId, Long roomId, Long employeeId) {
        return mapper.insertMember(companyId, roomId, employeeId);
    }

    public ChatMessageDTO findByClientKey(ChatMessageDTO message) {
        return mapper.findByClientKey(message);
    }
}
