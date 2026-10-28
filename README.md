# IEUM 프론트 시안

화면을 확인하는 정적 HTML 시안이다. STS 프로젝트(`develop`, `feature/common-layout`)와 별개인 브랜치라서 Spring 코드가 없다.
프론트 담당은 이 시안을 보고 JSP를 만든다. HTML 구조와 class 이름이 공통 CSS(`common.css`)와 같아서, 본문을 거의 그대로 JSP로 옮기면 된다.

- 데이터는 각자 브라우저(localStorage)에만 저장된다. 서버, DB, 로그인 설정이 필요 없다.
- 시안을 고치는 사람은 프론트 리드(홍동국)다. 바꿀 점은 직접 고치지 말고 프론트 리드에게 말한다.

## 받기

STS 프로젝트 폴더가 아닌 **다른 폴더**에 받는다.

```powershell
git clone -b feature/front-prototype https://github.com/fds213/Spring_Project_1.git ieum-prototype
```

시안이 바뀌면 `ieum-prototype` 폴더에서 아래 명령으로 최신 상태를 받는다.

```powershell
git pull
```

## 띄우기

`index.html`을 더블클릭하면 일부 기능이 동작하지 않는다. 아래 두 방법 중 하나로 서버를 띄워서 연다.

**Python**: `ieum-prototype` 폴더에서 실행한다. 끝낼 때는 `Ctrl+C`를 누른다.

```powershell
python -m http.server 8000
```

`python`을 쳤을 때 Microsoft Store가 열리면 Python이 설치되어 있지 않은 것이다. python.org에서 설치할 때 "Add python.exe to PATH"를 체크하고, 설치가 끝나면 PowerShell을 다시 연다.

열 주소:

- 소개 페이지: http://localhost:8000/ (주소만 열면 `site.html`로 이동한다)
- 그룹웨어: http://localhost:8000/index.html?preview
- 무료 체험 신청: http://localhost:8000/trial.html
- 도입 문의: http://localhost:8000/inquiry.html

**VS Code Live Server**: 폴더를 열고 `index.html`을 우클릭한 뒤 Open with Live Server를 누른다. 주소의 포트만 5500으로 바뀐다(예: http://127.0.0.1:5500/index.html?preview).

`?preview`를 붙이지 않으면 앱 설치 화면이 나온다.

## 들어가는 순서

예시 회사가 없으므로, 처음 한 번은 무료 체험을 신청해서 업체를 만든다.

1. `trial.html`에서 무료 체험을 신청한다. 업체 코드(`ieum0000` 형식)는 자동으로 붙는다.
2. 이메일 인증 단계에서 화면에 보이는 시연용 인증 코드를 입력한다. 실제 메일은 가지 않는다.
3. `index.html?preview`에서 로그인한다. 업체 코드는 신청할 때 받은 코드, 사번은 `20260001`, 비밀번호는 신청할 때 정한 것을 쓴다. 관리자 계정으로 들어간다.
4. 일반 직원 화면을 보려면 로그인 화면의 **계정 만들기**로 직원 계정을 신청하고, 관리자 계정에서 승인한다.
5. 운영자 화면은 업체 코드 `ieum`, 사번 `20260001`, 비밀번호 `ieum1234`로 들어간다.

탭을 닫으면 로그아웃된다. 브라우저 기록을 지우면 데이터가 사라지므로 1번부터 다시 한다.

## 화면 구조 보기

F12를 눌러 개발자 도구를 열고 Elements 탭에서 HTML 구조와 class 이름을 확인한다.
JSP에서는 이 본문을 `<ui:layout>` 안에 넣는다. 쓰는 법은 `feature/common-layout` 브랜치의 `WEB-INF/views/common/layout-sample.jsp`에 있다.
