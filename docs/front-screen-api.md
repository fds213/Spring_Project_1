# 프론트 화면과 서버 API 연동 (백엔드 공유용)

dev2에 넣은 화면과, 화면 JS가 서버에 요청하는 주소를 정리했다. 범위는 요구분석서 3장 '이번 구현 범위'(11/13 마감)를 따른다.
화면 JS는 시안(feature/front-prototype, 이제 화면 참고용)에서 가져와 dev2에 맞게 고쳤다.

> **4장의 주소와 값 모양은 프론트 제안이다(확정 아님).** 2026-10-30 develop에는 로그인(`POST /login`)과 화면 주소 말고는 백엔드 API가 아직 없다.
> 모듈 담당이 Controller를 만들면 **프론트가 그 주소·요청·응답에 맞춰 화면 JS를 고친다.** 제안과 다르게 만들어도 되고, 만든 주소를 프론트에 알려 주면 된다.
> 계정 만들기·비밀번호 찾기 화면은 백엔드 API가 생긴 뒤에 JS를 붙인다(지금은 화면 모양만).

## 1. 어떻게 넣었나

| 항목 | 위치 | 설명 |
|---|---|---|
| 화면 JSP | `WEB-INF/views/<모듈>/*.jsp` | `<ui:layout>` 안의 본문. 화면 자리는 `<section class="view on" id="view-<메뉴 키>">` |
| 게시판 JSP | `WEB-INF/views/board/{notice,dept}.jsp` | 목록·글 보기·글쓰기를 한 JSP에 두고, `board.js`가 주소(`/view/3`, `/write`, `/edit/3`)를 보고 하나를 보인다 |
| 소개 사이트 | `WEB-INF/views/site/{intro,trial,inquiry}.jsp` | `<ui:site>` 틀. 주소 `/`, `/trial`, `/inquiry` (화면 모양만, 체험 신청·문의 접수 JS는 API가 정해지면 붙인다) |
| 계정 만들기·비밀번호 찾기 | `WEB-INF/views/common/{join,join-password}.jsp` | 주소 `/join`, `/join/password` (화면 모양만. JS는 백엔드 API가 생긴 뒤에 그 주소에 맞춰 붙인다) |
| 화면 JS | `resources/js/util.js` + `resources/js/<모듈>/*.js` | 페이지마다 `util.js`(같이 쓰는 도구)와 그 모듈 JS만 불러오고, 페이지가 열리면 화면을 여는 함수를 부른다 |
| Controller·Service·DAO·Mapper | 없음 (이 브랜치는 프론트 파일만) | 각 기능은 팀 규칙대로 담당이 Controller → Service → DAO → Mapper → SQL 한 세트로 만든다. 프론트는 그 결과(주소·응답)에 맞춰 화면 JS를 고친다 |

화면 주소 → JSP 파일 (참고용. 주소는 사이드 메뉴와 같고, 담당이 정하면 바꿔도 된다):

| 화면 주소 (GET) | 돌려줄 JSP (view 이름) |
|---|---|
| `/` | `site/intro` |
| `/trial` | `site/trial` |
| `/inquiry` | `site/inquiry` |
| `/join` | `common/join` |
| `/join/password` | `common/join-password` |
| `/approval/write` | `approval/write` |
| `/approval/list` | `approval/list` |
| `/ticket/new` | `ticket/new` |
| `/ticket/mine` | `ticket/mine` |
| `/asset/mine` | `asset/mine` |
| `/attendance/my` | `attendance/my` |
| `/schedule/calendar` | `schedule/calendar` |
| `/employee/admin/list` | `employee/admin/list` |
| `/employee/join` | `employee/join` |
| `/employee/admin/dept` | `employee/admin/dept` |
| `/employee/admin/rank` | `employee/admin/rank` |
| `/subscription/billing` | `subscription/billing` |
| `/board/notice`, `/board/notice/view/{id}`, `/board/notice/write`, `/board/notice/edit/{id}` | `board/notice` (네 주소 모두 같은 JSP) |
| `/board/dept`, `/board/dept/view/{id}`, `/board/dept/write`, `/board/dept/edit/{id}` | `board/dept` (네 주소 모두 같은 JSP) |
| `/ops/company` | `ops/company` |
| `/ops/application-status` | `ops/application` |
| `/ops/inquiry-guest` | `ops/inquiry-guest` |

이번 범위에서 빠져 화면을 두지 않은 것: 운영자 증설 승인·인원 증설(CAP-01·02), 회원 접수·이음 장애 문의(OPS-04), 운영 데이터, 익명 게시판(요구사항에 없음), 근무 > 승인(휴가는 전자결재 휴가 신청서로), 가입 승인의 자리 대기, 직원 이메일 인증 코드.

## 2. 화면 JS가 서버를 부르는 방식

화면 JS는 `common.js`의 `api`로 서버를 부른다: `api.get(path, query)`, `api.post(path, body)` 두 가지뿐이다.

| 항목 | 규칙 |
|---|---|
| 주소 | 컨텍스트 경로 뒤: 일반 `/<모듈>/api/...`, 관리 역할 `/<모듈>/admin/api/...`, 이음 운영자 `/ops/api/...`, 로그인 없이(계정 만들기·비밀번호 찾기) `/join/api/...` |
| 요청 방식 | 데이터를 바꾸는 요청은 모두 POST (AGENTS.md). 고치기는 같은 주소로 POST, 지우기는 주소 끝에 `/delete`를 붙여 POST. GET은 아무것도 바꾸지 않는다 |
| 보내는 값 | GET은 쿼리 문자열, POST는 JSON 본문 (`Content-Type: application/json`) |
| 받는 값 (성공) | 팀 공통 모양 `{ "success": true, "message": "...", "data": ... }` → `api`가 `data`만 화면에 넘긴다. 감싸지 않은 데이터 JSON도 그대로 된다 |
| 실패 | HTTP 상태 코드 + `{ "message": "안내 문구" }` (또는 `{ "success": false, "message": ... }`). 화면은 `message`를 그대로 보여 준다. 200 + `success: false`도 실패로 처리한다 |
| 상태 코드 | 400 잘못된 값, 401 로그인 필요(로그인 화면으로 이동), 403 권한 없음·읽기 전용, 404 없음(볼 수 없는 것 포함), 409 충돌, 410 시간 지남, 429 너무 잦음 |
| CSRF | POST에 `common.js`가 토큰 헤더를 붙인다 (`layout.tag`의 `_csrf` meta) |
| 날짜 | ISO 문자열 (`2026-10-30T09:20:00`) |

## 3. 백엔드에서 같이 맞출 것

| 항목 | 지금 | 필요한 것 |
|---|---|---|
| 사원 관리 데이터 | 화면 `/employee/admin/list`는 HR·ADMIN, API `/employee/admin/api/employees/**`는 `/*/admin/**` 규칙이라 ADMIN만 | 인사 담당(HR)도 사원 목록 API를 쓰도록 규칙 추가 |
| 본인 비밀번호 변경 | 헤더 버튼과 창은 있다 (`common.js` 7) | `POST /employee/api/password` `{ current, password }` |
| 메뉴 배지 | `common.js`가 15초마다 `GET /badges` | 업체 직원 `{ notification, approval, chat, join }`, 운영자 `{ application, inquiryGuest }` |
| 부서·직급 삭제 기준 | 부서·직급 관리 화면이 먼저 확인한다 | 사용 중 계정이나 가입 요청이 있으면 409. 비활성 계정만 남았으면 삭제를 허용한다(계정은 지우지 않고 비활성화만 하므로). 그 비활성 직원들의 소속 표시는 백엔드가 정한다 |
| 티켓 상태 코드 | 내 티켓 화면은 AGENTS.md 의 티켓 상태로 보여 준다 | `RECEIVED` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`(요청자 확인), `CANCELED`, `REJECTED`. 상태는 IT 담당자가 티켓 처리에서 바꾼다 |
| 결제 | 토스 결제창 연동 전이라 이용 결제 2단계에 "결제 성공/실패로 진행" 임시 버튼이 있고 `success`를 보낸다 | 토스 테스트 결제를 붙이면 `success`는 빼고, 결제 결과는 서버가 토스 승인으로 정한다(AGENTS.md: 서버가 승인 결과를 확인한 뒤에만 반영) |
| 휴가 | 근태·연차 화면은 기록과 연차 현황·내역만 본다 | 휴가 신청·회수는 전자결재(`/approval/api/...` 휴가 양식). 최종 승인 때 연차 차감, `GET /attendance/api/leaves`의 내역에 나온다 |
| 소개 사이트 체험 신청·도입 문의 | 화면 모양만 있다 | 체험 신청과 `POST /inquiry` API를 정하면 화면 JS를 붙인다 |

## 4. API 목록 — 프론트 제안 (모두 79개)

### 이용 결제 (subscription) + 운영자 신청 현황

요청·응답 모양: `resources/js/` 아래 `subscription/billing.js`, `ops/application.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/subscription.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/subscription/api/billing` | 필요 |
| GET | `/subscription/api/quote` | 필요 |
| POST | `/subscription/api/payments` | 필요 |
| GET | `/ops/api/applications` | 필요 |
| POST | `/ops/api/applications/read` | 필요 |

### 사원·가입·부서·직급·비밀번호 (employee)

요청·응답 모양: `resources/js/` 아래 `employee/employee.js`, `employee/org-admin.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/employee.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/employee/admin/api/employees` | 필요 |
| POST | `/employee/admin/api/employees/{empNo}/status` | 필요 |
| POST | `/employee/admin/api/employees/{empNo}/head` | 필요 |
| POST | `/employee/admin/api/employees/{empNo}/hr` | 필요 |
| GET | `/employee/api/join` | 필요 |
| GET | `/employee/api/join/count` | 필요 |
| POST | `/employee/api/join/openings` | 필요 |
| POST | `/employee/api/join/openings/{scope}/delete` | 필요 |
| POST | `/employee/api/join/openings/delete` | 필요 |
| POST | `/employee/api/join/requests/{reqId}/approve` | 필요 |
| POST | `/employee/api/join/requests/{reqId}/reject` | 필요 |
| GET | `/join/api/companies/{code}` | 없이 |
| POST | `/join/api/companies/{code}/requests` | 없이 |
| GET | `/join/api/companies/{code}/requests/{reqId}` | 없이 |
| POST | `/join/api/companies/{code}/requests/{reqId}/delete` | 없이 |
| POST | `/join/api/password/code` | 없이 |
| POST | `/join/api/password/verify` | 없이 |
| POST | `/join/api/password/reset` | 없이 |
| POST | `/employee/api/password` | 필요 |
| GET | `/employee/admin/api/depts` | 필요 |
| POST | `/employee/admin/api/depts` | 필요 |
| POST | `/employee/admin/api/depts/{name}` | 필요 |
| POST | `/employee/admin/api/depts/{name}/delete` | 필요 |
| POST | `/employee/admin/api/depts/{name}/move` | 필요 |
| GET | `/employee/admin/api/ranks` | 필요 |
| POST | `/employee/admin/api/ranks` | 필요 |
| POST | `/employee/admin/api/ranks/{name}` | 필요 |
| POST | `/employee/admin/api/ranks/{name}/delete` | 필요 |
| POST | `/employee/admin/api/ranks/{name}/move` | 필요 |
| POST | `/employee/admin/api/depts/{name}/type` | 필요 |

### 근태·연차 (attendance)

요청·응답 모양: `resources/js/` 아래 `attendance/attendance.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/attendance.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/attendance/api/records` | 필요 |
| POST | `/attendance/api/device-link` | 필요 |
| POST | `/attendance/api/device-link/delete` | 필요 |
| GET | `/attendance/api/leaves` | 필요 |

### 일정 (schedule)

요청·응답 모양: `resources/js/` 아래 `schedule/calendar.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/schedule.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/schedule/api/schedules` | 필요 |
| POST | `/schedule/api/schedules` | 필요 |
| POST | `/schedule/api/schedules/{id}/delete` | 필요 |

### 전자결재 (approval)

요청·응답 모양: `resources/js/` 아래 `approval/approval.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/approval.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/approval/api/form-options` | 필요 |
| GET | `/approval/api/box` | 필요 |
| POST | `/approval/api/documents` | 필요 |
| POST | `/approval/api/documents/{id}/approve` | 필요 |
| POST | `/approval/api/documents/{id}/reject` | 필요 |
| POST | `/approval/api/documents/{id}/withdraw` | 필요 |
| POST | `/approval/api/drafts` | 필요 |
| POST | `/approval/api/drafts/{id}` | 필요 |
| POST | `/approval/api/drafts/{id}/delete` | 필요 |

### IT 티켓·내 장비 (ticket, asset)

요청·응답 모양: `resources/js/` 아래 `ticket/it-service.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/ticket.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/ticket/api/form-options` | 필요 |
| GET | `/ticket/api/tickets` | 필요 |
| POST | `/ticket/api/tickets` | 필요 |
| POST | `/ticket/api/tickets/{id}/comments` | 필요 |
| GET | `/asset/api/assets` | 필요 |

### 공지·부서 게시판 (board)

요청·응답 모양: `resources/js/` 아래 `board/board.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/board.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/board/api/notices` | 필요 |
| GET | `/board/api/notices/pinned` | 필요 |
| GET | `/board/api/notices/form` | 필요 |
| GET | `/board/api/notices/{id}` | 필요 |
| POST | `/board/api/notices` | 필요 |
| POST | `/board/api/notices/{id}` | 필요 |
| POST | `/board/api/notices/{id}/pin` | 필요 |
| POST | `/board/api/notices/{id}/delete` | 필요 |
| POST | `/board/api/notices/{id}/comments` | 필요 |
| POST | `/board/api/notices/{id}/comments/{cid}/delete` | 필요 |
| GET | `/board/api/dept/posts` | 필요 |
| GET | `/board/api/dept/posts/form` | 필요 |
| GET | `/board/api/dept/posts/{id}` | 필요 |
| POST | `/board/api/dept/posts` | 필요 |
| POST | `/board/api/dept/posts/{id}` | 필요 |
| POST | `/board/api/dept/posts/{id}/delete` | 필요 |
| POST | `/board/api/dept/posts/{id}/comments` | 필요 |
| POST | `/board/api/dept/posts/{id}/comments/{cid}/delete` | 필요 |

### 이음 운영자: 비회원 접수·업체 관리 (ops)

요청·응답 모양: `resources/js/` 아래 `ops/inquiry.js`, `ops/company.js` 맨 위 주석 · 처리 규칙(권한·검사·오류 코드) 참고: 시안 `demo/ops.js` (브랜치 feature/front-prototype, 화면 참고용)

| 메서드 | 주소 | 로그인 |
|---|---|---|
| GET | `/ops/api/guest-inquiries` | 필요 |
| POST | `/ops/api/guest-inquiries/{no}/replies` | 필요 |
| POST | `/ops/api/guest-inquiries/{no}/replies/{seq}/resend` | 필요 |
| GET | `/ops/api/companies` | 필요 |
| GET | `/ops/api/companies/{code}` | 필요 |
