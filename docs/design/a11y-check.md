# Accessibility service check on the reference TV (2026-11-15)

| Check | Result |
|---|---|
| Home on the **physical remote**, with Spotify in front | ✅ Cherrypicked came up. The owner pressed the real remote. |
| Home from `adb shell input keyevent 3` | Went to Projectivy. Shell-injected keys skip accessibility key filtering, so this is **not** a valid test. |
| Wake from sleep (main app Moonfin) | ✅ Moonfin opened about 2 s after waking. Log: `wake fg=io.github.chimado.cherrypicked open=org.moonfin.androidtv`. |
| Wake while on HDMI (HW15, through `com.tcl.tv`) | ✅ Nothing opened; the TV stayed on HDMI. ⚠️ No `CP` wake line was logged in that case, so the reason it skipped is unconfirmed. |
| TV-input packages found | `tvInputs=com.tcl.tv` |
| Setup "Home button" status and FixHome tile | Connected; no FixHome tile. |
| `:a11y` PSS | 5.7 MB |

## TCL-specific findings (affect real users)

1. **TCL's auto-start whitelist blocks the service.** Out of the box, TCL's `TclAppBoot` refuses to bind the service: `callee_does't_have_OP_AUTO_START_permission`. It needs `adb shell appops set io.github.chimado.cherrypicked APP_AUTO_START allow`, or the equivalent toggle in TCL's settings. Button Mapper has it allowed. **The Setup Home-button help text has to cover this on TCL devices.**
2. **Button Mapper catches Home first.** Bound ahead of Cherrypicked, it maps Home to Projectivy on the owner's setup, so with both enabled, Home never reaches Cherrypicked. Remapping (plan 2) is meant to replace Button Mapper anyway.
3. **Reinstalling over adb (`install -r`) cleared the enabled-services entry for Cherrypicked.** The health check (FixHome tile) is what catches this. Whether a normal app update does the same is unverified.
4. **Background starts:** wake auto-open works through the bound service. Boot auto-open was not tested, because it needs a reboot.

## After the session

The TV was restored. The accessibility setting is back to exactly what was saved before the session (Projectivy + Button Mapper), `APP_AUTO_START` for Cherrypicked is back to `ignore`, Projectivy is in front, and the TV is asleep.
