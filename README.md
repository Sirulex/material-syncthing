# material-syncthing

Modern Android client for [Syncthing](https://syncthing.net/). Kotlin, Jetpack Compose, Material 3 Expressive. Port of [Catfriend1/syncthing-android](https://github.com/Catfriend1/syncthing-android) rebuilt from scratch.

## Status

Early development. v0.1.0. Not on Play Store yet.

## Requirements

- Android 9 (API 28) or higher
- Target SDK 35, compile SDK 36
- JDK 17

## Features

- Embedded Syncthing native binary
- Foreground `dataSync` service with boot-on-start
- Compose UI with Material 3 Expressive components
- Pull-to-refresh, loading indicators, onboarding flow
- Device and folder management
- Health aggregation for immediate state updates

## Build

```
./gradlew :app:assembleDebug
```

Install:

```
./gradlew :app:installDebug
```

## Modules

| Module          | Purpose                                           |
|-----------------|---------------------------------------------------|
| `app`           | Activity, navigation, DI wiring                   |
| `core-native`   | Native binary launcher, config bootstrap          |
| `core-service`  | Foreground service, notifications, receivers     |
| `core-api`      | Ktor REST client, event stream, DTOs              |
| `data`          | DataStore settings, repositories, sync rules      |
| `ui-core`       | Shared Compose theme and components               |

## Permissions

Internet, foreground service (dataSync), notifications, boot completed, wake lock, network/wifi state, multicast, manage external storage (API 30+).

## Architecture

- Coroutines + Flow end to end. No AIDL, no listener callbacks.
- StateFlow shared via `AppContainer` singleton.
- DataStore for preferences.
- Ktor + kotlinx.serialization for REST.

See [docs/deviations.md](docs/deviations.md) and [docs/port-map.md](docs/port-map.md) for upstream divergence.

## License

[MPL-2.0](LICENSE). See [NOTICE](NOTICE) for upstream attribution.
