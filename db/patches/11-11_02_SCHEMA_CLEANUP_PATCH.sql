-- 기존 IEUM DB 전용. 빈 테이블 22개와 관련 시퀀스 16개를 정리한다.
-- 애플리케이션 쓰기를 중지하고 백업을 확인한 다음 파일을 스크립트로 실행한다.
-- DDL은 자동 확정된다. 실패하면 사후 점검과 실행 로그로 남은 작업을 확인한다.
SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

@@11-11_01_SCHEMA_CLEANUP_PRECHECK.sql

DECLARE
    c_drop_tables CONSTANT SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
        'ACCOUNT_PERMISSION', 'ATTENDANCE_ASSESSMENT', 'DAILY_NOTICE_VIEW', 'DATA_PURGE_RUN',
        'DEMO_ACCOUNT', 'DEMO_DEPARTMENT', 'DEPARTMENT_PERMISSION', 'ERROR_ARCHIVE',
        'EVENT_DEPARTMENT', 'EVENT_EMPLOYEE', 'MAP_BUNDLE_ITEM', 'MAP_MARKER_GRANT',
        'PAYMENT_REMINDER', 'ROLE_PERMISSION', 'SUPPORT_ACTION', 'SUPPORT_CONSENT',
        'USAGE_SNAPSHOT', 'MAP_BUNDLE_GRANT', 'SUPPORT_SCOPE', 'MAP_SHARE_REQUEST',
        'SUPPORT_GRANT', 'MAP_BUNDLE'
    );
    c_drop_sequences CONSTANT SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
        'SQ_ACCOUNT_PERMISSION', 'SQ_ATTENDANCE_ASSESSMENT', 'SQ_DAILY_NOTICE_VIEW', 'SQ_DATA_PURGE_RUN',
        'SQ_DEPARTMENT_PERMISSION', 'SQ_ERROR_ARCHIVE', 'SQ_MAP_BUNDLE', 'SQ_MAP_BUNDLE_GRANT',
        'SQ_MAP_MARKER_GRANT', 'SQ_MAP_SHARE_REQUEST', 'SQ_PAYMENT_REMINDER', 'SQ_SUPPORT_ACTION',
        'SQ_SUPPORT_CONSENT', 'SQ_SUPPORT_GRANT', 'SQ_SUPPORT_SCOPE', 'SQ_USAGE_SNAPSHOT'
    );
    v_name VARCHAR2(128);
    v_count PLS_INTEGER;
    v_has_row PLS_INTEGER;
BEGIN
    -- 목록 안의 자식 테이블부터 삭제한다. 목록 밖 FK가 있으면 DROP TABLE이 중단된다.
    FOR i IN 1 .. c_drop_tables.COUNT LOOP
        v_name := c_drop_tables(i);
        SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = v_name;
        IF v_count = 1 THEN
            EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || v_name || ' WHERE ROWNUM = 1' INTO v_has_row;
            IF v_has_row > 0 THEN
                RAISE_APPLICATION_ERROR(-20605, '삭제 직전 데이터가 확인되었습니다: ' || v_name);
            END IF;
            EXECUTE IMMEDIATE 'DROP TABLE ' || v_name;
            DBMS_OUTPUT.PUT_LINE('삭제 완료: ' || v_name);
        END IF;
    END LOOP;

    FOR i IN 1 .. c_drop_sequences.COUNT LOOP
        v_name := c_drop_sequences(i);
        SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = v_name;
        IF v_count = 1 THEN
            EXECUTE IMMEDIATE 'DROP SEQUENCE ' || v_name;
            DBMS_OUTPUT.PUT_LINE('삭제 완료: ' || v_name);
        END IF;
    END LOOP;
END;
/
