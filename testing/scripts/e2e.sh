#!/usr/bin/env bash
# End-to-end tests on a device/emulator. One line per test, then a summary.
#
#   testing/scripts/e2e.sh                 build + install + run all tests
#   testing/scripts/e2e.sh --no-build      reuse the installed APK
#   testing/scripts/e2e.sh filters pause   run only the named tests
#
# Tests: gating purchase filters pause autoresume schedule
# Details of a failure: testing/out/ (maestro-*.log, screenshots, logcat.txt)
set -u
source "$(dirname "${BASH_SOURCE[0]}")/device.sh"
cd "$TESTING_DIR/.."

BUILD=1; SELECTED=()
for a in "$@"; do case "$a" in --no-build) BUILD=0 ;; *) SELECTED+=("$a") ;; esac; done
ALL=(gating purchase filters pause autoresume schedule)
[ ${#SELECTED[@]} -eq 0 ] && SELECTED=("${ALL[@]}")

PASSED=0; FAILED=0; FAILS=()
fail() { echo "  ✗ $*"; return 1; }
expect() {  # expect "description" ACTUAL REGEX
  [[ "$2" =~ $3 ]] || fail "$1: got '$2'"
}
traffic() { for _ in 1 2 3; do dns "t$RANDOM.example.com" >/dev/null; done; }  # satisfies the 10 s connect watchdog
connect_cloudflare() {
  launch; sleep 1
  [[ "$(state)" =~ vpn=connected\(Cloudflare\) ]] && return 0
  hook CONNECT --es provider cloudflare >/dev/null
  # Keep DNS flowing until connected — the service's 10 s watchdog needs a query to succeed.
  for _ in $(seq 1 10); do traffic; [[ "$(state)" =~ vpn=connected ]] && return 0; sleep 1; done
  fail "could not connect: $(state)"
}

# ── Tests ─────────────────────────────────────────────────────────────────────
t_gating() {   # free users see upsells and land on the paywall
  hook PRO --ez on false >/dev/null
  local r; r="$(flow testing/maestro/free_user_paywall.yaml)"; local ok=$?
  hook PRO --ez on true >/dev/null
  [ $ok -eq 0 ] || fail "flow: $r"
}

t_purchase() { # a free user buys Pro (fake Play) and the feature unlocks
  hook PRO --ez clear true >/dev/null            # Pro comes from billing, not the override
  hook BILLING --es result reset >/dev/null      # fake Play, nothing owned
  wait_for "pro=false" 5 >/dev/null || { fail "not free after reset: $(state)"; return 1; }
  hook BILLING --es result success >/dev/null
  local r; r="$(flow testing/maestro/purchase_unlock.yaml)"; local ok=$?
  local s; s="$(wait_for "pro=true" 5)"
  hook BILLING --es result reset >/dev/null
  hook PRO --ez on true >/dev/null
  [ $ok -eq 0 ] || { fail "flow: $r"; return 1; }
  expect "acknowledged purchase" "$s" "pro=true .*billing=fake owned=OWNED"
}

t_filters() {  # an imported list blocks the domain and its subdomains, others pass
  hook PRO --ez on true >/dev/null
  connect_cloudflare || return 1
  hook TESTLIST --es domains e2e-blocked.example.org >/dev/null
  wait_for "busy=0.*" 20 >/dev/null; wait_for "lists=.*e2e-test" 10 >/dev/null || fail "list not active: $(state)" || return 1
  "$ADB" logcat -c </dev/null
  local sub="s$RANDOM.e2e-blocked.example.org" ctl="c$RANDOM.example.com"
  dns "e2e-blocked.example.org" >/dev/null; dns "$sub" >/dev/null; dns "$ctl" >/dev/null
  local log; log="$(queries 50)"
  expect "apex blocked"     "$log" "BLOCKED e2e-blocked\.example\.org" &&
  expect "subdomain blocked" "$log" "BLOCKED ${sub//./\\.}" &&
  expect "control forwarded" "$log" "FORWARD ${ctl//./\\.}" &&
  expect "real lookup works" "$(dns example.com)" "^RESOLVED [0-9]"
  local ok=$?
  hook TESTLIST --ez remove true >/dev/null
  return $ok
}

t_pause() {    # pause/resume through the real UI
  hook PRO --ez on true >/dev/null
  connect_cloudflare || return 1
  local r; r="$(flow testing/maestro/pause_resume.yaml)" || fail "flow: $r" || return 1
  traffic; wait_for "vpn=connected" 20 >/dev/null || fail "not reconnected: $(state)"
}

t_autoresume() {  # a timed pause ends by itself
  hook PRO --ez on true >/dev/null
  connect_cloudflare || return 1
  hook PAUSE --ei seconds 8 >/dev/null
  wait_for "vpn=paused\(manual" 10 >/dev/null || fail "did not pause: $(state)" || return 1
  sleep 9; traffic
  wait_for "vpn=connected" 20 >/dev/null || fail "did not auto-resume: $(state)"
}

t_schedule() {  # outside the window → off by schedule; inside → back on
  hook PRO --ez on true >/dev/null
  connect_cloudflare || return 1
  hook SCHEDULE --ez on true --ei start_in 60 --ei end_in 120 >/dev/null
  hook TICK >/dev/null
  wait_for "vpn=paused\(schedule" 10 >/dev/null || fail "not paused by schedule: $(state)" || { hook SCHEDULE --ez on false >/dev/null; return 1; }
  local r; r="$(flow testing/maestro/assert_visible.yaml -e TEXT="Off by Schedule")" || fail "UI: $r"
  hook SCHEDULE --ez on true --ei start_in -1 --ei end_in 60 >/dev/null
  hook TICK >/dev/null; sleep 2; traffic
  wait_for "vpn=connected" 20 >/dev/null || fail "not resumed by schedule: $(state)"
  local ok=$?
  hook SCHEDULE --ez on false >/dev/null
  return $ok
}

# ── Run ───────────────────────────────────────────────────────────────────────
if [ $BUILD -eq 1 ]; then
  ./gradlew :app:assembleDebug --console=plain -q >"$OUT/build.log" 2>&1 \
    || { echo "BUILD FAILED — see $OUT/build.log"; grep -E "^e:" "$OUT/build.log" | head -5; exit 1; }
  echo "build: ok"
fi
ensure_device || exit 1
[ $BUILD -eq 1 ] && echo "install: $(install_app)"
# Exempt from Doze so the battery-optimization prompt never covers the UI mid-test.
"$ADB" shell dumpsys deviceidle whitelist +$PKG </dev/null >/dev/null
launch; sleep 3
[ -n "$(state)" ] || { echo "ERROR debug hooks not reachable — is a debug build installed?"; exit 1; }
hook PRO --ez on true >/dev/null   # tests run as Pro unless they say otherwise

for t in "${SELECTED[@]}"; do
  start=$(date +%s)
  if "t_$t"; then PASSED=$((PASSED+1)); echo "PASS $t ($(( $(date +%s)-start ))s)"
  else FAILED=$((FAILED+1)); FAILS+=("$t"); echo "FAIL $t ($(( $(date +%s)-start ))s)"
       shot "fail-$t" >/dev/null; "$ADB" logcat -d -t 300 </dev/null > "$OUT/logcat-$t.txt"; fi
done
echo "── $PASSED passed, $FAILED failed${FAILS:+ (${FAILS[*]})} · state: $(state)"
[ $FAILED -eq 0 ]
