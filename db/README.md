# IEUM DB 스크립트

Oracle XE 21c / XEPDB1 / IEUM 계정 기준. 이 폴더의 내용은 GitHub 저장소의 `db/` 아래에 같은 구조로 배치한다. SQL과 실행 안내는 Git 저장소의 `db/` 디렉터리에서 함께 관리한다.

현재 생성 기준: **프로젝트 테이블 82개 / 시퀀스 66개 / 공통 코드 235개**. 별도 보존한 TEST_EMP는 프로젝트 테이블 수에 포함하지 않는다.

## 파일 용도

| 파일 | 용도 |
|---|---|
| [ddl/IEUM_DDL.sql](ddl/IEUM_DDL.sql) | **신규 빈 스키마**에 테이블·시퀀스·제약·인덱스 생성 |
| [data/IEUM_COMMON_CODE_DML.sql](data/IEUM_COMMON_CODE_DML.sql) | 신규 DB에 기본 공통 코드 235개 입력 |
| [data/init_auth_data.sql](data/init_auth_data.sql) | 신규 DB에 시연 업체·직원·계정 입력. 시연 보완 파일도 자동 실행 |
| [data/IEUM_DEMO_SEED.sql](data/IEUM_DEMO_SEED.sql) | 월간·연간 요금제, 시연 업체 기간·지점·자산·직원 지점 보완. init 또는 데이터 패치에서 호출 |
| [patches/11-02_01_SCHEMA_PATCH.sql](patches/11-02_01_SCHEMA_PATCH.sql) | 기존 DB의 11/02 구조 변경. 기존 자료 보존, 같은 정의는 검증 후 유지 |
| [patches/11-02_02_DATA_PATCH.sql](patches/11-02_02_DATA_PATCH.sql) | 기존 DB의 누락 코드·12개 코드 활성화·시연 자료 보완. 시연 보완과 점검 파일 자동 실행 |
| [patches/11-02_03_CHECK.sql](patches/11-02_03_CHECK.sql) | 읽기 전용 점검. 전달문 17개 항목 및 지정 코드 12개 조회 |
| [patches/11-02_04_ATTENDANCE_CORRECTION_PATCH.sql](patches/11-02_04_ATTENDANCE_CORRECTION_PATCH.sql) | 기존 DB에 근태정정 테이블·시퀀스·제약·인덱스·상태 코드 추가, 기존 시퀀스 최댓값 조정. 한 번 실행 |
| [patches/11-02_05_ATTENDANCE_CORRECTION_CHECK.sql](patches/11-02_05_ATTENDANCE_CORRECTION_CHECK.sql) | 근태정정 구조·코드·시퀀스 7개 항목 읽기 전용 점검 |
| [patches/11-03_01_ANNUAL_RENEW_PATCH.sql](patches/11-03_01_ANNUAL_RENEW_PATCH.sql) | 기존 DB에 연간 요금제 행 추가, 코드 `PLAN_YEARLY`·`PP_RENEW` 활성화. 구조 변경 없음. 다시 실행해도 결과가 같다 |
| [patches/11-03_02_ANNUAL_RENEW_CHECK.sql](patches/11-03_02_ANNUAL_RENEW_CHECK.sql) | 연간 요금제·코드 3개 항목 읽기 전용 점검 |
| [dev/IEUM_DDL_reset.sql](dev/IEUM_DDL_reset.sql) | **폐기 가능한 개발 DB 전용. 테이블 82개·시퀀스 66개와 데이터 삭제. 팀 DB 실행 금지** |
| [patches/11-06_01_LEAVE_GRANT_PATCH.sql](patches/11-06_01_LEAVE_GRANT_PATCH.sql) | 기존 DB의 연차 추가 부여 구조·코드 변경. 승인 후 1회 실행 |
| [patches/11-06_02_LEAVE_GRANT_CHECK.sql](patches/11-06_02_LEAVE_GRANT_CHECK.sql) | 연차 추가 부여 컬럼·제약·인덱스·코드 읽기 전용 점검 |
| [patches/11-06_03_ATTENDANCE_NOTIFICATION_PATCH.sql](patches/11-06_03_ATTENDANCE_NOTIFICATION_PATCH.sql) | 기존 DB에 근태 정정 알림 컬럼·FK·인덱스·CHECK·코드 추가. 승인 후 1회 실행 |
| [patches/11-06_04_ATTENDANCE_NOTIFICATION_CHECK.sql](patches/11-06_04_ATTENDANCE_NOTIFICATION_CHECK.sql) | 근태 정정 알림 구조·코드 읽기 전용 점검 |
| [patches/11-09_01_SUPPORT_NOTIFICATION_PATCH.sql](patches/11-09_01_SUPPORT_NOTIFICATION_PATCH.sql) | 기존 DB에 이음 장애 문의 답변 알림 컬럼·FK·인덱스·CHECK·코드 추가. 승인 후 1회 실행 |
| [patches/11-09_02_SUPPORT_NOTIFICATION_CHECK.sql](patches/11-09_02_SUPPORT_NOTIFICATION_CHECK.sql) | 이음 장애 문의 답변 알림 구조·코드 읽기 전용 점검 |
| [patches/11-10_01_BOARD_EXTRA_PATCH.sql](patches/11-10_01_BOARD_EXTRA_PATCH.sql) | 기존 DB에 익명 게시판·자료실 게시판 종류 코드 추가, CK_52_01 교체. 승인 후 1회 실행 |
| [patches/11-10_02_BOARD_EXTRA_CHECK.sql](patches/11-10_02_BOARD_EXTRA_CHECK.sql) | 익명 게시판·자료실 CHECK·코드 읽기 전용 점검 |

## 현재 팀 DB 상태

2026-11-02, `ieum_project` 연결의 XEPDB1 / IEUM에 구조·데이터 패치 및 **COMMIT 완료**. 점검 1~16번 완료, 17번의 `TEST_EMP`는 삭제하지 않고 보존했다. **현재 팀 DB에 같은 패치를 다시 실행할 필요는 없다.** 애플리케이션 기능 검증은 별도다.

- 티켓 VERSION, 문의 컬럼 3개, 부서명 UNIQUE, 채팅 읽음 복합 FK와 참조 키 반영.
- 최신 시퀀스 정책은 기존 65개와 신규 1개 모두 **MAXVALUE 99999 / NOCYCLE**. 기존 시작값·현재 번호는 초기화하지 않는다. 근태정정 패치까지 적용 완료했다.
- 11/06 연차 추가 부여 패치까지 반영한 공통 코드는 230개다. 기존 지정 코드 12개와 정정 상태 코드 5개, LC_GRANT는 모두 Y다. 팀 DB는 기존 MSG_IMAGE=Y를 유지하며 연간·연장 코드 활성화 패치는 미적용이므로 Y 115개다. 신규 적재 파일은 PLAN_YEARLY·PP_RENEW 활성화를 포함해 Y 116개다.
- PLAN_MONTHLY: 1개월·1인 4,000원·사용 Y. 시연 업체의 직원 지점 NULL 없음.
- ieum0001: 기존 지점 포함 지점 4개·자산 6개. ieum0002: 지점 3개·자산 5개.

## 연간 요금제·만료 전 연장 (11/03, 팀 DB 미적용)

이미 11/02 패치가 적용된 기존 DB는 `patches/11-03_01_ANNUAL_RENEW_PATCH.sql` 실행 → `patches/11-03_02_ANNUAL_RENEW_CHECK.sql` PASS 확인 → 같은 연결에서 직접 `COMMIT;`한다. 신규 빈 DB는 `IEUM_COMMON_CODE_DML.sql`과 `IEUM_DEMO_SEED.sql`에 들어 있어 실행하지 않는다. 넣기 전에도 앱은 돈다: 이용 결제 화면에 월간만 나오고, 월간 연장은 된다.

## 연차 추가 부여 (11/06, 팀 DB 적용 완료)

2026-11-06 팀 DB 패치 적용·COMMIT 완료.
점검 8개 항목 PASS와 CHECK·인덱스 정의 일치를 확인했다.
이미 적용한 팀 DB에는 패치를 다시 실행하지 않는다.

- LEAVE_LEDGER.NOTE: VARCHAR2(500 CHAR), 부여 사유.
- LEAVE_REQUEST_DAY_ID: 추가 부여 기록을 위해 NULL 허용.
- CK_33_01: 사용·복원은 신청일 필수, 추가 부여는 신청일 없음·사유 필수·0일 금지.
- CK_33_02: 기존 0.5일 단위 규칙 유지.
- UQ_33_01을 조건부 고유 인덱스 UX_33_01로 대체.
- LC_GRANT 추가. 신규 DB의 공통 코드 기준은 230개.
- 11/06 파일 기준: 테이블 82개·컬럼 794개·시퀀스 66개·공통 코드 230개.
- 신규 공통 코드 적재 파일의 USE_YN='Y'는 116개다.

### 기존 DB

공용 DB 실행은 별도 승인을 받은 뒤 진행한다.

1. 관련 쓰기를 멈추고 기존 미확정 작업이 없는 별도 연결을 사용한다.
2. 11-06_01_LEAVE_GRANT_PATCH.sql을 실행한다.
3. 11-06_02_LEAVE_GRANT_CHECK.sql의 모든 PASS와 CHECK·인덱스 식을 확인한다.
4. 같은 연결에서 직접 COMMIT한다.

DDL은 자동 확정되어 ROLLBACK으로 취소되지 않는다.
오류 발생 시 중단하고, 일부 적용 상태를 확인하기 전에는 패치를 다시 실행하지 않는다.
전체 DDL·공통 코드 적재 파일·reset은 기존 DB에 실행하지 않는다.

### 신규 빈 DB

기존 신규 DB 생성 순서를 따른다.
수정된 전체 DDL과 공통 코드 적재 파일에 이번 변경이 포함되어 있으므로
11-06_01 패치는 실행하지 않고, 11-06_02 점검만 추가로 실행한다.

공용 DB 반영과 CI 검증은 별개다.
DB 변경이 포함된 커밋을 PR #164 브랜치에도 반영한 뒤 CI를 다시 확인한다.

## 신규 빈 개발 DB 실행 순서

1. `ddl/IEUM_DDL.sql`
2. `data/IEUM_COMMON_CODE_DML.sql`
3. `data/init_auth_data.sql` — `IEUM_DEMO_SEED.sql`도 자동 실행
4. `patches/11-02_03_CHECK.sql` 및 `patches/11-02_05_ATTENDANCE_CORRECTION_CHECK.sql`
5. `patches/11-06_02_LEAVE_GRANT_CHECK.sql`
6. `patches/11-06_04_ATTENDANCE_NOTIFICATION_CHECK.sql`
7. `patches/11-09_02_SUPPORT_NOTIFICATION_CHECK.sql`
8. `patches/11-10_02_BOARD_EXTRA_CHECK.sql`
9. 오류와 결과를 확인한 뒤 같은 연결에서 직접 `COMMIT;`

신규 DDL에는 근태정정이 포함되어 있으므로
`11-02_04_ATTENDANCE_CORRECTION_PATCH.sql`은 실행하지 않는다.

기존 자료를 모두 버려도 되는 **별도 개발 DB에서만**, 위 순서 전에 `dev/IEUM_DDL_reset.sql`을 실행할 수 있다. 초기화는 ROLLBACK으로 복구되지 않는다. 신규 생성 DDL은 기존 객체를 건너뛰지 않는다. 이 생성·삭제 경로는 이번 팀 DB 패치 실행 검증에 포함하지 않았다.

## 근태 정정 알림 (11/06, 팀 DB 적용 완료)

2026-11-06 팀 DB 패치 적용·COMMIT 완료.
점검 6개 항목 PASS와 CHECK·FK·인덱스 정의 일치를 확인했다.
이미 적용한 팀 DB에는 패치를 다시 실행하지 않는다.

- NOTIFICATION.ATTENDANCE_CORRECTION_ID 추가.
- FK_69_07로 같은 업체의 근태 정정 요청을 참조한다.
- IX_69_06으로 업체·근태 정정 요청별 조회를 지원한다.
- CK_69_01은 알림 종류에 맞는 대상 ID 하나만 허용한다.
- NT_ATT_CORRECTION_REQUEST·NT_ATT_CORRECTION_DONE 추가.
- 신규 공통 코드 기준은 232개다.

기존 DB는 별도 실행 승인 후 03 패치 → 04 점검과 정의 확인 →
같은 연결에서 직접 COMMIT한다.
DDL은 자동 확정되므로 오류 발생 시 상태 확인 없이 재실행하지 않는다.

신규 빈 DB는 수정된 전체 DDL·공통 코드 적재 파일을 사용한다.
03 패치는 실행하지 않고 04 점검만 추가한다.

DB 변경을 develop과 팀 DB에 반영한 뒤 #174를 병합한다.
이후 근태 담당이 알림 호출과 화면 이동을 연결한다.

## 이음 장애 문의 답변 알림 (11/09, 팀 DB 미적용)

운영자가 이음 장애 문의에 답변하면 문의한 직원에게 알림을 저장하기 위한 변경이다.
읽음 표시는 이 알림의 READ_AT을 쓰므로 칸을 따로 늘리지 않는다.

- NOTIFICATION.SUPPORT_INQUIRY_ID 추가.
- FK_69_08로 같은 업체의 장애 문의를 참조한다.
- IX_69_07로 업체·장애 문의별 조회를 지원한다.
- CK_69_01은 알림 종류에 맞는 대상 ID 하나만 허용한다.
- NT_SUPPORT_REPLY 추가.
- 신규 공통 코드 기준은 233개다.

기존 DB는 별도 실행 승인 후 11-09_01 패치 → 11-09_02 점검과 정의 확인 →
같은 연결에서 직접 COMMIT한다.
DDL은 자동 확정되므로 오류 발생 시 상태 확인 없이 재실행하지 않는다.

신규 빈 DB는 수정된 전체 DDL·공통 코드 적재 파일을 사용한다.
11-09_01 패치는 실행하지 않고 11-09_02 점검만 추가한다.

DB 변경을 develop과 팀 DB에 반영한 뒤 알림 코드 PR을 병합한다.
패치보다 알림 코드가 먼저 들어가면 알림 저장이 답변과 같은 트랜잭션이라 운영자 답변이 실패한다.

## 익명 게시판·자료실 (11/10, 팀 DB 미적용)

요구분석서 3장 '추가 구현 범위'의 익명 게시판·자료실을 게시판 표에 담기 위한 변경이다.
두 게시판은 업체 전체 게시판이라 부서가 없다.
글의 작성자 칸(POST.AUTHOR_EMPLOYEE_ID)은 NOT NULL 그대로라, 익명 게시판의 작성자는 보여 줄 때 감춘다.

- BOARD_ANON(익명 게시판, 정렬 3)·BOARD_LIBRARY(자료실, 정렬 4) 추가.
- CK_52_01은 공지·익명·자료실은 부서 없음, 부서 게시판만 부서 있음을 허용한다.
- 칸·FK·인덱스는 늘지 않는다.
- 신규 공통 코드 기준은 235개다.

기존 DB는 별도 실행 승인 후 11-10_01 패치 → 11-10_02 점검과 정의 확인 →
같은 연결에서 직접 COMMIT한다.
DDL은 자동 확정되므로 오류 발생 시 상태 확인 없이 재실행하지 않는다.
CHECK를 바꾼 뒤 COMMIT 전에 연결이 끊겨 코드 2개만 빠졌다면 패치를 다시 실행하지 않고
(사전 점검에서 멈춘다) 패치 아래쪽 INSERT 두 문장만 실행한 뒤 11-10_02 점검을 다시 본다.

신규 빈 DB는 수정된 전체 DDL·공통 코드 적재 파일을 사용한다.
11-10_01 패치는 실행하지 않고 11-10_02 점검만 추가한다.

DB 변경을 develop과 팀 DB에 반영한 뒤 익명 게시판·자료실 코드 PR을 병합한다.
패치보다 코드가 먼저 들어가면 두 게시판의 줄을 만들 수 없어(FK_52_02·CK_52_01) 첫 글쓰기가 실패한다.

## 아직 11/02 변경이 적용되지 않은 기존 DB

DB 백업을 확보하고 앱의 쓰기 작업을 멈춘 뒤 다음 순서로 실행한다. 전체 DDL이나 init 파일로 갱신하지 않는다.

1. `patches/11-02_03_CHECK.sql`로 사전 점검
2. `patches/11-02_01_SCHEMA_PATCH.sql`
3. `patches/11-02_02_DATA_PATCH.sql` — 시연 보완·점검도 자동 실행
4. 오류 없이 완료됐는지, 점검 1~16번과 지정 코드 12개가 기대값인지 확인
5. 같은 연결에서 직접 `COMMIT;`. 데이터 변경을 취소하려면 COMMIT 전에 `ROLLBACK;`
6. 그다음 아래 순서로 근태정정 변경도 적용한다.
  - 11-02_04_ATTENDANCE_CORRECTION_PATCH.sql 실행
  - 11-02_05_ATTENDANCE_CORRECTION_CHECK.sql 실행 → 모두 PASS인지 확인
  - 오류가 없으면 같은 연결에서 COMMIT; 실행

17번은 옛 객체 정리 항목으로, 사용 여부를 확인한 뒤 별도로 결정한다. 자동 삭제하지 않는다. 16번은 기존 프로젝트 시퀀스 65개를 검사한다. 새 근태정정 시퀀스를 포함한 66개는 05 점검 파일에서 확인한다.

## 근태정정 추가 변경

팀장 최종 요청 기준: 알림 변경, AR_NO_OUT 추가, 기존 근태 판정 코드 USE_YN 변경은 제외한다. ATTENDANCE와 ATTENDANCE_EVENT의 컬럼·제약은 유지한다.

- ATTENDANCE_CORRECTION: 16개 컬럼, PK·복합 UNIQUE, CHECK 8개, FK 5개, 조회/중복 방지 인덱스 3개.
- CK_82_08은 원래 출근 시각보다 이른 퇴근 요청을 차단한다.
- CORRECTION_STATUS 및 ACR_REQUESTED/APPROVED/REJECTED/CANCELED의 총 5개 코드를 추가한다.
- 기존 시퀀스 65개 최댓값도 99999로 맞춘다. LAST_NUMBER가 99999 이상이면 중단하며 시작 번호를 재설정하지 않는다.
- reset의 두 프로젝트 목록과 실제 삭제 목록에 테이블·시퀀스를 추가했다. NOTIFICATION 이후, ATTENDANCE 이전에 정정 테이블을 삭제한다.

**근태정정 팀 DB 적용: 2026-11-02 적용·COMMIT 완료. 현재 팀 DB에 04를 다시 실행하지 않는다.**

진행 흐름: 시퀀스 현재 번호와 기존 객체 사전 조회 → 신규/기존 DB SQL·reset 동기화 → 파일 정적 검증 → 팀 DB 전용 04 패치 실행 → 05 사후 점검 → 입력 제약 테스트 → 테스트 행 롤백 → 상태 코드 COMMIT → 최종 점검.

검증 결과:

- 기존 프로젝트 시퀀스 65개 번호가 99999 미만인 것을 확인한 뒤 최댓값을 낮췄고, 새 시퀀스까지 66개 모두 MAXVALUE=99999를 확인했다. 번호 재설정은 하지 않았다.
- 팀 DB에서 사후 점검 7개 항목 모두 PASS, 제약 입력 테스트 12개 모두 PASS. 중복 대기 요청, 원래 출근보다 이른 퇴근(CK_82_08), 요청 시각 순서, 다른 날짜 퇴근, 승인·반려 필수값, 업체가 다른 직원 참조 등의 차단을 확인했다.
- 테스트는 SAVEPOINT로 모두 롤백해 ATTENDANCE_CORRECTION 행은 0건이다. 새 상태 코드 5개만 확정했고, COMMIT 후 미확정 트랜잭션 없음도 확인했다.
- 알림 정정 컬럼은 추가하지 않았고, 기존 근태 판정 코드 USE_YN도 유지했다.
- Oracle이 자동 생성하는 NOT NULL 제약을 제외하고 이름을 지정한 제약 15개를 점검하도록 05 검증문을 보완했다.
- 파일 정적 검증: 테이블 82개·컬럼 793개·FK 308개·시퀀스 66개·코드 229개, 기존 근태/알림 정의 유지 및 reset 목록 일치 확인. 신규 전체 생성/reset은 팀 DB에서 실행하지 않았다.

이미 11/02 기본 패치가 적용된 기존 DB는 **04 근태정정 패치만** 실행 → 05 점검 PASS 확인 → 같은 연결에서 직접 COMMIT한다. 원본 공통 코드 DML이나 전체 DDL을 재실행하지 않는다. 04는 이미 새 객체가 있으면 중단하므로 일부 실패 시 상태 확인 없이 다시 실행하지 않는다.

### 향후 서버 구현 기준

기능은 11/6 필수 통합 이후 구현한다. 아래는 확정된 구현 기준이며 **이번 SQL만으로 서버 기능이 구현되지는 않는다.**

- 전자결재와 분리: 직원 요청 → 부서장 승인·반려. 처리자가 없거나 본인이면 업체 관리자가 처리한다.
- 정정 신청 기간: 근무일 다음 날부터 달력 기준 7일, 마지막 날 23:59:59.999999까지. 11/2 근태는 11/9까지이며 `현재 한국 시각 < WORK_DATE + 8일`로 마감을 판정한다. 오늘·미래 근태 신청은 허용하지 않는다. 승인·반려 처리는 신청 기한 이후에도 가능하다.
- 이 기간은 서버에서 신규 요청 시 검사한다. 요청서 그대로의 테이블에는 기간 검사 트리거를 추가하지 않았다.
- FK는 같은 업체까지만 보장하므로 ATTENDANCE_ID가 실제로 같은 직원·같은 WORK_DATE의 행인지 서버에서 검증한다.
- 승인 시 대기 상태 조건부 변경과 일별 근태 UPDATE/INSERT를 한 트랜잭션으로 처리한다. 원래 값 비교/잠금으로 동시 수정도 확인한다. ATTENDANCE_EVENT 원본은 수정하지 않는다.
- 결근 반려·미요청은 1차에 넣을지와 정정 기간 7일은 아직 정하지 않았으며 구현 전에 팀장이 정할 제안이다.
- 기존 AR_NORMAL/AR_LATE/AR_EARLY 등은 USE_YN=N을 유지한다. 현재 판정 코드가 사용하지 않는다는 팀장 요청에 따른 것이며 향후 공통 코드 조회 필터와 함께 확인한다.

## 실행 시 주의

- 접속 서비스·계정이 맞는지 확인하고 기존 미확정 작업이 없는 별도 연결을 사용한다. 자동 커밋은 끈다.
- SQL Developer에서는 **파일을 열어 F5(스크립트 실행)**를 사용한다. SQLcl/SQL*Plus는 `@"전체경로/파일.sql"`로 실행한다. 파일은 UTF-8이다.
- `@@`로 다른 파일을 호출하므로 **폴더 구조를 유지**한다. 파일 내용을 워크시트에 복사하는 대신 파일 자체를 실행한다.
- **DDL은 즉시 확정되어 ROLLBACK으로 취소되지 않는다.** 또한 DDL은 앞선 DML도 확정할 수 있다. 데이터 패치 후 다른 DDL을 실행하기 전에 COMMIT 또는 ROLLBACK을 결정한다.
- 오류·파일 열기 실패·예상과 다른 점검 결과가 나오면 다음 단계와 COMMIT을 멈춘다. 구조 패치의 일부가 이미 확정됐을 수 있으므로 오류 원인을 먼저 확인한다.
- 부서명·이메일·1:1 방 중복, 잘못된 읽음 참조, 기대와 다른 기존 정의가 있으면 구조 패치가 중단된다. 임의 데이터 삭제로 해결하지 않는다.
- 시연 업체 `ieum0001`·`ieum0002`가 먼저 있어야 한다. 지점 주소와 장비는 시연 예시다. 직원의 기존 지점·기존 지급 자산·유료 이용 기간은 유지한다.
- 시연 기간은 **2026-12-01 00:00:00 한국 시간 만료**다. 이후 시연한다면 `data/IEUM_DEMO_SEED.sql`의 날짜와 기존 이용 기간을 확인해 조정한다.
- VERSION 컬럼만으로 동시 수정이 방지되지는 않는다. 서버 UPDATE에 버전 조건·증가·영향 행 수 확인이 필요하다.
- 읽음 FK가 참조하는 메시지를 물리 삭제할 때는 먼저 `LAST_READ_MESSAGE_ID`를 NULL로 바꾸는 등 삭제 순서를 맞춘다.
- 초기 계정은 개발 시연용 공통 비밀번호를 사용한다. 실제 서비스 계정으로 사용하지 않는다.

임시 생성·검증 코드, 실행 로그, 변경 전 백업, 해시, 중간 보고서, 압축 파일은 배포 대상에서 제외해 정리했다.
