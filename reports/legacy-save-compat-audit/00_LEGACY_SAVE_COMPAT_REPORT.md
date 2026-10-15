# R-2 — Legacy Save Compatibility Audit (READ-ONLY)

## 1. Baseline

| Item | Value |
|------|-------|
| Git HEAD | `6acdd6a` (Stage 1 checkpoint committed) |
| Working tree | clean for tracked files |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| JAR analysis method | `javap -c -p` on an extraction in `/tmp`; reference JAR never written |

**No code, data, config, or RMS record was modified.**

---

## 2. Current Source — `SaveStore.java`

### 2.1 Format constants (`:10-16`)

| Constant | Value | Line |
|----------|-------|------|
| `STORE` | `"holojava_fullgame_v1"` | `:10` |
| `RECORD_ID` | `1` | `:11` |
| `DATA_LEN` | `48` | `:12` |
| `SETTINGS_INDEX` | `47` | `:13` |
| `LEGACY_LEN` | `6` | `:14` |

No version byte, no checksum, no length field. The record is a bare 48-byte blob.

### 2.2 Read path — `load()` (`:52-75`)

| Line | Behaviour |
|------|-----------|
| `:55` | `openRecordStore(STORE, false)` |
| `:57` | `getRecord(RECORD_ID)` |
| `:58` | `loaded == null` → defaults kept |
| `:59-61` | `loaded.length == 6` → copy `loaded[0..4]` to `data[0..4]`, then `data[47] = loaded[5]` |
| `:63-64` | otherwise → `count = min(loaded.length, 48)`, copy `data[0..count-1]` |
| `:67,68` | `RecordStoreException` → **silently swallowed**, defaults kept |
| `:71-73` | `closeRecordStore()` |

`load()` **never writes**. It is read-only.

### 2.3 Write path — `save()` (`:77-91`)

| Line | Behaviour |
|------|-----------|
| `:80` | `openRecordStore(STORE, true)` |
| `:81` | `setRecord(RECORD_ID, data, 0, 48)` — always the full 48 bytes |
| `:82,84` | `RecordStoreException` → silently swallowed |
| `:87-89` | `closeRecordStore()` |

No transaction, no read-back verification, no backup.

### 2.4 Short / long record behaviour

| Record length | Branch | Outcome |
|---------------|--------|---------|
| `null` | `:58` false | defaults (fresh install) |
| 1-5 | `:63` | partial copy into `data[0..n-1]` — **interpreted as character bitmasks** |
| **6** | `:59` | legacy branch (see §4) |
| 7-47 | `:63` | partial copy; **`data[47]` stays 0 → settings lost** |
| 48 | `:63` | full copy — correct |
| >48 | `:63` | truncated to 48 |

---

## 3. Reference JAR — Static Bytecode Evidence

### 3.1 Allocation

```
SaveStore.<init>
   5: bipush 6
   7: newarray byte        →  data = new byte[6]
  15: bipush 6
  17: if_icmpge 33         →  zero loop runs 6 times
```

### 3.2 `isCompleted(int, int)` — argument order is **reversed**

```
  0: iload_1                     ← FIRST parameter  = stageOrder
  1: iflt 9
  4: iload_1
  5: iconst_5
  6: if_icmplt 11                ← stageOrder must be 0..4
  9: iconst_0; ireturn
 11: getfield data
 15: iload_1                     ← array index = stageOrder
 16: baload                      ← b = data[stageOrder]
 23: iconst_1
 24: iload_2                     ← SECOND parameter = characterIndex
 25: ishl                        ← (1 << characterIndex)
```

### 3.3 `setCompleted(int, int)`

```
  0: iload_1; iflt 19
  5: iconst_5; if_icmpge 19      ← stageOrder 0..4
 13: iload_2; iflt 19
 16: bipush 7; if_icmple 20      ← characterIndex 0..7
 24: iload_1; baload             ← data[stageOrder]
```

### 3.4 `load()` — plain truncating copy, no special-casing

```
 20: arraylength
 21: bipush 6
 23: if_icmpge 31                ← count = (len < 6) ? len : 6
 31: bipush 6
 34: iconst_0
 40: if_icmpge 60                ← for (i = 0; i < count; i++)
 52: baload                      ←   data[i] = loaded[i]
 53: bastore
```

There is **no** separate settings copy — in the JAR, `data[5]` *is* the settings byte.

### 3.5 `save()`

```
 20: arraylength
 21: invokevirtual RecordStore.setRecord(I[BII)V
```

Writes `data.length` = **6** bytes.

### 3.6 Layout summary

| | Array index | Bit position | Stage bound | Record size |
|---|---|---|---|---|
| **Reference JAR** | `stageOrder` | `characterIndex` | `0..4` | 6 |
| **Current source** | `characterIndex` | `stageOrder` | `0..7` | 48 |

**The two layouts are transposes of each other.**

---

## 4. How a 6-Byte Record Is Currently Misread

`SaveStore.java:59-61` copies `loaded[0..4]` → `data[0..4]` **by identical index**. Because the index means *stage* in the JAR but *character* in the source, the 5×5 completion matrix is transposed.

### 4.1 Worked example

**Legacy record written by the JAR** (`L[stage]` bit `char`):

| Byte | Binary | Legacy meaning |
|------|--------|----------------|
| `L[0]` | `0b00000001` | Character 0 completed Stage 0 |
| `L[1]` | `0b00000100` | Character 2 completed Stage 1 |
| `L[3]` | `0b00010000` | Character 4 completed Stage 3 |
| `L[5]` | `0b00000001` | hardcore = on |

**Current source loads it verbatim** into `data[0..4]` and reads `data[char]` bit `stage`:

| Source reads | Yields | Legacy truth | Verdict |
|--------------|--------|--------------|---------|
| `data[0]` bit 0 | Char 0 completed Stage 0 | Char 0 completed Stage 0 | ✅ correct *by symmetry* |
| `data[1]` bit 2 | **Char 1 completed Stage 2** | Char 2 completed Stage 1 | ❌ wrong |
| `data[3]` bit 4 | **Char 3 completed Stage 4** | Char 4 completed Stage 3 | ❌ wrong |
| `data[47]` | hardcore = on | hardcore = on | ✅ correct |

Only the `(char 0, stage 0)` diagonal survives, because the transpose fixes it.

### 4.2 What is *not* lost

The transpose is a **bijection** on the 25 completion bits — the total number of "completed" facts is preserved, and each one is mapped to a specific wrong `(character, stage)` pair. Settings (`L[5]` → `data[47]`) transfer correctly.

**No bits are dropped and no bits are invented.** The damage is *misattribution*, not loss.

### 4.3 Consequence

With the sequential unlock chain now in place (`stage5` requires order 3), a legacy save can mark the wrong stages complete for the wrong character. The player sees partially-open stages that do not match what was actually earned.

---

## 5. Is a Lossless Migration Possible?

### 5.1 Proven mapping

```
for (stage = 0; stage < 5; stage++)
    for (char = 0; char < 5; char++)
        if (L[stage] & (1 << char))  S[char] |= (1 << stage);
```

This is exactly the inverse of the JAR's `data[stageOrder] | (1 << characterIndex)`, and exactly what the source's `data[characterIndex] | (1 << stageOrder)` expects.

Settings: `S[47] = L[5]`. Legacy `selectedCharacter()` masks `0x0F`, source masks `0x3F`. Since the legacy writer only ever set bits 0-3 for the character index, bits 4-5 are always 0 and the 6-bit source field is a superset. A verbatim copy is safe **for JAR-written records**.

### 5.2 A verified safeguard — no atomic replace needed

`load()` is already read-only. If migration happens **in memory only** during `load()`, the 6-byte record stays on disk until the next `setCompleted` / `setHardcore` / `setSelectedCharacter` call, which already writes 48 bytes via `save()`.

Consequences:
- If the player never saves, the 6-byte record survives untouched and is re-migrated identically next launch (**idempotent**).
- **No temp-record dance, no read-back verification, no atomic-replace problem.** This sidesteps the `RecordStoreException` / non-atomic-write findings recorded in `reports/stage-progression-design/06_FINAL_GATE.md`.

The only residual risk: the first write after a *buggy* migration would destroy the original. That is a correctness-of-mapping risk, not an I/O-atomicity risk.

### 5.3 A new save format is **not** required

The 48-byte format already holds everything needed: `data[0..4]` for character bitmasks, `data[47]` for settings. **No version byte, no checksum, and no record-size change are necessary** to fix this — and none are needed to fix the stage-unlock problem, which was solved purely in catalog data.

---

## 6. Options

| | **A — Keep current behaviour** | **B — Detect and re-initialise** | **C — Migrate with proven transpose** |
|---|---|---|---|
| Action | none | on `length == 6`, keep defaults; do not copy | on `length == 6`, transpose bytes 0-4 into `data[0..4]`; `data[47] = loaded[5]` |
| Progress loss | none (bits kept) — **but wrongly attributed** | **total, silent** | none |
| Player experience | wrong stages unlocked for wrong characters | starts from scratch with no explanation | previous progress preserved and correct |
| Code surface | — | ~4 lines | ~8 lines |
| Needs new save format | no | no | no |
| Needs atomic write | no | no | **no** (see §5.2) |
| Reversible | n/a | **no** — original overwritten on next save | yes until first save |
| Risk of fabricating progress | **no** (bijection) | no | no |

**Explicit note on validity:** a 6-byte record is **not** inherently corrupt and **not** inherently valid. Legitimacy cannot be inferred from length alone. Additional signals exist but none are conclusive:

| Signal | Weakness |
|--------|----------|
| bits 5-7 of `L[0..4]` are 0 | `setCompleted` permits `characterIndex <= 7`, so those bits are *legal* even if the JAR's canvas made them unreachable |
| `L[5] & 0x3F < 5` (character index in range) | the source clamps anyway at `FullGameCanvas:59` |

Because no reliable discriminator exists, **any policy must tolerate an arbitrary 6-byte blob.** Options B and C both do: B keeps defaults, C transposes whatever is there. Neither can *detect* corruption, only handle it deterministically.

---

## 7. Recommendation — Lowest Risk

**Option C (in-memory transpose migration), with Option B's behaviour as the fallback if validation fails.**

Rationale:

1. **It is the only option that does not destroy real player progress.** A and B both lose something — A loses *meaning*, B loses *data*.
2. **It needs no new save format.** The 48-byte layout is already sufficient.
3. **It sidesteps the atomic-write problem entirely** by deferring the rewrite to the existing `save()` (§5.2).
4. **The mapping is proven** by bytecode on both sides (§3.2, §3.3) and by a worked counter-example (§4.1).
5. **It is idempotent** — re-running produces identical bytes.

Preconditions before implementation:

| # | Condition |
|---|-----------|
| P1 | Unit-test the transpose against hand-computed vectors, including the `char 0 / stage 0` diagonal and the all-zero and all-bits-set cases |
| P2 | Confirm `LEGACY_LEN` remains 6 and the store name / record ID are untouched |
| P3 | Decide whether an implausible settings byte should suppress migration (defaults) or be carried verbatim |
| P4 | Document that a 6-byte record cannot be *validated* — only deterministically *interpreted* |

Deliberately **not** recommended now: adding a version byte or checksum. Both are larger changes than the problem requires, and §5.3 shows the existing format suffices.

---

## 8. Static Test Plan

No emulator needed. All cases can be verified by reading `load()` logic against constructed byte arrays.

| # | Input record | Expected in-memory state | Expected on-disk after load |
|---|--------------|--------------------------|-----------------------------|
| T1 | `null` | all zero | unchanged (absent) |
| T2 | 6 B, all zero | all zero | unchanged |
| T3 | 6 B, `L[0]=0x01, L[1]=0x04, L[3]=0x10, L[5]=0x01` | `data[0] bit0`, `data[2] bit1`, `data[4] bit3`, `data[47] hardcore` | unchanged |
| T4 | 6 B, `L[s] = 0xFF` for all 5 | `data[c]` bits 0-4 set for all 5 chars (plus legacy bits 5-7 if present) | unchanged |
| T5 | 6 B, `L[5] = 0x0F` | `data[47] & 0x3F == 15`; canvas clamps character to 0 | unchanged |
| T6 | 48 B round-trip | identical | identical |
| T7 | 7-47 B | partial copy, `data[47] == 0` | unchanged |
| T8 | `>48` B | first 48 bytes | unchanged |
| T9 | `openRecordStore` throws | all zero | unchanged |
| T10 | After T3, then `setCompleted(0,1)` | `data[0] bit1` set | now 48 B |

---

## 9. Unverified Items

| # | Item |
|---|--------|
| U1 | Whether any real installation actually holds a 6-byte record — no save data available; JAR deployment history out of scope |
| U2 | On-device RMS behaviour: record size limits, `setRecord` atomicity, failure timing. Requires a device or emulator |
| U3 | Whether the JAR ever produced bits 5-7 in `L[0..4]` in practice. The canvas bounds `characterIndex` to 5, so probably not — but not provable statically |
| U4 | Whether users would prefer losing progress (B) over misattributed progress (A). A product decision, not a technical one |

---

## 10. Conclusion

The 6-byte legacy record is currently **misread, not merely unread** — the completion matrix is transposed, so progress is attributed to the wrong `(character, stage)` pairs. Nothing is dropped; everything is scrambled.

A lossless fix exists, is provable from bytecode on both sides, requires **no new save format**, and avoids the atomic-write hazard by migrating in memory during the existing read-only `load()`.

**No code was changed. Awaiting explicit approval before implementing any option.**
