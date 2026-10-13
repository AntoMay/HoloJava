#!/usr/bin/env python3
"""Build a reproducible local manifest of HoloCure extraction output."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from PIL import Image

DEFAULT_SOURCE = Path("/home/antomay/Projects/HoloCure_Unpacked")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build HoloCure asset manifest")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE,
                        help=f"Extraction root (default: {DEFAULT_SOURCE})")
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "reports",
                        help="Report output directory")
    parser.add_argument("--skip-audio-duration", action="store_true",
                        help="Do not call ffprobe for audio duration.")
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def classify_category(relative_path: Path) -> str:
    parts = relative_path.parts
    if not parts:
        return "root"
    top = parts[0].lower()
    mapping = {
        "sounds": "audio",
        "fonts": "font",
        "masks": "mask",
        "embedded_textures": "image_embedded",
        "shaders": "shader",
        "sprites": "sprite",
        "tilesets": "tileset",
        "textures": "texture_page",
        "textures_grouped": "texture_page",
        "texture_groups": "texture_group",
        "strings": "strings",
        "index": "index_metadata",
        "binary_logic": "binary_analysis",
        "binary_strings": "binary_analysis",
    }
    return mapping.get(top, "metadata_or_other")


def classify_file_kind(relative_path: Path) -> str:
    relative = relative_path.as_posix()
    if relative.startswith(("index/", "binary_logic/")):
        return "metadata_index"
    if relative.startswith("binary_strings/"):
        return "derived_text_export"
    if relative.startswith(("strings/", "sounds/", "sprites/", "textures", "texture_groups/", "embedded_textures/", "fonts/", "masks/", "shaders/", "tilesets/", "rooms/")):
        return "derived_extracted_resource"
    if relative.startswith(("tools/", "reports/")):
        return "project_tool_or_report"
    return "source_or_unknown"


def png_dimensions(path: Path) -> Tuple[Optional[int], Optional[int], Optional[str]]:
    if path.suffix.lower() not in {".png", ".jpg", ".jpeg", ".gif", ".bmp"}:
        return None, None, None
    try:
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            return image.size[0], image.size[1], image.format
    except Exception:
        return None, None, None


def _int_or_none(value: Any) -> Optional[int]:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def audio_metadata(path: Path) -> Dict[str, Any]:
    ext = path.suffix.lower()
    if ext not in {".wav", ".ogg", ".mp3", ".flac", ".aac", ".m4a", ".mid", ".midi"}:
        return {}
    command = [
        "ffprobe", "-v", "error",
        "-show_entries", "stream=codec_name,sample_rate,channels,duration",
        "-show_entries", "format=duration",
        "-of", "json", str(path),
    ]
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=10, check=False)
    except Exception as exc:
        return {"audio_error": str(exc)}
    if result.returncode != 0:
        return {"audio_error": result.stderr.strip()[:300]}
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError:
        return {"audio_error": "invalid ffprobe JSON output"}
    stream = payload.get("streams", [{}])[0] if payload.get("streams") else {}
    duration = stream.get("duration")
    if duration is None:
        duration = payload.get("format", {}).get("duration")
    try:
        duration_value = float(duration) if duration is not None else None
    except (TypeError, ValueError):
        duration_value = None
    return {
        "audio_codec": stream.get("codec_name"),
        "audio_sample_rate": _int_or_none(stream.get("sample_rate")),
        "audio_channels": _int_or_none(stream.get("channels")),
        "audio_duration_seconds": duration_value,
    }


def make_manifest_entries(source_root: Path, skip_audio_duration: bool) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    files = sorted((p for p in source_root.rglob("*") if p.is_file()),
                   key=lambda item: item.relative_to(source_root).as_posix())
    entries: List[Dict[str, Any]] = []
    errors: List[Dict[str, Any]] = []
    total = len(files)

    for index, path in enumerate(files, 1):
        relative = path.relative_to(source_root)
        try:
            stat = path.stat()
            digest = sha256_file(path)
            width, height, image_format = png_dimensions(path)
        except Exception as exc:
            errors.append({"relative_path": relative.as_posix(), "error": str(exc)})
            continue

        entry = {
            "relative_path": relative.as_posix(),
            "category": classify_category(relative),
            "file_kind": classify_file_kind(relative),
            "extension": path.suffix.lower(),
            "size_bytes": stat.st_size,
            "sha256": digest,
            "image_width": width,
            "image_height": height,
            "image_format": image_format,
            "audio_codec": None,
            "audio_sample_rate": None,
            "audio_channels": None,
            "audio_duration_seconds": None,
        }

        if not skip_audio_duration:
            audio = audio_metadata(path)
            if "audio_error" in audio:
                errors.append({"relative_path": relative.as_posix(), "error": audio.pop("audio_error")})
            entry.update(audio)

        entries.append(entry)

        if index % 1000 == 0:
            print(f"Processed {index}/{total}", flush=True)

    counts = Counter(entry["sha256"] for entry in entries)
    for entry in entries:
        entry["hash_occurrence_count"] = counts[entry["sha256"]]
        entry["is_duplicate_instance"] = counts[entry["sha256"]] > 1

    return entries, errors


def build_summary(entries: List[Dict[str, Any]], errors: List[Dict[str, Any]], source_root: Path) -> Dict[str, Any]:
    by_category: Dict[str, int] = {}
    by_extension: Dict[str, int] = {}
    by_file_kind: Dict[str, int] = {}
    total_bytes = 0
    image_count = 0
    audio_count = 0
    audio_duration = 0.0
    count_summary = Counter(entry["sha256"] for entry in entries)

    for entry in entries:
        total_bytes += entry["size_bytes"]
        by_category[entry["category"]] = by_category.get(entry["category"], 0) + 1
        by_extension[entry["extension"]] = by_extension.get(entry["extension"], 0) + 1
        by_file_kind[entry["file_kind"]] = by_file_kind.get(entry["file_kind"], 0) + 1
        if entry["image_width"] is not None:
            image_count += 1
        if entry["extension"] in {".wav", ".ogg", ".mp3", ".flac", ".aac", ".m4a", ".mid", ".midi"}:
            audio_count += 1
        if isinstance(entry["audio_duration_seconds"], (int, float)):
            audio_duration += float(entry["audio_duration_seconds"])

    return {
        "source_root": str(source_root),
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "physical_file_count": len(entries),
        "physical_size_bytes": total_bytes,
        "physical_size_gb_approx": round(total_bytes / (1024 ** 3), 3),
        "unique_sha256_count": len(count_summary),
        "duplicate_hash_groups": sum(1 for value in count_summary.values() if value > 1),
        "files_with_same_hash_count": sum(1 for entry in entries if entry["hash_occurrence_count"] > 1),
        "image_count_with_dimensions": image_count,
        "audio_file_count": audio_count,
        "audio_duration_seconds_approx": round(audio_duration, 3),
        "error_count": len(errors),
        "counts_by_category": dict(sorted(by_category.items())),
        "counts_by_file_kind": dict(sorted(by_file_kind.items())),
        "counts_by_extension": dict(sorted(by_extension.items())),
    }


def write_reports(entries: List[Dict[str, Any]], errors: List[Dict[str, Any]], source_root: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    summary = build_summary(entries, errors, source_root)
    manifest_path = output_dir / "asset_manifest.json"
    csv_path = output_dir / "asset_manifest.csv"
    summary_path = output_dir / "asset_manifest_summary.md"

    with manifest_path.open("w", encoding="utf-8") as handle:
        json.dump({"summary": summary, "errors": errors, "entries": entries},
                  handle, indent=2, ensure_ascii=False)

    fieldnames = list(entries[0].keys()) if entries else []
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(entries)

    lines = [
        "# Asset Manifest Summary",
        "",
        f"- Source root: `{summary['source_root']}`",
        f"- Generated (UTC): `{summary['generated_at_utc']}`",
        f"- Physical files: `{summary['physical_file_count']}`",
        f"- Physical size (bytes): `{summary['physical_size_bytes']}`",
        f"- Approximate size (GB): `{summary['physical_size_gb_approx']}`",
        f"- Unique SHA-256 hashes: `{summary['unique_sha256_count']}`",
        f"- Image files with dimensions: `{summary['image_count_with_dimensions']}`",
        f"- Audio files counted: `{summary['audio_file_count']}`",
        f"- Audio duration (seconds): `{summary['audio_duration_seconds_approx']}`",
        f"- Entry errors: `{summary['error_count']}`",
        "",
        "## Counts by Category",
        "",
        "| Category | Count |",
        "|---|---:|",
    ]
    for key, value in summary["counts_by_category"].items():
        lines.append(f"| {key} | {value} |")
    lines.extend(["", "## Counts by File Kind", "", "| File Kind | Count |", "|---|---:|"])
    for key, value in summary["counts_by_file_kind"].items():
        lines.append(f"| {key} | {value} |")
    lines.extend(["", "## Counts by Extension", "", "| Extension | Count |", "|---|---:|"])
    for key, value in summary["counts_by_extension"].items():
        lines.append(f"| `{key or '(none)'}` | {value} |")
    if errors:
        lines.extend(["", "## Errors", ""])
        for error in errors:
            lines.append(f"- `{error['relative_path']}`: {error['error']}")
    lines.extend([
        "",
        "## Counting Methodology",
        "",
        "1. Every regular file under the extraction root was enumerated recursively.",
        "2. Physical physical-file counts include derived exports, text reports, indexes, and metadata.",
        "3. Duplicate content is counted physically and identified by SHA-256 hash.",
        "4. Logical game resources are tracked separately in indexes and documentation.",
        "5. Image dimensions were parsed with Pillow.",
        "6. Audio metadata was obtained with ffprobe when available.",
    ])
    with summary_path.open("w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"Wrote: {manifest_path}")
    print(f"Wrote: {csv_path}")
    print(f"Wrote: {summary_path}")
    print(json.dumps(summary, indent=2, ensure_ascii=False))


def main() -> int:
    args = parse_args()
    source_root = args.source.expanduser().resolve()
    output_dir = args.output_dir.expanduser().resolve()
    if not source_root.is_dir():
        raise SystemExit(f"Source directory does not exist: {source_root}")
    print(f"Source: {source_root}")
    print(f"Output: {output_dir}")
    print("Scanning files...", flush=True)
    entries, errors = make_manifest_entries(source_root, args.skip_audio_duration)
    print("Writing reports...", flush=True)
    write_reports(entries, errors, source_root, output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
