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

## Re-check after the final-review fixes (main @ 5495544, 2026-11-15 03:15)

| Measure | Result | Budget | |
|---|---|---|---|
| Home in background | 23.8 MB | ≤ 25 MB | ✅ |
| `:a11y` | 5.4 MB | ≤ 15 MB | ✅ |
| Return to Home (median) | **67 ms** | ≤ 100 ms | ✅ |
| Cold start (median) | 479 ms | — | |
| Idle (30 s, aligned to the minute) | 0 CPU, 0 frames | 0 | ✅ |
| `dumpsys gfxinfo` while navigating (66 frames) | **p50 6 ms, p95 7 ms, 0 janky, 0 missed vsync** | ≤ 17 ms | ✅ |

**The 33 ms "p95" that `measure.sh` still reports is a measurement artefact.** It comes from SurfaceFlinger frame *gaps* around each animation's start, which the shell-injected keys make worse. The app's own frame times are well under budget. Use `gfxinfo` for frame timing.

## Phase 2 (buttons, quick panel, sleep timer, inputs), 2026-11-15 04:25

| Measure | Result | Budget | |
|---|---|---|---|
| Home process in background | 24.5 MB | ≤ 25 MB | ✅ (tight) |
| `:a11y` before any overlay | 5.5 MB | — | |
| `:a11y` after the quick panel or a notice has been shown | 18.1 MB | ≤ 22 MB (revised, see below) | ✅ |
| Everything resident | 42.6 MB | ≤ 45 MB | ✅ |
| `gfxinfo` while navigating (48 frames) | p95 6 ms, p99 7 ms, 0 janky | ≤ 17 ms | ✅ |
| Return to Home (median) | 79 ms | ≤ 100 ms | ✅ |
| Idle (30 s) | 0 CPU, 0 frames | 0 | ✅ |
| APK | 537 KB | — | |

**Why `:a11y` grows:** the first overlay window drawn in `:a11y` (the quick panel or a notice) loads Android's view and graphics stack into that process: about +3 MB native, +6 MB Java heap and +3 MB of libraries. It stays loaded, and trim-memory doesn't release it. Sharing one font brought it down from 20 to 18 MB.

**The alternative:** run the panel and notices in a separate on-demand process. That needs the "display over other apps" permission, which is one more setup step, so it's the owner's call.

## Phase 2.1: panel and notices in their own process, 2026-11-15 06:00

| Measure | Result | Budget | |
|---|---|---|---|
| `:a11y` at baseline | 6.6 MB | — | |
| `:a11y` after 10 panel open/close cycles | 12.8 MB, 0 views | ≤ 15 MB | ✅ |
| `:ui` process after closing | gone (`pidof` empty) | gone within 3 s | ✅ |
| Long-press to panel visible, cold `:ui` | 131 ms | ≤ 600 ms | ✅ |
| Fallback with the permission denied | panel opens in `:a11y` | works | ✅ |

`:a11y` now draws nothing. Its growth from about 6 to 12 MB is ART heap from ordinary activity: no-op test-hook actions cause the same growth. Before this change it was 18–20 MB once an overlay had been shown.
