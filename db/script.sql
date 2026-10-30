-- =====================================================================
-- 로그인 테스트용 임시 테이블 (정식 EMP 테이블 확정 후 교체 예정)
-- 실행 위치: 팀 DB (192.168.10.41:1521/XEPDB1, ieum 계정) - 이미 생성됨, 다시 실행하지 말 것
-- 테스트 계정 비밀번호: 모두 1234
-- PK (compcode, empnum): 사번은 업체 안에서만 유일 (요구분석서 v1.7 3.11)
-- =====================================================================

CREATE TABLE test_emp (
    compcode varchar2(20)  NOT NULL,
    empnum   varchar2(20)  NOT NULL,
    pw       varchar2(100) NOT NULL,
    name     varchar2(50)  NOT NULL,
    role     varchar2(20)  NOT NULL CHECK (role IN ('ROLE_EMP','ROLE_IT','ROLE_HR','ROLE_ADMIN','ROLE_OPS')),
    status   varchar2(20)  NOT NULL CHECK (status IN ('REQUESTED','REJECTED','APPROVED','ACTIVE','WAITING','RETIRED')),
    CONSTRAINT pk_test_emp PRIMARY KEY (compcode, empnum)
);

INSERT INTO test_emp VALUES ('ieum0001','20261027','$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2','홍길동','ROLE_EMP','ACTIVE');
INSERT INTO test_emp VALUES ('ieum0001','20261026','$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2','아무개','ROLE_IT','ACTIVE');
INSERT INTO test_emp VALUES ('ieum0001','20261025','$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2','고양이','ROLE_HR','ACTIVE');
INSERT INTO test_emp VALUES ('ieum0001','20261024','$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2','강아지','ROLE_ADMIN','ACTIVE');
INSERT INTO test_emp VALUES ('ieum0001','20261023','$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2','코알라','ROLE_EMP','RETIRED');   -- 퇴사 (로그인 차단 확인)
INSERT INTO test_emp VALUES ('ieum0002','20261027','$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2','다른회사홍길동','ROLE_EMP','ACTIVE');  -- 같은 사번, 다른 업체
INSERT INTO test_emp VALUES ('ieum0000','20260001','$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2','이음운영자','ROLE_OPS','ACTIVE');    -- 운영자

ALTER TABLE test_emp ADD fail_count NUMBER DEFAULT 0 NOT NULL; -- 로그인 실패 횟수
ALTER TABLE test_emp ADD lock_until DATE; -- 실패시 언제까지 계정 lock ?

SELECT compcode, empnum, name, fail_count, lock_until FROM test_emp;

-- 로그인 테스트후 db 조회용 sql문 입니다. 로그인 실패시 count잘 올라가는지? 5번틀리면 lock잘 걸리는지 ?
--SELECT compcode, empnum, name, status, fail_count, lock_until FROM test_emp WHERE compcode = 'ieum0001';

-- 테스트후 count와 lock시간 초기화 sql문 입니다.
--UPDATE test_emp SET fail_count = 0, lock_until = NULL WHERE compcode = 'ieum0001';

COMMIT;

select * from TBLTESTEMP;

-- =====================================================================
-- 채팅 테이블 (chat 모듈) — 제안안. DB 담당 검토 후 확정
--   · 사원 FK는 임시 테이블 tblTestEmp(empnum) 기준. 정식 사원 테이블 확정 시 교체
--   · chat_attachment는 공통 파일 테이블이 확정되면 통합 여부 재검토
-- ② spring 계정(XEPDB1)에서 실행
-- =====================================================================

-- 시퀀스: 테이블마다 1개
CREATE SEQUENCE seq_chat_room;
CREATE SEQUENCE seq_chat_member;
CREATE SEQUENCE seq_chat_message;
CREATE SEQUENCE seq_chat_attachment;

DROP SEQUENCE seq_chat_room;
DROP SEQUENCE seq_chat_member;
DROP SEQUENCE seq_chat_message;
DROP SEQUENCE seq_chat_attachment;

-- 채팅방. 삭제하지 않는다(접속자가 없어도 방은 남는다).
CREATE TABLE chat_room (
    roomId     NUMBER         PRIMARY KEY,
    room_type   VARCHAR2(10)   NOT NULL CHECK (room_type IN ('DIRECT', 'GROUP')),
    room_name   VARCHAR2(100),                       -- 그룹방 이름. 1:1은 NULL
    direct_key  VARCHAR2(210),                       -- 1:1 방 중복 방지: 두 사번을 정렬해 '작은:큰'
    created_by  VARCHAR2(100)  NOT NULL REFERENCES tblTestEmp(empnum),
    created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_chat_room_direct UNIQUE (direct_key),   -- NULL은 여러 개 허용 → 그룹방 영향 없음
    CONSTRAINT ck_chat_room_key CHECK (
        (room_type = 'DIRECT' AND direct_key IS NOT NULL) OR
        (room_type = 'GROUP'  AND direct_key IS NULL AND room_name IS NOT NULL))
);

-- 참여자. 퇴장은 행을 지우지 않고 left_at에 기록한다.
CREATE TABLE chat_member (
    member_id         NUMBER        PRIMARY KEY,
    roomId           NUMBER        NOT NULL REFERENCES chat_room(roomId),
    empnum            VARCHAR2(100) NOT NULL REFERENCES tblTestEmp(empnum),
    joined_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    left_at           TIMESTAMP,
    last_read_msg_id  NUMBER        DEFAULT 0 NOT NULL,   -- 읽음 위치. 앞으로만 갱신
    CONSTRAINT uq_chat_member UNIQUE (roomId, empnum)
);
CREATE INDEX idx_chat_member_emp ON chat_member (empnum);   -- 내 방 목록

-- 메시지. msg_id가 순서 ID(재접속 누락분 조회 기준).
CREATE TABLE chat_message (
    msgId       NUMBER         PRIMARY KEY,
    roomId      NUMBER         NOT NULL REFERENCES chat_room(roomId),
    sender       VARCHAR2(100)  NOT NULL REFERENCES tblTestEmp(empnum),  -- 서버가 인증 정보로 채움
    msg_type     VARCHAR2(10)   NOT NULL CHECK (msg_type IN ('TEXT', 'IMAGE')),
    content      VARCHAR2(4000),
    created_at   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,            -- 서버 시각
    CONSTRAINT ck_chat_message_content CHECK (msg_type = 'IMAGE' OR content IS NOT NULL)
);
CREATE INDEX idx_chat_message_room ON chat_message (roomId, msgId);


-- 이미지 첨부. 업로드 후 메시지에 연결하므로 msg_id는 처음에 NULL.
CREATE TABLE chat_attachment (
    attach_id      NUMBER         PRIMARY KEY,
    room_id        NUMBER         NOT NULL REFERENCES chat_room(roomId),   -- 권한 검사 기준
    msg_id         NUMBER         REFERENCES chat_message(msgId),
    uploader       VARCHAR2(100)  NOT NULL REFERENCES tblTestEmp(empnum),
    original_name  VARCHAR2(255)  NOT NULL,
    stored_name    VARCHAR2(100)  NOT NULL,                                  -- UUID
    content_type   VARCHAR2(50)   NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    file_size      NUMBER         NOT NULL CHECK (file_size <= 5242880),     -- 5MB
    created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_chat_attach_stored UNIQUE (stored_name),
    CONSTRAINT uq_chat_attach_msg UNIQUE (msg_id)                            -- 메시지당 1장
);

INSERT INTO chat_room (roomId, room_type, room_name, created_by)
VALUES (seq_chat_room.NEXTVAL, 'GROUP', '테스트방', '20261027');

INSERT INTO chat_member (member_id, roomId, empnum)
VALUES (seq_chat_member.NEXTVAL, seq_chat_room.CURRVAL, '20261027');
INSERT INTO chat_member (member_id, roomId, empnum)
VALUES (seq_chat_member.NEXTVAL, seq_chat_room.CURRVAL, '20261026');

select * from chat_room;
select * from chat_member;
select * from chat_message;

drop table chat_room cascade constraints ;
drop table CHAT_MEMBER cascade constraints ;
drop table CHAT_MESSAGE cascade constraints ;
drop table CHAT_ATTACHMENT cascade constraints ;

commit;