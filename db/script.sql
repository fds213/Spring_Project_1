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
COMMIT;