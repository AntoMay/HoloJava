#!/bin/sh
# Public build: both artifacts, starting from zero coins.
#
# What is different from the development builds:
#
#   * ShopStartingCapital.VALUE is forced to 0, so a fresh or spent-down install
#     is left with whatever the player has actually earned. The development
#     builds keep the large capital checked into ShopStartingCapital.java, which
#     exists so the shop can be driven while the coin economy has no real
#     earning rate.
#
#   * Both artifacts land in dist/Public/{mobile,desktop}/.
#
# The capital is compiled in rather than passed as a system property because a
# real MIDP device cannot be given -D flags, and FreeJ2ME-Plus's own
# restartApp() puts them after -jar where the JVM ignores them. There is no
# runtime switch that would work on an actual handset.
#
# The override is done by compiling from a copy of the sources with that one
# file rewritten. Nothing under src/ is modified, so the development and test
# builds keep their large capital.
#
# Outputs:
#   dist/Public/mobile/HoloJavaFullGame.jar    preverified, sideload on a phone
#   dist/Public/mobile/HoloJavaFullGame.jad    descriptor
#   dist/Public/desktop/HoloJavaFullGame.jar  runs in FreeJ2ME-Plus
#   dist/Public/desktop/HoloJavaFullGame.jad  descriptor
#
# Both artifacts are called HoloJavaFullGame.jar: the directory says which is
# mobile and which is desktop, so the file name does not have to.
#
# Usage: tools/build_public.sh [capital]     # capital defaults to 0
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

PUBLIC_CAPITAL=${1:-0}

BUILD="$ROOT/build/public"
SRC="$BUILD/src"
CLASSES="$BUILD/classes"
PREVERIFIED="$BUILD/preverified"
DIST_MOBILE="$ROOT/dist/Public/mobile"
DIST_DESKTOP="$ROOT/dist/Public/desktop"
STUBS="$ROOT/deps/java-me/cldcapi11-2.0.4.jar:$ROOT/deps/java-me/midpapi20-2.0.4.jar"

case "$PUBLIC_CAPITAL" in
    ''|*[!0-9]*) echo "capital must be a whole number, got: $PUBLIC_CAPITAL" >&2; exit 1 ;;
esac

rm -rf "$BUILD"
mkdir -p "$SRC" "$CLASSES/assets" "$CLASSES/data/wiki" "$PREVERIFIED" "$DIST_MOBILE" "$DIST_DESKTOP"

cp -a "$ROOT/src/java-me-fullgame/." "$SRC/"
# src/ is copied verbatim, so an editor backup dropped next to a source file
# would ship to players. Strip them here rather than relying on the copy
# being clean.
find "$SRC" -name '*.bak' -delete
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
print("  ShopStartingCapital.VALUE forced to %sL" % value)
PY

echo "compiling and gating against the CLDC/MIDP stubs"
# --release 8 for the compile, then tools/verify_cldc_classes.sh rewrites javac's
# generated StringBuilder and checks every emitted java.*/javax.* reference
# against the stubs: with plain --release 8 javac resolves java.* from the desktop
# JDK, so nothing else would catch a phone build using APIs a handset lacks
# (ArrayList, String.matches(), ...). Compiling against the stubs as the boot
# classpath was tried first and cannot work: every concatenation fails on CLDC's
# missing StringBuilder. See the long note in tools/build_fullgame.sh.
# shellcheck disable=SC2046
javac --release 8 -nowarn -sourcepath "$SRC" -cp "$STUBS" -d "$CLASSES" \
    $(find "$SRC" -name '*.java' | sort)
tools/verify_cldc_classes.sh "$CLASSES"

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
# No -dontwarn: verify_cldc_classes.sh proved these class files reference only
# classes and members the stubs declare, and ProGuard exits non-zero on any
# unresolved reference it still finds.
proguard -verbose \
  -injars "$CLASSES" \
  -outjars "$PREVERIFIED" \
  -libraryjars "$ROOT/deps/java-me/cldcapi11-2.0.4.jar" \
  -libraryjars "$ROOT/deps/java-me/midpapi20-2.0.4.jar" \
  -keep 'public class me.holojava.fullgame.FullGameMIDlet extends javax.microedition.midlet.MIDlet { public <init>(); public void startApp(); public void pauseApp(); public void destroyApp(boolean); }' \
  -dontshrink -dontoptimize -dontobfuscate -microedition >/dev/null

mkdir -p "$PREVERIFIED/META-INF" "$PREVERIFIED/data/wiki"
cp "$CLASSES/META-INF/MANIFEST.MF" "$PREVERIFIED/META-INF/MANIFEST.MF"
# Guarantee the runtime CSV survives preverification; ContentCatalog reads it
# through getResourceAsStream.
cp "$CLASSES/data/wiki/collabs.csv" "$PREVERIFIED/data/wiki/collabs.csv"

PHONE_JAR="$DIST_MOBILE/HoloJavaFullGame.jar"
rm -f "$PHONE_JAR" "$DIST_MOBILE/HoloJavaFullGame.jad"
(cd "$PREVERIFIED" && zip -qr "$PHONE_JAR" META-INF assets data me)
PHONE_SIZE=$(stat -c %s "$PHONE_JAR")

# -------------------------------------------------------------- desktop JAR --
# Plain javac output, because ProGuard 6.2.2 emits a StackMapTable a desktop
# JVM rejects. See the header of tools/build_fullgame_desktop.sh.
echo "packaging the desktop artifact"
DESKTOP_JAR="$DIST_DESKTOP/HoloJavaFullGame.jar"
rm -f "$DESKTOP_JAR" "$DIST_DESKTOP/HoloJavaFullGame.jad"
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

write_jad "$PHONE_JAR"   "$DIST_MOBILE/HoloJavaFullGame.jad"  "$PHONE_SIZE"
write_jad "$DESKTOP_JAR" "$DIST_DESKTOP/HoloJavaFullGame.jad" "$DESKTOP_SIZE"

cat <<SUMMARY

Public build ready in dist/Public/
  starting coin capital : $PUBLIC_CAPITAL

  mobile/HoloJavaFullGame.jar   $PHONE_SIZE bytes  (sideload on a phone)
  desktop/HoloJavaFullGame.jar  $DESKTOP_SIZE bytes  (FreeJ2ME-Plus)
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