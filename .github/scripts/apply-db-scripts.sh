#!/usr/bin/env bash
# db/README.md의 '신규 빈 개발 DB 실행 순서'대로 스크립트를 넣는다. 오류가 하나라도 나면 실패로 끝난다.
# 사용: apply-db-scripts.sh <Oracle 컨테이너> <서비스 이름> <IEUM 비밀번호>
set -euo pipefail

container=$1
service=$2
password=$3

sqlplus_in() { # <컨테이너 안 폴더>: 표준 입력의 명령을 IEUM 계정으로 실행한다
  docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 -w "$1" "$container" \
    sqlplus -L -S "ieum/${password}@//localhost:1521/${service}"
}

# APP_USER는 컨테이너가 healthy가 된 뒤에 만들어질 수 있어 접속될 때까지 기다린다
for attempt in $(seq 1 30); do
  if sqlplus_in /tmp <<<'EXIT' >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" = 30 ]; then
    echo "IEUM 계정으로 접속하지 못했습니다." >&2
    exit 1
  fi
  sleep 5
done

docker cp db "${container}:/tmp/db"

log=$(mktemp)
for file in ddl/IEUM_DDL.sql data/IEUM_COMMON_CODE_DML.sql data/init_auth_data.sql; do
  echo "::group::${file}"
  # 스크립트 안의 WHENEVER SQLERROR는 SQL 오류만 멈춘다. 없는 파일(@@)·sqlplus 자기 오류(SP2-)는 멈추지 않고
  # 종료 코드 0으로 끝날 수 있고, ORA 번호가 256의 배수면 SQL.SQLCODE도 0이 되므로 출력까지 본다.
  status=0
  sqlplus_in "/tmp/db/$(dirname "$file")" <<SQL | tee "$log" || status=$?
WHENEVER OSERROR EXIT 9 ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
@$(basename "$file")
COMMIT;
EXIT 0
SQL
  echo "::endgroup::"
  if grep -E '^(ORA|SP2|PLS|TNS)-[0-9]+|compilation errors' "$log" || [ "$status" != 0 ]; then
    echo "${file}: 위 오류로 멈췄습니다(sqlplus 종료 코드 ${status})." >&2
    exit 1
  fi
done

invalid=$(sqlplus_in /tmp <<'SQL'
SET HEADING OFF FEEDBACK OFF PAGESIZE 0
SELECT COUNT(*) FROM user_objects WHERE status <> 'VALID';
EXIT 0
SQL
)
if [ "$(echo "$invalid" | tr -d '[:space:]')" != "0" ]; then
  echo "VALID가 아닌 객체가 있습니다: ${invalid}" >&2
  exit 1
fi
echo "DB 스크립트 적용 완료"
