#!/usr/bin/env bash
# Rebuilds RoamCast-GrapheneOS.apk from the original RoamCast.apk.
#
#   ./build.sh path/to/RoamCast.apk [out.apk]
#
# Needs: java 17+, python3, curl, zip. Tools are downloaded into ./.tools.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
IN="$(realpath "${1:?usage: build.sh RoamCast.apk [out.apk]}")"
OUT="$(realpath -m "${2:-$HERE/RoamCast-GrapheneOS.apk}")"
TOOLS="$HERE/.tools"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$TOOLS"

fetch() { [ -s "$TOOLS/$1" ] || curl -fsSL -o "$TOOLS/$1" "$2"; }
fetch apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar
fetch uber-apk-signer.jar https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
fetch android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-35/android.jar
if [ ! -s "$TOOLS/jadx/lib/jadx-1.5.1-all.jar" ]; then   # provides D8 (java -> dex)
  curl -fsSL -o "$WORK/jadx.zip" https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip
  unzip -qo "$WORK/jadx.zip" -d "$TOOLS/jadx"
fi

echo "== decode"
java -jar "$TOOLS/apktool.jar" d -f -o "$WORK/app" "$IN" >/dev/null

echo "== patch"
python3 "$HERE/patch.py" "$WORK/app"

echo "== compile helpers"
mkdir -p "$WORK/classes" "$WORK/dex"
javac -source 11 -target 11 -Xlint:-options -encoding UTF-8 -cp "$TOOLS/android.jar" \
  -d "$WORK/classes" $(find "$HERE/java" -name '*.java')
java -cp "$TOOLS/jadx/lib/jadx-1.5.1-all.jar" com.android.tools.r8.D8 --release --min-api 30 \
  --lib "$TOOLS/android.jar" --output "$WORK/dex" $(find "$WORK/classes" -name '*.class')

echo "== build"
java -jar "$TOOLS/apktool.jar" b -o "$WORK/unsigned.apk" "$WORK/app" >/dev/null
n=1; while unzip -l "$WORK/unsigned.apk" | grep -q " classes$( [ $n -gt 1 ] && echo $n ).dex$"; do n=$((n+1)); done
cp "$WORK/dex/classes.dex" "$WORK/classes$n.dex"
(cd "$WORK" && zip -q -0 unsigned.apk "classes$n.dex")

echo "== sign"
java -jar "$TOOLS/uber-apk-signer.jar" -a "$WORK/unsigned.apk" -o "$WORK/signed" >/dev/null
cp "$WORK"/signed/*.apk "$OUT"
echo "built $OUT"
