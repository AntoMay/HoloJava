#!/usr/bin/env python3
"""Re-transcribe HoloCure BGM OGGs to MIDI using basic-pitch with higher fidelity.

Outputs to assets/fullgame/audio/, matching the stage mapping in BgmAudio.java.
Arena Hutan Sawit (stage 10) uses a custom MP3 from Downloads.

Run from repo root with the basic-pitch venv:
  /home/antomay/Projects/_tools/basic-pitch/.venv311/bin/python tools/transcribe_bgm.py
"""

import sys
from pathlib import Path

# Ensure basic-pitch venv is on path
VENV_PYTHON = Path("/home/antomay/Projects/_tools/basic-pitch/.venv311/bin/python")
if not VENV_PYTHON.exists():
    VENV_PYTHON = Path("/home/antomay/Projects/_tools/basic-pitch/.venv/bin/python")

SRC_DIR = Path("/home/antomay/Projects/HoloCure_Unpacked/sounds/bgm")
OUT_DIR = Path("/home/antomay/Projects/HoloJava/assets/fullgame/audio")
SAWIT_MP3 = Path("/home/antomay/Downloads/M.B.G (Mas Bahlil Ganteng)  Full Lirik Lagu Viral 2026.mp3")

# Stage index -> (source filename, output filename)
# Matches BgmAudio.musicFor() order
TRACKS = [
    (0, "bgm_suspect.ogg",          "bgm_suspect.mid"),
    (1, "bgm_candygoround.ogg",     "bgm_candygoround.mid"),
    (2, "bgm_halloweennight.ogg",   "bgm_halloweennight.mid"),
    (3, "bgm_identity.ogg",         "bgm_identity.mid"),
    (4, "bgm_fantasykiseki.ogg",    "bgm_fantasykiseki.mid"),
    (5, "bgm_adtruck.ogg",          "bgm_adtruck.mid"),
    (6, "bgm_stardust.ogg",         "bgm_stardust.mid"),
    (7, "bgm_mythortreat.ogg",      "bgm_mythortreat.mid"),
    (8, "bgm_bokuragakurukara.ogg", "bgm_bokuragakurukara.mid"),
    (9, "bgm_concert.ogg",          "bgm_concert.mid"),
    # Stage 10 (Arena Hutan Sawit) - custom MP3
    (10, None,                      "bgm_palm_forest.mid"),
]

# Menu theme
MENU_TRACK = ("bgm_SSS.ogg", "bgm_SSS.mid")

# Higher fidelity settings for basic-pitch — tuned for ~150 KB/track ceiling
# - onset_threshold: lower = more sensitive = more notes (default 0.5)
# - frame_threshold: lower = more frames = more detail (default 0.3)
# - minimum_note_length: shorter = more short notes (default 127.7 ms)
# - minimum_frequency / maximum_frequency: wider range = more notes
# - multiple_pitch_bends: True adds pitch bend events (larger MIDI)
# - melodia_trick: True enables improved pitch estimation
TRANSCRIBE_KWARGS = {
    "onset_threshold": 0.42,           # slightly less sensitive = fewer notes
    "frame_threshold": 0.28,           # slightly less frame detail
    "minimum_note_length": 40.0,       # filter very short notes
    "minimum_frequency": 35.0,         # slightly higher bass floor
    "maximum_frequency": 7000.0,       # slightly lower treble ceiling
    "multiple_pitch_bends": False,     # disable pitch bends (big size saver)
    "melodia_trick": True,             # keep improved pitch estimation
    "midi_tempo": 120.0,               # explicit tempo
}

def transcribe_one(input_path: Path, output_path: Path, **kwargs):
    """Run basic-pitch prediction and save MIDI."""
    from basic_pitch.inference import predict_and_save, Model
    from basic_pitch import ICASSP_2022_MODEL_PATH
    
    # Load the default model
    model = Model(ICASSP_2022_MODEL_PATH)
    
    predict_and_save(
        audio_path_list=[str(input_path)],
        output_directory=str(output_path.parent),
        save_midi=True,
        sonify_midi=False,
        save_model_outputs=False,
        save_notes=False,
        model_or_model_path=model,
        **kwargs
    )
    # basic-pitch names output as <input_stem>_basic_pitch.mid
    generated = output_path.parent / f"{input_path.stem}_basic_pitch.mid"
    if generated.exists():
        generated.rename(output_path)
        return output_path.stat().st_size
    else:
        # Fallback: maybe the stem has special chars, find any _basic_pitch.mid
        for f in output_path.parent.glob("*_basic_pitch.mid"):
            f.rename(output_path)
            return output_path.stat().st_size
        raise RuntimeError(f"basic-pitch did not produce {generated}")

def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Using basic-pitch venv: {VENV_PYTHON}")
    print(f"Source dir: {SRC_DIR}")
    print(f"Output dir: {OUT_DIR}")
    print(f"Settings: {TRANSCRIBE_KWARGS}")
    print()

    # Stage tracks
    for stage_idx, src_name, out_name in TRACKS:
        out_path = OUT_DIR / out_name
        if stage_idx == 10:  # Arena Hutan Sawit
            if not SAWIT_MP3.exists():
                print(f"  [SKIP] {out_name}: custom MP3 not found at {SAWIT_MP3}")
                continue
            input_path = SAWIT_MP3
            print(f"  [10] {out_name} <- {SAWIT_MP3.name} (custom)")
        else:
            input_path = SRC_DIR / src_name
            if not input_path.exists():
                print(f"  [SKIP] {out_name}: source not found at {input_path}")
                continue
            print(f"  [{stage_idx}] {out_name} <- {src_name}")

        try:
            size = transcribe_one(input_path, out_path, **TRANSCRIBE_KWARGS)
            kb = size / 1024
            status = "✓" if size <= 153600 else "⚠ OVER 150 KB"
            print(f"       -> {kb:.1f} KB {status}")
        except Exception as e:
            print(f"       !! FAILED: {e}")

    # Menu theme
    menu_src = SRC_DIR / MENU_TRACK[0]
    menu_out = OUT_DIR / MENU_TRACK[1]
    if menu_src.exists():
        print(f"  [menu] {MENU_TRACK[1]} <- {MENU_TRACK[0]}")
        try:
            size = transcribe_one(menu_src, menu_out, **TRANSCRIBE_KWARGS)
            kb = size / 1024
            status = "✓" if size <= 153600 else "⚠ OVER 150 KB"
            print(f"       -> {kb:.1f} KB {status}")
        except Exception as e:
            print(f"       !! FAILED: {e}")
    else:
        print(f"  [SKIP] menu: source not found at {menu_src}")

    print("\nDone. Verify with: sh tools/run_fullgame_tests.sh")

if __name__ == "__main__":
    main()