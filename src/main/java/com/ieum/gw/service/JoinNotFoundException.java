package com.ieum.gw.service;

/** 가입 승인 화면에서 고른 요청이 없다(404) */
public class JoinNotFoundException extends RuntimeException {

    public JoinNotFoundException(String message) {
        super(message);
    }
}