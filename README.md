# PardaDNS

An Android app that blocks ads, trackers and malware **system-wide** by routing DNS through an on-device VPN tunnel. No traffic leaves your phone except DNS queries sent to the resolver you pick.

Built with Kotlin, Jetpack Compose, Hilt, Room and Firebase.

## Features

- **Local VPN tunnel** (`VpnService`): intercepts DNS only. Regular traffic is not proxied.
- **Choose your resolver:** AdGuard, Cloudflare (incl. security/family variants), Quad9, Mullvad, OpenDNS, CleanBrowsing, Google, NextDNS, Control D and more, or add a custom provider.
- **Built-in blocklists:** OISD, StevenBlack, AdGuard DNS filter, URLhaus, Phishing Army, plus adult-content lists. Lists are matched on-device through a hash index.
- **Per-app control:** exclude chosen apps from the tunnel.
- **Analytics:** queries and blocks by hour, day, domain and app; a searchable query log; DNS latency per provider. Everything is stored locally and auto-purged.
- **Automation:** timed pause, auto-resume and protection schedules.
- **Quick Settings tile** to toggle protection.
- **Pro unlock:** one-time Google Play purchase (`pro_lifetime`), gated by Firebase Remote Config.

## Project layout

```
app/src/main/java/com/example/pardadns/
  billing/      Play Billing wrapper
  data/         Room DB, repositories, blocklist parser and catalog
  di/           Hilt modules
  domain/       VPN state, events, use cases
  service/      VpnService, DNS forwarder/packet parser, scheduler, tile
  ui/           Compose screens and view models
testing/        adb debug hooks, Maestro flows and an end-to-end script
```

A file-by-file map is in [docs/APP_INDEX.md](docs/APP_INDEX.md).

## Build

Requirements: JDK 21 (Android Studio's bundled JBR works) and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug
```

Windows users can run `./build-install.ps1` to build, install on a connected device and tail the logs.

### Firebase

Firebase Analytics, Crashlytics and Remote Config are used. The repository ships a placeholder `app/google-services.json.example`; the build copies it to `app/google-services.json` if none exists, so the app compiles out of the box (Firebase features will not report anywhere). To enable them, create a Firebase project for package `com.panda.dns`, download its `google-services.json` into `app/`, and publish `remoteconfig.template.json` as your Remote Config template.

### Release signing

Copy `keystore.properties.example` to `keystore.properties` and point it at your own keystore. Never commit either the keystore or the properties file.

## Testing

Unit tests live in `app/src/test`. On-device end-to-end tests (paywall gating, fake Play purchase, filters, pause/resume, schedule) are described in [testing/README.md](testing/README.md).

```bash
./gradlew test
testing/scripts/e2e.sh
```

## Privacy

DNS queries are processed on-device and forwarded only to the resolver you select. Firebase collects anonymous usage and crash data. See [docs/privacy-policy.html](docs/privacy-policy.html), and replace the placeholder contact email before publishing the app.

## Third-party blocklists

Blocklists are downloaded at runtime from their original publishers and remain under their own licences.

## License

MIT, see [LICENSE](LICENSE).
