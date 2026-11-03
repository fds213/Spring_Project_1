#!/usr/bin/env bash
# JSP 컴파일과 앱 기동 검사에 쓸 Tomcat과 Ant를 Maven Central에서 받는다. JspC가 Ant 클래스를 쓰는데 Tomcat에는 없다.
# 사용: get-tomcat.sh <받을 폴더>. 끝나면 <받을 폴더>/ant.jar와 <받을 폴더>/bin/catalina.sh가 있다.
set -euo pipefail

folder=$1
repository=https://repo.maven.apache.org/maven2
tomcat_version=9.0.122
tomcat_sha512=1f2f7d822a407999d954e7eb4fb1e78998c2a9372bb466d27e8cefd2521ef779183744f39dfa50a77ce3b798e7fe30bf2fd43fd1d6a2e6135617af1ddda6ba2a
ant_version=1.10.14
ant_sha512=9fa6296d1d224e16e6d0ae270e128098d480f8b54dee3deb13d348bf033fa621498dfdafea61d84f0b4710d12c21a42f05be076ddce385754879c8782a333c4e

fetch() { # <주소> <저장할 파일> <sha512>
  curl -fsSL --retry 3 --max-time 120 -o "$2" "$1"
  echo "$3  $2" | shasum -a 512 -c - >/dev/null
}

mkdir -p "$folder"
fetch "${repository}/org/apache/tomcat/tomcat/${tomcat_version}/tomcat-${tomcat_version}.tar.gz" "${folder}/tomcat.tar.gz" "$tomcat_sha512"
tar -xzf "${folder}/tomcat.tar.gz" -C "$folder" --strip-components=1
rm "${folder}/tomcat.tar.gz"
fetch "${repository}/org/apache/ant/ant/${ant_version}/ant-${ant_version}.jar" "${folder}/ant.jar" "$ant_sha512"
echo "Tomcat ${tomcat_version}, Ant ${ant_version} 받음: ${folder}"
