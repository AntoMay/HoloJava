#!/usr/bin/env python3
"""Offline static verification for the full-game MIDlet artifact."""
import csv
import os
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "dist/java-me-fullgame/HoloJavaFullGame.jar"
JAD = ROOT / "dist/java-me-fullgame/HoloJavaFullGame.jad"
CLASSES = ROOT / "build/java-me-fullgame/classes"


def fail(message):
    print("FAIL:", message)
    raise SystemExit(1)


def main():
    if not JAR.exists() or not JAD.exists():
        fail("full-game JAR/JAD missing")
    jar_size = JAR.stat().st_size
    with zipfile.ZipFile(JAR) as archive:
        bad = archive.testzip()
        if bad:
            fail("corrupt ZIP entry: " + bad)
        names = set(archive.namelist())
        if any(name.startswith("javax/") for name in names):
            fail("API stub classes packaged in JAR")
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
        if "me.holojava.fullgame.FullGameMIDlet" not in manifest:
            fail("manifest MIDlet class missing")
        expected_asset_root = "assets/fullgame/"
        if not any(name.startswith(expected_asset_root) for name in names):
            fail("full-game assets missing from JAR")
        for path in (ROOT / "assets/fullgame").rglob("*.png"):
            entry = path.relative_to(ROOT).as_posix()
            if entry not in names:
                fail("resource path mismatch: " + entry)
        for path in (ROOT / "assets/fullgame").rglob("*.mid"):
            entry = path.relative_to(ROOT).as_posix()
            if entry not in names:
                fail("audio resource path mismatch: " + entry)
        if "me/holojava/fullgame/FullGameMIDlet.class" not in names:
            fail("MIDlet class missing from JAR")
        collab_entry = "data/wiki/collabs.csv"
        if collab_entry not in names:
            fail("collab catalog missing from JAR: " + collab_entry)
        jar_collabs = archive.read(collab_entry)
        src_collabs = (ROOT / "data/wiki/collabs.csv").read_bytes()
        if jar_collabs != src_collabs:
            fail("collab catalog in JAR differs from data/wiki/collabs.csv")
    jad_size = None
    for line in JAD.read_text().splitlines():
        if line.startswith("MIDlet-Jar-Size:"):
            jad_size = int(line.split(":", 1)[1].strip())
    if jad_size != jar_size:
        fail("JAD size %s != JAR size %s" % (jad_size, jar_size))

    with (ROOT / "reports/fullgame_asset_manifest.csv").open(newline="") as handle:
        manifest_rows = list(csv.DictReader(handle))
    if len(manifest_rows) != 188:
        fail("expected 188 asset manifest rows, got %d" % len(manifest_rows))
    for row in manifest_rows:
        if not (ROOT / row["project_path"]).exists():
            fail("asset manifest path missing: " + row["project_path"])

    expected_counts = {
        "characters.csv": 47,
        "weapons.csv": 94,
        "items.csv": 54,
        "stages.csv": 10,
        "bosses.csv": 15,
        "modes.csv": 4,
        "collabs.csv": 30,
    }
    wiki_tables = {}
    for name, count in expected_counts.items():
        path = ROOT / "data/wiki" / name
        with path.open(newline="") as handle:
            rows = list(csv.DictReader(handle))
        if len(rows) != count:
            fail("%s row count %d != %d" % (name, len(rows), count))
        wiki_tables[name] = rows
    weapon_names = {row["name"] for row in wiki_tables["weapons.csv"]}
    main_count = sum(1 for row in wiki_tables["weapons.csv"] if row["category"] == "main")
    if main_count != 47:
        fail("wiki main weapon count %d != 47" % main_count)
    for row in wiki_tables["characters.csv"]:
        if row["main_weapon"] not in weapon_names:
            fail("character main weapon unresolved: " + row["name"])
    for row in wiki_tables["collabs.csv"]:
        if row["type"] == "collab" and (row["weapon_a"] not in weapon_names or row["weapon_b"] not in weapon_names):
            fail("collab prerequisite unresolved: " + row["name"])
    seen_encounters = set()
    for row in wiki_tables["bosses.csv"]:
        if row["scheduled_time_seconds"].isdigit():
            key = (row["stage_id"], row["scheduled_time_seconds"])
            if key in seen_encounters:
                fail("duplicate documented boss encounter: %s" % (key,))
            seen_encounters.add(key)

    # Compile and execute the Java catalog validator against the pinned stubs.
    with tempfile.TemporaryDirectory(prefix="holojava-catalog-") as temp:
        temp_path = Path(temp)
        check = temp_path / "CatalogCheck.java"
        check.write_text(
            "package me.holojava.fullgame;\n"
            "public class CatalogCheck { public static void main(String[] a) {"
            " String e=ContentCatalog.validate(); if(e!=null) throw new RuntimeException(e);"
            " System.out.println(ContentCatalog.DOCUMENTED_CHARACTER_COUNT + \":\" +"
            " ContentCatalog.DOCUMENTED_WEAPON_COUNT + \":\" + ContentCatalog.DOCUMENTED_ITEM_COUNT"
            " + \":\" + ContentCatalog.COLLABS.length); } }\n",
            encoding="utf-8",
        )
        class_dir = temp_path / "classes"
        class_dir.mkdir()
        classpath = str(ROOT / "deps/java-me/cldcapi11-2.0.4.jar") + ":" + str(ROOT / "deps/java-me/midpapi20-2.0.4.jar") + ":" + str(ROOT / "build/java-me-fullgame/classes")
        sources = [
            str(ROOT / "src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java"),
            str(ROOT / "src/java-me-fullgame/me/holojava/fullgame/MathUtil.java"),
            str(check),
        ]
        subprocess.run(["javac", "--release", "8", "-cp", classpath, "-d", str(class_dir), *sources], check=True)
        result = subprocess.run(["java", "-cp", str(class_dir) + ":" + classpath, "me.holojava.fullgame.CatalogCheck"], check=True, text=True, capture_output=True)
        print("catalog:", result.stdout.strip())
        parts = result.stdout.strip().split(":")
        if len(parts) != 4:
            fail("unexpected catalog check output: " + result.stdout.strip())
        if int(parts[3]) != len(wiki_tables["collabs.csv"]):
            fail("runtime COLLABS %s != collabs.csv rows %d" % (parts[3], len(wiki_tables["collabs.csv"])))

    print("PASS: JAR=%d bytes, assets=%d, wiki rows verified" % (jar_size, len(manifest_rows)))


if __name__ == "__main__":
    main()
