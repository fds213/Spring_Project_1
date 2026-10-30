package com.ieum.gw.chat.service;

import com.ieum.gw.chat.dao.ChatDAO;
import com.ieum.gw.chat.dto.ChatMessageDTO;
import com.ieum.gw.chat.dto.ChatRoomDTO;
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

//    public List<ChatEmpDto> getEmpList(String empnum) {
//        return dao.getEmpList(empnum);
//    }
}
