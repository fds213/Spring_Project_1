package com.ieum.gw.controller;

import com.ieum.gw.code.ChatRoomType;
import com.ieum.gw.dto.AttachmentDTO;
import com.ieum.gw.server.ChatServerEndpoint;
import com.ieum.gw.server.ChatUpdateEndpoint;
import com.ieum.gw.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.ChatRoomDTO;
import com.ieum.gw.service.ChatService;
import com.ieum.gw.dto.LoginUser;
import com.ieum.gw.dto.EmpSummaryDTO;
import com.ieum.gw.dto.EmployeeOptionDTO;
import com.ieum.gw.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = "/chat")
public class ChatController {

    private final ChatService cService;
    private final EmployeeService eService;
    private final AttachmentService attachmentService;
    private final ChatServerEndpoint chatEndpoint;
    private final ChatUpdateEndpoint chatUpdates;

    /** 방별 안 읽은 수와 전체 합계를 반환한다 */
    @GetMapping("/unread")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unread(@AuthenticationPrincipal LoginUser user) {
        List<ChatRoomDTO> rooms = cService.getRoomList(user.getCompanyId(), user.getEmployeeId());
        int total = rooms.stream().mapToInt(ChatRoomDTO::getUnreadCount).sum();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("total", total, "rooms", rooms));
    }


    /** 이미지 한 장을 저장하고 새 메시지일 때만 방에 알린다 */
    @PostMapping(value = "/room/{roomId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ChatMessageDTO sendImage(@PathVariable long roomId,
                                    @RequestParam String clientMessageKey,
                                    @RequestParam("image") List<MultipartFile> images,
                                    @AuthenticationPrincipal LoginUser user) throws IOException {
        if (images.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 한 장을 선택해 주세요.");
        }
        ChatService.MessageSaveResult result;
        try {
            result = cService.sendImage(roomId, user, clientMessageKey, images.get(0));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        ChatMessageDTO message = result.getMessage();
        message.setSenderName(user.getName());
        if (result.isInserted()) chatEndpoint.publishMessage(message, user);
        return message;
    }

    /** 참여자가 요청한 채팅 이미지를 반환한다 */
    @GetMapping("/room/{roomId}/messages/{messageId}/image")
    public ResponseEntity<Resource> image(@PathVariable long roomId, @PathVariable long messageId,
                                          @AuthenticationPrincipal LoginUser user) throws IOException {
        AttachmentDTO attachment = cService.getImage(roomId, messageId, user);
        if (attachment == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .body(attachmentService.load(attachment));
    }


//    @GetMapping(value = "/rooms.do")
//    public String rooms(Model model) {
//
//        return "chat/chatroom";
//    }

    // 채팅 메인
    @GetMapping(value = "/rooms")
    public String rooms(Model model, @AuthenticationPrincipal LoginUser user) {
        addRoomsPage(model, user);
        return "chat/rooms";
    }

    /** 채팅방: 방 목록 화면과 같은 두 칸 화면에서 오른쪽에 이 방을 연다. 대화는 화면 JS가 메시지 주소로 불러온다 */
    @GetMapping(value = "/room/{roomId}")
    public String room(Model model, @PathVariable long roomId, @AuthenticationPrincipal LoginUser user) {
        // 참여하지 않은 방이면 여기서 403(AccessDeniedException)으로 끝난다
        cService.getRoomMembers(user.getCompanyId(), roomId, user.getEmployeeId());
        addRoomsPage(model, user);
        model.addAttribute("roomId", roomId);
        return "chat/rooms";
    }

    /** 두 칸 채팅 화면의 공통 모델: 새 대화 창의 사원 목록, 내 직원 번호. 방 목록은 화면이 GET /chat/unread 로 받는다 */
    private void addRoomsPage(Model model, LoginUser user) {
        List<EmpSummaryDTO> empList = eService.listActiveEmp(user.getCompanyId(), user.getEmployeeId());
        model.addAttribute("empList", empList);
        model.addAttribute("employeeId", user.getEmployeeId());
    }

    /** 방 참여자(직원 번호·이름·부서, 본인 포함). 대화 칸의 참여자 목록과 1:1 대화 버튼이 쓴다 */
    @GetMapping("/room/{roomId}/members")
    @ResponseBody
    public List<EmployeeOptionDTO> members(@PathVariable long roomId, @AuthenticationPrincipal LoginUser user) {
        return cService.getRoomMembers(user.getCompanyId(), roomId, user.getEmployeeId());
    }

    // 이전 메시지 받기
    /** 이전 대화 한 페이지를 반환한다 */
    @GetMapping("/room/{roomId}/messages")
    @ResponseBody
    public List<ChatMessageDTO> messages(@PathVariable Long roomId, @RequestParam(required = false) Long beforeMessageId, @AuthenticationPrincipal LoginUser user) {

        if (beforeMessageId != null && beforeMessageId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "메시지 ID가 올바르지 않습니다.");
        }

        return cService.getMessages(
                user.getCompanyId(), roomId,
                user.getEmployeeId(), beforeMessageId);
    }

    // 이전 메시지 보다 더 전 메시지 받기
    /** 재접속 시 마지막 수신 이후의 대화를 반환한다 */
    @GetMapping("/room/{roomId}/messages/after")
    @ResponseBody
    public List<ChatMessageDTO> messagesAfter(@PathVariable Long roomId, @RequestParam Long afterMessageId, @AuthenticationPrincipal LoginUser user) {

        if (afterMessageId < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "메시지 ID가 올바르지 않습니다.");
        }

        return cService.getMessagesAfter(
                user.getCompanyId(), roomId,
                user.getEmployeeId(), afterMessageId);
    }

    // 읽음 처리
    /** 읽음 위치를 저장하고 배지 갱신을 알린다 */
    @PostMapping("/room/{roomId}/read")
    @ResponseBody
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @PathVariable Long roomId,
            @RequestParam Long messageId,
            @AuthenticationPrincipal LoginUser user) {

        if (messageId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "메시지 ID가 올바르지 않습니다.");
        }

        cService.markRead(
                user.getCompanyId(), roomId,
                user.getEmployeeId(), messageId);
        chatUpdates.publishUser(user.getCompanyId(), user.getEmployeeId());
    }

    /** 1:1 상대방의 읽음 위치를 반환한다 */
    @GetMapping("/room/{roomId}/read")
    @ResponseBody
    public Map<String, String> readState(
            @PathVariable Long roomId,
            @AuthenticationPrincipal LoginUser user) {

        Long lastReadId = cService.getOtherLastReadId(
                user.getCompanyId(), roomId, user.getEmployeeId());

        return Map.of("lastReadMessageId", lastReadId.toString());
    }

    // 채팅방 생성
    /** 채팅방을 만들거나 기존 1:1 방으로 이동한다 */
    @PostMapping("/createroom")
    public String createroom(@RequestParam ChatRoomType roomType, @RequestParam(required = false) String roomName, @RequestParam(required = false) List<Long> members, @AuthenticationPrincipal LoginUser user) {

        try {
            Long roomId = cService.createRoom(
                    user.getCompanyId(), user.getEmployeeId(),
                    roomType, roomName, members);

            chatUpdates.publishRoom(user.getCompanyId(), roomId);
            return "redirect:/chat/room/" + roomId;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // 채팅방 퇴장
    @PostMapping(value = "/leaveroom")
    public String leaveroom(Model model, @RequestParam String roomName, @RequestParam List<String> members, @AuthenticationPrincipal LoginUser user) {

        return "chat/leaveroom";
    }

    // 채팅방 삭제( 필요한가 ? )
//    @PostMapping(value = "/delroom")
//    public String delroom(Model model, @RequestParam String roomName, @RequestParam List<String> members, @AuthenticationPrincipal LoginUser user) {
//
//        return "chat/delroom";
//    }
}
