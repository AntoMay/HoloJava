#!/usr/bin/env python3
"""Cross-reference GameMaker indexes with native HoloCure symbols and strings."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Dict, List, Set

DEFAULT_UNPACKED = Path("/home/antomay/Projects/HoloCure_Unpacked")


def read_index_names(path: Path) -> List[str]:
    if not path.exists():
        return []
    lines = [line.strip() for line in path.read_text(encoding="utf-8", errors="replace").splitlines()]
    names = []
    for line in lines:
        if not line or line.startswith("Count:"):
            continue
        names.append(line)
    return names


def native_object_names(path: Path) -> Set[str]:
    names: Set[str] = set()
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = re.match(r"^gml_Object_(.+?)_(?:Create|Destroy|Alarm|Step|Collision|Keyboard|Mouse|Other|Draw|KeyPress|KeyRelease|Trigger|CleanUp|Gesture|PreCreate)(?:_\d+)?$", line)
        if match:
            names.add(match.group(1))
    return names


def native_room_names(path: Path) -> Set[str]:
    names: Set[str] = set()
    event_suffixes = ("Create", "PreCreate", "Destroy", "Step", "Draw", "Other", "Alarm")
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = re.match(r"^gml_Room(?:CC)?_(.+)$", line)
        if match:
            candidate = match.group(1)
            for suffix in event_suffixes:
                suffix_pattern = re.compile(rf"^(.+?)_(?:\d+_)?{suffix}$", re.IGNORECASE)
                suffix_match = suffix_pattern.match(candidate)
                if suffix_match:
                    candidate = suffix_match.group(1)
            names.add(candidate)
    return names


def find_exact_names_in_text(collection: List[str], text: str) -> Set[str]:
    found: Set[str] = set()
    pattern = re.compile("|".join(re.escape(name) for name in sorted(collection, key=len, reverse=True)))
    for match in pattern.finditer(text):
        found.add(match.group(0))
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description="Cross-reference HoloCure symbols and indexes")
    parser.add_argument("--unpacked", type=Path, default=DEFAULT_UNPACKED)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "reports")
    args = parser.parse_args()

    root = args.unpacked.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)

    indexes = {
        "GameObjects": root / "index" / "GameObjects.txt",
        "Rooms": root / "index" / "Rooms.txt",
        "Sprites": root / "index" / "Sprites.txt",
        "Sounds": root / "index" / "Sounds.txt",
        "Scripts": root / "index" / "Scripts.txt",
        "GlobalInitScripts": root / "index" / "GlobalInitScripts.txt",
    }
    index_names = {name: read_index_names(path) for name, path in indexes.items()}

    gm_objects = index_names["GameObjects"]
    gm_rooms = index_names["Rooms"]
    gm_sprites = index_names["Sprites"]
    gm_sounds = index_names["Sounds"]

    native_object_path = root / "binary_logic" / "gml_objects.txt"
    native_room_path = root / "binary_logic" / "gml_rooms.txt"
    binary_symbols_text = (root / "binary_logic" / "gml_symbols.txt").read_text(encoding="utf-8", errors="replace")
    binary_strings_path = root / "binary_strings" / "HoloCure.exe.strings.txt"

    native_objects = native_object_names(native_object_path)
    native_rooms = native_room_names(native_room_path)

    object_exact_overlap = sorted(set(gm_objects) & native_objects)
    object_gm_only = sorted(set(gm_objects) - native_objects)
    object_native_only = sorted(native_objects - set(gm_objects))

    room_exact_overlap = sorted(set(gm_rooms) & native_rooms)
    room_gm_only = sorted(set(gm_rooms) - native_rooms)
    room_native_only = sorted(native_rooms - set(gm_rooms))

    # Targeted strings scan.
    found_sounds: Set[str] = set()
    found_sprites: Set[str] = set()
    if binary_strings_path.exists():
        text = binary_strings_path.read_text(encoding="utf-8", errors="replace")
        found_sounds = find_exact_names_in_text(gm_sounds, text)
        found_sprites = find_exact_names_in_text(gm_sprites, text)

    candidate_objects = {
        "player_character": [name for name in gm_objects if "player" in name.lower() or "char" in name.lower()],
        "enemies_bosses": [name for name in gm_objects if any(token in name.lower() for token in ("enemy", "boss", "mob", "minion", "foe"))],
        "projectiles_attacks": [name for name in gm_objects if any(token in name.lower() for token in ("projectile", "bullet", "attack", "weapon", "laser", "shot", "missile"))],
        "items_progression": [name for name in gm_objects if any(token in name.lower() for token in ("item", "upgrade", "pickup", "chest", "crate", "shop", "tome"))],
        "managers_controllers": [name for name in gm_objects if any(token in name.lower() for token in ("manager", "controller", "input", "game", "ui", "text"))],
    }

    detailed_files = {
        "Objects_Detail": root / "index" / "Objects_Detail.txt",
        "Rooms_Detail": root / "index" / "Rooms_Detail.txt",
        "Variables_Detail": root / "index" / "Variables_Detail.txt",
    }

    lines = [
        "# Cross-Reference Report",
        "",
        "## GameMaker Index Counts",
        "",
        "| Index | Count |",
        "|---|---:|",
    ]
    for name, names in index_names.items():
        lines.append(f"| {name} | {len(names)} |")

    lines.extend([
        "",
        "## Native Object Name Coverage",
        "",
        f"- GameMaker object names: {len(gm_objects)}",
        f"- Native object/event base names: {len(native_objects)}",
        f"- Exact overlap: {len(object_exact_overlap)}",
        f"- GameMaker-only: {len(object_gm_only)}",
        f"- Native-only: {len(object_native_only)}",
        "",
        "### Exact Overlap Examples",
    ])
    lines.extend(f"- `{name}`" for name in object_exact_overlap[:50])

    lines.extend([
        "",
        "## Native Room Name Coverage",
        "",
        f"- GameMaker room names: {len(gm_rooms)}",
        f"- Native room names: {len(native_rooms)}",
        f"- Exact overlap: {len(room_exact_overlap)}",
        f"- GameMaker-only: {len(room_gm_only)}",
        f"- Native-only: {len(room_native_only)}",
        "",
        "### Exact Room Overlap",
    ])
    lines.extend(f"- `{name}`" for name in room_exact_overlap)
    lines.extend([
        "",
        "### Native Rooms Without GameMaker Index Match",
    ])
    lines.extend(f"- `{name}`" for name in room_native_only[:50])

    lines.extend([
        "",
        "## Strong Candidate Object Groups",
        "",
    ])
    for group, names in candidate_objects.items():
        lines.append(f"### {group}")
        lines.append("")
        lines.append(f"Count: {len(names)}")
        lines.extend(f"- `{name}`" for name in names[:60])
        lines.append("")

    lines.extend([
        "## Native String References",
        "",
        f"- Sound names found in `HoloCure.exe.strings.txt`: {len(found_sounds)} of {len(gm_sounds)}",
        f"- Sprite names found in `HoloCure.exe.strings.txt`: {len(found_sprites)} of {len(gm_sprites)}",
        "",
        "### Sound Reference Examples",
    ])
    lines.extend(f"- `{name}`" for name in sorted(found_sounds)[:60])
    lines.extend([
        "",
        "### Sprite Reference Examples (Top Sorted)",
    ])
    for name in sorted(found_sprites)[:60]:
        lines.append(f"- `{name}`")

    lines.extend([
        "",
        "## Detailed Export Status",
        "",
    ])
    for name, path in detailed_files.items():
        if path.exists():
            line_count = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
            lines.append(f"- `{name}`: present, {line_count} lines, size {path.stat().st_size} bytes")
        else:
            lines.append(f"- `{name}`: absent")

    lines.extend([
        "",
        "## Interpretation Notes",
        "",
        "1. Native object names and GameMaker object names can differ by casing or generated-name stripping.",
        "2. The overlap counts are exact-string measurements, not behavioral proof.",
        "3. Room names prefixed with `RoomCC_` are likely room creation code variants.",
        "4. Sound/sprite string hits indicate names embedded in native code or data, not necessarily runtime references.",
        "5. Native symbol popularity and exact event mapping must be validated against observation before gameplay implementation.",
    ])

    report_path = output / "crossref_report.md"
    with report_path.open("w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    # Write JSON companion for programmatic reuse.
    payload = {
        "index_counts": {name: len(names) for name, names in index_names.items()},
        "native_object_count": len(native_objects),
        "native_room_count": len(native_rooms),
        "object_overlap_count": len(object_exact_overlap),
        "room_overlap_count": len(room_exact_overlap),
        "sound_reference_count": len(found_sounds),
        "sprite_reference_count": len(found_sprites),
        "candidate_objects": candidate_objects,
        "object_overlap": object_exact_overlap,
        "object_gm_only": object_gm_only,
        "object_native_only": object_native_only,
        "room_overlap": room_exact_overlap,
        "room_gm_only": room_gm_only,
        "room_native_only": room_native_only,
        "sound_references": sorted(found_sounds),
        "sprite_references": sorted(found_sprites),
        "detailed_export_status": {
            name: {
                "present": path.exists(),
                "line_count": len(path.read_text(encoding="utf-8", errors="replace").splitlines()) if path.exists() else 0,
            }
            for name, path in detailed_files.items()
        },
    }
    with (output / "crossref_report.json").open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)

    print(f"Wrote: {report_path}")
    print(f"Wrote: {output / 'crossref_report.json'}")
    print(f"GameMaker objects: {len(gm_objects)}")
    print(f"Native object bases: {len(native_objects)}")
    print(f"Exact object overlap: {len(object_exact_overlap)}")
    print(f"Sound references: {len(found_sounds)}/{len(gm_sounds)}")
    print(f"Sprite references: {len(found_sprites)}/{len(gm_sprites)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

