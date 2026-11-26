#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -s)" = Darwin ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17)
    export JAVA_HOME
fi
exec mvn -B -ntp clean verify "$@"
