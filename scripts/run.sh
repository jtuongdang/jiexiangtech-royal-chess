#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
jar=
for candidate in chess-desktop/target/royal-chess-*-all.jar; do
    [ -f "$candidate" ] && jar=$candidate
done
if [ -z "$jar" ]; then
    echo "Build with scripts/build.sh before launching." >&2
    exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then
    java="$JAVA_HOME/bin/java"
elif [ "$(uname -s)" = Darwin ]; then
    java="$(/usr/libexec/java_home -v 17)/bin/java"
else
    java=java
fi
exec "$java" -jar "$jar" "$@"
