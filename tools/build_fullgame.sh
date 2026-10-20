#!/bin/sh
# Development build of the phone (MIDP) artifact, using the pinned local CLDC/MIDP
# stubs and ProGuard. Ships the development starting coin capital, so the shop can
# be driven without grinding. The zero-coin counterpart is tools/build_public.sh.
#
# Output: dist/DevBuild/mobile/
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
BUILD="$ROOT/build/java-me-fullgame"
DIST="$ROOT/dist/DevBuild/mobile"
STUBS="$ROOT/deps/java-me/cldcapi11-2.0.4.jar:$ROOT/deps/java-me/midpapi20-2.0.4.jar"
rm -rf "$BUILD/classes" "$BUILD/preverified"
mkdir -p "$BUILD/classes/assets" "$BUILD/classes/data/wiki" "$BUILD/preverified" "$BUILD/manifest" "$DIST"
cp -a "$ROOT/assets/fullgame" "$BUILD/classes/assets/"
# Package the collab catalog at the runtime resource path read by
# ContentCatalog (/data/wiki/collabs.csv). Content unchanged.
cp "$ROOT/data/wiki/collabs.csv" "$BUILD/classes/data/wiki/collabs.csv"
# Compile with the desktop's Java 8 signatures, then gate what was emitted.
#
# With --release 8, javac resolves java.* from the desktop JDK's own signatures,
# so a phone build could freely use ArrayList, StringBuilder, BufferedReader,
# String.isEmpty() or String.matches() -- none of which exist on a CLDC handset.
# That is exactly how WikiCatalogData shipped: it compiled, every test passed, and
# the game died of NoClassDefFoundError on the first frame of a real phone.
#
# Compiling with the stubs as the boot classpath (-bootclasspath) was tried as
# the direct fix, and it does reject those at their source line. It cannot be
# used: since Java 5 javac compiles every concatenation ("a" + b) into
# java.lang.StringBuilder, CLDC has no StringBuilder, and these sources
# concatenate in about a hundred places -- so the compile dies on generated code
# nobody wrote and the hand-written violations are never even reached.
#
# So the check moves to the emitted bytecode. tools/verify_cldc_classes.sh runs
# two steps: rewrite the generated StringBuilder to StringBuffer (API-identical,
# and CLDC has it), then check every java.*/javax.* reference in the class files
# against the pinned stubs. That is the stronger check anyway -- it covers what
# the programmer wrote AND what the compiler emitted, resolves members through
# inheritance, and sees types that appear only inside a descriptor.
# shellcheck disable=SC2046
javac --release 8 -nowarn \
  -sourcepath "$ROOT/src/java-me-fullgame" -cp "$STUBS" -d "$BUILD/classes" \
  $(find "$ROOT/src/java-me-fullgame" -name '*.java' | sort)
tools/verify_cldc_classes.sh "$BUILD/classes"
# No -dontwarn: verify_cldc_classes.sh above proved these class files reference
# only classes and members the stubs declare, and ProGuard exits non-zero on any
# unresolved reference it still finds -- a second, independent gate over what
# actually ships.
proguard -verbose \
  -injars "$BUILD/classes" \
  -outjars "$BUILD/preverified" \
  -libraryjars "$ROOT/deps/java-me/cldcapi11-2.0.4.jar" \
  -libraryjars "$ROOT/deps/java-me/midpapi20-2.0.4.jar" \
  -keep 'public class me.holojava.fullgame.FullGameMIDlet extends javax.microedition.midlet.MIDlet { public <init>(); public void startApp(); public void pauseApp(); public void destroyApp(boolean); }' \
  -dontshrink -dontoptimize -dontobfuscate -microedition
cat > "$BUILD/manifest/MANIFEST.MF" <<'MANIFEST'
Manifest-Version: 1.0
MIDlet-Name: HoloJava
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava, , me.holojava.fullgame.FullGameMIDlet
MIDlet-Icon: assets/fullgame/ui/holocure_icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MANIFEST
mkdir -p "$BUILD/preverified/META-INF"
cp "$BUILD/manifest/MANIFEST.MF" "$BUILD/preverified/META-INF/MANIFEST.MF"
# Guarantee the runtime CSV survives preverification regardless of ProGuard
# resource handling; ContentCatalog reads it via getResourceAsStream.
mkdir -p "$BUILD/preverified/data/wiki"
cp "$ROOT/data/wiki/collabs.csv" "$BUILD/preverified/data/wiki/collabs.csv"
rm -f "$DIST/HoloJavaFullGame.jar" "$DIST/HoloJavaFullGame.jad"
(cd "$BUILD/preverified" && zip -r "$DIST/HoloJavaFullGame.jar" META-INF assets data me >/dev/null)
JAR_SIZE=$(stat -c %s "$DIST/HoloJavaFullGame.jar")
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
python3 "$ROOT/tools/verify_fullgame_build.py"
printf 'Built %s (%s bytes)\n' "$DIST/HoloJavaFullGame.jar" "$JAR_SIZE"
