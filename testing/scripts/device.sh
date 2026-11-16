#!/usr/bin/env bash
# Device helpers for agents and humans. Source it:  source testing/scripts/device.sh
# Every helper prints one short line so results are cheap to read.

# ── Environment (works without any pre-set variables) ─────────────────────────
export MSYS_NO_PATHCONV=1
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ] && [ ! -x "$JAVA_HOME/bin/java.exe" ]; then
  JAVA_HOME="$(ls -d "$HOME"/.jdks/jdk-21* 2>/dev/null | sort -r | head -1)"
fi
export JAVA_HOME
SDK="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
export ANDROID_HOME="$SDK"
export PATH="$JAVA_HOME/bin:$SDK/platform-tools:$HOME/.maestro-cli/maestro/bin:$PATH"
export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
ADB="adb"
PKG="com.panda.dns"
ACTIVITY="com.example.pardadns.MainActivity"
HOOKS="$PKG/com.example.pardadns.debughooks.DebugHooksReceiver"
TESTING_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$TESTING_DIR/out"; mkdir -p "$OUT"

# ── Device ────────────────────────────────────────────────────────────────────
# Uses a connected device, else boots the first AVD (pixel_api31 by default).
ensure_device() {
  if "$ADB" devices | grep -qE $'\t(device)$'; then echo "device: $("$ADB" devices | awk 'NR==2{print $1}')"; return 0; fi
  local avd="${AVD:-$("$SDK/emulator/emulator" -list-avds 2>/dev/null | head -1)}"
  [ -z "$avd" ] && { echo "ERROR no device and no AVD (see .claude/skills/android-emulator-verify)"; return 1; }
  echo "booting $avd…"
  ("$SDK/emulator/emulator" -avd "$avd" -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect >"$OUT/emulator.log" 2>&1 &)
  timeout 400 "$ADB" wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null \
    && "$ADB" shell input keyevent 82 </dev/null && echo "device: booted $avd"
}

install_app() {
  local apk="$TESTING_DIR/../app/build/outputs/apk/debug/app-debug.apk"
  # MSYS_NO_PATHCONV stops Git Bash rewriting paths, so hand adb.exe a Windows path ourselves.
  command -v cygpath >/dev/null && apk="$(cygpath -w "$apk")"
  "$ADB" install -r "$apk" </dev/null | tail -1
}

launch()  { "$ADB" shell am start -n "$PKG/$ACTIVITY" </dev/null >/dev/null 2>&1; }

# ── Debug hooks: hook ACTION [am-extras…]  → prints the hook's one-line reply ──
hook() {
  local action="$1"; shift
  "$ADB" shell am broadcast -n "$HOOKS" -a "pandadns.debug.$action" "$@" </dev/null \
    | sed -n 's/.*data="\(.*\)".*/\1/p'
}
state() { hook STATE; }

# wait_for REGEX [timeout_s]  — poll STATE until it matches; prints final state
wait_for() {
  local re="$1" t="${2:-30}" s=""
  for _ in $(seq 1 "$t"); do s="$(state)"; [[ "$s" =~ $re ]] && { echo "$s"; return 0; }; sleep 1; done
  echo "TIMEOUT waiting for /$re/ — last: $s"; return 1
}

# ── DNS: dns DOMAIN → BLOCKED | RESOLVED <ip> (via the device resolver) ─────────
dns() {
  local out; out="$("$ADB" shell "ping -c 1 -W 2 $1 2>&1 | head -1" </dev/null)"
  if [[ "$out" == *"unknown host"* ]]; then echo "BLOCKED"; else echo "RESOLVED $(echo "$out" | sed -n 's/.*(\([0-9.]*\)).*/\1/p')"; fi
}
# Last N query-log lines from the VPN service (debug builds): "BLOCKED x.com" / "FORWARD y.com"
queries() { "$ADB" logcat -d -s "PardaDNS/Query:D" </dev/null | tail -"${1:-10}" | sed 's/.*PardaDNS\/Query: //'; }

# ── UI (prefer Maestro flows; these are for quick looks) ──────────────────────
# texts → visible text on screen, one line
texts() {
  for _ in 1 2 3 4 5; do
    "$ADB" shell uiautomator dump /sdcard/ui.xml </dev/null 2>&1 | grep -q "dumped to" && break; sleep 1
  done
  "$ADB" exec-out cat /sdcard/ui.xml </dev/null | grep -oE '(text|content-desc)="[^"]+"' \
    | sed -E 's/^(text|content-desc)="(.*)"$/\2/; s/&amp;/\&/g' | awk '!seen[$0]++' | paste -sd'|' -
}
# shot NAME → testing/out/NAME.png (only when the layout itself is under test)
shot() { "$ADB" exec-out screencap -p </dev/null > "$OUT/$1.png" && echo "$OUT/$1.png"; }

# flow FILE [-e KEY=VAL…] → runs one Maestro flow, prints PASS/FAIL + failing step
flow() {
  local f="$1"; shift
  local log="$OUT/maestro-$(basename "$f" .yaml).log"
  if maestro test "$@" "$f" >"$log" 2>&1; then echo "PASS"; else echo "FAIL $(grep -E '❌|FAILED|Assertion|not found' "$log" | head -2 | tr '\n' ' ')"; return 1; fi
}
