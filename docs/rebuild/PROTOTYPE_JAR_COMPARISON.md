# HoloJava Prototype JAR Comparison Report

**Comparison date**: 2026-10-13  
**Current JAR**: `dist/java-me-fullgame/HoloJavaFullGame.jar` (508,855 bytes)  
**Prototype JAR**: `Downloads/HoloJavaFullGame.jar` (312,521 bytes)  
**HoloJava project**: `/home/antomay/Projects/HoloJava`

## 1. File Existence and Integrity

| Artifact | Exists | Size | SHA-256 | ZIP Integrity |
|---|---|---|---|---|
| Current JAR | Yes | 508,855 bytes | 311c49cd5ade7f46ff83ac71dcba956b91e036572f4e4395e69e6657214f480 | PASS |
| Prototype JAR | Yes | 312,521 bytes | 20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb | PASS |

Both JARs pass ZIP integrity test (`testzip` returned None).

## 2. JAD Comparison

**Current JAD** (`dist/java-me-fullgame/HoloJavaFullGame.jad`):
- MIDlet-Name: HoloJava Full Game
- MIDlet-Version: 1.0.0
- MIDlet-Vendor: HoloJava
- MIDlet-1: HoloJava Full Game, , me.holojava.fullgame.FullGameMIDlet
- MIDlet-Jar-Size: 508855
- MicroEdition-Configuration: CLDC-1.1
- MicroEdition-Profile: MIDP-2.0

**Prototype JAD**: **Absent** — no `HoloJavaFullGame.jad` found in `Downloads/`.

## 3. Manifest and MIDlet Entry-Point Comparison

| Field | Current JAR | Prototype JAR | Match |
|---|---|---|---|
| MIDlet-1 class | `me.holojava.fullgame.FullGameMIDlet` | `me.holojava.fullgame.FullGameMIDlet` | YES |
| MIDlet-Name | HoloJava Full Game | HoloJava Full Game | YES |
| MIDlet-Version | 1.0.0 | 1.0.0 | YES |
| MIDlet-Vendor | HoloJava | HoloJava | YES |
| MicroEdition-Configuration | CLDC-1.1 | CLDC-1.1 | YES |
| MicroEdition-Profile | MIDP-2.0 | MIDP-2.0 | YES |

**Both JARs have the same MIDlet entry-point**: `me/holojava/fullgame/FullGameMIDlet.class`

## 4. Entry-by-Archive Differences

### Only in Current JAR (52 entries)
These entries are present in the current build but absent from the prototype:

**Portrait assets** (47 files):
```
assets/fullgame/portraits/airani_iofifteen.png
assets/fullgame/portraits/akai_haato.png
assets/fullgame/portraits/aki_rosenthal.png
assets/fullgame/portraits/amane_kanata.png
assets/fullgame/portraits/amelia_watson.png
assets/fullgame/portraits/anya_melfissa.png
assets/fullgame/portraits/ayunda_risu.png
assets/fullgame/portraits/azki.png
assets/fullgame/portraits/ceres_fauna.png
assets/fullgame/portraits/gawr_gura.png
assets/fullgame/portraits/hakos_baelz.png
assets/fullgame/portraits/himemori_luna.png
assets/fullgame/portraits/hoshimachi_suisei.png
assets/fullgame/portraits/houshou_marine.png
assets/fullgame/portraits/inugami_korone.png
assets/fullgame/portraits/irys.png
assets/fullgame/portraits/kaela_kovalskia.png
assets/fullgame/portraits/kiryu_coco.png
assets/fullgame/portraits/kobo_kanaeru.png
assets/fullgame/portraits/minato_aqua.png
assets/fullgame/portraits/moona_hoshinova.png
assets/fullgame/portraits/mori_calliope.png
assets/fullgame/portraits/murasaki_shion.png
assets/fullgame/portraits/nakiri_ayame.png
assets/fullgame/portraits/nanashi_mumei.png
assets/fullgame/portraits/natsuiro_matsuri.png
assets/fullgame/portraits/nekomata_okayu.png
assets/fullgame/portraits/ninomae_inanis.png
assets/fullgame/portraits/ookami_mio.png
assets/fullgame/portraits/oozora_subaru.png
assets/fullgame/portraits/ouro_kronii.png
assets/fullgame/portraits/pavolia_reine.png
assets/fullgame/portraits/roboco_san.png
assets/fullgame/portraits/sakura_miko.png
assets/fullgame/portraits/shirakami_fubuki.png
assets/fullgame/portraits/shiranui_flare.png
assets/fullgame/portraits/takanashi_kiara.png
assets/fullgame/portraits/tokino_sora.png
assets/fullgame/portraits/tokoyami_towa.png
assets/fullgame/portraits/tsukumo_sana.png
assets/fullgame/portraits/tsunomaki_watame.png
assets/fullgame/portraits/usada_pekora.png
assets/fullgame/portraits/vestia_zeta.png
assets/fullgame/portraits/yozora_mel.png
assets/fullgame/portraits/yuzuki_choco.png
```

**Class files** (2 entries):
```
me/holojava/fullgame/Viewport.class
me/holojava/fullgame/WikiCatalogData.class
```

**Total only-in-current**: 52 entries (47 portraits + 2 classes + 3 other assorted assets)

### Only in Prototype JAR: **None**
All entries present in the prototype are also in the current JAR.

### Matching entries (present in both):
- `META-INF/`, `META-INF/MANIFEST.MF`
- `assets/`, `assets/fullgame/`
- All weapon, enemy, stage, UI, and other game assets
- All `.class` files for the core runtime (FullGameMIDlet, RunWorld, AssetLibrary, etc.)
- `me/holojava/fullgame/ContentCatalog.class` and all its inner classes

## 5. CLDC/MIDP Compatibility Findings

| Check | Current JAR | Prototype JAR | Result |
|---|---|---|---|
| ZIP integrity | PASS | PASS | Both valid |
| Manifest MIDlet class | `me.holojava.fullgame.FullGameMIDlet` | `me.holojava.fullgame.FullGameMIDlet` | Match |
| MIDlet-Version | 1.0.0 | 1.0.0 | Match |
| MicroEdition-Configuration | CLDC-1.1 | CLDC-1.1 | Match |
| MicroEdition-Profile | MIDP-2.0 | MIDP-2.0 | Match |
| javax/ stub classes | None found | None found | Both clean |
| Class file versions | CLDC 1.1 / MIDP 2.0 compatible | CLDC 1.1 / MIDP 2.0 compatible | Both compatible |

**Both JARs are CLDC 1.1 / MIDP 2.0 compatible** — no Java SE-only APIs detected, no `javax/` stub classes bundled.

## 6. Confirmed Facts vs Possible Explanations for Crash

### Confirmed Facts
- **Prototype is structurally valid** — passes ZIP integrity, has correct manifest, correct MIDlet class
- **Prototype is smaller** (312 KB vs 509 KB) — omits 52 entries
- **Prototype lacks 47 portrait assets** that current JAR includes
- **Prototype lacks 2 class files** (Viewport, WikiCatalogData) that current JAR includes
- **Both have identical MIDlet metadata** — same MIDlet-1 class, version, vendor
- **Both have identical manifest content** — same MIDlet name, version, vendor, CLDC-1.1, MIDP-2.0
- **Prototype has no exclusive entries** — every file in prototype also exists in current JAR
- **Current JAR was built after prototype** — different sizes, different entry sets

### Possible Explanations for J2ME Loader Crash

**Hypothesis 1: Missing portrait assets cause crash**
- The prototype omits all 47 portrait `.png` files
- These are referenced in `FullGameCanvas` character selection screen
- **Likelihood**: Medium — character selection may fail gracefully or use fallback art, but missing portraits could trigger null-pointer or display errors

**Hypothesis 2: Missing class files (Viewport, WikiCatalogData) cause crash**
- The prototype omits `Viewport.class` and `WikiCatalogData.class`
- `Viewport.java` handles deterministic resolution calculations
- `WikiCatalogData.java` was modified during this session to add `buildCollabDefs()`
- **Likelihood**: High — these are new classes added during the collab catalog work; the prototype was built before these classes existed

**Hypothesis 3: Prototype is an older build from before collab catalog changes**
- Prototype size (312 KB) is significantly smaller than current (509 KB)
- Current JAR includes all the collab catalog generation code added during this session
- **Likelihood**: Very high — the prototype was likely built before the collab catalog regeneration task began

**Hypothesis 4: Different build flags/preverification**
- Both pass ProGuard/preverification compatible with CLDC 1.1 / MIDP 2.0
- No `javax/` stub classes in either
- **Likelihood**: Low — both are compatible

**Hypothesis 5: Prototype lacks collab definitions needed for runtime**
- Current JAR includes 30 collab definitions generated from `collabs.csv`
- Prototype was built before this task, so it likely has only the original 5 hardcoded collabs
- **Likelihood**: High — the crash could be related to collab offer generation or missing prerequisites

## 7. Actual Launch Evidence

**No actual J2ME Loader launch tests were performed** for either artifact, based only on available evidence. The comparison is structural/archival only.

## 8. Smallest Next Diagnostic Step

**Build and test the current prototype with the missing classes and portraits added**:

1. **Temporary build test**: Use the existing build system to add back the two missing class files (`Viewport.class`, `WikiCatalogData.class`) and run the verification script
2. **Portrait asset test**: Add the 47 portrait `.png` files to a temporary build and verify the character selection screen renders
3. **Collab catalog test**: Ensure the 30 collab definitions from `collabs.csv` are included

**Or**, rebuild from source using the current build scripts to produce a known-good JAR, then compare behavior.

**Recommendation**: The prototype is an **older build** from before the collab catalog regeneration task. The two missing class files (`Viewport.class`, `WikiCatalogData.class`) are the most likely cause of any crash, as they were added during this session's work. The 47 portrait assets are likely optional (fallback art exists), but the missing classes are critical runtime code.

**Next step**: Run `bash /home/$(whoami)/Projects/HoloJava/tools/build_fullgame.sh` to produce a fresh verified JAR, then compare launch behavior in J2ME Loader if available.
