#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ "$(uname -s)" != Darwin ]; then
    echo "macOS packaging requires a macOS host." >&2
    exit 1
fi
output=${1:-dist}
type=${2:-app-image}
case "$type" in
    app-image) set -- ;;
    dmg) set -- --license-file LICENSE ;;
    *) echo "Specify app-image or dmg." >&2; exit 1 ;;
esac
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17)
fi
jar=
for candidate in chess-desktop/target/royal-chess-*-all.jar; do
    [ -f "$candidate" ] && jar=$candidate
done
[ -n "$jar" ] || { echo "Build with scripts/build.sh before packaging." >&2; exit 1; }
name=$(basename "$jar")
version=${name#royal-chess-}
version=${version%-all.jar}
input=$(mktemp -d "${TMPDIR:-/tmp}/royal-package.XXXXXX")
trap 'unlink "$input/$name"; rmdir "$input"' EXIT
cp "$jar" "$input/"
mkdir -p "$output"
"$JAVA_HOME/bin/jpackage" --type "$type" --input "$input" --dest "$output" \
    --name 'ChessMaster Royal' --app-version "$version" --main-jar "$name" \
    --main-class com.jiexiangtech.chess.desktop.Main \
    --add-modules java.desktop,java.sql,java.management,java.naming,jdk.crypto.ec,jdk.unsupported \
    --vendor JIEXIANGTECH --copyright '© 2026 JIEXIANGTECH. All rights reserved.' \
    --mac-package-name ChessMaster --mac-package-identifier com.jiexiangtech.chess \
    --icon chess-desktop/src/main/resources/assets/icons/app-icon.icns "$@"
