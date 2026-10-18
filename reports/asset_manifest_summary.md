# Asset Manifest Summary

- Source root: `/home/antomay/Projects/HoloCure_Unpacked`
- Generated (UTC): `2026-10-17T22:56:54.092540+00:00`
- Physical files: `34422`
- Physical size (bytes): `463584164`
- Approximate size (GB): `0.432`
- Unique SHA-256 hashes: `8124`
- Image files with dimensions: `33494`
- Audio files counted: `380`
- Audio duration (seconds): `3334.965`
- Entry errors: `0`

## Counts by Category

| Category | Count |
|---|---:|
| audio | 380 |
| binary_analysis | 6 |
| font | 46 |
| image_embedded | 26 |
| index_metadata | 36 |
| mask | 839 |
| metadata_or_other | 1 |
| shader | 480 |
| sprite | 8121 |
| strings | 2 |
| texture_group | 8176 |
| texture_page | 16302 |
| tileset | 7 |

## Counts by File Kind

| File Kind | Count |
|---|---:|
| derived_extracted_resource | 34379 |
| derived_text_export | 1 |
| metadata_index | 41 |
| source_or_unknown | 1 |

## Counts by Extension

| Extension | Count |
|---|---:|
| `.bin` | 96 |
| `.csv` | 23 |
| `.json` | 1 |
| `.ogg` | 15 |
| `.png` | 33494 |
| `.txt` | 428 |
| `.wav` | 365 |

## Counting Methodology

1. Every regular file under the extraction root was enumerated recursively.
2. Physical physical-file counts include derived exports, text reports, indexes, and metadata.
3. Duplicate content is counted physically and identified by SHA-256 hash.
4. Logical game resources are tracked separately in indexes and documentation.
5. Image dimensions were parsed with Pillow.
6. Audio metadata was obtained with ffprobe when available.
