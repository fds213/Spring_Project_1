-- 전달문의 1~17번 점검 SELECT. 읽기 전용. 변경 전/후 동일 계정에서 실행한다.
SET DEFINE OFF
SELECT '1 가입 활성화 칸' 항목, CASE WHEN COUNT(*) = 2 THEN '완료' ELSE '할 일' END 결과 FROM user_tab_columns WHERE table_name = 'DEPARTMENT' AND column_name IN ('JOIN_OPEN_UNTIL', 'JOIN_OPENED_BY')
UNION ALL SELECT '2 가입 활성화 외래키', CASE WHEN COUNT(*) = 1 THEN '완료' ELSE '할 일' END FROM user_constraints WHERE constraint_name = 'FK_DEPARTMENT_JOIN_OPENER'
UNION ALL SELECT '3 채팅 읽음 칸', CASE WHEN COUNT(*) = 1 THEN '완료' ELSE '할 일' END FROM user_tab_columns WHERE table_name = 'CHAT_MEMBER' AND column_name = 'LAST_READ_MESSAGE_ID'
UNION ALL SELECT '4 그룹 채팅방 제약', CASE WHEN (SELECT COUNT(*) FROM user_constraints WHERE table_name = 'CHAT_ROOM' AND constraint_name = 'UQ_55_01') = 0 AND (SELECT COUNT(*) FROM user_indexes WHERE table_name = 'CHAT_ROOM' AND uniqueness = 'UNIQUE' AND index_type LIKE 'FUNCTION%') >= 1 THEN '완료' ELSE '할 일' END FROM dual
UNION ALL SELECT '5 티켓 VERSION 칸', CASE WHEN COUNT(*) = 1 THEN '완료' ELSE '할 일' END FROM user_tab_columns WHERE table_name = 'IT_TICKET' AND column_name = 'VERSION'
UNION ALL SELECT '6 공통 코드 224행 이상과 PD_TRIAL', CASE WHEN COUNT(*) >= 224 AND SUM(CASE WHEN code = 'PD_TRIAL' THEN 1 ELSE 0 END) = 1 THEN '완료' ELSE '할 일' END FROM common_code
UNION ALL SELECT '7 이메일 고유 인덱스', CASE WHEN COUNT(*) = 1 THEN '완료' ELSE '할 일' END FROM user_indexes WHERE index_name = 'UX_COMPANY_EMAIL' AND uniqueness = 'UNIQUE'
UNION ALL SELECT '8 월간 요금제', CASE WHEN COUNT(*) = 1 THEN '완료' ELSE '할 일' END FROM subscription_plan WHERE plan_code = 'PLAN_MONTHLY' AND use_yn = 'Y' AND duration_months = 1 AND monthly_unit_price = 4000
UNION ALL SELECT '9 시험 업체 이용 기간', CASE WHEN COUNT(DISTINCT p.company_id) = 2 THEN '완료' ELSE '할 일' END FROM service_period p JOIN company c ON c.company_id = p.company_id WHERE c.company_code IN ('ieum0001', 'ieum0002') AND p.starts_at <= CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS TIMESTAMP) AND p.ends_at > CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Seoul' AS TIMESTAMP)
UNION ALL SELECT '10 시험 업체 지점', CASE WHEN COUNT(*) = 2 THEN '완료' ELSE '할 일' END FROM (SELECT b.company_id FROM branch b JOIN company c ON c.company_id = b.company_id WHERE c.company_code IN ('ieum0001', 'ieum0002') AND b.use_yn = 'Y' GROUP BY b.company_id HAVING COUNT(*) >= 3)
UNION ALL SELECT '11 시험 업체 자산', CASE WHEN (SELECT COUNT(*) FROM asset a JOIN company c ON c.company_id = a.company_id WHERE c.company_code IN ('ieum0001', 'ieum0002')) > 0 AND (SELECT COUNT(*) FROM branch b JOIN company c ON c.company_id = b.company_id WHERE c.company_code IN ('ieum0001', 'ieum0002') AND NOT EXISTS (SELECT 1 FROM asset a WHERE a.company_id = b.company_id AND a.branch_id = b.branch_id)) = 0 THEN '완료' ELSE '할 일' END FROM dual
UNION ALL SELECT '12 시험 업체 직원 근무 지점', CASE WHEN COUNT(*) = 0 THEN '완료' ELSE '할 일' END FROM employee e JOIN company c ON c.company_id = e.company_id WHERE c.company_code IN ('ieum0001', 'ieum0002') AND e.branch_id IS NULL
UNION ALL SELECT '13 문의 칸(회사명·이름·휴대전화)', CASE WHEN COUNT(*) = 3 THEN '완료' ELSE '할 일' END FROM user_tab_columns WHERE table_name = 'PUBLIC_INQUIRY' AND column_name IN ('COMPANY_NAME', 'REQUESTER_NAME', 'PHONE')
UNION ALL SELECT '14 부서 이름 고유 제약', CASE WHEN COUNT(*) = 1 THEN '완료' ELSE '할 일' END FROM user_constraints WHERE constraint_name = 'UQ_03_02'
UNION ALL SELECT '15 읽음 칸 외래키', CASE WHEN COUNT(*) = 1 THEN '완료' ELSE '할 일' END FROM user_constraints WHERE constraint_name = 'FK_CHAT_MEMBER_LAST_READ'
UNION ALL SELECT '16 시퀀스 최댓값', CASE WHEN COUNT(*) = 0 THEN '완료' ELSE '할 일' END FROM user_sequences WHERE max_value = 99999
UNION ALL SELECT '17 옛 시험 표·시퀀스', CASE WHEN COUNT(*) = 0 THEN '완료' ELSE '할 일' END FROM (SELECT table_name n FROM user_tables WHERE table_name IN ('TEST_EMP', 'TBLTESTEMP', 'CHAT_ATTACHMENT') UNION ALL SELECT sequence_name FROM user_sequences WHERE sequence_name IN ('SEQ_CHAT_ROOM', 'SEQ_CHAT_MEMBER', 'SEQ_CHAT_MESSAGE', 'SEQ_CHAT_ATTACHMENT'));

-- 추가 확인: 사용 코드 12개가 모두 Y여야 한다.
SELECT CODE, USE_YN FROM COMMON_CODE
WHERE CODE IN ('TK_QUESTION','EV_LEAVE','POST_PUBLISHED','POST_REMOVED','FILE_REMOVED','FC_IMAGE','FC_DOCUMENT','FC_SPREADSHEET','FC_PRESENTATION','FC_ARCHIVE','FC_OTHER','EMP_REGULAR') ORDER BY CODE;
-- 오류가 나면 일부 변경이 적용된 상태일 수 있다. 오류와 항목을 기록하고 아래 안내 문서를 확인한다.
