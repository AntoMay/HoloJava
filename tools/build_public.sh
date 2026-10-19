#!/bin/sh
# Public build: both artifacts, with the coin top-up compiled in.
#
# What is different from the development builds:
#
#   * ShopStartingCapital.VALUE is replaced with PUBLIC_CAPITAL, so a fresh or
#     spent-down install is topped up to that balance and the whole shop can be
#     driven without grinding. The development and test builds keep the zero
#     default checked into ShopStartingCapital.java.
#
#   * Both artifacts land in dist/public/ instead of their own directories.
#
# The capital is compiled in rather than passed as a system property because a
# real MIDP device cannot be given -D flags, and FreeJ2ME-Plus's own
# restartApp() puts them after -jar where the JVM ignores them. There is no
# runtime switch that would work on an actual handset.
#
# The override is done by compiling from a copy of the sources with that one
# file rewritten. Nothing under src/ is modified, and the default zero stays in
# place for development and for tools/run_fullgame_tests.sh, whose SaveStore
# tests pin a fresh install to no coins.
#
# Outputs:
#   dist/public/HoloJavaFullGame.jar            preverified, sideload on a phone
#   dist/public/HoloJavaFullGame-desktop.jar    runs in FreeJ2ME-Plus
#   dist/public/HoloJavaFullGame.jad             descriptor for the phone JAR
#   dist/public/HoloJavaFullGame-desktop.jad    descriptor for the desktop JAR
#
# Usage: tools/build_public.sh [capital]
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

# Large enough that nothing in the shop is out of reach: the priciest row is
# Max HP Up at 36,000. Callers can override by passing a number.
PUBLIC_CAPITAL=${1:-999999999}

BUILD="$ROOT/build/public"
SRC="$BUILD/src"
CLASSES="$BUILD/classes"
PREVERIFIED="$BUILD/preverified"
DIST="$ROOT/dist/public"
STUBS="$ROOT/deps/java-me/cldcapi11-2.0.4.jar:$ROOT/deps/java-me/midpapi20-2.0.4.jar"

case "$PUBLIC_CAPITAL" in
    ''|*[!0-9]*) echo "capital must be a whole number, got: $PUBLIC_CAPITAL" >&2; exit 1 ;;
esac

rm -rf "$BUILD"
mkdir -p "$SRC" "$CLASSES/assets" "$CLASSES/data/wiki" "$PREVERIFIED" "$DIST"

cp -a "$ROOT/src/java-me-fullgame/." "$SRC/"
cp -a "$ROOT/assets/fullgame" "$CLASSES/assets/"
cp "$ROOT/data/wiki/collabs.csv" "$CLASSES/data/wiki/collabs.csv"

# Rewrite only the constant. The rest of ShopStartingCapital's documentation is
# kept because it is about the mechanism, which does not change.
CAPITAL_FILE="$SRC/me/holojava/fullgame/ShopStartingCapital.java"
test -f "$CAPITAL_FILE" || { echo "cannot find $CAPITAL_FILE" >&2; exit 1; }
python3 - "$CAPITAL_FILE" "$PUBLIC_CAPITAL" <<'PY'
import re, sys
path, value = sys.argv[1], sys.argv[2]
src = open(path).read()
patched, n = re.subn(r"static final long VALUE = \d+L;",
                     "static final long VALUE = %sL;" % value, src)
if n != 1:
    sys.exit("expected exactly one VALUE declaration to patch, found %d" % n)
open(path, "w").write(patched)
print("  ShopStartingCapital.VALUE = %sL" % value)
PY

echo "compiling (javac --release 8)"
# shellcheck disable=SC2046
javac --release 8 -sourcepath "$SRC" -cp "$STUBS" -d "$CLASSES" $(find "$SRC" -name '*.java' | sort)

mkdir -p "$CLASSES/META-INF"
cat > "$CLASSES/META-INF/MANIFEST.MF" <<'MANIFEST'
Manifest-Version: 1.0
MIDlet-Name: HoloJava
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava, , me.holojava.fullgame.FullGameMIDlet
MIDlet-Icon: assets/fullgame/ui/holocure_icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MANIFEST

# ---------------------------------------------------------------- phone JAR --
# ProGuard preverification, same as tools/build_fullgame.sh.
echo "preverifying for the phone (ProGuard)"
proguard -verbose \
  -injars "$CLASSES" \
  -outjars "$PREVERIFIED" \
  -libraryjars "$ROOT/deps/java-me/cldcapi11-2.0.4.jar" \
  -libraryjars "$ROOT/deps/java-me/midpapi20-2.0.4.jar" \
  -keep 'public class me.holojava.fullgame.FullGameMIDlet extends javax.microedition.midlet.MIDlet { public <init>(); public void startApp(); public void pauseApp(); public void destroyApp(boolean); }' \
  -dontshrink -dontoptimize -dontobfuscate -dontwarn -microedition >/dev/null

mkdir -p "$PREVERIFIED/META-INF" "$PREVERIFIED/data/wiki"
cp "$CLASSES/META-INF/MANIFEST.MF" "$PREVERIFIED/META-INF/MANIFEST.MF"
# Guarantee the runtime CSV survives preverification; ContentCatalog reads it
# through getResourceAsStream.
cp "$CLASSES/data/wiki/collabs.csv" "$PREVERIFIED/data/wiki/collabs.csv"

PHONE_JAR="$DIST/HoloJavaFullGame.jar"
rm -f "$PHONE_JAR" "$DIST/HoloJavaFullGame.jad"
(cd "$PREVERIFIED" && zip -qr "$PHONE_JAR" META-INF assets data me)
PHONE_SIZE=$(stat -c %s "$PHONE_JAR")

# -------------------------------------------------------------- desktop JAR --
# Plain javac output, because ProGuard 6.2.2 emits a StackMapTable a desktop
# JVM rejects. See the header of tools/build_fullgame_desktop.sh.
echo "packaging the desktop artifact"
DESKTOP_JAR="$DIST/HoloJavaFullGame-desktop.jar"
rm -f "$DESKTOP_JAR" "$DIST/HoloJavaFullGame-desktop.jad"
(cd "$CLASSES" && zip -qr "$DESKTOP_JAR" META-INF assets data me)
DESKTOP_SIZE=$(stat -c %s "$DESKTOP_JAR")

# The whole point of the desktop artifact is that the classes actually load on a
# JVM, so prove it rather than assuming it.
javap -v -p -c "$CLASSES/me/holojava/fullgame/FullGameMIDlet.class" > /dev/null

write_jad() {
    cat > "$2" <<JAD
MIDlet-Name: HoloJava
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava, , me.holojava.fullgame.FullGameMIDlet
MIDlet-Jar-URL: $(basename "$1")
MIDlet-Jar-Size: $3
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
JAD
}

write_jad "$PHONE_JAR"   "$DIST/HoloJavaFullGame.jad"         "$PHONE_SIZE"
write_jad "$DESKTOP_JAR" "$DIST/HoloJavaFullGame-desktop.jad" "$DESKTOP_SIZE"

cat <<SUMMARY

Public build ready in dist/public/
  starting coin capital : $PUBLIC_CAPITAL

  HoloJavaFullGame.jar            $PHONE_SIZE bytes  (sideload on a phone)
  HoloJavaFullGame-desktop.jar    $DESKTOP_SIZE bytes  (FreeJ2ME-Plus)
SUMMARY

EMULATOR=${EMULATOR:-/home/antomay/Projects/tools/freej2me-plus/build/freej2me_plus.jar}
if [ -f "$EMULATOR" ]; then
    cat <<RUN

Play the desktop build with:
  java -Dfile.encoding=ISO_8859_1 -jar "$EMULATOR" \\
    "file://$DESKTOP_JAR" 0 320 240 3

  -Dfile.encoding must come BEFORE -jar, or FreeJ2ME restarts forever on an
  encoding mismatch and renders nothing. Arguments after the jar path:
  0 = windowed, 320 240 = LCD size, 3 = scale.
RUN
fi