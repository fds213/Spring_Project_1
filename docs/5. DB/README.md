# 5. DB

이음 테이블 정의서와 신규 DB 설치용 SQL을 담았다. Oracle XE 21c의 XEPDB1에 준비한 빈 IEUM 스키마에서 실행한다. 생성 기준은 **테이블 60개, 컬럼 615개, 외래키 231개, 시퀀스 50개, 공통 코드 235개**다. 전체 구조는 [4. ERD](../4.%20ERD/README.md)에서 확인할 수 있다.

[테이블 정의서 PDF](%EC%9D%B4%EC%9D%8C_%ED%85%8C%EC%9D%B4%EB%B8%94%EC%A0%95%EC%9D%98%EC%84%9C.pdf)

## 로컬 Oracle에 설치하기

1. SQL Developer에서 **호스트 localhost, 포트 1521, 서비스 이름 XEPDB1**로 관리자 계정에 연결한다. 포트나 서비스 이름을 설치할 때 바꿨다면 그 값을 사용한다.
2. 로컬 XEPDB1에 IEUM 계정이 없으면 아래 SQL로 만든다. 예시 비밀번호는 실행 전에 본인 비밀번호로 바꾼다. 이미 계정이 있으면 이 단계는 건너뛴다.

```sql
CREATE USER IEUM IDENTIFIED BY "Replace_With_Your_Local_Password"
    DEFAULT TABLESPACE USERS QUOTA 200M ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO IEUM;
```

3. 같은 localhost 주소에 IEUM 계정으로 새 연결을 만들고 접속을 확인한다.

```sql
SELECT USER AS LOGIN_USER, SYS_CONTEXT('USERENV', 'CON_NAME') AS PDB
FROM DUAL;
```

조회 결과가 IEUM과 XEPDB1이면 아래 순서로 실행한다. 이미 프로젝트 테이블이 있다면 먼저 보관할 데이터가 있는지 확인한다. 폐기 가능한 로컬 개발 DB를 비울 때는 [개발용 reset 안내](../../db/README.md#신규-빈-개발-db-실행-순서)를 따른다.

| 순서 | 파일 | 작업 |
| --- | --- | --- |
| 1 | [IEUM_DDL.sql](ddl/IEUM_DDL.sql) | 테이블, 시퀀스, 제약, 인덱스 생성 |
| 2 | [IEUM_COMMON_CODE_DML.sql](data/IEUM_COMMON_CODE_DML.sql) | 공통 코드 입력 |
| 3 | [init_auth_data.sql](data/init_auth_data.sql) | 시연용 업체, 직원, 로그인 계정 입력 |
| 3에서 자동 실행 | [IEUM_DEMO_SEED.sql](data/IEUM_DEMO_SEED.sql) | 요금제, 이용 기간, 지점, 자산 등 시연 데이터 보완 |

시연 데이터 SQL 두 파일은 같은 data 폴더에 둔다. SQL Developer에서는 UTF-8 파일을 열고 F5로 실행한다. 각 파일의 조회 결과와 [구조 점검 SQL](../../db/patches/11-11_03_SCHEMA_CLEANUP_CHECK.sql)의 PASS를 확인한 뒤 같은 연결에서 COMMIT한다. 시연 계정은 테스트용이다.

### 애플리케이션 연결

애플리케이션도 로컬 DB를 쓰려면 [db.properties.example](../../src/main/resources/config/db.properties.example)을 같은 폴더의 db.properties로 복사하고 접속 값을 아래처럼 지정한다. db.password에는 로컬 IEUM 계정 비밀번호를 입력하고 Tomcat을 재시작한다.

```properties
db.driver=net.sf.log4jdbc.sql.jdbcapi.DriverSpy
db.url=jdbc:log4jdbc:oracle:thin:@localhost:1521/XEPDB1
db.username=ieum
db.password=
```

편집 기준은 저장소의 [db 폴더](../../db/README.md)다. 이 폴더의 네 SQL은 제출 시점의 신규 설치용 사본이다. 기존 팀 DB 정리와 개발 DB 초기화는 저장소의 실행 절차를 따른다.
