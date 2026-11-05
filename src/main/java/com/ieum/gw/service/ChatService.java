package com.ieum.gw.service;

import com.ieum.gw.code.ChatRoomType;
import com.ieum.gw.dao.ChatDAO;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.ChatRoomDTO;
import com.ieum.gw.dto.CompanyUsageDTO;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Value
    public static class MessageSaveResult {
        ChatMessageDTO message;
        boolean inserted;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
    private static final String IMAGE_TYPE = "MSG_IMAGE";
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private ChatDAO dao;

    @Autowired
    private EmployeeService eService;

    @Autowired
    private SubscriptionService sService;

    public void hitv() {
        System.out.println("hi zz");
    }

    /** 같은 키로 재전송한 내용이 기존 메시지와 일치하는지 검사한다 */
    private void validateReplay(ChatMessageDTO requested, ChatMessageDTO existing) {
        if (!Objects.equals(requested.getChatRoomId(), existing.getChatRoomId())
                || !Objects.equals(requested.getMessageType(), existing.getMessageType())
                || !Objects.equals(requested.getMessageText(), existing.getMessageText())) {
            throw new IllegalArgumentException(
                    "같은 전송 키를 다른 방이나 내용에 사용할 수 없습니다.");
        }
    }

    /** 참여 중인 사원이 방에 속하는지 확인한다 */
    public boolean isMember(Long companyId, long roomId, Long empNum) {
        return dao.isMember(companyId, roomId, empNum) == 1;
    }

    /** 활성 직원이 참여 중인 방에 접근할 수 있는지 확인한다 */
    public boolean canAccessRoom(Long companyId, long roomId, Long employeeId) {
        return isMember(companyId, roomId, employeeId)
                && eService.isActiveEmployee(companyId, employeeId);
    }

    /** 전송 전에 방을 잠그고 참여 권한과 업체 이용 상태를 검사한다 */
    private void lockForSend(ChatMessageDTO dto) {
        // 같은 방은 ID 발급 전에 잠가 메시지 ID와 커밋 순서를 맞춘다.
        if (dao.lockRoom(dto.getCompanyId(), dto.getChatRoomId()) == null
                || !canAccessRoom(dto.getCompanyId(), dto.getChatRoomId(), dto.getSenderEmployeeId())) {
            throw new AccessDeniedException("채팅방에 접근할 수 없습니다.");
        }
        CompanyUsageDTO usage = sService.getCompanyUsage(dto.getCompanyId());
        if (usage != null && usage.isExpired()) {
            throw new AccessDeniedException("이용 기간이 끝나 메시지를 전송할 수 없습니다.");
        }
    }

    /** 텍스트를 저장하거나 같은 키로 저장된 메시지를 반환한다 */
    @Transactional
    public MessageSaveResult insertMessage(ChatMessageDTO dto) {
        lockForSend(dto);

        // 빈 메시지와 1000자 검사
        String text = dto.getMessageText();

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
        if (text.codePointCount(0, text.length()) > 1000) {
            throw new IllegalArgumentException("메시지는 1,000자까지 보낼 수 있습니다.");
        }

        validateClientKey(dto.getClientMessageKey());

        ChatMessageDTO existing = dao.findByClientKey(dto);
        if (existing != null) {
            validateReplay(dto, existing);
            return new MessageSaveResult(existing, false);
        }

        try {
            dao.insertMessage(dto);
        } catch (DuplicateKeyException e) {
            existing = dao.findByClientKey(dto);
            if (existing == null) throw e;

            validateReplay(dto, existing);
            return new MessageSaveResult(existing, false);
        }

        return new MessageSaveResult(dto, true);
    }

    /** 메시지 전송 키의 길이와 허용 문자를 검사한다 */
    private void validateClientKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("메시지 전송 키가 올바르지 않습니다.");
        }
    }

    /** 이미지를 저장하거나 같은 키로 저장된 동일 이미지를 반환한다 */
    @Transactional(rollbackFor = IOException.class)
    public MessageSaveResult sendImage(long roomId, LoginUser user, String key, MultipartFile file)
            throws IOException {
        validateClientKey(key);
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("10MB 이하의 JPG·PNG 이미지 한 장을 선택해 주세요.");
        }

        ChatMessageDTO message = new ChatMessageDTO();
        message.setCompanyId(user.getCompanyId());
        message.setChatRoomId(roomId);
        message.setSenderEmployeeId(user.getEmployeeId());
        message.setMessageType(IMAGE_TYPE);
        message.setClientMessageKey(key);
        lockForSend(message);

        ChatMessageDTO existing = dao.findByClientKey(message);
        if (existing != null) {
            validateReplay(message, existing);
            AttachmentDTO original = attachmentService.get(existing.getAttachmentId(), user.getCompanyId());
            try (InputStream in = attachmentService.load(original).getInputStream()) {
                if (!Arrays.equals(file.getBytes(), in.readNBytes((int) file.getSize() + 1))) {
                    throw new IllegalArgumentException("같은 전송 키를 다른 이미지에 사용할 수 없습니다.");
                }
            }
            return new MessageSaveResult(existing, false);
        }

        // 공통 저장소가 확장자와 파일 시그니처를 검사한다.
        AttachmentDTO attachment = attachmentService.upload(file, user, "chat");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 이미지 저장이 롤백되면 파일을 정리한다 */
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        fileStorage.delete(attachment.getStorageKey());
                    } catch (IOException e) {
                        LOG.warn("롤백된 채팅 이미지 파일 삭제 실패", e);
                    }
                }
            }
        });
        message.setAttachmentId(attachment.getAttachmentId());
        attachmentService.markAttached(attachment.getAttachmentId(), user.getCompanyId());
        dao.insertMessage(message);
        return new MessageSaveResult(message, true);
    }

    /** 방 참여 권한을 확인하고 이미지 정보를 반환한다 */
    public AttachmentDTO getImage(long roomId, long messageId, LoginUser user) {
        if (!canAccessRoom(user.getCompanyId(), roomId, user.getEmployeeId())) {
            throw new AccessDeniedException("채팅방에 접근할 수 없습니다.");
        }
        Long attachmentId = dao.findImageAttachmentId(user.getCompanyId(), roomId, messageId);
        return attachmentId == null ? null : attachmentService.get(attachmentId, user.getCompanyId());
    }

    public List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId) {
        if (!eService.isActiveEmployee(companyId, employeeId)) {
            throw new AccessDeniedException("사용 중인 재직 계정만 조회할 수 있습니다.");
        }
        return dao.getRoomList(companyId, employeeId);
    }

    /** 채팅방에 남아 있는 참여자 ID를 조회한다 */
    public List<Long> getRoomMemberIds(Long companyId, Long roomId) {
        return dao.getRoomMemberIds(companyId, roomId);
    }

    /** 참여 권한을 확인하고 방 참여자의 이름을 반환한다 */
    public List<String> getRoomMemberNames(Long companyId, long roomId, Long employeeId) {
        if (!canAccessRoom(companyId, roomId, employeeId)) {
            throw new AccessDeniedException("채팅방에 접근할 수 없습니다.");
        }
        List<Long> ids = getRoomMemberIds(companyId, roomId);
        List<String> names = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 1000) {
            // 사원 이름 조회의 Oracle IN 제한에 맞춰 나눈다.
            List<Long> batch = ids.subList(start, Math.min(start + 1000, ids.size()));
            names.addAll(eService.getEmployeeNames(companyId, batch).values());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    /** 기준 메시지보다 이전 대화 한 페이지를 조회한다 */
    public List<ChatMessageDTO> getMessages(
            Long companyId, Long roomId, Long employeeId,
            Long beforeMessageId) {

        if (!eService.isActiveEmployee(companyId, employeeId)
                || !isMember(companyId, roomId, employeeId)) {
            throw new AccessDeniedException("채팅방 참여자만 조회할 수 있습니다.");
        }

        List<ChatMessageDTO> messages =
                dao.getMessages(companyId, roomId, beforeMessageId);

        fillSenderNames(companyId, messages);
        Collections.reverse(messages);

        return messages;
    }

    /** 조회한 메시지에 작성자 이름을 채운다 */
    private void fillSenderNames(
            Long companyId, List<ChatMessageDTO> messages) {

        List<Long> senderIds = messages.stream()
                .map(ChatMessageDTO::getSenderEmployeeId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> senderNames =
                eService.getEmployeeNames(companyId, senderIds);

        for (ChatMessageDTO message : messages) {
            message.setSenderName(senderNames.getOrDefault(
                    message.getSenderEmployeeId(), "알 수 없는 사용자"));
        }
    }

    /** 기준 메시지 이후 대화 한 페이지를 조회한다 */
    public List<ChatMessageDTO> getMessagesAfter(
            Long companyId, Long roomId, Long employeeId,
            Long afterMessageId) {

        if (afterMessageId == null || afterMessageId < 0) {
            throw new IllegalArgumentException("메시지 ID가 올바르지 않습니다.");
        }

        if (!eService.isActiveEmployee(companyId, employeeId)
                || !isMember(companyId, roomId, employeeId)) {
            throw new AccessDeniedException("채팅방 참여자만 조회할 수 있습니다.");
        }

        List<ChatMessageDTO> messages =
                dao.getMessagesAfter(companyId, roomId, afterMessageId);

        fillSenderNames(companyId, messages);
        return messages;
    }

    /** 참여자의 마지막 읽음 위치를 앞으로 갱신한다 */
    @Transactional
    public void markRead(Long companyId, Long roomId,
                         Long employeeId, Long messageId) {

        if (messageId == null || messageId <= 0) {
            throw new IllegalArgumentException("메시지 ID가 올바르지 않습니다.");
        }

        if (!eService.isActiveEmployee(companyId, employeeId)
                || !isMember(companyId, roomId, employeeId)) {
            throw new AccessDeniedException("채팅방 참여자만 읽음 처리할 수 있습니다.");
        }

        CompanyUsageDTO usage = sService.getCompanyUsage(companyId);
        if (usage != null && usage.isExpired()) {
            throw new AccessDeniedException("이용 기간이 끝나 읽음 처리할 수 없습니다.");
        }

        if (dao.markRead(companyId, roomId, employeeId, messageId) != 1) {
            throw new AccessDeniedException("읽음 처리할 메시지를 확인할 수 없습니다.");
        }
    }

    /** 사원의 전체 안 읽은 메시지 수를 조회한다 */
    public int countUnread(Long companyId, Long employeeId) {
        if (!eService.isActiveEmployee(companyId, employeeId)) {
            throw new AccessDeniedException("사용 중인 재직 계정만 조회할 수 있습니다.");
        }

        return dao.countUnread(companyId, employeeId);
    }

    /** 1:1 상대방이 마지막으로 읽은 메시지 ID를 조회한다 */
    public Long getOtherLastReadId(
            Long companyId, Long roomId, Long employeeId) {

        if (!eService.isActiveEmployee(companyId, employeeId)
                || !isMember(companyId, roomId, employeeId)) {
            throw new AccessDeniedException("채팅방 참여자만 조회할 수 있습니다.");
        }

        return dao.getOtherLastReadId(companyId, roomId, employeeId);
    }

    /** 참여자를 검증해 채팅방을 만들거나 기존 1:1 방을 반환한다 */
    @Transactional
    public Long createRoom(
            Long companyId, Long employeeId,
            ChatRoomType roomType, String roomName,
            List<Long> members) {

        if (!eService.isActiveEmployee(companyId, employeeId)) {
            throw new AccessDeniedException("사용 중인 재직 계정만 방을 만들 수 있습니다.");
        }

        CompanyUsageDTO usage = sService.getCompanyUsage(companyId);
        if (usage != null && usage.isExpired()) {
            throw new AccessDeniedException("이용 기간이 끝나 방을 만들 수 없습니다.");
        }

        if (roomType == null || members == null) {
            throw new IllegalArgumentException("방 종류와 참여자를 선택해 주세요.");
        }

        Set<Long> participants = new LinkedHashSet<>(members);
        participants.remove(employeeId);

        if (participants.isEmpty()) {
            throw new IllegalArgumentException("대화할 상대를 선택해 주세요.");
        }

        if (roomType == ChatRoomType.ROOM_DIRECT
                && participants.size() != 1) {
            throw new IllegalArgumentException("1:1 대화는 상대를 한 명만 선택해 주세요.");
        }
        if (roomType == ChatRoomType.ROOM_GROUP && participants.size() < 2) {
            throw new IllegalArgumentException("그룹 대화는 본인 외 두 명 이상 선택해 주세요.");
        }

        for (Long participantId : participants) {
            if (!eService.isActiveEmployee(companyId, participantId)) {
                throw new AccessDeniedException("같은 업체의 활성 직원만 참여할 수 있습니다.");
            }
        }

        ChatRoomDTO room = new ChatRoomDTO();
        room.setCompanyId(companyId);
        room.setCreatedByEmployeeId(employeeId);
        room.setRoomType(roomType.name());

        if (roomType == ChatRoomType.ROOM_DIRECT) {
            Long otherId = participants.iterator().next();
            room.setDirectEmployeeLow(Math.min(employeeId, otherId));
            room.setDirectEmployeeHigh(Math.max(employeeId, otherId));

            Long existingId = dao.findDirectRoom(room);
            if (existingId != null) return existingId;
        } else {
            String name = roomName == null ? "" : roomName.strip();

            if (name.isBlank() || name.codePointCount(0, name.length()) > 200) {
                throw new IllegalArgumentException("그룹방 이름을 1~200자로 입력해 주세요.");
            }

            room.setRoomName(name);
        }

        try {
            if (dao.insertRoom(room) != 1) {
                throw new IllegalStateException("채팅방 저장에 실패했습니다.");
            }
        } catch (DuplicateKeyException e) {
            // 동시에 같은 1:1 방을 만들면 먼저 생성된 방을 재사용한다.
            if (roomType == ChatRoomType.ROOM_DIRECT) {
                Long existingId = dao.findDirectRoom(room);
                if (existingId != null) return existingId;
            }
            throw e;
        }

        participants.add(employeeId);

        for (Long participantId : participants) {
            if (dao.insertMember(companyId, room.getChatRoomId(), participantId) != 1) {
                throw new IllegalStateException("채팅방 참여자 저장에 실패했습니다.");
            }
        }

        return room.getChatRoomId();
    }


    
}
