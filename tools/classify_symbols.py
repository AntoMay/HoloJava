#!/usr/bin/env python3
"""Classify native GameMaker symbol strings from HoloCure extraction output."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Dict, List

DEFAULT_UNPACKED = Path("/home/antomay/Projects/HoloCure_Unpacked")

LOGICAL_SYSTEM_ORDER = [
    "enemy_boss",
    "weapon_projectile",
    "enemy_boss_behavior",  # reserved for context-based labels
    "xp_level_progression",
    "stage_spawn",
    "collision_damage",
    "player_character",
    "item_upgrade",
    "audio_visual",
    "ui_menu_state",
    "save_meta",
    "manager_init_utility",
    "input",
    "engine_runtime",
    "unknown",
]

KEYWORD_PRIORITY = [
    ("camera_cinematic", ["camera", "view", "cinematic", "shake"]),
    ("xp_level_progression", ["levelup", "level_up", "fandomexp", "experience", "expbar", "exp_bar"]),
    ("weapon_projectile", ["weapon", "projectile", "bullet", "missile", "laser", "attack", "slash", "hitbox", "shot", "bomb", "slash", "skill"]),
    ("enemy_boss", ["boss", "enemy", "mob", "foe", "mini", "miniboss"]),
    ("stage_spawn", ["spawn", "wave", "stage", "room", "biome", "loot", "chest"]),
    ("collision_damage", ["collision", "damage", "hurt", "knock", "hit", "death", "die", "health", "hp", "armor", "shield"]),
    ("player_character", ["player", "character", "char_", "unlock", "select", "charselect"]),
    ("item_upgrade", ["item", "upgrade", "shop", "stamp", "tome", "charm", "crate", "box", "holocoin", "holochaos", "pickup", "super"]),
    ("audio_visual", ["bgm", "snd", "sound", "audio", "shader", "light", "glr", "particle", "effect", "draw", "sprite", "texture", "scribble", "flash", "camera", "view"]),
    ("ui_menu_state", ["ui", "menu", "button", "tooltip", "hud", "pause", "title", "popup", "text", "typewriter", "dialog", "select", "screen", "window", "gameover", "game_over", "mainmenu"]),
    ("save_meta", ["save", "settings", "option", "config", "profile", "achievement", "firebase", "leaderboard", "account", "steam", "prefs"]),
    ("input", ["input", "keyboard", "gamepad", "joystick", "controller", "button", "axis", "touch"]),
    ("manager_init_utility", ["manager", "controller", "init", "main", "utility", "math", "array", "string", "struct", "diag", "scribble", "firebase", "http", "web", "server"]),
    ("engine_runtime", ["room", "object", "event", "script", "thread", "value", "token", "binding", "global", "gml"]),
]


def classify_symbol(symbol: str) -> str:
    lower = symbol.lower()
    for category, keywords in KEYWORD_PRIORITY:
        if any(keyword in lower for keyword in keywords):
            return category
    return "unknown"


def read_lines(path: Path) -> List[str]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        return [line.strip() for line in handle if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser(description="Classify HoloCure native symbols")
    parser.add_argument("--unpacked", type=Path, default=DEFAULT_UNPACKED)
    parser.add_argument("--output", type=Path,
                        default=Path(__file__).resolve().parents[1] / "reports")
    args = parser.parse_args()
    root = args.unpacked.resolve()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)

    source_files = {
        "gml_symbols.txt": root / "binary_logic" / "gml_symbols.txt",
        "gml_scripts.txt": root / "binary_logic" / "gml_scripts.txt",
        "gml_globalscripts.txt": root / "binary_logic" / "gml_globalscripts.txt",
        "gml_objects.txt": root / "binary_logic" / "gml_objects.txt",
        "gml_rooms.txt": root / "binary_logic" / "gml_rooms.txt",
    }

    rows: List[Dict[str, str]] = []
    for source_name, path in source_files.items():
        for symbol in read_lines(path):
            rows.append({
                "source_file": source_name,
                "symbol": symbol,
                "primary_category": classify_symbol(symbol),
            })

    classification_path = out / "symbol_classification.csv"
    with classification_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["source_file", "symbol", "primary_category"])
        writer.writeheader()
        writer.writerows(rows)

    summary: Dict[str, Dict[str, int]] = {source: {} for source in source_files}
    for row in rows:
        summary[row["source_file"]][row["primary_category"]] = (
            summary[row["source_file"]].get(row["primary_category"], 0) + 1
        )

    overall = {}
    for row in rows:
        overall[row["primary_category"]] = overall.get(row["primary_category"], 0) + 1

    # Save top symbols for each system to make the report useful.
    top_by_category: Dict[str, List[str]] = {}
    for category in sorted(overall):
        top_by_category[category] = [
            row["symbol"]
            for row in rows
            if row["primary_category"] == category
        ][:25]

    json_payload = {
        "total_symbols": len(rows),
        "sources": {name: len(read_lines(path)) for name, path in source_files.items() if path.exists()},
        "overall_counts": dict(sorted(overall.items())),
        "counts_by_source": {
            source: dict(sorted(counter.items())) for source, counter in summary.items()
        },
        "top_symbols_by_category": top_by_category,
    }
    with (out / "symbol_classification.json").open("w", encoding="utf-8") as handle:
        json.dump(json_payload, handle, indent=2, ensure_ascii=False)

    lines = [
        "# Symbol Classification Summary",
        "",
        f"- Total symbols classified: `{len(rows)}`",
        "",
        "## Overall Counts",
        "",
        "| Category | Count |",
        "|---|---:|",
    ]
    for category, count in sorted(overall.items(), key=lambda item: (-item[1], item[0])):
        lines.append(f"| {category} | {count} |")
    lines.extend(["", "## Counts by Source", ""])
    for source, counter in summary.items():
        lines.append(f"### {source}")
        lines.append("")
        lines.append("| Category | Count |")
        lines.append("|---|---:|")
        for category, count in sorted(counter.items(), key=lambda item: (-item[1], item[0])):
            lines.append(f"| {category} | {count} |")
        lines.append("")
    lines.extend(["## Top Symbols by Category", ""])
    for category, symbols in top_by_category.items():
        lines.append(f"### {category}")
        lines.extend(f"- `{symbol}`" for symbol in symbols)
        lines.append("")

    with (out / "symbol_classification_summary.md").open("w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"Symbols classified: {len(rows)}")
    print(f"Wrote: {classification_path}")
    print(f"Wrote: {out / 'symbol_classification.json'}")
    print(f"Wrote: {out / 'symbol_classification_summary.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

