CREATE TABLE tblTestEmp (
    empnum varchar2(100) PRIMARY KEY,
    pw varchar2(100)  NOT NULL,
    name varchar2(50) NOT NULL,
    role   varchar2(50) NOT NULL CHECK (role IN ('ROLE_EMP', 'ROLE_IT', 'ROLE_HR', 'ROLE_ADMIN')),
    status varchar2(50) NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED'))
);

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261027', '$2a$10$F5.J0gqea5ggM.AoBxwEyOBebVR20p1PusHyJ1e9hMCCmTBPSbGh6', '홍길동', 'ROLE_EMP',   'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261026', '$2a$10$FeQiyNUFwJP1s9YiG9FjreIDIff5zUstFx4Ztnf0lTxsXSJlxFGe6', '가나디', 'ROLE_IT',    'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261025', '$2a$10$8ZMkCMd7V/ipWr5zzTeB8etc0QzPR3Sdr8d0trL0SmVl8HZ1GeGG6', '하이연', 'ROLE_HR',    'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261024', '$2a$10$VxPLmiEUBUDRfwUaA8c5MOr7bNSfrBpWnVW0wZBEqNuc4DFEg8v0m', '보준원', 'ROLE_ADMIN', 'ACTIVE');

INSERT INTO tblTestEmp (empnum, pw, name, role, status)
VALUES ('20261023', '$2a$10$Duqu78dQgBlWF/eHEJDPFOptOyxsj6lIwD4dIHf5oJ2YrJQpBmu1K', '보요한', 'ROLE_EMP',   'RETIRED');   -- 퇴사자

COMMIT;

select * from TBLTESTEMP;