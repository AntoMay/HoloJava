#!/bin/sh
# Offline full-game build using the pinned local CLDC/MIDP stubs and ProGuard.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
BUILD="$ROOT/build/java-me-fullgame"
DIST="$ROOT/dist/java-me-fullgame"
STUBS="$ROOT/deps/java-me/cldcapi11-2.0.4.jar:$ROOT/deps/java-me/midpapi20-2.0.4.jar"
rm -rf "$BUILD/classes" "$BUILD/preverified"
mkdir -p "$BUILD/classes/assets" "$BUILD/preverified" "$BUILD/manifest" "$DIST"
cp -a "$ROOT/assets/fullgame" "$BUILD/classes/assets/"
javac --release 8 -sourcepath "$ROOT/src/java-me-fullgame" -cp "$STUBS" -d "$BUILD/classes" $(find "$ROOT/src/java-me-fullgame" -name '*.java' | sort)
proguard -verbose \
  -injars "$BUILD/classes" \
  -outjars "$BUILD/preverified" \
  -libraryjars "$ROOT/deps/java-me/cldcapi11-2.0.4.jar" \
  -libraryjars "$ROOT/deps/java-me/midpapi20-2.0.4.jar" \
  -keep 'public class me.holojava.fullgame.FullGameMIDlet extends javax.microedition.midlet.MIDlet { public <init>(); public void startApp(); public void pauseApp(); public void destroyApp(boolean); }' \
  -dontshrink -dontoptimize -dontobfuscate -dontwarn -microedition
cat > "$BUILD/manifest/MANIFEST.MF" <<'MANIFEST'
Manifest-Version: 1.0
MIDlet-Name: HoloJava Full Game
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava Full Game, , me.holojava.fullgame.FullGameMIDlet
MIDlet-Icon: assets/fullgame/ui/holocure_icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MANIFEST
mkdir -p "$BUILD/preverified/META-INF"
cp "$BUILD/manifest/MANIFEST.MF" "$BUILD/preverified/META-INF/MANIFEST.MF"
rm -f "$DIST/HoloJavaFullGame.jar" "$DIST/HoloJavaFullGame.jad"
(cd "$BUILD/preverified" && zip -r "$DIST/HoloJavaFullGame.jar" META-INF assets me >/dev/null)
JAR_SIZE=$(stat -c %s "$DIST/HoloJavaFullGame.jar")
cat > "$DIST/HoloJavaFullGame.jad" <<JAD
MIDlet-Name: HoloJava Full Game
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava Full Game, , me.holojava.fullgame.FullGameMIDlet
MIDlet-Jar-URL: HoloJavaFullGame.jar
MIDlet-Jar-Size: $JAR_SIZE
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
JAD
python3 "$ROOT/tools/verify_fullgame_build.py"
printf 'Built %s (%s bytes)\n' "$DIST/HoloJavaFullGame.jar" "$JAR_SIZE"
