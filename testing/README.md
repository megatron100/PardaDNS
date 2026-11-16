# testing/

On-device test tooling for PardaDNS. Everything test-only lives here. The only hooks into the app are a `debug` source-set entry in `app/build.gradle.kts` and a debug-only query log line in `PardaVpnService`.

| Path | What it is |
|---|---|
| `debug-hooks/` | A broadcast receiver compiled into **debug builds only**. It sets app state over adb (Pro, fake Play billing, connect, pause, schedule, filters) and replies with one line. |
| `maestro/` | [Maestro](https://maestro.dev) UI flows. They tap and assert by visible text. |
| `scripts/device.sh` | Shell helpers (`hook`, `state`, `wait_for`, `dns`, `queries`, `texts`, `shot`, `flow`). Each prints one short line. |
| `scripts/e2e.sh` | The end-to-end suite: build → install → tests. It prints one line per test. |
| `out/` | Logs, screenshots and logcat from failures (gitignored). |

## Run

```bash
testing/scripts/e2e.sh                  # build + install + all tests
testing/scripts/e2e.sh --no-build pause # reuse the installed APK, one test
```

Tests: `gating` (free users hit the paywall), `purchase` (buying Pro with the fake Play unlocks the feature), `filters` (an imported list blocks a domain and its subdomains), `pause` (pause and resume in the UI), `autoresume` (a timed pause ends by itself), `schedule` (off outside the hours, back on inside).

**Setup the suite needs:**
- **JDK 21:** in `~/.jdks`.
- **Android SDK:** with an emulator or a connected device. `ensure_device` boots the first AVD if nothing is connected.
- **Maestro CLI:** in `~/.maestro-cli`, or on your `PATH`.
- **VPN consent:** granted once by connecting from the UI.

## Debug hooks by hand

```bash
source testing/scripts/device.sh
state                                    # vpn=connected(Cloudflare) pro=true filters=56968 lists=... busy=0 schedule=off
hook PRO --ez on false                   # force Pro off (debug override; `--ez clear true` removes it)
hook BILLING --es result pending         # fake Play: next "Upgrade to Pro" is success|cancel|pending (reset = owns nothing)
hook CONNECT --es provider cloudflare    # app must be in the foreground on Android 12+
hook PAUSE --ei seconds 10
hook SCHEDULE --ez on true --ei start_in 60 --ei end_in 120 && hook TICK
hook TESTLIST --es domains ads.example.org
dns ads.example.org                      # BLOCKED | RESOLVED 1.2.3.4
queries 5                                # BLOCKED ads.example.org / FORWARD other.com
```

The full action list is documented at the top of `debug-hooks/java/.../DebugHooksReceiver.kt`.

## Adding a test
1. **UI steps:** write a Maestro flow in `maestro/`. Start it with `launchApp: { stopApp: false }` so the VPN service survives.
2. **The test itself:** add a `t_<name>` function to `scripts/e2e.sh`. Set up state with `hook`, check behaviour with `state`, `dns` or `queries`, and check the UI with `flow`.
3. **Register it:** add the name to `ALL`.
