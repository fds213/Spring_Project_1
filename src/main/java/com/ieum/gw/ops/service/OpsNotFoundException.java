package com.ieum.gw.ops.service;

/** 운영자 화면에서 고른 것이 없다(404) */
public class OpsNotFoundException extends RuntimeException {

    public OpsNotFoundException(String message) {
        super(message);
    }
}
