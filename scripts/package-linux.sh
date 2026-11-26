#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ "$(uname -s)" != Linux ]; then
    echo "Linux packaging requires a Linux host." >&2
    exit 1
fi
output=${1:-dist}
type=${2:-app-image}
case "$type" in
    app-image|deb|rpm) ;;
    *) echo "Specify app-image, deb or rpm." >&2; exit 1 ;;
esac
jpackage=jpackage
if [ -n "${JAVA_HOME:-}" ]; then jpackage="$JAVA_HOME/bin/jpackage"; fi
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
"$jpackage" --type "$type" --input "$input" --dest "$output" \
    --name ChessMasterRoyal --app-version "$version" --main-jar "$name" \
    --main-class com.jiexiangtech.chess.desktop.Main \
    --add-modules java.desktop,java.net.http,java.sql,java.management,java.naming,jdk.crypto.ec,jdk.unsupported \
    --vendor JIEXIANGTECH --copyright '© 2026 JIEXIANGTECH. All rights reserved.' \
    --icon chess-desktop/src/main/resources/assets/branding/app-icon.png
