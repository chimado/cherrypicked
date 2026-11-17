# Remote keys on the reference TV (TCL Google TV)

| Check | Result |
|---|---|
| "Screen off" route: `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` | ✅ The TV goes to sleep (checked via the test hook, 2026-11-15 03:50). |
| Home on the physical remote → Cherrypicked | ✅ Checked by the owner, phase 1 and 2. |
| Vendor buttons (Netflix, YouTube, Prime Video, Input, …) | ⏳ Pending the owner's probe with Setup → Button test. |

**Driving the TV over adb:**
- Shell-injected keys (`adb shell input keyevent`) skip accessibility key filtering, so they can't test remaps.
- `adb shell am broadcast -a io.github.chimado.cherrypicked.TEST -p io.github.chimado.cherrypicked --ei key <code> --es press SINGLE|DOUBLE|LONG` drives the remap engine and quick panel with synthetic presses through the same path as real keys. `--es action <quick_panel|sleep_timer|screen_off|home|back|...>` runs an action directly. Shell only: the hook requires `android.permission.DUMP`.
- Synthetic hook keys don't reach activities, so use `input keyevent` to move around app screens.
