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
