package com.ieum.gw.chat.handler;

import com.ieum.gw.common.dto.LoginUser;
import com.ieum.gw.chat.dto.ChatMessageDTO;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final List<WebSocketSession> sessionList = new ArrayList<>();
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        Authentication auth = (Authentication) session.getPrincipal();
        LoginUser user = (LoginUser) auth.getPrincipal();
        String empnum = user.getEmpnum();
//        String empnum = session.getPrincipal().getName();   // 로그인 사번

        sessionList.add(session);
        System.out.println("sessionList = " + sessionList.size());
        sessionList.forEach(s-> {
            try {
                s.sendMessage(new TextMessage(empnum+"님께서 입장하셨습니다."));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        Gson gson = new Gson();

        Authentication auth = (Authentication) session.getPrincipal();
        LoginUser user = (LoginUser) auth.getPrincipal();
        String empnum = user.getEmpnum();

        sessionList.forEach(s-> {
            try {
                ChatMessageDTO chatMessage = gson.fromJson(message.getPayload(), ChatMessageDTO.class);
                s.sendMessage(new TextMessage(empnum + " : "+chatMessage.getContent()+"["+chatMessage.getDate()+"]"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        sessionList.remove(session);
        System.out.println("session = " + sessionList.size());

        Authentication auth = (Authentication) session.getPrincipal();
        LoginUser user = (LoginUser) auth.getPrincipal();
        String empnum = user.getEmpnum();

        sessionList.forEach(s-> {
            try {
                s.sendMessage(new TextMessage(empnum+"님께서 퇴장하셨습니다."));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}