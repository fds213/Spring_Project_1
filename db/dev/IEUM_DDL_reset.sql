-- 개발 DB 초기화 전용. 지정한 프로젝트 테이블 60개·시퀀스 50개와 데이터가 삭제된다.
-- 팀 공유 DB에는 실행하지 않는다. 이 파일은 재생성하지 않고 삭제만 한다.
-- 이전 82개 구조의 DB를 60개 구조로 바꿀 때는 먼저 README의 정리 패치 절차를 따른다.
-- 복구가 필요하면 실행 전에 별도 DB 백업을 확보한다. DDL/PURGE는 ROLLBACK 불가.
SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

-- 1. 실행 계정 및 삭제 대상 밖에서 들어오는 FK 확인
DECLARE
    v_external_fk_count PLS_INTEGER;
BEGIN
    IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'IEUM'
       OR SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'IEUM' THEN
        RAISE_APPLICATION_ERROR(-20001, 'IEUM 계정과 IEUM 스키마에서 실행해야 한다.');
    END IF;
    SELECT COUNT(*) INTO v_external_fk_count
      FROM ALL_CONSTRAINTS child
      JOIN ALL_CONSTRAINTS parent
        ON parent.OWNER = child.R_OWNER
       AND parent.CONSTRAINT_NAME = child.R_CONSTRAINT_NAME
     WHERE child.CONSTRAINT_TYPE = 'R'
       AND parent.OWNER = USER
       AND parent.TABLE_NAME IN (
           'COMMON_CODE', 'COMPANY', 'DEPARTMENT', 'JOB_GRADE', 'BRANCH',
           'EMPLOYEE', 'USER_ACCOUNT', 'ACCOUNT_REQUEST', 'EMAIL_VERIFICATION', 'EMAIL_RATE_LIMIT',
           'EMPLOYEE_NO_COUNTER', 'SUBSCRIPTION_PLAN', 'SERVICE_PERIOD', 'CAPACITY_REQUEST', 'PAYMENT_ORDER',
           'PAYMENT', 'SUBSCRIPTION_CHANGE', 'CAPACITY_LIMIT', 'APPROVAL_DOCUMENT', 'APPROVAL_STEP',
           'APPROVAL_HISTORY', 'LEAVE_REQUEST', 'LEAVE_REQUEST_DAY', 'LEAVE_CYCLE', 'LEAVE_LEDGER',
           'LEAVE_SLOT', 'EQUIPMENT_REQUEST', 'ATTENDANCE', 'ATTENDANCE_DEVICE', 'DEVICE_EMPLOYEE',
           'ATTENDANCE_EVENT', 'HOLIDAY', 'OVERTIME_RECORD', 'ASSET', 'IT_TICKET',
           'TICKET_COMMENT', 'TICKET_HISTORY', 'ASSET_ASSIGNMENT', 'ASSET_HISTORY', 'CALENDAR_EVENT',
           'BOARD', 'POST', 'POST_COMMENT', 'CHAT_ROOM', 'CHAT_MEMBER',
           'CHAT_MESSAGE', 'WORK_MAP', 'MAP_MARKER', 'ATTACHMENT', 'APPROVAL_FILE',
           'POST_FILE', 'TICKET_FILE', 'NOTIFICATION', 'PUBLIC_INQUIRY', 'PUBLIC_REPLY',
           'MAIL_OUTBOX', 'MAIL_ATTEMPT', 'SUPPORT_INQUIRY', 'SUPPORT_REPLY', 'ATTENDANCE_CORRECTION'
       )
       AND (child.OWNER <> USER OR child.TABLE_NAME NOT IN (
           'COMMON_CODE', 'COMPANY', 'DEPARTMENT', 'JOB_GRADE', 'BRANCH',
           'EMPLOYEE', 'USER_ACCOUNT', 'ACCOUNT_REQUEST', 'EMAIL_VERIFICATION', 'EMAIL_RATE_LIMIT',
           'EMPLOYEE_NO_COUNTER', 'SUBSCRIPTION_PLAN', 'SERVICE_PERIOD', 'CAPACITY_REQUEST', 'PAYMENT_ORDER',
           'PAYMENT', 'SUBSCRIPTION_CHANGE', 'CAPACITY_LIMIT', 'APPROVAL_DOCUMENT', 'APPROVAL_STEP',
           'APPROVAL_HISTORY', 'LEAVE_REQUEST', 'LEAVE_REQUEST_DAY', 'LEAVE_CYCLE', 'LEAVE_LEDGER',
           'LEAVE_SLOT', 'EQUIPMENT_REQUEST', 'ATTENDANCE', 'ATTENDANCE_DEVICE', 'DEVICE_EMPLOYEE',
           'ATTENDANCE_EVENT', 'HOLIDAY', 'OVERTIME_RECORD', 'ASSET', 'IT_TICKET',
           'TICKET_COMMENT', 'TICKET_HISTORY', 'ASSET_ASSIGNMENT', 'ASSET_HISTORY', 'CALENDAR_EVENT',
           'BOARD', 'POST', 'POST_COMMENT', 'CHAT_ROOM', 'CHAT_MEMBER',
           'CHAT_MESSAGE', 'WORK_MAP', 'MAP_MARKER', 'ATTACHMENT', 'APPROVAL_FILE',
           'POST_FILE', 'TICKET_FILE', 'NOTIFICATION', 'PUBLIC_INQUIRY', 'PUBLIC_REPLY',
           'MAIL_OUTBOX', 'MAIL_ATTEMPT', 'SUPPORT_INQUIRY', 'SUPPORT_REPLY', 'ATTENDANCE_CORRECTION'
       ));
    IF v_external_fk_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20002, '대상 밖 테이블의 참조 FK가 있어 중단한다. 참조 관계를 확인해야 한다.');
    END IF;
END;
/

-- 2. 지정한 테이블 삭제 (테이블 인덱스와 제약도 함께 삭제됨)
DECLARE
    PROCEDURE drop_project_table(p_name VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP TABLE ' || p_name || ' CASCADE CONSTRAINTS PURGE';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE <> -942 THEN RAISE; END IF;
    END;
BEGIN
    drop_project_table('SUPPORT_REPLY');
    drop_project_table('SUPPORT_INQUIRY');
    drop_project_table('MAIL_ATTEMPT');
    drop_project_table('MAIL_OUTBOX');
    drop_project_table('PUBLIC_REPLY');
    drop_project_table('PUBLIC_INQUIRY');
    drop_project_table('NOTIFICATION');
    drop_project_table('TICKET_FILE');
    drop_project_table('POST_FILE');
    drop_project_table('APPROVAL_FILE');
    drop_project_table('ATTACHMENT');
    drop_project_table('MAP_MARKER');
    drop_project_table('WORK_MAP');
    drop_project_table('CHAT_MESSAGE');
    drop_project_table('CHAT_MEMBER');
    drop_project_table('CHAT_ROOM');
    drop_project_table('POST_COMMENT');
    drop_project_table('POST');
    drop_project_table('BOARD');
    drop_project_table('CALENDAR_EVENT');
    drop_project_table('ASSET_HISTORY');
    drop_project_table('ASSET_ASSIGNMENT');
    drop_project_table('TICKET_HISTORY');
    drop_project_table('TICKET_COMMENT');
    drop_project_table('IT_TICKET');
    drop_project_table('ASSET');
    drop_project_table('OVERTIME_RECORD');
    drop_project_table('HOLIDAY');
    drop_project_table('ATTENDANCE_EVENT');
    drop_project_table('DEVICE_EMPLOYEE');
    drop_project_table('ATTENDANCE_DEVICE');
    drop_project_table('ATTENDANCE_CORRECTION');
    drop_project_table('ATTENDANCE');
    drop_project_table('EQUIPMENT_REQUEST');
    drop_project_table('LEAVE_SLOT');
    drop_project_table('LEAVE_LEDGER');
    drop_project_table('LEAVE_CYCLE');
    drop_project_table('LEAVE_REQUEST_DAY');
    drop_project_table('LEAVE_REQUEST');
    drop_project_table('APPROVAL_HISTORY');
    drop_project_table('APPROVAL_STEP');
    drop_project_table('APPROVAL_DOCUMENT');
    drop_project_table('CAPACITY_LIMIT');
    drop_project_table('SUBSCRIPTION_CHANGE');
    drop_project_table('PAYMENT');
    drop_project_table('PAYMENT_ORDER');
    drop_project_table('CAPACITY_REQUEST');
    drop_project_table('SERVICE_PERIOD');
    drop_project_table('SUBSCRIPTION_PLAN');
    drop_project_table('EMPLOYEE_NO_COUNTER');
    drop_project_table('EMAIL_RATE_LIMIT');
    drop_project_table('EMAIL_VERIFICATION');
    drop_project_table('ACCOUNT_REQUEST');
    drop_project_table('USER_ACCOUNT');
    drop_project_table('EMPLOYEE');
    drop_project_table('BRANCH');
    drop_project_table('JOB_GRADE');
    drop_project_table('DEPARTMENT');
    drop_project_table('COMPANY');
    drop_project_table('COMMON_CODE');
END;
/

-- 3. 지정한 시퀀스 삭제
DECLARE
    PROCEDURE drop_project_sequence(p_name VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || p_name;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE <> -2289 THEN RAISE; END IF;
    END;
BEGIN
    drop_project_sequence('SQ_SUPPORT_REPLY');
    drop_project_sequence('SQ_SUPPORT_INQUIRY');
    drop_project_sequence('SQ_MAIL_ATTEMPT');
    drop_project_sequence('SQ_MAIL_OUTBOX');
    drop_project_sequence('SQ_PUBLIC_REPLY');
    drop_project_sequence('SQ_PUBLIC_INQUIRY');
    drop_project_sequence('SQ_NOTIFICATION');
    drop_project_sequence('SQ_ATTACHMENT');
    drop_project_sequence('SQ_MAP_MARKER');
    drop_project_sequence('SQ_WORK_MAP');
    drop_project_sequence('SQ_CHAT_MESSAGE');
    drop_project_sequence('SQ_CHAT_ROOM');
    drop_project_sequence('SQ_POST_COMMENT');
    drop_project_sequence('SQ_POST');
    drop_project_sequence('SQ_BOARD');
    drop_project_sequence('SQ_CALENDAR_EVENT');
    drop_project_sequence('SQ_ASSET_HISTORY');
    drop_project_sequence('SQ_ASSET_ASSIGNMENT');
    drop_project_sequence('SQ_TICKET_HISTORY');
    drop_project_sequence('SQ_TICKET_COMMENT');
    drop_project_sequence('SQ_IT_TICKET');
    drop_project_sequence('SQ_ASSET');
    drop_project_sequence('SQ_OVERTIME_RECORD');
    drop_project_sequence('SQ_HOLIDAY');
    drop_project_sequence('SQ_ATTENDANCE_EVENT');
    drop_project_sequence('SQ_ATTENDANCE_DEVICE');
    drop_project_sequence('SQ_ATTENDANCE_CORRECTION');
    drop_project_sequence('SQ_ATTENDANCE');
    drop_project_sequence('SQ_LEAVE_LEDGER');
    drop_project_sequence('SQ_LEAVE_CYCLE');
    drop_project_sequence('SQ_LEAVE_REQUEST_DAY');
    drop_project_sequence('SQ_APPROVAL_HISTORY');
    drop_project_sequence('SQ_APPROVAL_STEP');
    drop_project_sequence('SQ_APPROVAL_DOCUMENT');
    drop_project_sequence('SQ_CAPACITY_LIMIT');
    drop_project_sequence('SQ_SUBSCRIPTION_CHANGE');
    drop_project_sequence('SQ_PAYMENT');
    drop_project_sequence('SQ_PAYMENT_ORDER');
    drop_project_sequence('SQ_CAPACITY_REQUEST');
    drop_project_sequence('SQ_SERVICE_PERIOD');
    drop_project_sequence('SQ_SUBSCRIPTION_PLAN');
    drop_project_sequence('SQ_EMAIL_RATE_LIMIT');
    drop_project_sequence('SQ_EMAIL_VERIFICATION');
    drop_project_sequence('SQ_ACCOUNT_REQUEST');
    drop_project_sequence('SQ_USER_ACCOUNT');
    drop_project_sequence('SQ_EMPLOYEE');
    drop_project_sequence('SQ_BRANCH');
    drop_project_sequence('SQ_JOB_GRADE');
    drop_project_sequence('SQ_DEPARTMENT');
    drop_project_sequence('SQ_COMPANY');
END;
/


PROMPT 초기화 완료. 빈 스키마에 IEUM_DDL.sql을 별도로 실행한다.
