package com.ieum.gw.chat.controller;

import com.ieum.gw.chat.dto.ChatEmpDto;
import com.ieum.gw.chat.dto.ChatRoomDTO;
import com.ieum.gw.chat.service.ChatService;
import com.ieum.gw.common.dto.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = "/chat")
public class ChatController {

    private final ChatService cService;

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
        String empnum = user.getEmpnum();
        List<ChatRoomDTO> roomlist = cService.getRoomList(empnum);
        model.addAttribute("user", user); // empnum만 넘겨도 ㄱㅊ을듯 나중에 판단
        model.addAttribute("roomList", roomlist);

        // 사원 조회는 나중에 emp 쪽 public 메소드를 호출하는걸로
        // 지금은 임시로 여기서 작업
        // getEmpList는 나를 제외한 사원 목록임
        List<ChatEmpDto> emplist = cService.getEmpList(user.getEmpnum());
        model.addAttribute("empList", emplist);


        return "chat/rooms";
    }

    // 채팅방
    @GetMapping(value = "/room/{roomId}")
    public String room(Model model,@PathVariable long roomId, @AuthenticationPrincipal LoginUser user) {

        model.addAttribute("roomId", roomId);
        return "chat/room";
    }

    // 채팅방 생성
    @PostMapping(value = "/createroom")
    public String createroom(Model model, @RequestParam String roomName, @RequestParam List<String> members, @AuthenticationPrincipal LoginUser user) {

        return "chat/createroom";
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
