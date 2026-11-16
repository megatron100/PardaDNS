# PardaDNS App Code Index

Package root: `app/src/main/java/com/example/pardadns/`. Entry points, then layers.

## Entry points and app wiring

| File | Declares | Role |
|---|---|---|
| `PardaDnsApplication.kt` | `PardaDnsApplication` | Application class (Hilt root) |
| `MainActivity.kt` | `MainActivity` | Single activity, hosts Compose UI |
| `di/AppModule.kt` | `AppModule` (abstract) | Hilt bindings for repositories and DB |
| `service/PardaTileService.kt` | `PardaTileService` | Quick Settings tile to toggle VPN |

## Service layer (`service/`)

| File | Declares | Role |
|---|---|---|
| `PardaVpnService.kt` | `PardaVpnService`, `VpnServiceEntryPoint` | Android `VpnService`; owns the tunnel |
| `DnsForwarder.kt` | `DnsForwarder` (internal) | Forwards intercepted DNS queries to upstream providers |
| `DnsPacketUtils.kt` | `DnsPacketUtils` (internal object) | Parses and builds DNS packets |
| `StatsBatcher.kt` | `StatsBatcher`, `PendingRecord` (internal) | Batches query stats before writing to DB |
| `TrafficMonitor.kt` | `TrafficMonitor`, `TrafficSnapshot` | Per-app traffic sampling |

## Domain layer (`domain/`)

| File | Declares | Role |
|---|---|---|
| `event/VpnEventBus.kt` | `VpnEvent`, `VpnEventBus` | VPN event bus |
| `model/VpnState.kt` | `VpnState` (sealed) | Connection state |
| `model/VpnAction.kt` | `VpnAction` (sealed) | User/system VPN intents |
| `usecase/ToggleVpnUseCase.kt` | `ToggleVpnUseCase` | Start/stop VPN |

## Data layer (`data/`)

**Models**
- `model/AppInfo.kt`: `AppInfo` (installed app)
- `model/DnsProvider.kt`: `DnsProvider` (upstream resolver config)

**Room database (`data/db/`)**
- `PardaDatabase.kt`: `PardaDatabase` (abstract `RoomDatabase`)
- `StatsDao.kt`: `StatsDao` (interface)
- `LifetimeTotalsRow.kt`: `LifetimeTotalsRow`
- Entities in `db/entity/`:
  - `QueryLogEntity`: individual DNS queries
  - `DomainStatsEntity`: per-domain aggregates
  - `AppQueryStatsEntity`: per-app aggregates
  - `HourlyStatsEntity`, `DailyStatsEntity`: time-bucketed aggregates
  - `DnsLatencyEntity`: provider latency samples

**Repositories (`data/repository/`)**
- `DnsRepository` (interface) / `DnsRepositoryImpl`: DNS provider config and selection
- `StatsRepository` (interface, plus `LifetimeTotals`) / `StatsRepositoryImpl`: stats reads and writes
- `BlocklistRepository`: blocked domains
- `AppFilterRepository`: per-app VPN include/exclude
- `RemoteConfigRepository`: remote feature/config flags
- `ThemeRepository`: persisted theme choice

## UI layer (`ui/`)

**Navigation and top-level screens**
- `AppNavHost.kt`: `AppNavHost`, the navigation graph
- `SplashScreen.kt`: `SplashScreen`
- `PardaMainScreen.kt`: `PardaMainScreen` (home)
- `MainViewModel.kt`: `MainViewModel`
- `SettingsScreen.kt` / `SettingsViewModel.kt`
- `AboutScreen.kt`: `AboutScreen`
- `ProPaywallScreen.kt`: `ProPaywallScreen`
- `StatsComingSoonScreen.kt`: `StatsComingSoonScreen`

**DNS and providers**
- `AddProviderScreen.kt`: `AddProviderScreen` (custom provider, with validation and test)
- `DohWarningDialog.kt`: `DohWarningDialog`
- `BatteryOptimizationDialog.kt`: `BatteryOptimizationDialog`
- `BlocklistScreen.kt`: `BlocklistScreen`
- `AppFilterScreen.kt`: `AppFilterScreen`

**Analytics and logs**
- `AnalyticsScreen.kt`: `AnalyticsScreen`, charts and cards
- `AllQueriesScreen.kt`: `AllQueriesScreen`, `AllQueriesType`
- `QueryLogScreen.kt`: `QueryLogScreen`, `QueryFilter`
- `DnsPerformanceScreen.kt`: `DnsPerformanceScreen`

**Analytics ViewModels (`ui/analytics/`)**
- `AnalyticsViewModel.kt`: `AnalyticsViewModel`, `ChartSeries`, `ChartGranularity`, `AppEntry`
- `AllQueriesViewModel.kt`
- `QueryLogViewModel.kt`
- `DnsPerformanceViewModel.kt`

**Shared components (`ui/components/`)**
- `ShieldHeader.kt`: `ShieldHeader`
- `CollapsibleShieldLayout.kt`: `CollapsibleShieldLayout`
- `ProviderCard.kt`: `ProviderCard`

**Theme (`ui/theme/`)**
- `Theme.kt`: `PardaDNSTheme`
- `Color.kt`, `Type.kt`, `ThemeMode.kt` (`ThemeMode`: SYSTEM, LIGHT, DARK)

## Tests
- `app/src/test/.../ExampleUnitTest.kt`: template only
- `app/src/androidTest/.../ExampleInstrumentedTest.kt`: template only

## Resources and config
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/`: `values/` (strings, colors, themes), `values-v31/`, `drawable/`, `mipmap-*`, `xml/` (backup and data extraction rules)
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/google-services.json` (gitignored; copy from `google-services.json.example`)
- Root: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`

## Notes on the index
- Scope: Kotlin sources under `app/src/main`. Declarations are the top-level ones found by search. Private composables are listed only where they are notable.
- Not covered: per-function signatures and line counts.
