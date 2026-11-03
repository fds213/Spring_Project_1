#!/usr/bin/env bash
# 풀어 둔 WAR의 JSP를 Tomcat의 JspC로 모두 컴파일한다. 앱을 띄워도 JSP는 그 화면을 열 때에야 컴파일돼 여기서 미리 본다.
# 사용: check-jsp.sh <Tomcat 폴더(get-tomcat.sh)> <풀어 둔 WAR 폴더>
set -euo pipefail

tomcat=$1
webapp=$2
output=$(mktemp -d)

# -die가 없으면 JspC는 컴파일 오류가 있어도 종료 코드 0으로 끝난다
java -Dfile.encoding=UTF-8 -cp "${tomcat}/lib/*:${tomcat}/bin/tomcat-juli.jar:${tomcat}/ant.jar" \
  org.apache.jasper.JspC -webapp "$webapp" -d "$output" -javaEncoding UTF-8 -source 11 -target 11 -compile -die

# JSP를 하나도 찾지 못한 채 통과하는 일을 막는다
expected=$(find "$webapp" -name '*.jsp' | wc -l | tr -d ' ')
compiled=$(find "$output" -name '*_jsp.class' | wc -l | tr -d ' ')
if [ "$expected" = 0 ] || [ "$compiled" != "$expected" ]; then
  echo "JSP ${expected}개 중 ${compiled}개만 컴파일됐습니다." >&2
  exit 1
fi
echo "JSP 컴파일 완료: ${compiled}개"
