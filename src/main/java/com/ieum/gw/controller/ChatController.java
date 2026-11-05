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

    @GetMapping("/unread")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unread(@AuthenticationPrincipal LoginUser user) {
        List<ChatRoomDTO> rooms = cService.getRoomList(user.getCompanyId(), user.getEmployeeId());
        int total = rooms.stream().mapToInt(ChatRoomDTO::getUnreadCount).sum();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("total", total, "rooms", rooms));
    }


    @PostMapping(value = "/room/{roomId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ChatMessageDTO sendImage(@PathVariable long roomId,
                                    @RequestParam String clientMessageKey,
                                    @RequestParam("image") List<MultipartFile> images,
                                    @AuthenticationPrincipal LoginUser user) throws IOException {
        if (images.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 한 장을 선택해 주세요.");
        }
        ChatMessageDTO message;
        try {
            message = cService.sendImage(roomId, user, clientMessageKey, images.get(0));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        message.setSenderName(user.getName());
        chatEndpoint.publishMessage(message, user);
        return message;
    }

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
        // @RequestParam("roomName") String roomName

        // 현재 세션 유저
        // 의 참여 채팅방
//        Long empNum = user.getEmployeeId();
        List<ChatRoomDTO> roomlist = cService.getRoomList(user.getCompanyId(), user.getEmployeeId());
        model.addAttribute("user", user); // empnum만 넘겨도 ㄱㅊ을듯 나중에 판단
        model.addAttribute("roomList", roomlist);

        List<EmpSummaryDTO> empList = eService.listActiveEmp(user.getCompanyId(), user.getEmployeeId());
        model.addAttribute("empList", empList);


        return "chat/rooms";
    }

    // 채팅방
    @GetMapping(value = "/room/{roomId}")
    public String room(Model model,@PathVariable long roomId, @AuthenticationPrincipal LoginUser user) {
        List<ChatMessageDTO> messages = cService.getMessages(user.getCompanyId(), roomId, user.getEmployeeId(), null);

        model.addAttribute("roomId", roomId);
        model.addAttribute("messageList", messages);
        model.addAttribute("employeeId", user.getEmployeeId());
        model.addAttribute("roomMembers", cService.getRoomMemberNames(
                user.getCompanyId(), roomId, user.getEmployeeId()));

        return "chat/room";
    }

    // 이전 메시지 받기
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
