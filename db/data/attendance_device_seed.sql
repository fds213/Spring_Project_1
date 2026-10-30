-- =====================================================================
-- 근태 단말기(지문 시뮬레이터) 초기 데이터 (시연·테스트용)
-- 실행: 팀 DB(ieum 계정). init_auth_data.sql(업체·사원) 다음에 실행한다. PK는 컬럼 기본값(SQ_*.NEXTVAL)
-- 단말기 키는 해시(SHA-256)만 넣는다. 원래 키는 git에 올리지 않고 db/device-keys.local.txt 로 따로 전한다
--   (값을 비운 예시: db/device-keys.example). 시뮬레이터 화면에서 이 키를 입력하면 그 업체의 단말기가 된다.
-- 단말기 사용자 번호는 업체 안에서만 쓰는 번호다. 두 업체가 같은 0001 을 써도 단말기(키)가 달라 섞이지 않는다.
-- 다시 실행하면 고유 제약 때문에 실패한다(이미 들어간 상태). 지우고 다시 넣을 때만 아래 정리 문장을 먼저 실행한다:
--   DELETE FROM device_employee WHERE attendance_device_id IN (SELECT attendance_device_id FROM attendance_device WHERE device_name = '정문 지문 단말기');
--   DELETE FROM attendance_device WHERE device_name = '정문 지문 단말기';
-- =====================================================================

-- 1. 업체마다 단말기 1대
INSERT INTO attendance_device (company_id, device_name, device_key_hash)
SELECT company_id, '정문 지문 단말기', 'fa9b2bd690b5a7c45a8e08a2bd851c953516cbaf5c290c162aae963f0fdb813a' FROM company WHERE company_code = 'ieum0001';
INSERT INTO attendance_device (company_id, device_name, device_key_hash)
SELECT company_id, '정문 지문 단말기', 'e8811e1be325df058d0719c38e9cfa02371417de749cf0245081d37e4a81b4bf' FROM company WHERE company_code = 'ieum0002';

-- 2. 단말기 사용자 번호 ↔ 사원 (코알라 20260005 는 퇴사자: 체크가 거절되는지 보는 용도)
INSERT INTO device_employee (company_id, attendance_device_id, device_user_no, employee_id)
SELECT e.company_id, d.attendance_device_id, '000' || SUBSTR(e.employee_no, 8, 1), e.employee_id
FROM employee e
JOIN company c ON c.company_id = e.company_id
JOIN attendance_device d ON d.company_id = e.company_id AND d.device_name = '정문 지문 단말기'
WHERE c.company_code = 'ieum0001' AND e.employee_no IN ('20260001', '20260002', '20260003', '20260004', '20260005');

INSERT INTO device_employee (company_id, attendance_device_id, device_user_no, employee_id)
SELECT e.company_id, d.attendance_device_id, '000' || SUBSTR(e.employee_no, 8, 1), e.employee_id
FROM employee e
JOIN company c ON c.company_id = e.company_id
JOIN attendance_device d ON d.company_id = e.company_id AND d.device_name = '정문 지문 단말기'
WHERE c.company_code = 'ieum0002' AND e.employee_no IN ('20260001', '20260002');

COMMIT;

-- 확인
SELECT c.company_code, d.device_name, de.device_user_no, e.employee_no, e.employee_name, e.employment_status
FROM device_employee de
JOIN attendance_device d ON d.attendance_device_id = de.attendance_device_id
JOIN company c ON c.company_id = de.company_id
JOIN employee e ON e.employee_id = de.employee_id
ORDER BY c.company_code, de.device_user_no;
