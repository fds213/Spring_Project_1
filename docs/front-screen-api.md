# 프론트 화면 정리

dev2에 넣은 화면과, 화면 JS가 서버를 부르는 방식을 정리했다. 범위는 요구분석서 3장 '이번 구현 범위'(11/13 마감)를 따른다.
화면 JS는 시안(feature/front-prototype, 이제 화면 참고용)에서 가져와 dev2에 맞게 고쳤다.

> API(주소·요청·응답)는 모듈 담당이 Controller → Service → DAO → Mapper 한 세트로 정한다. 프론트는 그 코드에 맞춰 화면 JS를 고친다.
> 2026-10-30 develop에는 로그인(`POST /login`)과 화면 주소 말고는 백엔드 API가 아직 없다. 계정 만들기·비밀번호 찾기 화면은 백엔드 API가 생긴 뒤에 JS를 붙인다(지금은 화면 모양만).

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
