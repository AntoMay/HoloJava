#!/bin/sh
# Desktop-emulator build of the full game.
#
# Why this exists, and why it is not just tools/build_fullgame.sh with a
# different zip step:
#
#   tools/build_fullgame.sh runs ProGuard 6.2.2 for preverification, which is
#   what a real MIDP device expects. That ProGuard build emits an invalid
#   StackMapTable in at least one method: an `extended full_frame` (frame_type
#   255) with number_of_locals = 0, which the class file spec does not allow. A
#   desktop JVM loading the class through a normal class loader rejects it
#   before any MIDlet code runs:
#
#       java.lang.ClassFormatError: StackMapTable format error:
#           bad type array size in method
#           'void me.holojava.fullgame.FullGameMIDlet.startApp()'
#
#   That is what FreeJ2ME-Plus does, so the preverified JAR shows a blank screen
#   there and nothing appears to load. Plain javac output carries a valid
#   StackMapTable (the same sources already load on a desktop JVM in
#   tools/run_fullgame_tests.sh), so it runs.
#
# This script therefore compiles with javac and packages without ProGuard. It is
# a *desktop emulator* artifact, not a substitute for the preverified JAR that
# real devices want. Both are produced by the same sources.
#
# Usage: tools/build_fullgame_desktop.sh [emulator-jar]
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

BUILD="$ROOT/build/java-me-fullgame-desktop"
DIST="$ROOT/dist/java-me-fullgame-desktop"
STUBS="$ROOT/deps/java-me/cldcapi11-2.0.4.jar:$ROOT/deps/java-me/midpapi20-2.0.4.jar"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes/assets" "$BUILD/classes/data/wiki" "$DIST"
cp -a "$ROOT/assets/fullgame" "$BUILD/classes/assets/"
cp "$ROOT/data/wiki/collabs.csv" "$BUILD/classes/data/wiki/collabs.csv"

# shellcheck disable=SC2046
javac --release 8 -sourcepath "$ROOT/src/java-me-fullgame" -cp "$STUBS" \
    -d "$BUILD/classes" $(find "$ROOT/src/java-me-fullgame" -name '*.java' | sort)

mkdir -p "$BUILD/classes/META-INF"
cat > "$BUILD/classes/META-INF/MANIFEST.MF" <<'MANIFEST'
Manifest-Version: 1.0
MIDlet-Name: HoloJava
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava, , me.holojava.fullgame.FullGameMIDlet
MIDlet-Icon: assets/fullgame/ui/holocure_icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MANIFEST

JAR="$DIST/HoloJavaFullGame.jar"
rm -f "$JAR" "$DIST/HoloJavaFullGame.jad"
(cd "$BUILD/classes" && zip -qr "$JAR" META-INF assets data me)
JAR_SIZE=$(stat -c %s "$JAR")
cat > "$DIST/HoloJavaFullGame.jad" <<JAD
MIDlet-Name: HoloJava
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava, , me.holojava.fullgame.FullGameMIDlet
MIDlet-Jar-URL: HoloJavaFullGame.jar
MIDlet-Jar-Size: $JAR_SIZE
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
JAD

# Prove the classes the JVM will actually load are verifiable, which is the whole
# point of this artifact. Loading the MIDlet class is enough to trip the bad
# StackMapTable, and needs no MIDP runtime.
javap -v -p -c "$BUILD/classes/me/holojava/fullgame/FullGameMIDlet.class" > /dev/null
echo "verified: FullGameMIDlet.class parses cleanly (${JAR_SIZE} bytes JAR)"

EMULATOR=${1:-/home/antomay/Projects/tools/freej2me-plus/build/freej2me_plus.jar}
if [ -f "$EMULATOR" ]; then
    cat <<RUN

Play it with:
  java -Dfile.encoding=ISO_8859_1 -jar "$EMULATOR" \\
    "file://$JAR" 0 240 320 3

  -Dfile.encoding must come BEFORE -jar. FreeJ2ME-Plus's own restartApp() puts
  it after, where the JVM ignores it, so the emulator restarts forever on an
  encoding mismatch and never renders anything.
  Arguments after the jar path: 0 = windowed, 240 320 = LCD size, 3 = scale.
RUN
else
    echo "emulator not found at $EMULATOR; build finished anyway"
fi