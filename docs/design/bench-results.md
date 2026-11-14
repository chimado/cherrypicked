# Toolkit benchmark results (2026-11-15)

**Device:** TCL Google TV, Android 12, 32-bit ARM, 1.8 GB RAM. Every contender draws the same screen (`reference.png`). Each one was compiled with `speed-profile` before measuring, and the runs were done with `measure.sh`. Screenshots taken on the TV are in `results/`.

| Contender | RAM in background (while another app runs) | RAM on screen | Cold start (median) | Home → back (median) | Frames while navigating | Idle (30 s) | APK |
|---|---|---|---|---|---|---|---|
| Kotlin + custom Canvas | **20.3 MB** | 20.5 MB | 533 ms | 78 ms | smooth (p95 16.7 ms) | 0 frames, 0 CPU | 452 KB |
| Kotlin + Views | **20.8 MB** | 21.1 MB | 510 ms | 92 ms | smooth (p95 16.7 ms) | 0 frames, 0 CPU | 454 KB |
| Kotlin + Compose | 26.7 MB | 23.3 MB | 452 ms | 93 ms | p95 33 ms (some dropped frames) | 0 / 0 | 1.7 MB |
| Rust + Slint (Skia GL) | 40.7 MB | 44.5 MB | **240 ms** | **66 ms** | p95 33 ms | 0 / 0 | 5.6 MB |
| Flutter (Impeller) | 51.8 MB | 56.7 MB | 568 ms | 117 ms | not captured (SurfaceView layer) | 0 / 0 | 11.7 MB |
| Rust + GPUI (Vulkan) | 83.6 MB | 84.3 MB | 257 ms | 73 ms | **~12 fps** (p50 83 ms) | 1 frame, wakes every second | 6.2 MB |
| *Baseline: Projectivy* | *52.8 MB* | *53.1 MB* | | | | *0 CPU* | |
| *Baseline: Button Mapper* | *13.0 MB* | | | | | | |

## Notes
- **Background RAM matters most.** A launcher spends about 99% of its life behind Jellyfin.
- **Cold start barely matters.** It happens after a reboot, or after the system kills the launcher. The launcher resumes from the background on every Home press, so the warm "Home → back" time is the one you feel.
- **Views and custom Canvas are effectively tied.** Views makes Setup, lists and accessibility much easier to build.
- **Compose:** fine to use, but costs about 6 MB more and drops a frame now and then on this GPU.
- **Rust:**
  - Both Rust options start fastest but cost 2–4x the memory, because each carries its own renderer (Skia or wgpu).
  - GPUI also stutters on this GPU and wakes up every second while idle.
- **Flutter** uses as much memory as Projectivy before adding any features.

## Not covered
- **Accessibility service cost.** Enabling it needs a change to the TV's security settings, so it was skipped. Plan: run it in its own tiny process, measured once the real app exists.
- **Real app banners.** All contenders loaded the same bundled PNGs instead of reading banners from PackageManager.
