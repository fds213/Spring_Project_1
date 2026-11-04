package com.ieum.gw.service;

import com.ieum.gw.dao.ChatDAO;
import com.ieum.gw.dto.ChatMessageDTO;
import com.ieum.gw.dto.ChatRoomDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {

    @Autowired
    private ChatDAO dao;

    public void hitv() {
        System.out.println("hi zz");
    }

    public boolean isMember(long roomId, Long empNum) {
        return dao.isMember(roomId, empNum) == 1;
    }

    @Transactional
    public void insertMessage(ChatMessageDTO dto) {
        dao.insertMessage(dto);
    }

    public List<ChatRoomDTO> getRoomList(Long companyId, Long employeeId) {
        return dao.getRoomList(companyId, employeeId);
    }
    
}
