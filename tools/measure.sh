#!/usr/bin/env bash
# Measure Cherrypicked on the TV: tools/measure.sh   (installs dist/cherrypicked.apk)
# Results go to tools/results/cherrypicked.txt plus a screenshot.
# ponytail: throwaway spike harness, no retries or statistics beyond median/p95.
set -uo pipefail
cd "$(dirname "$0")"
A=~/Android/Sdk/platform-tools/adb; S=TV_IP:5555
# This TV's wireless adb drops often ("offline"/"closed"): reconnect and retry.
ad() {
  local o i
  for i in 1 2 3 4; do
    o=$($A -s $S "$@" 2>&1) && ! grep -qE '^(error: closed|adb: device offline)' <<<"$o" && { printf '%s\n' "$o"; return 0; }
    $A disconnect $S >/dev/null 2>&1; $A connect $S >/dev/null 2>&1; sleep 2
  done
  printf '%s\n' "$o"; return 1
}
name=cherrypicked; P=io.github.chimado.cherrypicked; apk=../dist/cherrypicked.apk
HOME_APP=com.spocky.projengmenu/.ui.home.MainActivity
mkdir -p results; out=results/$name.txt; : > "$out"
log() { echo "$*" | tee -a "$out"; }

front() { ad shell dumpsys activity activities | grep -m1 -E 'ResumedActivity' ; }
# The user's rule: hands off unless the TV sits idle on Projectivy (or on one of our bench apps).
f=$(front); grep -qE 'projengmenu|io\.github\.chimado\.cherrypicked' <<<"$f" || { echo "TV is in use: $f"; exit 3; }

pss() { ad shell dumpsys meminfo "$P" | awk '/TOTAL PSS:/{print $3; exit}'; }
median() { sort -n | awk '{a[NR]=$1} END{print a[int((NR+1)/2)]}'; }
tt() { awk -F': ' '/TotalTime/{t=$2} /WaitTime/{w=$2} END{print (t!="" ? t : "w" w)}'; }

ad install -r "$apk" >/dev/null || { log "install failed"; exit 1; }
# Match what the device does after a day of use: compile with bundled baseline profiles.
ad shell cmd package compile -m speed-profile -f "$P" >/dev/null
comp=$(ad shell cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER "$P" | tail -1 | tr -d '\r')
log "contender: $name  ($comp)"
log "apk_kb: $(( $(stat -c %s "$apk") / 1024 ))"

# Cold start, 5 runs.
cold=(); for i in 1 2 3 4 5; do
  ad shell am force-stop "$P"; sleep 2
  cold+=( "$(ad shell am start -W -n "$comp" | tt)" )
done
log "cold_ms: ${cold[*]}  median $(printf '%s\n' "${cold[@]}" | median)"
ad logcat -c; ad shell am force-stop "$P"; sleep 2; ad shell am start -W -n "$comp" >/dev/null; sleep 8
log "first_frame_log: $(ad logcat -d | grep -m1 CPBENCH | sed 's/.*CPBENCH//')"
for i in 1 2 3; do $A -s $S exec-out screencap -p > "results/$name.png" 2>/dev/null && [ -s "results/$name.png" ] && break; $A connect $S >/dev/null; sleep 2; done

# Memory on screen, idle.
m=(); for i in 1 2 3; do m+=( "$(pss)" ); sleep 3; done
log "pss_onscreen_kb: ${m[*]}"

# Idle cost: CPU ticks and frames over 30 s with nothing happening.
pid=$(ad shell pidof "$P" | tr -d '\r')
ticks() { ad shell cat /proc/"$pid"/stat | awk '{print $14+$15}'; }
layer=$(ad shell dumpsys SurfaceFlinger --list | grep "$P" | grep -v -iE 'background|bounds' | tail -1 | tr -d '\r')
ad shell dumpsys SurfaceFlinger --latency-clear >/dev/null
t0=$(ticks); sleep 30; t1=$(ticks)
idle_frames=$(ad shell dumpsys SurfaceFlinger --latency "$layer" | awk 'NR>1 && $2>0 && $2<9e18' | wc -l)
log "idle_cpu_ticks_30s: $((t1 - t0))   idle_frames_30s: $idle_frames   (layer: $layer)"

# Navigation: frame intervals while focus animates.
iv=$(mktemp); trap 'rm -f "$iv"' EXIT
ad shell dumpsys SurfaceFlinger --latency-clear >/dev/null
for k in 22 22 21 21 20 22 22 19 21; do ad shell input keyevent $k; sleep 0.5; done
ad shell dumpsys SurfaceFlinger --latency "$layer" | awk 'NR>1 && $2>0 && $2<9e18 {print $2}' |
  awk 'NR>1{d=($1-p)/1e6; if(d<200) print d} {p=$1}' | sort -n > "$iv"
n=$(wc -l < "$iv")
if [ "$n" -gt 0 ]; then
  p50=$(awk -v n="$n" 'NR==int(n*0.5)+1' "$iv"); p95=$(awk -v n="$n" 'NR==int(n*0.95)+1' "$iv")
  slow=$(awk '$1>34' "$iv" | wc -l); mx=$(tail -1 "$iv")
  log "nav_frames: $n  interval_ms p50 $p50  p95 $p95  max $mx  over34ms $slow"
else log "nav_frames: 0 (layer latency unavailable)"; fi

# Warm return (what pressing Home feels like): go to Settings, come back. 5 runs.
warm=(); for i in 1 2 3 4 5; do
  ad shell am start -W -a android.settings.SETTINGS >/dev/null; sleep 3
  warm+=( "$(ad shell am start -W -n "$comp" | tt)" )
done
log "warm_return_ms: ${warm[*]}  median $(printf '%s\n' "${warm[@]}" | median)"

# Background memory: what it costs while another app plays.
ad shell am start -W -a android.settings.SETTINGS >/dev/null; sleep 20
log "pss_background_kb: $(pss)   alive: $(ad shell pidof "$P" >/dev/null && echo yes || echo no)"
log "pss_a11y_kb: $(ad shell dumpsys meminfo "$P:a11y" | awk '/TOTAL PSS:/{print $3; exit}')"

ad shell am force-stop "$P"
ad shell am start -n "$HOME_APP" >/dev/null
log "done"
