#!/bin/sh
# Compiles and runs every HoloJava full-game test suite.
#
# Two output directories, and the order matters:
#
#   shadow  javax.microedition.lcdui.Image, a test-only stand-in for the MIDP class. The pinned
#           stub JARs make every Image member throw java.lang.Error, which AssetLibrary.image()
#           does not catch because it catches Exception. Without this, no AssetLibrary can be
#           built and no real RunWorld can be constructed, which the lifecycle integration suite
#           depends on. It must come FIRST on the classpath so it wins over the stub JARs.
#   out     production classes and the test classes.
#
# The shadow is never packaged: tools/build_fullgame.sh compiles only src/java-me-fullgame.
#
# Usage: tools/run_fullgame_tests.sh [SuiteName ...]
# With no arguments every *Test class under tools/tests is run.

set -e

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"

CP="deps/java-me/cldcapi11-2.0.4.jar:deps/java-me/midpapi20-2.0.4.jar:data"
BUILD=${HOLOJAVA_TEST_BUILD:-/tmp/holojava-tests}
SHADOW="$BUILD/shadow"
OUT="$BUILD/out"

rm -rf "$BUILD"
mkdir -p "$SHADOW" "$OUT"

echo "== compiling test-only Image stand-in =="
# shellcheck disable=SC2086
javac --release 8 -nowarn \
    -cp "deps/java-me/cldcapi11-2.0.4.jar:deps/java-me/midpapi20-2.0.4.jar" \
    -d "$SHADOW" tools/tests/javax/microedition/lcdui/Image.java

echo "== compiling production sources =="
# The emitted bytecode is gated against the stubs immediately after this, so the
# suite fails whenever the phone build would (see the long note in
# tools/build_fullgame.sh: with --release 8 javac resolves java.* from the desktop
# JDK, which is how non-CLDC code -- ArrayList, String.matches(), Math.atan2 --
# once passed every test and then failed on a handset). The test sources below
# compile AFTER the gate has run: they run on the desktop and are never packaged,
# so they may use desktop APIs without those ever being checked.
# shellcheck disable=SC2046
javac --release 8 -nowarn -cp "$CP" -d "$OUT" \
    $(find src/java-me-fullgame -name '*.java' | sort)

tools/verify_cldc_classes.sh "$OUT"

# Mirror production packaging (tools/build_fullgame.sh): stage the collab
# catalog at the runtime resource path so ContentCatalog reads CSV, not fallback.
mkdir -p "$OUT/data/wiki"
cp "$root/data/wiki/collabs.csv" "$OUT/data/wiki/collabs.csv"

echo "== cldc gate self-test (a violating fixture must be rejected) =="
# The gate above only means something if it still bites. Compile a class that
# uses APIs no CLDC handset has, run it through the same rewrite-then-check
# pipeline, and fail the run if the checker accepts it -- or if it crashes
# instead of reporting, because a traceback exits non-zero too and would
# otherwise masquerade as a rejection.
fixture="$BUILD/cldc-gate-fixture"
mkdir -p "$fixture"
cat > "$fixture/CldcGateFixture.java" <<'EOF'
import java.util.ArrayList;

// Deliberately non-CLDC. Compiled only to prove tools/check_cldc_compliance.py
// still rejects violations; never packaged, never loaded by a suite.
public final class CldcGateFixture {
    private final ArrayList<String> cache = new ArrayList<String>();

    boolean probe(String text) {
        return text.isEmpty() || text.matches("[0-9]+") || cache.isEmpty();
    }
}
EOF
javac --release 8 -nowarn -d "$fixture" "$fixture/CldcGateFixture.java"
python3 tools/stringbuilder_to_stringbuffer.py "$fixture" >/dev/null
if python3 tools/check_cldc_compliance.py \
        "deps/java-me/cldcapi11-2.0.4.jar" "deps/java-me/midpapi20-2.0.4.jar" \
        -- "$fixture" > "$fixture/gate.log" 2>&1; then
    echo "FATAL: the CLDC gate accepted a fixture using java.util.ArrayList" >&2
    exit 1
elif ! grep -q "CLDC compliance FAILED" "$fixture/gate.log"; then
    echo "FATAL: the CLDC gate did not report a clean rejection:" >&2
    cat "$fixture/gate.log" >&2
    exit 1
fi
echo "gate rejected the fixture as required"

echo "== compiling test sources =="
# shellcheck disable=SC2046
javac --release 8 -Xlint:-options -nowarn -cp "$OUT:$CP" -d "$OUT" \
    $(find tools/tests -name '*.java' | sort)

if [ "$#" -gt 0 ]; then
    suites=""
    for name in "$@"; do
        suites="$suites me.holojava.fullgame.$name"
    done
else
    suites=$(find tools/tests -name '*Test.java' \
        | sed 's|tools/tests/||; s|/|.|g; s|\.java$||' | sort)
fi

total=0
failed=0
passed=0
echo
echo "== running suites =="
for suite in $suites; do
    log="$BUILD/$(echo "$suite" | tr '.' '_').log"
    if java -cp "$SHADOW:$OUT:$CP" "$suite" > "$log" 2>&1; then
        status=PASS
        passed=$((passed + 1))
    else
        status=FAIL
        failed=$((failed + 1))
    fi
    checks=$(sed -n 's/^checks=\([0-9]*\).*/\1/p' "$log" | tail -1)
    [ -n "$checks" ] || checks=0
    total=$((total + checks))
    printf '%-40s %-6s %6s checks\n' "${suite##*.}" "$status" "$checks"
    if [ "$status" = FAIL ]; then
        echo "-------- output --------"
        cat "$log"
        echo "-----------------------"
    fi
done

echo
echo "suites run     : $((passed + failed))"
echo "suites passed  : $passed"
echo "suites failed  : $failed"
echo "total checks   : $total"

[ "$failed" -eq 0 ] || exit 1
