# Open Questions

These questions should be resolved before the rebuild implementation begins.

## Product Scope

1. Should HoloJava remain inspired by HoloCure but deliberately smaller, or continue to aim at broad reconstruction over multiple years?
2. Is it acceptable for the first public build to feature only one playable character and one stage?
3. Should meta-progression (shop/upgrades/G.Rank) remain out of scope until after a complete playable run?
4. Is save data required for any early milestone?

## Runtime Target

5. Will the primary runtime remain J2ME Loader first, or should Nokia devices be tested in parallel?
6. Should any feature require `pointer/touch` or audio, or remain strictly key-based and silent?
7. Is a 40 ms fixed tick acceptable as the baseline?
8. Which heap and image-memory limit should be treated as practical worst case?

## Asset Pipeline

9. How large should the first project-local content snapshot be?
10. Should assets stay copied from the unpacked directory, or be referenced through `HOLOJAVA_ASSET_ROOT` without copying?
11. If item icons and background art ship, should they be resized or used at source resolution?
12. What attribution/extraction license review process is acceptable before local redistribution?

## Gameplay Ambiguities

13. What should movement aiming use when the vectors are zero and the player is facing a previous direction?
14. Which one character and weapon should be prioritized as the first verified content set?
15. Should HP and damage values use documented HoloCure numbers from selected content, or generic temporary constants?
16. Should EXP threshold progression match documented in-game values, or a simple documented temporary curve?
17. Are movement baselines in logical 320x240 units acceptable, or should a custom fixed scale be defined?
18. Can level-up item offers omit exact probability distributions initially?
19. Do timed events require stage-specific charts, or can a simplified standard curve be documented as approximate?
20. Should special attacks and skills be excluded entirely from the initial vertical slice?
21. What simple metric defines runtime success parity?
22. How much of the wiki math should be implemented as exact functions versus documented estimates?
23. Should the build process use the standard ProGuard workflow strictly?

## Documentation Consistency

24. Several old documents still state Phase 3 had not started. Should historical process text be preserved, or amended while retaining an audit trail?
25. Is `docs/rebuild/` the permanent home for rebuild audit docs, or should findings move into existing docs later?

## Recommended Decision Pattern

Each answer should be recorded in `docs/rebuild/OPEN_QUESTIONS.md`, with the chosen value, a date, and the approving user. No rebuild coding should infer unapproved product decisions.

## Resolved for the Current Rebuild

- Runtime target remains CLDC 1.1/MIDP 2.0 with J2ME Loader first.
- The first playable character set is the five default Myth characters with verified local idle/run/portrait families.
- Stage Mode completes when the documented final boss is defeated; Endless Mode ends on death; Time Mode targets 4,000 fans.
- Enemy and boss art with insufficient local evidence use explicit fallback mappings.
- Save data is limited to optional RMS stage completion and Hardcore state.
- Audio is silent by default because MIDP playback has not been verified reliably on the target runtime.
