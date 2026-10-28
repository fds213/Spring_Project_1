package com.ieum.gw.chat.service;

import com.ieum.gw.chat.dao.ChatDAO;
import com.ieum.gw.chat.dto.ChatMessageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    @Autowired
    private ChatDAO dao;

    public void hitv() {
        System.out.println("hi zz");
    }

    public boolean isMember(long rId, String empnum) {
        return dao.isMember(rId, empnum) == 1;
    }

    @Transactional
    public void insertMessage(ChatMessageDTO dto) {
        dao.insertMessage(dto);
    }
}
