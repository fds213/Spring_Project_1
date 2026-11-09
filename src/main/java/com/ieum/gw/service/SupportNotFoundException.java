package com.ieum.gw.service;

/** 고른 이음 장애 문의가 없다(404) */
public class SupportNotFoundException extends RuntimeException {

    public SupportNotFoundException(String message) {
        super(message);
    }
}
