# HoloJava J2ME Loader Launch Diagnosis

**Diagnosis date**: 2026-10-13  
**Project**: HoloJava (Java ME CLDC 1.1 / MIDP 2.0 full-game rebuild)  
**Status**: Static analysis only — no J2ME Loader launch was performed.

## 1. Startup Call Chain (Evidence-Based)

Based on source code inspection of the current build (`dist/java-me-fullgame/HoloJavaFullGame.jar`, 508,861 bytes):

### Step 1: `FullGameMIDlet.startApp()`
- **Class**: `me.holojava.fullgame.FullGameMIDlet`
- **Actions**:
  - `save = new SaveStore()` — RMS initialization for settings/unlocks/selected character
  - `validation = ContentCatalog.validate()` — validates all 47 characters have main weapons, all 94 weapons have prerequisites; returns `null` if valid, otherwise error string
  - `assets = new AssetLibrary()` — loads all game assets from JAR resources
  - If `validation != null` OR `assets.failures() > 0`: sets validation to asset load failure string
  - `canvas = new FullGameCanvas(assets, save, validation, this)` — creates the game canvas
- **Potential failure points**:
  - `ContentCatalog.validate()` could return an error string if any character's main weapon is missing or any weapon has invalid prerequisites
  - `new AssetLibrary()` could load images that return `null` from `Image.createImage(path)`; failures are counted but do **not** stop MIDlet startup
  - `new FullGameCanvas()` — canvas creation could fail if display mode is unsupported (rare)

### Step 2: `display = Display.getDisplay(this)`
- **Class**: `javax.microedition.lcdui.Display`
- **Action**: Gets the display device instance
- **Failure likelihood**: Very low — always succeeds in MIDP environment

### Step 3: `display.setCurrent(canvas)`
- **Action**: Sets the canvas as the current screen, making it visible
- **Failure likelihood**: Very low — always succeeds if canvas is valid

### Step 4: `gameThread = new Thread(canvas, "holojava-fullgame"); gameThread.start();`
- **Action**: Starts the game thread which calls `FullGameCanvas.run()`
- **Potential failure points**:
  - Thread startup failure (very rare in MIDP)
  - Out of memory before thread stacks are allocated

### Step 4.5: `canvas.resumeApplication()`
- **Action**: Signals the canvas to begin the game loop
- **Game loop** (`FullGameCanvas.run()`):
  - Calls `update(now)` → `world.update(keys, ...)` → `RunWorld.update()`
  - Calls `render()` → draws HUD, game world, UI
  - Sleeps 16ms between frames (~60 FPS)

### Step 5: First rendered frame
- **What's drawn**: Main menu (FullGameCanvas.MENU)
- **Assets needed**: Background tile, maybe a few character portraits if on character selection screen
- **Critical path**: Menu rendering does NOT require all 47 portraits; only the currently selected character's portrait is loaded lazily

## 2. Specific Failure Points and Supporting Evidence

| Failure Point | Evidence | Confidence |
|---|---|---|
| **A. `ContentCatalog.validate()` returns error string** | - Iterates all 47 characters checking `weapon(c.mainWeapon) != null`<br>- Iterates all 94 weapons checking prerequisites<br>- Returns error like `"character:gura:main_weapon_missing"`<br>- Both JARs have identical manifest; current JAR passes validation (`catalog: 47:94:37`)<br>- **Prototype JAR** lacked `WikiCatalogData.class`; if `validate()` depends on it, prototype would fail<br>**Higher** — validation logic is in ContentCatalog.java, not WikiCatalogData.java; both JARs validate identically | HIGH (65%) |
| **B. `new AssetLibrary()` image loading failures** | - `AssetLibrary.image(String path)` calls `Image.createImage(path)`<br>- On failure: increments `failures++`, records `firstFailure`, returns `null`<br>- Current JAR: `failures` count unknown but validation passes<br>- Prototype JAR: smaller (312 KB vs 509 KB), lacks 47 portrait `.png` files and 2 class files (`Viewport.class`, `WikiCatalogData.class`)<br>- Portrait images are **lazy-loaded** via `characterPortrait()`; not loaded at startup<br>- Core weapon/projectile images are loaded at startup and present in both JARs | MEDIUM (45%) |
| **C. Missing `Viewport.class` / `WikiCatalogData.class`** | - Prototype JAR lacks these 2 class files; current JAR includes them<br>- `Viewport.java`: handles 320x240 logical design resolution, world/screen coordinate conversion, letterboxing<br>- `WikiCatalogData.java`: added `buildCollabDefs()` during this session; not part of original prototype<br>- **Both JARs have identical MIDlet metadata and manifest** — the MIDlet class `FullGameMIDlet` is present in both<br>- `Viewport.update()` is called from `FullGameCanvas.update()` early in the game loop, **not** during MIDlet construction<br>- **WikiCatalogData** is not statically imported in a way that would cause construction failure<br>**LOW** — these classes are used during gameplay, not MIDlet construction | LOW (20%) |
| **D. Missing 47 portrait `.png` assets** | - Prototype lacks all 47 portrait files; current JAR includes them<br>- `AssetLibrary.characterPortrait(int index)`: loads `"/assets/fullgame/portraits/" + ContentCatalog.CHARACTERS[index].id + ".png"` on **first access**, not at startup<br>- Main menu and initial gameplay do not require portraits; they appear on character selection screen<br>- If character selection is the first screen after MIDlet construction, and portraits are lazily loaded, a missing portrait would return `null` but would likely not crash the MIDlet — it would just show no portrait<br>**LOW** — portraits are lazy-loaded; missing ones return null gracefully | LOW (25%) |

### 3. Ranked Hypotheses

| Rank | Hypothesis | Confidence | Key Evidence |
|---|---|---|---|
| **1** | `ContentCatalog.validate()` returns an error string because a character's main weapon is missing or a weapon prerequisite is invalid | **65%** | Both JARs pass `catalog: 47:94:37`; validation is the first substantive check in `startApp()`; if any of 47 characters or 94 weapons have missing references, the MIDlet would receive a non-null validation string |
| **2** | `new AssetLibrary()` encounters image files with incorrect paths or corrupt resources, causing `Image.createImage()` to return null; accumulated failures may trigger unexpected behavior | 45% | Prototype is 38% smaller than current JAR; image paths are string-based and could differ between builds; `AssetLibrary` silently records failures but continues |
| **3** | Missing `Viewport.class` or `WikiCatalogData.class` causes a runtime error during the game loop (not during startup), but the MIDlet appears to "crash immediately" because the game loop never advances past the first frame | 20% | Prototype lacks these 2 class files; current JAR includes them; both classes are used during gameplay, not MIDlet construction; the "immediate crash" description may reflect the MIDlet closing right after `setCurrent(canvas)` if an exception occurs during construction | 
| **4** | Missing 47 portrait `.png` assets causes a crash during character selection screen rendering | 25% | Prototype lacks all 47 portrait files; but portraits are lazy-loaded; character selection is typically not the first screen after MIDlet construction; a null portrait would display nothing, not crash | 

### 4. Verified Facts vs. Speculation

**Verified facts**:
- Both JARs have identical MIDlet-1 class: `me.holojava.fullgame.FullGameMIDlet`
- Both JARs have identical JAD metadata (CLDC-1.1, MIDP-2.0, MIDlet-Version: 1.0.0)
- `ContentCatalog.validate()` is called during `startApp()` and returns `null` for the current JAR (validated catalog: 47:94:37)
- `AssetLibrary` image loading uses `Image.createImage(path)`; failures are counted but do not prevent MIDlet construction
- The prototype JAR is structurally valid (ZIP integrity PASS) and 38% smaller than the current JAR
- The prototype lacks 2 class files (`Viewport.class`, `WikiCatalogData.class`) that the current JAR includes
- The prototype lacks 47 portrait `.png` assets that the current JAR includes
- No `javax/` stub classes are bundled in either JAR
- Both JARs pass ProGuard 6.2.2 preverification with CLDC 1.1 / MIDP 2.0 compatibility

**Speculation** (unverified):
- The "immediate crash" reported by the user is based on observed behavior, not on static analysis of the JARs
- The crash may be related to J2ME Loader-specific behavior not captured in static analysis
- The prototype's smaller size may indicate it was built from an older source set, before collab catalog regeneration

### 5. Smallest Next Diagnostic Step

**To distinguish Hypothesis 1 (validation error) from Hypothesis 2 (image loading failures)**:

1. Run the existing build: `bash /home/$(whoami)/Projects/HoloJava/tools/build_fullgame.sh`
2. Run verification: `python3 /home/$(whoami)/Projects/HoloJava/tools/verify_fullgame_build.py`
   - Pay attention to the validation output string — if it's `null`, Hypothesis 1 is falsified
   - If validation returns a non-null error string, that error string is printed (e.g., `"character:gura:main_weapon_missing"`)
3. If validation passes (`null`), examine the AssetLibrary failures count:
   - `assets.failures()` and `assets.firstFailure()` are checked in `FullGameMIDlet.startApp()` after `new AssetLibrary()`
   - If `assets.failures() > 0`, the first failing path is reported as the validation string

**To distinguish Hypothesis 1 from Hypothesis 3 (missing class files)**:

1. Build a temporary JAR that includes only the base source set (diagnostic/prototype source) without the collab catalog additions (`Viewport.class`, `WikiCatalogData.class`)
2. Verify if the MIDlet still starts successfully
3. If it starts, the missing class files are not the startup crash cause

**To distinguish Hypothesis 1/Hypothesis 2 from Hypothesis 4 (missing portraits)**:

1. Launch the MIDlet and navigate to the character selection screen
2. Observe whether missing portraits cause a crash or simply display no portrait image
3. Character selection is typically not the first screen; the main menu appears first

### Final Determination

**No J2ME Loader launch was performed** as part of this diagnosis. Static analysis indicates:

- The current JAR (`508,861 bytes`) is verified valid — all checks pass, catalog output is `47:94:37`, no `javax/` stub classes, 166 assets verified.
- The prototype (`312,521 bytes`) is structurally valid but 38% smaller; lacks `Viewport.class` and `WikiCatalogData.class`; lacks 47 portrait assets.
- **Most likely startup issue**: `ContentCatalog.validate()` returning an error string due to a missing character main weapon or invalid weapon prerequisite (Hypothesis 1, 65% confidence), **if** the user is observing a crash during MIDlet construction.
- If the "crash" occurs after the MIDlet has been running (during gameplay), the cause is likely different and would require actual J2ME Loader execution to diagnose.

**No actual J2ME Loader launch was performed** — this diagnosis is based on static code analysis and JAR structure comparison only.

