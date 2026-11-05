#!/usr/bin/env bash
# Gradle-free build: aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner (+ bundletool for the AAB).
# Runs on Windows (Git Bash) and Linux (CI).
#
# Outputs:
#   build/ig-group-guard-debug.apk     app, debug key (local testing)
#   build/ig-group-guard-fixture.apk   test fixture (fake Instagram share sheet), debug key
#   build/ig-group-guard-release.apk   app, upload key (GitHub Releases)        } only when the upload
#   build/ig-group-guard-release.aab   app bundle, upload key (Google Play)     } key is configured
#
# Upload key, either:
#   - env GG_UPLOAD_KS (keystore path), GG_UPLOAD_ALIAS, GG_UPLOAD_PASS   (CI), or
#   - a properties file with those keys: GG_UPLOAD_PROPS, default ~/.android/ig-group-guard-upload.properties
# Never commit the keystore or the properties file.
# ponytail: no Gradle; switch to an Android Studio project if dependencies are ever needed.
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"

HOME_DIR="${USERPROFILE:-$HOME}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-}/Android/Sdk}}"
BT="$SDK/build-tools/${BUILD_TOOLS:-36.0.0}"
JAR="$SDK/platforms/android-36/android.jar"
DEBUG_KS="${DEBUG_KEYSTORE:-$HOME_DIR/.android/debug.keystore}"
BUNDLETOOL="${BUNDLETOOL:-$HOME_DIR/.android/tools/bundletool-all-1.18.3.jar}"
PROPS="${GG_UPLOAD_PROPS:-$HOME_DIR/.android/ig-group-guard-upload.properties}"
PY=""; for c in python3 python; do "$c" -c "" >/dev/null 2>&1 && { PY=$c; break; }; done
[ -n "$PY" ] || { echo "Python 3 not found" >&2; exit 1; }

# Windows build-tools ship .bat/.exe wrappers; Linux ones have no extension.
tool() { for t in "$BT/$1" "$BT/$1.bat" "$BT/$1.exe"; do [ -f "$t" ] && { echo "$t"; return; }; done
         echo "missing build tool: $1 in $BT" >&2; exit 1; }
AAPT2=$(tool aapt2); D8=$(tool d8); ZIPALIGN=$(tool zipalign); APKSIGNER=$(tool apksigner)

zip_add() { # <zip> <file> <name inside zip>
  "$PY" -I -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1],'a').write(sys.argv[2],sys.argv[3])" "$@"
}

compile() { # <module dir> -> build/<module>/{res.zip,classes.dex,aligned.apk}
  local mod=$1 out=build/$1
  mkdir -p "$out/classes" "$out/gen"
  "$AAPT2" compile --dir "$mod/res" -o "$out/res.zip"
  "$AAPT2" link -I "$JAR" --manifest "$mod/AndroidManifest.xml" \
    --java "$out/gen" -o "$out/base.apk" "$out/res.zip"
  # shellcheck disable=SC2046  # file lists are plain paths without spaces
  javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$JAR" -Xlint:-options -Werror \
    -d "$out/classes" $(find "$mod/src" "$out/gen" -name '*.java')
  # shellcheck disable=SC2046
  "$D8" --release --lib "$JAR" --min-api 26 --output "$out" $(find "$out/classes" -name '*.class')
  zip_add "$out/base.apk" "$out/classes.dex" classes.dex
  "$ZIPALIGN" -f -p 4 "$out/base.apk" "$out/aligned.apk"
}

sign_apk() { # <module> <keystore> <alias> <pass> <output>
  "$APKSIGNER" sign --ks "$2" --ks-key-alias "$3" --ks-pass "pass:$4" --key-pass "pass:$4" \
    --out "$5" "build/$1/aligned.apk"
  "$APKSIGNER" verify "$5"
  echo "Built $5"
}

bundle() { # app module -> build/ig-group-guard-release.aab (signed below with jarsigner)
  local out=build/app stage=build/app/bundle
  "$AAPT2" link --proto-format -I "$JAR" --manifest app/AndroidManifest.xml \
    -o "$out/proto.apk" "$out/res.zip"
  rm -rf "$stage" && mkdir -p "$stage"
  "$PY" -I - "$out/proto.apk" "$out/classes.dex" "$stage/base.zip" <<'EOF'
import sys, zipfile
src, dex, dst = sys.argv[1:]
with zipfile.ZipFile(src) as z, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as o:
    for n in z.namelist():
        o.writestr("manifest/AndroidManifest.xml" if n == "AndroidManifest.xml" else n, z.read(n))
    o.write(dex, "dex/classes.dex")
EOF
  java -jar "$BUNDLETOOL" build-bundle --modules="$stage/base.zip" --output=build/ig-group-guard-release.aab
  java -jar "$BUNDLETOOL" validate --bundle=build/ig-group-guard-release.aab >/dev/null
}

if [ ! -f "$DEBUG_KS" ]; then # fresh machine / CI: make a throwaway debug key
  mkdir -p "$(dirname "$DEBUG_KS")"
  keytool -genkeypair -keystore "$DEBUG_KS" -alias androiddebugkey -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

rm -rf build
compile app
compile fixture
sign_apk app "$DEBUG_KS" androiddebugkey android build/ig-group-guard-debug.apk
sign_apk fixture "$DEBUG_KS" androiddebugkey android build/ig-group-guard-fixture.apk

if [ -z "${GG_UPLOAD_KS:-}" ] && [ -f "$PROPS" ]; then
  GG_UPLOAD_KS=$(grep '^GG_UPLOAD_KS=' "$PROPS" | cut -d= -f2- | tr -d '\r')
  GG_UPLOAD_ALIAS=$(grep '^GG_UPLOAD_ALIAS=' "$PROPS" | cut -d= -f2- | tr -d '\r')
  GG_UPLOAD_PASS=$(grep '^GG_UPLOAD_PASS=' "$PROPS" | cut -d= -f2- | tr -d '\r')
fi
if [ -n "${GG_UPLOAD_KS:-}" ]; then
  sign_apk app "$GG_UPLOAD_KS" "$GG_UPLOAD_ALIAS" "$GG_UPLOAD_PASS" build/ig-group-guard-release.apk
  bundle
  jarsigner -keystore "$GG_UPLOAD_KS" -storepass "$GG_UPLOAD_PASS" -keypass "$GG_UPLOAD_PASS" \
    -sigalg SHA256withRSA -digestalg SHA-256 build/ig-group-guard-release.aab "$GG_UPLOAD_ALIAS" >/dev/null
  jarsigner -verify build/ig-group-guard-release.aab >/dev/null
  echo "Built build/ig-group-guard-release.aab"
else
  echo "No upload key: skipped release APK and AAB."
fi
