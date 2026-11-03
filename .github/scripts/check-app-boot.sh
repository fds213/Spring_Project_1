#!/usr/bin/env bash
# 풀어 둔 WAR를 실제 Tomcat에 올려 앱이 뜨는지 본다. web.xml·Spring·Security 설정 오류, 같은 주소를 가진 Controller,
# 빠진 Bean, 생성자 순환 참조는 단위 테스트로 잡히지 않고 앱을 띄워야 드러난다.
# 사용: check-app-boot.sh <Tomcat 폴더(get-tomcat.sh)> <풀어 둔 WAR 폴더> <Oracle 주소(host:port/서비스)> <IEUM 비밀번호>
# 쓰는 포트는 HTTP_PORT(기본 8080)와 그 값 + 1(Tomcat 종료 포트)이다.
set -euo pipefail

tomcat=$1
webapp=$2
oracle=$3
password=$4
http_port=${HTTP_PORT:-8080}
shutdown_port=$((http_port + 1))
start_timeout=180

base=$(mktemp -d)
mkdir -p "${base}/logs" "${base}/temp" "${base}/work" "${base}/webapps" "${base}/files"
cp -R "${tomcat}/conf" "${base}/conf"
sed -i.bak -e "s/port=\"8080\"/port=\"${http_port}\"/" -e "s/port=\"8005\"/port=\"${shutdown_port}\"/" "${base}/conf/server.xml"
cp -R "$webapp" "${base}/webapps/ROOT"

# 토스·카카오 키는 넣지 않는다. 없어도 서버가 떠야 한다.
cat > "${base}/webapps/ROOT/WEB-INF/classes/config/db.properties" <<PROPERTIES
db.driver=net.sf.log4jdbc.sql.jdbcapi.DriverSpy
db.url=jdbc:log4jdbc:oracle:thin:@//${oracle}
db.username=ieum
db.password=${password}
PROPERTIES
echo "file.root=${base}/files" > "${base}/webapps/ROOT/WEB-INF/classes/config/file.properties"

export CATALINA_HOME="$tomcat" CATALINA_BASE="$base" CATALINA_PID="${base}/tomcat.pid"
export CATALINA_OPTS="-Dfile.encoding=UTF-8 -Duser.language=en"
url="http://localhost:${http_port}"
stop_tomcat() { "${tomcat}/bin/catalina.sh" stop 10 -force >/dev/null 2>&1 || true; }
trap stop_tomcat EXIT

fail() { # 원인을 찾을 수 있게 Tomcat 로그를 함께 보여 준다
  echo "::error::앱 기동 검사 실패: $1"
  echo "=== 원인으로 보이는 줄(가장 안쪽 예외가 대개 맨 아래) ==="
  # 시각을 떼고 같은 줄을 지운다. 예외 메시지가 여러 줄이라(같은 주소 오류의 Controller·주소) 뒤의 세 줄까지 보되 호출 위치 줄은 뺀다.
  grep -hsE -A3 'SEVERE|^Caused by: ' "${base}"/logs/* \
    | grep -vE '^[[:space:]]+(at |\.\.\. )|^--$' \
    | sed -E 's/^[0-9]{2}-[A-Za-z]{3}-[0-9]{4} [0-9:.]+ //' \
    | awk '!seen[$0]++' | cut -c1-400 | tail -30 || true
  echo "=== catalina.out 끝부분 ==="
  grep -sv VersionLoggerListener "${base}/logs/catalina.out" | cut -c1-400 | tail -40 || true
  exit 1
}

status_of() { # <주소 경로> [curl 옵션]: HTTP 상태 코드. 응답을 끝까지 받지 못하면(화면을 그리다 끊긴 200 포함) 000
  local path=$1 code
  shift
  code=$(curl -sS -o /dev/null -m 30 -w '%{http_code}' "$@" "${url}${path}") || code=000
  echo "$code"
}

expect() { # <주소 경로> <기대 상태 코드>
  local actual
  actual=$(status_of "$1")
  if [ "$actual" != "$2" ]; then
    fail "GET $1 응답이 ${actual}입니다(기대값 $2, 000은 응답을 끝까지 받지 못한 것)."
  fi
  echo "GET $1 -> $actual"
}

# 이미 포트를 쓰는 프로그램이 있으면 새 Tomcat이 아니라 그 프로그램을 검사하게 된다
[ "$(curl -s -o /dev/null -m 5 -w '%{http_code}' "${url}/" || true)" = 000 ] || fail "포트 ${http_port}을 이미 다른 프로그램이 쓰고 있습니다."
"${tomcat}/bin/catalina.sh" start || fail "Tomcat을 시작하지 못했습니다."

# Tomcat은 앱을 다 올린 뒤에 요청을 받기 시작한다. 첫 응답이 오면 앱 시작이 끝난 것이다. 응답의 내용은 아래 expect가 본다.
SECONDS=0
while [ "$(curl -s -o /dev/null -m 30 -w '%{http_code}' "${url}/login" || true)" = 000 ]; do
  if ! kill -0 "$(cat "$CATALINA_PID")" 2>/dev/null; then
    fail "Tomcat이 시작 중에 꺼졌습니다."
  fi
  if [ "$SECONDS" -ge "$start_timeout" ]; then
    fail "${start_timeout}초 안에 응답이 없습니다."
  fi
  sleep 2
done
echo "앱 시작까지 약 ${SECONDS}초"

expect /login 200
expect / 200
expect /resources/js/util.js 200
expect /home 302 # 로그인 전이라 로그인 화면으로 보낸다

# 시험 계정(db/data/init_auth_data.sql)으로 로그인해 공통 머리글이 붙는 첫 화면까지 연다
cookies="${base}/cookies"
curl -sS -m 30 -c "$cookies" -o "${base}/login.html" "${url}/login" || fail "로그인 화면을 받지 못했습니다."
csrf=$(sed -n '/name="_csrf"/{s/.*value="\([^"]*\)".*/\1/p;q;}' "${base}/login.html")
[ -n "$csrf" ] || fail "로그인 화면에서 CSRF 값을 찾지 못했습니다."
location=$(curl -sS -o /dev/null -m 30 -b "$cookies" -c "$cookies" -w '%{redirect_url}' "${url}/login" \
  --data-urlencode "_csrf=${csrf}" --data-urlencode "companyCode=ieum0001" \
  --data-urlencode "username=20260001" --data-urlencode "password=1234") \
  || fail "로그인 요청에 응답이 없습니다."
[ "$location" = "${url}/home" ] || fail "시험 계정 로그인이 첫 화면으로 가지 않았습니다(이동: ${location:-없음})."
echo "로그인 -> $location"
home=$(status_of /home -b "$cookies")
[ "$home" = 200 ] || fail "로그인 뒤 GET /home 응답이 ${home}입니다(기대값 200, 000은 응답을 끝까지 받지 못한 것)."
echo "로그인 뒤 GET /home -> $home"

echo "앱 기동 검사 통과"
