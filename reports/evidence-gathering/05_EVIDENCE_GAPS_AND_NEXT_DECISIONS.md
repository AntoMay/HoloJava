# HoloJava Evidence Gathering — Evidence Gaps & Next Decisions

## 1. Summary of Phase 1 Findings

| Area | Evidence Status | Classification |
|------|----------------|----------------|
| requiredForLevel algorithm | CONFIRMED_PARITY (source=bytecode) | ✅ Complete |
| requiredForLevel exact values (level 2-50) | INSUFFICIENT_EVIDENCE | ❌ Gap |
| chooseOfferType algorithm | CONFIRMED_PARITY | ✅ Complete |
| chooseOfferType effective distribution (with fillOffer fallbacks) | INSUFFICIENT_EVIDENCE | ❌ Gap |
| offerUsed[64] modulo indexing | CONFIRMED_PARITY | ✅ Complete |
| offerUsed collision behavior | CONFIRMED_SOURCE_BEHAVIOR (overwrite) | ⚠️ Partial |
| offerUsed reset points | CONFIRMED_PARITY (3 reset locations) | ✅ Complete |
| heldOffer 4-slot limit | CONFIRMED_PARITY | ✅ Complete |
| heldOffer meta-offer exclusion | CONFIRMED_PARITY | ✅ Complete |
| heldOffer cross-run persistence | CONFIRMED_SOURCE_BEHAVIOR | ✅ Complete |
| MathUtil.power/roundEven | CONFIRMED_PARITY | ✅ Complete |

---

## 2. Critical Evidence Gaps

### Gap 1: requiredForLevel Numeric Values (Level 2-50)
- **What's missing**: Exact EXP threshold for each level
- **Why**: MathUtil uses custom logApprox/expApprox with clamping, not standard Math.pow
- **Impact**: Cannot verify progression curve matches design intent
- **Resolution**: Implement MathUtil approximations in external test harness (Java SE)

### Gap 2: Effective Offer Distribution After fillOffer Fallbacks
- **What's missing**: Actual probability of each offer type appearing to player
- **Why**: chooseOfferType initial distribution modified by fillOffer success/failure chains
- **Impact**: Design claims about "skill bias" may not match actual player experience
- **Resolution**: Static control-flow analysis of fillOffer with all failure paths

### Gap 3: offerUsed Hash Collision Impact
- **What's missing**: Quantified collision rate for actual weapon/item IDs
- **Why**: Need ContentCatalog weapon/item IDs to compute `hashCode() & 63`
- **Impact**: May significantly reduce variety before 64 unique items
- **Resolution**: Extract weapon/item IDs from ContentCatalog bytecode or source

### Gap 4: JAR Parity for fillOffer/applyOffer Detail
- **What's missing**: Bytecode-level verification of all fillOffer branches
- **Why**: fillOffer is 150+ bytecode instructions; only partially analyzed
- **Impact**: Fallback logic (WEAPON->COINS, ITEM->FOOD) not verified in JAR
- **Resolution**: Full fillOffer bytecode disassembly and comparison

### Gap 5: Cross-Source Evidence (HoloCure/Wiki)
- **What's missing**: Comparison with HoloCure_Unpacked, HoloCure source, Wiki docs
- **Why**: Paths exist but not accessed per review constraints
- **Impact**: Cannot validate design intent against upstream
- **Resolution**: Access `/home/antomay/Projects/HoloCure_Unpacked` and `/home/antomay/Projects/HoloCure` if permitted

---

## 3. Non-Critical Gaps (Deferred)

| Gap | Area | Reason Deferred |
|-----|------|-----------------|
| Damage formula exact values | Combat | UNCERTAIN — requires runtime |
| AI decision probabilities | Enemy AI | UNCERTAIN — requires runtime |
| Progression balancing | XP/coins | UNCERTAIN — requires playthrough data |
| RMS save format | Persistence | INSUFFICIENT_EVIDENCE — need bytecode |
| Asset loading error paths | Resources | INSUFFICIENT_EVIDENCE — need bytecode |
| Inner class implementations | RunWorld$* | JAR_ONLY — 7 inner classes not in source |

---

## 4. Recommended Next Decisions

### Decision 1: Proceed to Test Harness Implementation?
- **Question**: Should we build external Java SE harness to compute requiredForLevel values and simulate offer distribution?
- **Options**:
  - A) Yes — implement MathUtil + offer logic in test harness (2-4 hours)
  - B) No — accept INSUFFICIENT_EVIDENCE for numeric values
  - C) Partial — compute requiredForLevel only, defer offer distribution
- **Recommendation**: **A** — closes Gap 1 and enables TB-001 completion

### Decision 2: Access Cross-Source Repositories?
- **Question**: Should we inspect `/home/antomay/Projects/HoloCure_Unpacked` and `/home/antomay/Projects/HoloCure`?
- **Options**:
  - A) Yes — inspect for design intent validation
  - B) No — stay within HoloJava-only scope
- **Recommendation**: **A** if user permits; closes Gap 5

### Decision 3: Complete fillOffer Bytecode Analysis?
- **Question**: Should we disassemble and document all fillOffer branches (WEAPON, ITEM, STAT, COLLAB, fallbacks)?
- **Options**:
  - A) Yes — full 150+ instruction analysis (2-3 hours)
  - B) No — accept PARTIAL_MATCH for fillOffer
- **Recommendation**: **A** — critical for Gap 2 and Gap 4

### Decision 4: Extract ContentCatalog IDs for Collision Analysis?
- **Question**: Should we extract weapon/item IDs from ContentCatalog to compute offerUsed collisions?
- **Options**:
  - A) Yes — parse ContentCatalog.java or bytecode for all IDs
  - B) No — accept theoretical collision analysis only
- **Recommendation**: **A** — straightforward static extraction

---

## 5. Implementation Readiness Assessment

| Candidate | Evidence Status | Ready for Implementation? |
|-----------|----------------|---------------------------|
| Fix requiredForLevel precision | POTENTIAL_BUG (level≥2) | **NO** — need exact values first (Gap 1) |
| Adjust chooseOfferType weights | CONFIRMED_DESIGN_LIMIT | **NO** — design intent, not bug |
| Increase offerUsed capacity | CONFIRMED_DESIGN_LIMIT | **NO** — design intent, not bug |
| Change heldOffer slot limit | CONFIRMED_DESIGN_LIMIT | **NO** — design intent, not bug |
| Add collision protection to offerUsed | DESIGN_INTENT_UNVERIFIED | **MAYBE** — need collision quantification (Gap 3) |

**Conclusion**: **No implementation candidates are ready**. All "bugs" from initial audit reclassified as design limits. Only potential bug is requiredForLevel precision at high levels, which needs numeric verification first.

---

## 6. Final Quality Gate

- [x] SHA-256 JAR verified unchanged: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- [x] Git HEAD unchanged: `bdc415e`
- [x] Git status: Only untracked report directories
- [x] Source code unchanged: `git diff` empty
- [x] All prior reports intact
- [x] New reports in `/home/antomay/Projects/HoloJava/reports/evidence-gathering/`
- [x] No runtime execution, no builds, no source modifications

---

## 7. Files Created in This Phase

| File | Size | Purpose |
|------|------|---------|
| `00_BASELINE_AND_TOOLING.md` | ~3 KB | Baseline verification, tooling, scope |
| `01_REQUIRED_FOR_LEVEL_BYTECODE_ANALYSIS.md` | ~8 KB | requiredForLevel source/bytecode match, MathUtil analysis |
| `02_CHOOSE_OFFER_TYPE_CONTROL_FLOW.md` | ~7 KB | chooseOfferType control flow, probability, fillOffer interaction |
| `03_OFFER_USED_AND_HELD_OFFER.md` | ~7 KB | offerUsed lifecycle, heldOffer lifecycle, interaction |
| `04_TEST_DESIGN.md` | ~8 KB | 34 deterministic test cases across 4 suites |
| `05_EVIDENCE_GAPS_AND_NEXT_DECISIONS.md` | This file | Gap analysis, decision matrix, readiness |

**Total**: 6 new evidence-gathering reports.

---

## 8. Stop Condition Met

Phase 1 (bytecode/source analysis) and Phase 2 (test design) complete. No implementation performed. All constraints honored. Awaiting explicit user approval for:
1. External test harness implementation
2. Cross-source repository access
3. Complete fillOffer bytecode analysis
4. ContentCatalog ID extraction for collision analysis
