-- 익명 게시판·자료실: 기존 DB 전용 1회 패치.
-- 별도 실행 승인 후 관련 쓰기를 멈추고 미확정 작업이 없는 연결에서 실행한다.
-- DDL은 자동 확정된다. 일부 실패 시 상태 확인 없이 다시 실행하지 않는다.

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

-- 다른 연결이 게시판 줄을 만드는 순간과 겹쳐도 바로 실패하지 않고 30초까지 기다린다.
ALTER SESSION SET DDL_LOCK_TIMEOUT = 30;

-- 이미 적용된 구조와 예상하지 않은 기존 상태에서는 아무것도 바꾸지 않고 중단한다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'BOARD'
       AND CONSTRAINT_NAME = 'CK_52_01'
       AND (SEARCH_CONDITION_VC LIKE '%BOARD_ANON%'
            OR SEARCH_CONDITION_VC LIKE '%BOARD_LIBRARY%');

    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'CK_52_01에 익명 게시판·자료실이 이미 있습니다.');
    END IF;

    -- 앞선 실행이 중간에 멈춰 임시 제약이 남았으면 db/README.md의 복구 순서를 따른다.
    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'CK_52_01_TMP';

    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20002, '임시 제약 CK_52_01_TMP가 남아 있습니다. db/README.md 복구 순서를 보세요.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'BOARD'
       AND CONSTRAINT_NAME = 'CK_52_01'
       AND CONSTRAINT_TYPE = 'C'
       AND STATUS = 'ENABLED'
       AND VALIDATED = 'VALIDATED'
       AND SEARCH_CONDITION_VC LIKE '%BOARD_NOTICE%'
       AND SEARCH_CONDITION_VC LIKE '%BOARD_DEPT%';

    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20003, '기존 CK_52_01 상태가 예상과 다릅니다.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'BOARD'
       AND CONSTRAINT_NAME = 'FK_52_02'
       AND CONSTRAINT_TYPE = 'R'
       AND STATUS = 'ENABLED';

    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20004, '게시판 종류 FK_52_02를 확인하세요.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM COMMON_CODE
     WHERE CODE = 'BOARD_TYPE';

    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20005, '상위 코드 BOARD_TYPE이 없습니다.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM COMMON_CODE
     WHERE CODE IN ('BOARD_ANON', 'BOARD_LIBRARY')
        OR (PARENT_CODE = 'BOARD_TYPE' AND SORT_ORDER IN (3, 4));

    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20006, '익명 게시판·자료실 코드나 정렬 순서 3·4번이 이미 있습니다.');
    END IF;
END;
/

-- CHECK는 새 정의를 임시 이름으로 먼저 추가·검증한 뒤 옛 정의를 지우고 이름을 바꾼다.
-- 어느 단계에서 멈춰도 옛 규칙이나 새 규칙 중 하나는 걸려 있다. 멈춘 뒤 이어 가는 법은 db/README.md.

-- 1단계: 새 규칙 추가. 전사 공지·익명 게시판·자료실은 업체 전체 게시판이라 부서가 없고, 부서 게시판만 부서가 있다.
ALTER TABLE BOARD ADD CONSTRAINT CK_52_01_TMP CHECK (
    (BOARD_TYPE IN ('BOARD_NOTICE', 'BOARD_ANON', 'BOARD_LIBRARY')
        AND DEPARTMENT_ID IS NULL)
 OR (BOARD_TYPE = 'BOARD_DEPT'
        AND DEPARTMENT_ID IS NOT NULL)
) ENABLE VALIDATE;

-- 2단계: 옛 규칙 삭제.
ALTER TABLE BOARD DROP CONSTRAINT CK_52_01;

-- 3단계: 새 규칙을 원래 이름으로.
ALTER TABLE BOARD RENAME CONSTRAINT CK_52_01_TMP TO CK_52_01;

-- DML은 모든 DDL 뒤에 수행한다.
INSERT INTO COMMON_CODE (
    CODE, PARENT_CODE, CODE_NAME, DESCRIPTION, USE_YN, SORT_ORDER
)
SELECT 'BOARD_ANON',
       'BOARD_TYPE',
       '익명 게시판',
       '작성자 이름을 보이지 않는 업체 게시판',
       'Y',
       3
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM COMMON_CODE
     WHERE CODE = 'BOARD_ANON'
);

INSERT INTO COMMON_CODE (
    CODE, PARENT_CODE, CODE_NAME, DESCRIPTION, USE_YN, SORT_ORDER
)
SELECT 'BOARD_LIBRARY',
       'BOARD_TYPE',
       '자료실',
       '업체 직원이 문서 파일을 올리고 내려받는 게시판',
       'Y',
       4
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM COMMON_CODE
     WHERE CODE = 'BOARD_LIBRARY'
);

-- 사후 점검과 정의 확인 후 같은 연결에서 직접 COMMIT한다.
-- 오류 발생 시 중단한다. ROLLBACK으로 앞선 DDL은 취소되지 않는다.
