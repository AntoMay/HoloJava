# HoloJava Post-Audit Review — Backlog Review & Remediation Readiness

## 1. Validasi Ulang Remediation Backlog

### 1.1 Re-evaluasi Prioritas (Audit Sebelumnya vs Review)

| ID | Temuan | Prioritas Audit | Prioritas Review | Status Kesiapan | Alasan Perubahan |
|----|--------|-----------------|------------------|-----------------|------------------|
| P0-001 | requiredForLevel(1) == 79 | CRITICAL | **INFO / NO_CHANGE** | **NO_CHANGE** | Hardcoded, sudah benar |
| P0-002 | offerUsed[64] sink | HIGH | **CONFIRMED_DESIGN_LIMIT** | **NO_CHANGE** | Modulo 64 sengaja |
| P1-003 | chooseOfferType bias | HIGH | **CONFIRMED_DESIGN_LIMIT** | **NO_CHANGE** | Weighted distro by design |
| P1-004 | heldOffer 4-slot | HIGH | **CONFIRMED_DESIGN_LIMIT** | **NO_CHANGE** | Meta offers excluded by design |
| P1-005 | offerCount minimum | HIGH | **NOT_A_BUG** | **NO_CHANGE** | Fallback ensures 5/6 offers |
| P2-006 | heldOffer reset | MEDIUM | **NOT_A_BUG** | **NO_CHANGE** | Reset works correctly |
| P2-007 | statOfferCounts overflow | MEDIUM | **CONFIRMED_DESIGN_LIMIT** | **DEFER** | Level >1000 unrealistic |
| P2-008 | weaponLevels maxLevel | MEDIUM | **NOT_A_BUG** | **NO_CHANGE** | Math.min protects |
| P2-009 | itemLevels maxLevel | MEDIUM | **NOT_A_BUG** | **NO_CHANGE** | Math.min protects |
| P2-010 | nextSpawnAt/nextWaveAt | MEDIUM | **INSUFFICIENT_EVIDENCE** | **NEEDS_MORE_EVIDENCE** | Belum full trace |
| P2-011 | ringEventDone/hordeEventDone | MEDIUM | **INSUFFICIENT_EVIDENCE** | **NEEDS_MORE_EVIDENCE** | Belum full trace |
| P2-012 | skillAcquired/skillReadyAt/specialUsed | MEDIUM | **INSUFFICIENT_EVIDENCE** | **NEEDS_MORE_EVIDENCE** | Belum full trace |
| - | requiredForLevel precision (lvl>1) | (NEW) | **POTENTIAL_BUG** | **NEEDS_TEST_DESIGN** | Floating-point off-by-one risk |

## 2. Kandidat Perbaikan Setelah Validasi

### READY_FOR_APPROVAL (0 item)
> Tidak ada item yang siap untuk approval — semua "bug" ternyata design limits.

### NEEDS_TEST_DESIGN (1 item)

| ID | Area | Ruang Lingkup | Kriteria Penerimaan | Dependency |
|----|------|---------------|---------------------|------------|
| TB-001 | requiredForLevel precision (level ≥ 2) | Buat test deterministik level 2-50 verify monotonic, no off-by-one | - Monotonic increasing<br>- No zero/negative<br>- Compare dengan reference values jika ada | MathUtil.power, roundEven sudah verified |

### NEEDS_MORE_EVIDENCE (4 items)

| ID | Area | Bukti yang Diperlukan | Estimasi Waktu |
|----|------|----------------------|----------------|
| EM-001 | nextSpawnAt/nextWaveAt full usage | Trace semua read/write di RunWorld.update() chain | 2-4 jam |
| EM-002 | ringEventDone/hordeEventDone full usage | Trace updateTimedEvents(), spawnWave() | 1-2 jam |
| EM-003 | skillAcquired/skillReadyAt/specialUsed full usage | Trace applyOffer(), activateSkill(), activateSpecial(), update() | 2-3 jam |
| EM-004 | JAR parity: level-up method bytecode | Disassemble generateOffers, applyOffer, requiredForLevel | 2-4 jam |

### DEFER (1 item)

| ID | Area | Alasan |
|----|------|--------|
| DF-001 | statOfferCounts overflow | Level >1000 tidak realistis dalam gameplay normal; int 32-bit cukup |

### NO_CHANGE (7 items)

| ID | Area | Alasan |
|----|------|--------|
| NC-001 | requiredForLevel(1) == 79 | Hardcoded, working as designed |
| NC-002 | offerUsed[64] modulo 64 sink | Design choice untuk prevent hoarding |
| NC-003 | chooseOfferType weighted distro | Explicit design: Skill 35%, Weapon 35%, Item 25%, Stat 5% |
| NC-004 | heldOffer 4-slot limit | Meta offers (REROLL/HOLD) excluded by design |
| NC-005 | offerCount minimum 5/6 | Fallback fillOffer ensures always 5/6 offers |
| NC-006 | weaponLevels maxLevel | Math.min(current.maxLevel, ...) protects |
| NC-007 | itemLevels maxLevel | Math.min(current.maxLevel, ...) protects |

## 3. Dependency Graph

```mermaid
TB-001 (requiredForLevel test)
    └─ requires: MathUtil.power, roundEven (verified)

EM-001 (nextSpawnAt trace)
    └─ requires: Full RunWorld.update() trace

EM-002 (ring/horde events)
    └─ requires: updateTimedEvents(), spawnWave()

EM-003 (skill/special)
    └─ requires: applyOffer(), activateSkill(), activateSpecial()

EM-004 (JAR bytecode)
    ├─ requires: javap -c on RunWorld.class
    └─ enables: TB-001 validation against JAR
```

## 4. Rekomendasi Urutan Kerja

### Fase 1: Evidence Gathering (Week 1)
1. **EM-004**: Disassemble JAR level-up methods → validate TB-001 assumptions
2. **EM-001, EM-002, EM-003**: Full code trace RunWorld.update() chain

### Fase 2: Test Design (Week 2)
3. **TB-001**: Design deterministic test untuk requiredForLevel level 2-50
4. **TB-002**: Design test untuk chooseOfferType distribusi (1000+ sample)
5. **TB-003**: Design test untuk offerUsed[64] sink boundary (>64 weapons)

### Fase 3: Decision Points (Week 3)
6. Review test results → decide if any fix needed
7. Jika TB-001 fail → fix requiredForLevel precision
8. Jika TB-002 unexpected → design review chooseOfferType weights
9. Jika TB-003 hit → evaluate offerUsed capacity increase

## 5. Kriteria Penerimaan per Kandidat

| Kandidat | Kriteria Penerimaan | Metode Verifikasi |
|----------|---------------------|-------------------|
| TB-001 requiredForLevel | Monotonic, >0, no off-by-one di level 2-50 | Static test harness (no runtime) |
| TB-002 chooseOfferType | Skill 30-40%, Weapon 30-40%, Item 20-30%, Stat <10% | Static probabilistic test |
| TB-003 offerUsed sink | Sink hit >64 unique weapons, reset on new run | Static simulation |
| EM-001 nextSpawnAt | Full read/write trace documented | Code review |
| EM-004 JAR parity | Bytecode matches source logic | javap -c comparison |

## 5. Risiko Regresi

| Perubahan | Risiko | Mitigasi |
|-----------|--------|----------|
| requiredForLevel formula | Breaks XP progression balance | Test level 1-50 monotonic |
| chooseOfferType weights | Changes skill acquisition pace | A/B test design, not code |
| offerUsed capacity | Affects long-run variety | Only increase if proven problem |
| MathUtil.power/roundEven | Affects ALL power calculations | Comprehensive regression test |

## 6. Kesimpulan Backlog

**Tidak ada bug kritis yang butuh perbaikan segera.** Semua temuan "HIGH/CRITICAL" dari audit sebelumnya terbukti **design limits yang sengaja**.

**Next Action**: Fokus pada **evidence gathering (EM-001-004)** dan **test design (TB-001-003)**. Hanya setelah test hasilnya menunjukkan masalah nyata, baru pertimbangkan implementasi.
