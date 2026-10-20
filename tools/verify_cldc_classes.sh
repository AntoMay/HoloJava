#!/bin/sh
# Makes a directory of freshly compiled classes safe to hand to a CLDC handset,
# and proves it. Two steps, and the order is mandatory:
#
#   1. stringbuilder_to_stringbuffer.py -- javac has compiled "a" + b into
#      java.lang.StringBuilder since Java 5. CLDC has no StringBuilder, but it
#      does have StringBuffer with an identical public API, so the generated
#      references are rewritten in place.
#   2. check_cldc_compliance.py -- every java.*/javax.* class and member the
#      class files reference is checked against the pinned CLDC/MIDP stubs.
#      This is the check `javac --release 8` cannot make: it resolves java.*
#      from the desktop JDK's own signatures, which is how non-CLDC code
#      (ArrayList, String.matches(), Math.atan2) used to pass every build and
#      then fail on a real handset or an emulator.
#
# Compiling with the stubs as the boot classpath was tried first. It does catch
# hand-written violations at their source line, but it cannot be used: every
# concatenation fails against CLDC's stubs, so the compile dies on generated
# code nobody wrote and the real violations are never even reached. Checking
# the emitted bytecode instead covers the programmer's code AND the compiler's
# output, and resolves inherited members and descriptor-only types along the
# way.
#
# Usage: tools/verify_cldc_classes.sh <classes-dir>
set -eu

if [ "$#" -ne 1 ]; then
    echo "usage: verify_cldc_classes.sh <classes-dir>" >&2
    exit 2
fi

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CLASSES=$1

python3 "$ROOT/tools/stringbuilder_to_stringbuffer.py" "$CLASSES"
python3 "$ROOT/tools/check_cldc_compliance.py" \
    "$ROOT/deps/java-me/cldcapi11-2.0.4.jar" \
    "$ROOT/deps/java-me/midpapi20-2.0.4.jar" \
    -- "$CLASSES"
