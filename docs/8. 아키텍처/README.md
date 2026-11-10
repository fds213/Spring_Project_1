# 8. 아키텍처

- 넣는 것: 시스템 구성도 `이음_아키텍처.png`(또는 `.pdf`)와 원본
- 담당: 김상준(검토 서장호). 공통 설정(`web.xml`·Spring 설정 XML·Security·`pom.xml`)을 맡은 사람이 그리고, 백엔드 리드가 계층과 웹소켓 채팅 칸을 본다.
- 담을 것의 예: 브라우저·PWA → Tomcat 9(Spring 5.3 MVC·Security·MyBatis) → Oracle, 외부 연동(토스 결제·카카오 지도·메일 SMTP·웹소켓 채팅), 계층(Controller → Service → DAO → Mapper). 기술 목록은 AGENTS.md '기술 스택'을 따른다.

올리는 법·크기 제한·공개 저장소 주의는 [`docs/README.md`](../README.md)에 있다.
