package com.ieum.gw.mapper;

import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.ChatRoomDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMapper {
    /** 참여 중인 사원이 방에 속하는지 확인한다 */
    int isMember(Long companyId, long roomId, Long employeeId);

    void insertMessage(ChatMessageDTO dto);

    /** 이미지 메시지에 연결된 첨부 ID를 조회한다 */
    Long findImageAttachmentId(Long companyId, Long roomId, Long messageId);

    /** 채팅방에 남아 있는 참여자 ID를 조회한다 */
    List<Long> getRoomMemberIds(Long companyId, Long roomId);

    /** 메시지를 저장하기 전에 방 행을 잠근다 */
    Long lockRoom(Long companyId, Long roomId);

    /** 사원이 참여한 방을 최근 활동 순으로 조회한다 */
    List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId);

    /** 기준 메시지보다 이전 대화 한 페이지를 조회한다 */
    List<ChatMessageDTO> getMessages(Long companyId, Long roomId, Long beforeMessageId);

    /** 기준 메시지 이후 대화 한 페이지를 조회한다 */
    List<ChatMessageDTO> getMessagesAfter(Long companyId, Long roomId, Long afterMessageId);

    /** 참여자의 마지막 읽음 위치를 앞으로 갱신한다 */
    int markRead(Long companyId, Long roomId, Long employeeId, Long messageId);

    /** 사원의 전체 안 읽은 메시지 수를 조회한다 */
    int countUnread(Long companyId, Long employeeId);

    /** 1:1 상대방이 마지막으로 읽은 메시지 ID를 조회한다 */
    Long getOtherLastReadId(Long companyId, Long roomId, Long employeeId);

    /** 두 사원이 사용 중인 1:1 방을 찾는다 */
    Long findDirectRoom(ChatRoomDTO room);

    /** 채팅방을 저장한다 */
    int insertRoom(ChatRoomDTO room);

    /** 채팅방 참여자를 등록한다 */
    int insertMember(Long companyId, Long roomId, Long employeeId);

    /** 전송 키로 이미 저장된 메시지를 찾는다 */
    ChatMessageDTO findByClientKey(ChatMessageDTO message);
}
