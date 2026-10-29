package com.ieum.gw.chat.controller;

import com.ieum.gw.chat.dto.ChatRoomDTO;
import com.ieum.gw.chat.service.ChatService;
import com.ieum.gw.common.dto.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService cService;

    @GetMapping(value = "/chatroom.do")
    public String chatroom(Model model) {

        return "chat/chatroom";
    }
    @GetMapping(value = "/chatlist.do")
    public String chatlist(Model model, @AuthenticationPrincipal LoginUser user) {
        // @RequestParam("roomName") String roomName

        // 현재 세션 유저
        // 의 참여 채팅방
        String empnum = user.getEmpnum();
        List<ChatRoomDTO> roomlist = cService.getRoomList(empnum);


        model.addAttribute("user", user); // empnum만 넘겨도 ㄱㅊ을듯
        model.addAttribute("roomList", roomlist);
        return "chat/chatlist";
    }
}
