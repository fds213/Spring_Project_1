package com.ieum.gw.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 장애·문의 접수 화면의 자산·지점 선택 목록. */
@Getter
@RequiredArgsConstructor
public class TicketFormOptionsDTO {
    private final List<AssetOption> assets;
    private final List<BranchOption> branches;
    private final Long defaultBranchId;

    /** 제조 일련번호가 없는 자산은 serial이 null일 수 있다. */
    @Getter
    @RequiredArgsConstructor
    public static class AssetOption {
        private final Long id;
        private final String name;
        private final String serial;
        private final Long branchId;
    }

    @Getter
    @RequiredArgsConstructor
    public static class BranchOption {
        private final Long id;
        private final String name;
    }
}
