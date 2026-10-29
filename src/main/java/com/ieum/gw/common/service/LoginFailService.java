package com.ieum.gw.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ieum.gw.common.dao.AuthDAO;

@Service
public class LoginFailService {

    @Autowired
    private AuthDAO dao;

    // 비밀번호 틀림: 횟수 +1, 5번째면 10분 잠금
    public void recordFail(String compcode, String empnum) {
        dao.recordFail(compcode, empnum);
    }

    // 로그인 성공: 횟수·잠금 초기화
    public void resetFail(String compcode, String empnum) {
        dao.resetFail(compcode, empnum);
    }
}