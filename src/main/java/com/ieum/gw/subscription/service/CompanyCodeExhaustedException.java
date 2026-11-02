package com.ieum.gw.subscription.service;

/** 업체 코드를 10번 뽑아 모두 겹쳤다(기능명세서 1.4). 새 체험 신청을 받을 수 없다고 안내한다 */
public class CompanyCodeExhaustedException extends RuntimeException {

    public CompanyCodeExhaustedException() {
        super("업체 코드를 더 만들 수 없어 지금은 새 체험 신청을 받을 수 없습니다. 도입 문의로 연락해 주세요.");
    }
}
