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

    /** 참여 중인 사원이 방에 속하는지 확인한다 */
    public int isMember(Long companyId, long roomId, Long empNum) {
        return mapper.isMember(companyId, roomId, empNum);
    }

    public void insertMessage(ChatMessageDTO dto) {
        mapper.insertMessage(dto);
    }

    /** 이미지 메시지에 연결된 첨부 ID를 조회한다 */
    public Long findImageAttachmentId(Long companyId, Long roomId, Long messageId) {
        return mapper.findImageAttachmentId(companyId, roomId, messageId);
    }

    /** 채팅방에 남아 있는 참여자 ID를 조회한다 */
    public List<Long> getRoomMemberIds(Long companyId, Long roomId) {
        return mapper.getRoomMemberIds(companyId, roomId);
    }

    /** 메시지를 저장하기 전에 방 행을 잠근다 */
    public Long lockRoom(Long companyId, Long roomId) {
        return mapper.lockRoom(companyId, roomId);
    }

    /** 사원이 참여한 방을 최근 활동 순으로 조회한다 */
    public List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId) {
        return mapper.getRoomList(companyId, employeeId);
    }

    /** 기준 메시지보다 이전 대화 한 페이지를 조회한다 */
    public List<ChatMessageDTO> getMessages(Long companyId, Long roomId, Long beforeMessageId) {
        return mapper.getMessages(companyId, roomId, beforeMessageId);
    }

    /** 기준 메시지 이후 대화 한 페이지를 조회한다 */
    public List<ChatMessageDTO> getMessagesAfter(Long companyId, Long roomId, Long afterMessageId) {
        return mapper.getMessagesAfter(companyId, roomId, afterMessageId);
    }

    /** 참여자의 마지막 읽음 위치를 앞으로 갱신한다 */
    public int markRead(Long companyId, Long roomId, Long employeeId, Long messageId) {
        return mapper.markRead(companyId, roomId, employeeId, messageId);
    }

    /** 사원의 전체 안 읽은 메시지 수를 조회한다 */
    public int countUnread(Long companyId, Long employeeId) {
        return mapper.countUnread(companyId, employeeId);
    }

    /** 1:1 상대방이 마지막으로 읽은 메시지 ID를 조회한다 */
    public Long getOtherLastReadId(Long companyId, Long roomId, Long employeeId) {
        return mapper.getOtherLastReadId(companyId, roomId, employeeId);
    }

    /** 두 사원이 사용 중인 1:1 방을 찾는다 */
    public Long findDirectRoom(ChatRoomDTO room) {
        return mapper.findDirectRoom(room);
    }

    /** 채팅방을 저장한다 */
    public int insertRoom(ChatRoomDTO room) {
        return mapper.insertRoom(room);
    }

    /** 채팅방 참여자를 등록한다 */
    public int insertMember(Long companyId, Long roomId, Long employeeId) {
        return mapper.insertMember(companyId, roomId, employeeId);
    }

    /** 전송 키로 이미 저장된 메시지를 찾는다 */
    public ChatMessageDTO findByClientKey(ChatMessageDTO message) {
        return mapper.findByClientKey(message);
    }
}
