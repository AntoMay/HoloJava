#!/bin/sh
# Builds every artifact into dist/, from a clean slate.
#
#   dist/DevBuild/mobile/HoloJavaFullGame.jar            dev, preverified for a phone
#   dist/DevBuild/desktop/HoloJavaFullGame.jar           dev, FreeJ2ME-Plus
#   dist/Public/mobile/HoloJavaFullGame.jar              public, preverified
#   dist/Public/desktop/HoloJavaFullGame.jar             public, FreeJ2ME-Plus
#
# The two builds differ in exactly one thing: the compiled-in starting coin
# capital. Development ships a large balance so the shop can be driven without
# grinding; public ships zero so players earn what they spend. See
# src/java-me-fullgame/me/holojava/fullgame/ShopStartingCapital.java.
#
# dist/ is emptied first so a stale artifact from an older layout cannot be left
# behind looking current.
#
# Usage: tools/build_all.sh [public-capital]
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

printf '\n=== clearing dist/\n'
rm -rf "$ROOT/dist"
mkdir -p "$ROOT/dist"

printf '\n=== development build (large coin capital)\n'
tools/build_fullgame.sh
tools/build_fullgame_desktop.sh

printf '\n=== public build (zero coins)\n'
if [ "$#" -ge 1 ]; then
    tools/build_public.sh "$1"
else
    tools/build_public.sh
fi

printf '\n=== dist/\n'
find "$ROOT/dist" -type f | sort | sed "s|$ROOT/||"