package com.ieum.gw.subscription.service;

/** 이 이메일로 만든 업체가 이미 있다(만료·자료 삭제된 업체 포함, ACC-01). 체험 신청 화면에는 409로 알린다 */
public class TrialEmailUsedException extends RuntimeException {

    public TrialEmailUsedException() {
        super("이미 이 이메일로 만든 업체가 있습니다. 그 업체의 관리자 계정으로 로그인해 주세요.");
    }
}
