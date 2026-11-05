-- 기존 DB에 자산 지급·회수 요청의 버전을 확인할 컬럼을 추가합니다.
ALTER TABLE ASSET ADD (VERSION NUMBER(10) DEFAULT 0 NOT NULL);