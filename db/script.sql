-- =====================================================================
-- 로그인 테스트용 임시 테이블 (정식 EMP 테이블 확정 후 교체 예정)
-- 테스트 계정 비밀번호: 모두 1234
--
-- 실행 순서
--   ① system 계정으로 접속 (SID xe 아님! 서비스 이름 XEPDB1)
--   ② spring 계정으로 접속 (서비스 이름 XEPDB1)
-- =====================================================================

-- ---------------------------------------------------------------------
-- ① system 계정(XEPDB1)에서 1번만 실행
--    안 하면 INSERT 때 ORA-01950 (테이블스페이스 권한 없음) 발생
-- ---------------------------------------------------------------------
-- ALTER USER spring QUOTA UNLIMITED ON USERS;


-- ---------------------------------------------------------------------
-- ② spring 계정(XEPDB1)에서 실행
-- ---------------------------------------------------------------------
CREATE TABLE tblTestEmp (
    empnum varchar2(100) PRIMARY KEY,
    pw     varchar2(100) NOT NULL,
    name   varchar2(50)  NOT NULL,
    role   varchar2(50)  NOT NULL CHECK (role IN ('ROLE_EMP', 'ROLE_IT', 'ROLE_HR', 'ROLE_ADMIN')),
    status varchar2(50)  NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED'))
);

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261027', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', '홍길동', 'ROLE_EMP', 'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261026', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', '아무개', 'ROLE_IT', 'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261025', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', '고양이', 'ROLE_HR', 'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261024', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', '강아지', 'ROLE_ADMIN', 'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261023', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', '코알라', 'ROLE_EMP', 'RETIRED');   -- 퇴사자 (로그인 차단 확인용)

COMMIT;

select * from TBLTESTEMP;