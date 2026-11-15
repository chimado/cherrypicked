# Performance on the reference TV (2026-11-15)

**Device:** TCL Google TV, Android 12, armeabi-v7a, 1.8 GB RAM.
**Build:** `feat/core-launcher` at 034b9e1, release (R8), measured with `tools/measure.sh` while Cherrypicked's accessibility service was enabled.

| Measure | Result | Budget (spec §10) | |
|---|---|---|---|
| Home process in background (PSS) | **23.1 MB** | ≤ 25 MB | ✅ |
| `:a11y` process (PSS) | **5.7 MB** | ≤ 15 MB | ✅ |
| Everything that stays resident | **28.8 MB** | ≤ 45 MB (Projectivy + Button Mapper today: ~66 MB) | ✅ |
| Home on screen (PSS) | 23.0 MB | — | |
| Return to Home (median) | **90 ms** | ≤ 100 ms | ✅ |
| Cold start (median) | 595 ms | — | |
| Idle CPU ticks (30 s) | 0 | 0 | ✅ |
| Idle frames (30 s) | 1 | 0 | ⚠️ |
| Navigation frame interval p95 | **33.3 ms** (p50 16.7, max 33.3) | ≤ 17 ms | ❌ |
| APK size | 493 KB | — | |

## Notes

- **Idle frames.** The one idle frame is most likely the once-a-minute clock update landing inside the 30 s window. That update is by design. This is not confirmed: the run didn't record a timestamp for the frame.
- **Navigation is over budget.** Some focus-animation frames take two vsyncs: no frame runs over 34 ms, but at least 5% of them take 33 ms. The benchmark's simpler screen hit p95 16.7 ms. The likely cause is the `BlurMaskFilter` glow being redrawn on every animation frame across the larger tiles. It has not been profiled yet.
- **`:a11y` memory** was measured separately, after re-enabling the service. The script's own `pss_a11y_kb` line came back empty, because reinstalling the APK removed the service from the enabled list (see `a11y-check.md`).
