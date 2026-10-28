package com.ieum.gw.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatController {

    @GetMapping(value = "/chatroom.do")
    public String chatroom(Model model) {

        return "chat/chatroom";
    }
//    @GetMapping(value = "/chat.do")
//    public String chat(Model model) {
//
//        return "chat";
//    }
}
