# 10. 요약본 및 소스 코드

- 넣는 것: 요약본 `이음_요약본.pdf`와 원본(`이음_요약본.docx` 등)
- 담당: 최준원
- 소스 코드는 이 저장소 자체라 이 폴더에 넣지 않는다. 마감 때 `main`에 붙인 태그로 zip을 만들어 따로 낸다(zip은 저장소에 올리지 않는다).

## 소스 코드 zip 만드는 법

마감 때 `develop` → `main`을 병합하고 태그를 붙인 뒤(예: `v1.0`), 저장소 폴더에서 아래를 실행한다.

```bash
git fetch --tags
git archive --format=zip --prefix=IEUM/ -o ../이음_소스코드.zip v1.0
```

- `git archive`: 그 태그 시점에 저장소에 올라간 파일만 묶는다. `.gitignore`에 있는 키 파일(`db.properties`·`toss.properties`·`kakao.properties`·`mail.properties` 등)과 `target/`은 들어가지 않는다.
- `--prefix=IEUM/`: zip을 풀면 `IEUM/` 폴더 하나가 나온다.
- `-o ../이음_소스코드.zip`: 저장소 바깥(한 단계 위 폴더)에 만든다.
- GitHub의 태그 페이지(Releases → 태그 → Source code (zip))에서 받아도 같은 내용이다.

올리는 법·크기 제한·공개 저장소 주의는 [`docs/README.md`](../README.md)에 있다.
