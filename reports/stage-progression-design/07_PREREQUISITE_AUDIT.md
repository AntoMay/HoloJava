# HoloJava — Tahap 3C: Character Catalog & Settings Prerequisite Audit

## Evidence base
`WikiCatalogData.java`, `ContentCatalog.java`, `data/wiki/characters.csv`, `tools/build_fullgame.sh`, reference JAR (`unzip -l`), `javap -c -p` on extracted `SaveStore.class`.

---

## 1. Character Catalog — **UNRESOLVED (4 sub-blockers)**

| # | Fact (verified) | Evidence |
|---|-----------------|----------|
| 1.1 | `buildCharacters()` is a stub returning `new CharacterDef[0]` | `WikiCatalogData.java:165-167` |
| 1.2 | `CHARACTERS` binds directly to it — sole initialization path | `ContentCatalog.java:168` |
| 1.3 | CSV data exists: **47 data rows**, 11 columns, no trailing newline on last row | `data/wiki/characters.csv` |
| 1.4 | Quote-aware parser **exists** (`parseCsvLine`); file contains 0 `""` escapes, so its simple toggle suffices | `WikiCatalogData.java:85-102` |

| # | Blocker | Evidence |
|---|---------|----------|
| **B1** | **CSV is not packaged into the JAR.** Build zips only `META-INF assets me`. Reference JAR contains **0** `data/` entries. `getResourceAsStream("/data/wiki/characters.csv")` returns `null` at runtime | `tools/build_fullgame.sh:32`; `unzip -l` |
| **B2** | CSV has 11 columns; `CharacterDef` has 14 fields. Missing: `skill`, `playable`, `assetGroup`, `fallbackArt` — need defaults | `ContentCatalog.java:48-63` |
| **B3** | CSV `main_weapon` holds **display names** ("Paint Brush"); `weapon(id)` matches **snake_case ids** ("trident_thrust"). `validate()` requires a match per character | `ContentCatalog.java:410`; CSV col 3 |
| **B4** | `validate()` rejects any character with `playable == false` | `ContentCatalog.java:411` |

**B3 severity**: 5 of 47 names map by meaning; 42 have no id in `BASE_WEAPONS` (20 entries, `ContentCatalog.java:172-195`). A name→id table does not exist.

**Smallest viable change (not implemented)**: hardcode a `CharacterDef[]` literal beside `BASE_WEAPONS`, reusing the existing `character(...)` factory. Requires a 47-entry name→id mapping and `playable=true` on every entry. Cost: 1 file, no build-script change, no runtime resource dependency. Reading the CSV instead would require B1+B2+B3 to be solved first (3 files).

**Not assumed**: the 47 CSV rows are *not* proven to match the reference JAR's roster or any legacy save.

---

## 2. Settings Byte — Reconciled via Bytecode

| Operation | Reference JAR (`data[5]`) | Current source (`data[47]`) |
|-----------|----------------------------|------------------------------|
| `hardcore()` | `& 1` → bit 0 | `& 1` → bit 0 |
| `setHardcore` | `\|= 1` / `&= -2` | `\|= 1` / `&= ~1` |
| `selectedCharacter()` | **`& 15` (0x0F) → bits 0-3** | **`& 0x3F` → bits 0-5** |
| `setSelectedCharacter` | `(d & 240) \| (v & 15)` — preserves bits 4-7 | `(d & 0xC0) \| (v & 0x3F)` — preserves bits 6-7 |

**Correction to prior reports**: `01_BYTE_MAPPING_EVIDENCE.md` §4.3 and `04_CRITICAL_VALIDATION.md` §3.5 assert "0x3F on both sides". **Wrong** — legacy uses `0x0F`. Bytecode is authoritative.

**Does `setSelectedCharacter()` overwrite the hardcore bit?** **Yes — in both implementations.** Bit 0 is inside the character mask in each. This is a pre-existing design flaw carried over from the legacy JAR, **not** a regression introduced by the current source.

**Verbatim settings copy — verdict: CONDITIONALLY SAFE.**
- Legacy only ever writes bits 0-3, so bits 4-5 are always `0` in a JAR-written record.
- Char index therefore lands at ≤15, well inside the source's 6-bit field. Hardcore (bit 0) and bits 6-7 map identically.
- **UNRESOLVED**: the proposed validity rule `(d[5] & 0x3F) < 5` validates 6 bits while the legacy range is 4 bits. Arbitrary 6-byte records not produced by the reference JAR are **not proven** safe to copy verbatim.

---

## Recommendation

Both blockers remain open. Neither is resolvable by save/progression work.

1. **Settings** — migrate verbatim only for records validated as JAR-produced; otherwise treat the byte as unknown and keep defaults.
2. **Character catalog** — must land first. `CHARACTERS.length == 0` makes `isCompleted`/`setCompleted` reject every index, so no progression feature can function regardless of `SaveStore` correctness.

Stop — awaiting review.
