#!/bin/sh
# Maven Wrapper 3.3.4 (only-script distribution).
set -eu
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi
MVNW_DIR="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/apache-maven-3.9.11"
MAVEN_HOME="$MVNW_DIR/apache-maven-3.9.11"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  command -v curl >/dev/null 2>&1 || { echo "curl is required to bootstrap Maven" >&2; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required to bootstrap Maven" >&2; exit 1; }
  mkdir -p "$MVNW_DIR"
  archive="$MVNW_DIR/maven.zip"
  curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip" -o "$archive"
  unzip -q "$archive" -d "$MVNW_DIR"
  rm -f "$archive"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
