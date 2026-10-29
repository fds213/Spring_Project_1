# AGENTS.md — 사내 업무·IT 지원 통합 시스템

팀원 7명이 쓰는 코딩 에이전트(Claude Code 등)의 공통 지침이다. 사람도 같은 규칙을 따른다.
이 파일을 바꿀 때는 PR로 팀 합의를 거친다. `TODO(회의)` 표시는 팀 회의에서 확정할 항목이다.

## 프로젝트

- 사내 그룹웨어(전자결재·근태·일정·게시판·채팅)와 IT Service Hub(티켓·자산·지점·회사 지도)를 하나로 합친 Spring MVC 웹 서비스다.
- 기준 문서: `docs/사내_그룹웨어_IT_Service_Hub_통합_프로젝트_계획서.md`. 기능 범위·업무 흐름·권한·상태 전이 규칙은 이 문서를 따른다. 해당 기능을 만들기 전에 관련 장을 먼저 읽는다.
- 문서에 없는 기능·테이블·컬럼·화면을 임의로 만들지 않는다. 요구가 불분명하거나 문서와 다르면 추측하지 말고 사람에게 묻는다.
- 일정: 필수 기능 통합 11/6, 기능 추가 마감 11/9, 발표 11/13. 필수 기능이 끝나기 전에는 계획서 12장의 '추가 기능'을 작업하지 않는다.

## 기술 스택

- Java 11, Spring Framework 5.3.39(XML 설정), Spring Security 5.8.16, JSP·JSTL, jQuery·Ajax, MyBatis, Oracle, Maven(WAR), Tomcat 9, JUnit 5·Spring Test, Lombok.
- 쓰지 않는다: Spring Boot, JPA·Hibernate, React·Vue, Gradle, Kotlin.
- `pom.xml`의 의존성 추가·버전 변경은 하지 않는다. 필요하면 이유를 정리해 사람에게 요청한다.
- Oracle은 XE 21c를 쓰고, PK는 SEQUENCE로 만든다(테이블마다 시퀀스 1개).
- DB는 공용 Oracle 서버 한 대에 모두 접속한다. 테이블 변경은 DDL 스크립트로 Git에 올리고 DB 리드가 공용 DB에 반영한다. TODO(회의): 공용 DB를 둘 곳.
- WebSocket은 `@ServerEndpoint` 방식이다. `SpringConfigurator`를 상속한 Configurator로 Service를 주입받고, 핸드셰이크 때 서버의 인증 정보에서 로그인 사용자를 넘긴다. 채팅 담당이 사전 구현에서 확인해 공유한 공통 예제를 따르고, 이 방식이 안 되면 Spring `WebSocketHandler`로 바꾼다. `spring-websocket` 의존성은 담당 1이 추가한다.

## 빌드·확인

- 컴파일 `mvn -q compile`, 테스트 `mvn test`, 패키징 `mvn package`(결과물 `target/*.war`). Tomcat 9에 배포해 실행한다.
- 작업을 끝냈다고 보고하기 전에 최소한 컴파일과 관련 테스트를 통과시킨다. 실행하지 못한 확인은 못 했다고 말한다.

## 개발 환경(Windows·맥)

팀원 대부분은 Windows + STS로, 일부는 맥 + VS Code로 개발한다. 중앙 서버는 리눅스일 수 있다. 어느 환경에서도 똑같이 돌도록 아래를 지킨다.

- 파일 경로는 `\`나 `/`를 문자열로 이어 붙이지 않는다. `new File(폴더, 이름)` 또는 `Paths.get(폴더, 이름)`을 쓴다. 수업 예제의 `path + "\\" + 이름` 방식은 따라 하지 않는다.
- 파일·폴더·URL·JSP 이름은 대소문자까지 똑같이 쓴다. Windows와 맥은 대소문자를 구분하지 않지만 리눅스 서버는 구분한다. 대소문자만 바꾸는 이름 변경은 `git mv`로 한다.
- IDE 설정 파일(`.classpath`, `.project`, `.settings/`)은 프로젝트 설정을 바꿀 때만 커밋한다. 자기 IDE가 저절로 바꾼 내용은 커밋에서 뺀다.
- DB 접속 정보처럼 사람마다 다른 값은 Git에 올리지 않는 설정 파일에 둔다('권한·보안'의 `*.example` 규칙). 자기 PC의 값으로 공통 설정 파일을 고쳐 커밋하지 않는다.
- 모든 파일(SQL·JSP·properties 포함)은 UTF-8로 저장한다. Windows의 SQL Developer나 메모장으로 저장할 때는 인코딩을 확인한다.
- 서버 시간대는 한국(`Asia/Seoul`)으로 맞춘다. 출퇴근·결재 같은 시각은 서버에서 정하고, 사용자 PC가 보낸 시각을 믿지 않는다.

## 패키지·이름 규칙

- 루트 패키지: `com.ieum.gw`. 수업 템플릿의 `com.test.java`는 이 이름으로 바꾸고, 모듈 패키지는 그 아래에 둔다(예: `com.ieum.gw.approval.controller`).
- 모듈 패키지: `employee`, `approval`, `attendance`, `ticket`, `asset`, `location`, `schedule`, `board`, `chat`, `notification`, `common`.
- 모듈 안 구조: `controller`, `service`, `dao`, `mapper`, `dto`. JSP는 `src/main/webapp/WEB-INF/views/<모듈>/`. 공통 설정(담당 1)이 정한 위치와 다르면 공통 설정을 따른다.
- Mapper XML은 모듈별 하위 폴더 없이 `src/main/resources/mapper/`에 바로 둔다. 파일 이름은 짝이 되는 Mapper 인터페이스 이름과 같게 하고(예: `ApprovalMapper.xml`), namespace는 그 인터페이스의 전체 이름(예: `com.ieum.gw.approval.mapper.ApprovalMapper`)으로 쓴다. 한 모듈에 Mapper가 더 필요하면 인터페이스와 XML을 한 쌍씩 더 만든다(예: `ApprovalLineMapper`).
- 클래스 이름: `ApprovalController`, `ApprovalService`, `ApprovalDAO`, `ApprovalMapper`, `ApprovalDTO`. URL: `/<모듈>/<기능>`(예: `/approval/list`), 관리 화면은 `/<모듈>/admin/<기능>`(예: `/employee/admin/list`).
- DB 테이블·컬럼은 스네이크 케이스(`emp_id`), Java 필드는 카멜 케이스(`empId`). Oracle 예약어(`DATE`, `USER`, `LEVEL`, `COMMENT`, `SIZE`, `ORDER`, `GROUP` 등)를 이름으로 쓰지 않는다.

## 수정 범위

- 자기 담당 모듈의 패키지·JSP 폴더와 Mapper XML 파일 안에서만 수정한다.
- 다른 모듈의 기능이 필요하면 그 모듈 Service의 공개 메서드를 호출한다. Controller끼리 호출하지 않는다. 필요한 메서드가 없으면 직접 만들지 말고, 담당자에게 요청할 메서드 이름·입력·반환값을 정리해 사람에게 알린다.
- 아래 공통 파일은 담당자만 수정한다. 에이전트는 수정하지 않고 필요한 변경 내용만 제안한다.
  - `pom.xml`, `web.xml`, `root-context.xml`, `servlet-context.xml`, Security 설정 XML, 공통 레이아웃 JSP, `common` 패키지 → 담당 1
  - DDL·초기 데이터 스크립트 → DB 담당
  - `AGENTS.md`, `docs/` 계획서 → 팀 합의

| 담당 | 모듈 | 이름 |
|---|---|---|
| 1 | `employee`, `common`(인증·권한·파일 처리) | TODO(회의) |
| 2 | `approval` | TODO(회의) |
| 3 | `attendance`(근태·연차·휴일) | TODO(회의) |
| 4 | `ticket` | TODO(회의) |
| 5 | `asset`, `location` | TODO(회의) |
| 6 | `schedule`, `board`, `notification`, 메인 | TODO(회의) |
| 7 | `chat` | TODO(회의) |

| 공통 역할 | 이름 |
|---|---|
| 팀장 | 최준원 |
| DB 리드·DB 스크립트 취합 | 정상욱 |
| 백엔드 리드 | 서장호 |
| 프론트 리드·공통 레이아웃 | 홍동국 |
| 보조 통합, 문서 취합, 발표자 | TODO(회의) |

## DB·MyBatis

- MyBatis는 Mapper 인터페이스 방식으로 쓰고, 호출은 Service → DAO → Mapper 순서로 한다(수업 ToyProject와 같은 구조).
  - Mapper(`mapper` 패키지)는 `@Mapper`를 붙인 인터페이스다. SQL 하나에 메서드 하나를 두고, 메서드 이름은 Mapper XML의 `id`와 같게 한다.
  - DAO(`dao` 패키지)는 `@Repository` 클래스다. Mapper를 주입받아 감싸고, Service는 DAO만 호출한다. Service나 Controller가 Mapper를 직접 부르지 않는다.
  - `SqlSessionTemplate`에 `"namespace.id"` 문자열을 넘기는 방식은 쓰지 않는다.
  - 공통 설정(담당 1)은 `root-context.xml`에서 `@Mapper`가 붙은 인터페이스만 Mapper로 등록한다(`<mybatis-spring:scan base-package="com.ieum.gw" annotation="org.apache.ibatis.annotations.Mapper" />`). 그래서 `@Mapper`를 빼먹으면 Mapper가 등록되지 않는다.
- 테이블·컬럼 추가·변경은 DB 담당을 거친다. 에이전트는 `CREATE`·`ALTER` 스크립트를 직접 고치지 않고, 필요한 SQL을 PR 설명이나 사람에게 제안한다.
- MyBatis 파라미터는 `#{}`만 쓴다. `${}`는 정렬 컬럼처럼 서버에서 허용 목록으로 검증한 값에만 쓴다.
- 검색·페이징은 동적 SQL(`<where>`, `<if>`)로 만든다.
- 이력이 있는 데이터(사원, 자산, 결재 문서, 티켓)는 `DELETE`하지 않고 상태값(퇴사, 사용 중지, 폐기 등)으로 처리한다.

## 상태 코드

DB에는 영문 코드를 저장하고 화면에는 한글로 표시한다. 코드는 `common`의 enum 한 곳에서만 정의하고, 문자열을 코드 곳곳에 직접 쓰지 않는다. 아래 코드값을 쓴다.

- 결재 문서: `DRAFT`(임시저장) → `IN_PROGRESS`(진행) → `APPROVED`(완료) 또는 `REJECTED`(반려)
- 결재선: `WAITING`(대기), `APPROVED`(승인), `REJECTED`(반려)
- 티켓: `RECEIVED`(접수) → `ASSIGNED`(배정) → `IN_PROGRESS`(처리 중) → `RESOLVED`(처리 완료) → `CLOSED`(종료). 그 밖에 `CANCELED`(취소), `REJECTED`(반려), 재요청 `RESOLVED` → `IN_PROGRESS`.
- 계획서 5장에 없는 상태 변경은 Service에서 예외로 막는다.

## 권한·보안

- 로그인과 URL 접근은 Spring Security로 처리한다. 데이터별 권한(결재 문서 관계자, 티켓 요청자·담당자, 채팅방 참여자, 같은 부서)은 Service에서 검사한다. URL이나 파라미터의 ID만 바꿔서 남의 자료를 볼 수 없어야 한다.
- 로그인 사용자 정보는 서버의 인증 정보에서만 가져온다. 요청 파라미터나 WebSocket 메시지에 담긴 사용자 ID·작성자 값을 믿지 않는다.
- 관리 기능은 별도 사이트가 아니라 같은 사이트 안의 관리자 메뉴로 둔다. 관리 화면 URL(`/<모듈>/admin/**`)은 Spring Security에서 관리 역할만 통과시킨다.
- 공개 회원가입은 없다. 첫 관리자 계정은 초기 데이터로 만들고, 사원 계정은 관리자가 만든다. 초기 비밀번호로 첫 로그인하면 비밀번호 변경 화면을 먼저 띄운다.
- 비밀번호는 BCrypt로 해시해 저장한다. 평문으로 저장하거나 로그에 남기지 않는다.
- JSP에서 사용자 입력을 출력할 때는 `<c:out>` 또는 `fn:escapeXml`로 이스케이프한다.
- 파일 업로드·다운로드마다 소속 자료의 접근 권한을 검사하고, 확장자와 실제 파일 형식을 확인하며, 저장 파일명은 UUID로 만든다.
- DB 비밀번호·API 키 같은 비밀값은 git에 올리지 않는다. 비밀값 파일은 `.gitignore`에 등록하고 값을 비운 예시 파일(`*.example`)만 올린다. 카카오 지도 JavaScript 키는 개발·발표 도메인으로 제한한다.

## 트랜잭션·동시 처리

- `@Transactional`은 Service에 붙인다. 결재 완료, 연차 사용, 휴가 일정 생성, 티켓 생성, DB 알림 저장처럼 함께 성공해야 하는 작업은 한 트랜잭션으로 묶는다.
- WebSocket 전송이나 외부 API 호출은 트랜잭션 안에서 하지 않고 커밋 뒤에 한다. 전송이 실패해도 업무 처리는 취소되지 않게 한다.
- 결재 처리, 연차 차감, 자산 지급은 `SELECT ... FOR UPDATE` 또는 조건부 UPDATE(`WHERE status = ? AND current_step = ?`) 후 영향 받은 행 수를 확인한다. 티켓은 `version` 컬럼으로 동시 수정을 감지한다.
- 원본 결재 문서 ID에 고유 제약을 두어 연차 사용·휴가 일정·티켓이 두 번 생기지 않게 한다.

## 테스트

- 결재 단계 처리, 티켓 상태 전이, 중복 승인·동시 반영 규칙은 JUnit 5·Spring Test 테스트를 함께 작성한다.
- 테스트는 `@DisplayName`에 한국어로 시나리오를 적는다.

## Git

- 브랜치: `main`(발표·제출용, 항상 시연 가능한 상태), `develop`(중간 통합), `feature/<모듈>-<기능>`(각자 작업, 예: `feature/approval-line`). `develop`·`main`에 직접 push하거나 force push하지 않는다(GitHub 브랜치 보호).
- 작업 시작 전에 최신 `develop`을 내 브랜치에 반영한다.
- 작업 단위가 끝나면 PR → 리뷰 1명 승인 → `develop`에 병합한다. PR은 하루 단위로 작게 만들어 매일 병합하고, 마지막에 몰아서 합치지 않는다.
- PR 설명에 확인 방법, DB 변경, 다른 모듈에 주는 영향, AI 사용 범위를 적는다.
- 커밋 메시지: `[모듈] 작업 내용`(예: `[approval] 순차 승인 처리`).
- `develop`이 깨지면 깨뜨린 사람이 먼저 고치고, 그동안 다른 병합은 멈춘다.
- `develop` → `main`은 점검 시점(기본 화면 완성, 핵심 흐름 완성, 필수 통합, 기능 마감, 제출 전)에만 병합하고, 넣을 때마다 태그(`v0.1`, `v0.2` …)를 붙인다. 아래를 모두 통과해야 넣는다.
  1. 저장소를 새로 받아 `mvn package` 성공
  2. `mvn test` 통과
  3. DDL·초기 데이터로 새로 만든 DB에서 실행됨
  4. 핵심 시나리오 직접 확인: 휴가 결재, 장비 지급, 장애 처리, 두 계정 채팅
  5. 막고 있는 필수 버그 없음
- 리뷰는 반나절 안에 한다. 에이전트로 1차 리뷰를 해도 되지만 승인은 사람이 한다.
  - 일반 기능 PR: 리뷰 짝 1명(연동이 많은 기능끼리 짝, 예: 결재↔근태, 티켓↔자산)
  - Mapper·DDL 변경: DB 리드
  - 보안·트랜잭션·기능 간 연동 변경: 백엔드 리드
  - 공통 레이아웃·CSS·JS: 프론트 리드
  - `develop` → `main` 병합, 공통 설정 파일: 팀장(또는 통합 담당)
- 에이전트는 사람이 시키지 않으면 commit·push·PR 생성을 하지 않는다.

## 에이전트 작업 방식

- 새 기능은 팀이 공유한 공통 예제(Controller → Service → DAO → Mapper → JSP 한 세트)와 같은 패턴으로 만든다.
- 여러 모듈이나 공통 파일에 걸친 큰 변경은 하지 않는다. 필요하면 먼저 계획을 사람에게 보여 준다.
- 작업이 끝나면 무엇을 왜 바꿨는지 짧게 요약한다. 담당자가 그 코드를 직접 설명할 수 있어야 한다(팀원별 AI 사용 기록·발표 질의 대비).
- 답변·주석·문서는 한국어로 쓴다.
- 개인 지침 파일(`CLAUDE.local.md` 등)은 각자 판단해 만든다.
