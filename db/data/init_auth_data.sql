-- =====================================================================
-- 로그인·사원 초기 데이터 (시연·테스트용)
-- 실행: 팀 DB(ieum 계정). PK는 컬럼 기본값(SQ_*.NEXTVAL)으로 자동 생성
-- 순서: COMPANY → DEPARTMENT·JOB_GRADE → EMPLOYEE → USER_ACCOUNT → 부서장 → 사번 카운터
-- 비밀번호: 모두 1234 (BCrypt)
-- 사번 규칙: 발급 연도 4자리 + 업체별 일련번호 4자리, 업체마다 0001부터 (요구분석서 ACC-03)
-- =====================================================================

-- 1. 업체 (ieum0000 = 이음 운영 업체)
INSERT INTO company (company_code, company_name, trial_email, requested_seats, trial_requested_at, email_verified_at)
VALUES ('ieum0000', '이음(운영)', 'ops@ieum.test', 10, LOCALTIMESTAMP, LOCALTIMESTAMP);
INSERT INTO company (company_code, company_name, trial_email, requested_seats, trial_requested_at, email_verified_at)
VALUES ('ieum0001', '테스트업체A', 'admin@ieum0001.test', 10, LOCALTIMESTAMP, LOCALTIMESTAMP);
INSERT INTO company (company_code, company_name, trial_email, requested_seats, trial_requested_at, email_verified_at)
VALUES ('ieum0002', '테스트업체B', 'admin@ieum0002.test', 10, LOCALTIMESTAMP, LOCALTIMESTAMP);

-- 2. 부서
INSERT INTO department (company_id, department_name, sort_order)
SELECT company_id, '운영팀', 1 FROM company WHERE company_code = 'ieum0000';
INSERT INTO department (company_id, department_name, sort_order)
SELECT company_id, '경영지원팀', 1 FROM company WHERE company_code = 'ieum0001';
INSERT INTO department (company_id, department_name, sort_order)
SELECT company_id, '개발팀', 2 FROM company WHERE company_code = 'ieum0001';
INSERT INTO department (company_id, department_name, sort_order)
SELECT company_id, 'IT지원팀', 3 FROM company WHERE company_code = 'ieum0001';
INSERT INTO department (company_id, department_name, sort_order)
SELECT company_id, '영업팀', 1 FROM company WHERE company_code = 'ieum0002';

-- 3. 직급 (업체마다 같은 6단계, 요구분석서 3.9 직급별 경력)
INSERT INTO job_grade (company_id, grade_name, sort_order)
SELECT c.company_id, g.grade_name, g.sort_order
FROM company c CROSS JOIN (SELECT '사원' AS grade_name, 1 AS sort_order FROM dual UNION ALL SELECT '주임' AS grade_name, 2 AS sort_order FROM dual UNION ALL SELECT '대리' AS grade_name, 3 AS sort_order FROM dual UNION ALL SELECT '과장' AS grade_name, 4 AS sort_order FROM dual UNION ALL SELECT '차장' AS grade_name, 5 AS sort_order FROM dual UNION ALL SELECT '부장' AS grade_name, 6 AS sort_order FROM dual) g
WHERE c.company_code IN ('ieum0000', 'ieum0001', 'ieum0002');

-- 4. 사원 (이전 경력: 사원 0·주임 24·대리 48·과장 84·차장 120·부장 156개월)
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260001', '이음운영자', d.department_id, g.job_grade_id, DATE '2024-01-02', 84, 'ST_SERVING', NULL
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = '운영팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '과장'
WHERE c.company_code = 'ieum0000';
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260001', '강아지', d.department_id, g.job_grade_id, DATE '2020-03-02', 156, 'ST_SERVING', NULL
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = '경영지원팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '부장'
WHERE c.company_code = 'ieum0001';
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260002', '홍길동', d.department_id, g.job_grade_id, DATE '2024-03-04', 48, 'ST_SERVING', NULL
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = '개발팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '대리'
WHERE c.company_code = 'ieum0001';
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260003', '아무개', d.department_id, g.job_grade_id, DATE '2025-01-02', 24, 'ST_SERVING', NULL
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = 'IT지원팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '주임'
WHERE c.company_code = 'ieum0001';
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260004', '고양이', d.department_id, g.job_grade_id, DATE '2022-07-01', 84, 'ST_SERVING', NULL
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = '경영지원팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '과장'
WHERE c.company_code = 'ieum0001';
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260005', '코알라', d.department_id, g.job_grade_id, DATE '2025-05-02', 0, 'ST_RETIRED', DATE '2026-09-30'
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = '개발팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '사원'
WHERE c.company_code = 'ieum0001';
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260001', '다른회사관리자', d.department_id, g.job_grade_id, DATE '2021-04-01', 156, 'ST_SERVING', NULL
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = '영업팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '부장'
WHERE c.company_code = 'ieum0002';
INSERT INTO employee (company_id, employee_no, employee_name, department_id, job_grade_id, hire_date, prior_career_months, employment_status, retire_date)
SELECT c.company_id, '20260002', '다른회사홍길동', d.department_id, g.job_grade_id, DATE '2025-02-03', 0, 'ST_SERVING', NULL
FROM company c
JOIN department d ON d.company_id = c.company_id AND d.department_name = '영업팀'
JOIN job_grade  g ON g.company_id = c.company_id AND g.grade_name = '사원'
WHERE c.company_code = 'ieum0002';

-- 5. 로그인 계정
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'ops@ieum.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_OPS', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP, NULL
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0000' AND e.employee_no = '20260001';  -- 이음운영자
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'admin@ieum0001.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_ADMIN', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP, NULL
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0001' AND e.employee_no = '20260001';  -- 강아지
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'hong@ieum0001.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_EMP', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP, NULL
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0001' AND e.employee_no = '20260002';  -- 홍길동
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'it@ieum0001.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_IT', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP, NULL
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0001' AND e.employee_no = '20260003';  -- 아무개
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'hr@ieum0001.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_HR', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP, NULL
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0001' AND e.employee_no = '20260004';  -- 고양이
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'koala@ieum0001.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_EMP', 'RETIRED', LOCALTIMESTAMP, LOCALTIMESTAMP, LOCALTIMESTAMP
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0001' AND e.employee_no = '20260005';  -- 코알라
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'admin@ieum0002.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_ADMIN', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP, NULL
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0002' AND e.employee_no = '20260001';  -- 다른회사관리자
INSERT INTO user_account (company_id, employee_id, email, password_hash, role_code, status_code, email_verified_at, activated_at, deactivated_at)
SELECT e.company_id, e.employee_id, 'hong@ieum0002.test', '$2a$10$/EgOc8YI8ovfzFdRPnYzoesPSCh.0z2cMh49WedJZttUw3BPFNRS2', 'ROLE_EMP', 'ACTIVE', LOCALTIMESTAMP, LOCALTIMESTAMP, NULL
FROM employee e JOIN company c ON c.company_id = e.company_id
WHERE c.company_code = 'ieum0002' AND e.employee_no = '20260002';  -- 다른회사홍길동

-- 6. 부서장 (사원이 생긴 뒤에 지정)
UPDATE department d
SET head_employee_id = (SELECT e.employee_id FROM employee e WHERE e.company_id = d.company_id AND e.employee_no = '20260001')
WHERE d.department_name = '운영팀' AND d.company_id = (SELECT company_id FROM company WHERE company_code = 'ieum0000');
UPDATE department d
SET head_employee_id = (SELECT e.employee_id FROM employee e WHERE e.company_id = d.company_id AND e.employee_no = '20260001')
WHERE d.department_name = '경영지원팀' AND d.company_id = (SELECT company_id FROM company WHERE company_code = 'ieum0001');
UPDATE department d
SET head_employee_id = (SELECT e.employee_id FROM employee e WHERE e.company_id = d.company_id AND e.employee_no = '20260002')
WHERE d.department_name = '개발팀' AND d.company_id = (SELECT company_id FROM company WHERE company_code = 'ieum0001');
UPDATE department d
SET head_employee_id = (SELECT e.employee_id FROM employee e WHERE e.company_id = d.company_id AND e.employee_no = '20260001')
WHERE d.department_name = '영업팀' AND d.company_id = (SELECT company_id FROM company WHERE company_code = 'ieum0002');

-- 7. 업체별 마지막 사번 일련번호 (다음 가입 승인 때 이어서 발급)
INSERT INTO employee_no_counter (company_id, last_serial)
SELECT company_id, 1 FROM company WHERE company_code = 'ieum0000';
INSERT INTO employee_no_counter (company_id, last_serial)
SELECT company_id, 5 FROM company WHERE company_code = 'ieum0001';
INSERT INTO employee_no_counter (company_id, last_serial)
SELECT company_id, 2 FROM company WHERE company_code = 'ieum0002';

COMMIT;

-- 확인
SELECT c.company_code, e.employee_no, e.employee_name, dp.department_name, g.grade_name,
       a.role_code, a.status_code, e.employment_status
FROM user_account a
JOIN company    c  ON c.company_id     = a.company_id
JOIN employee   e  ON e.employee_id    = a.employee_id
JOIN department dp ON dp.department_id = e.department_id
JOIN job_grade  g  ON g.job_grade_id   = e.job_grade_id
ORDER BY c.company_code, e.employee_no;
