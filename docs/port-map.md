# Port map — Catfriend1 → material-syncthing

Legend: ⬜ todo · 🟨 in progress · ✅ done · ❌ rewrite (not port)

## Ported to Kotlin (structure preserved)

| Source (Catfriend1)                                      | Target (material-syncthing)                              | Status |
|----------------------------------------------------------|----------------------------------------------------------|--------|
| `service/SyncthingRunnable.java`                         | `core-native/native/NativeLauncher.kt`                   | ⬜     |
| `service/SyncthingService.java`                          | `core-service/service/SyncthingService.kt`               | ⬜     |
| `util/ConfigXml.java`                                    | `core-native/native/ConfigBootstrapper.kt`               | ⬜     |
| `service/NotificationHandler.java`                       | `core-service/service/NotificationController.kt`         | ⬜     |
| `service/RunConditionMonitor.java`                       | `data/SyncConstraints.kt`                                | ⬜     |
| `service/EventProcessor.java`                            | `core-api/events/EventStream.kt`                         | ⬜     |
| `receiver/BootReceiver.java`                             | `core-service/service/BootReceiver.kt`                   | ⬜     |
| `receiver/AppConfigReceiver.java`                        | `core-service/service/AppConfigReceiver.kt`              | ⬜     |
| `service/ReceiverManager.java`                           | `core-service/service/ReceiverManager.kt`                | ⬜     |
| `service/Constants.java`                                 | (folded into relevant Kotlin files)                      | ⬜     |
| `service/AppPrefs.java`                                  | `data/SettingsStore.kt` (partial — pref key names)       | ⬜     |

### Notes on specific ports

- **SyncthingRunnable → NativeLauncher:** Catfriend1 handles exit codes (0=shutdown, 3=restart, 1/2/9/64=crash), Android 14+ gateway IP fallback, MulticastLock for local discovery, log trimming to 200K lines, and env var setup (STNOUPGRADE, STNORESTART, STHASHING, FALLBACK_NET_GATEWAY_IPV4). All must be ported.
- **SyncthingService:** Complex state machine (INIT→STARTING→ACTIVE→DISABLED→ERROR). Handles deferred shutdown while STARTING to avoid ANR. Config export/import with AES-256 zip encryption. WebUI port availability check before launch.
- **RunConditionMonitor:** 11+ condition checkers (WiFi, charging, battery saver, flight mode, time schedule, metered WiFi, mobile data, roaming, whitelisted WiFi). Per-object custom sync conditions for folders/devices. Reboot detection edge case.
- **EventProcessor:** 20+ event types. MediaScanner integration for file updates. Out-of-disk error detection. Skips MediaScanner for receive-encrypted folders.
- **NotificationHandler:** Two persistent channels (running vs waiting). Deterministic notification IDs. Crash/consent/device-connect/folder-share notifications.
- **ConfigXml:** DOM-based XML parser for config.xml at startup (before REST available). BCrypt password hashing. Folder marker file detection after import.
- **DeviceStateHolder** does not exist as separate file in Catfriend1. State (LocalCompletion, RemoteCompletion, online device count) tracked in RestApi.java. Will be folded into repositories.

## Rewritten (no source reuse)

| Source (Catfriend1)                                      | Reason                                                   | Status |
|----------------------------------------------------------|----------------------------------------------------------|--------|
| `service/RestApi.java`                                   | Replaced with Ktor + kotlinx.serialization               | ❌     |
| `activities/*.java`                                      | Compose rewrite, no 1:1 mapping                          | ❌     |
| `fragments/*.java`                                       | Compose rewrite, no 1:1 mapping                          | ❌     |
| `res/layout/*.xml`                                       | Compose rewrite, no XML layouts                          | ❌     |
| `util/Util.java`                                         | Replaced with Kotlin stdlib + extension fns              | ❌     |
| `util/FileUtils.java`                                    | Replaced with Kotlin stdlib + extension fns              | ❌     |
| `util/PermissionUtil.java`                               | Replaced with modern permission APIs                     | ❌     |
| `util/TextWatcherAdapter.java`                           | Not needed in Compose                                    | ❌     |
| `util/Compression.java`                                  | Simple enum, inline where needed                         | ❌     |
| `util/ConfigRouter.java`                                 | Replaced with Ktor client routing                        | ❌     |
| `util/JobUtils.java`                                     | Not needed, using coroutines                             | ❌     |
| `util/Luhn.java`                                         | Device ID validation, rewrite in Kotlin                  | ❌     |
| `service/SyncthingServiceBinder.java`                    | Not needed, using StateFlow for service comms            | ❌     |
| `service/SyncTriggerJobService.java`                     | Replaced with WorkManager / direct service control       | ❌     |
| `service/QuickSettingsTile*.java`                         | Out of scope for v0.1                                    | ❌     |
| `service/TestData.java`                                  | Test fixtures, rewrite as needed                         | ❌     |
| `model/*.java` (27 model classes)                        | Rewrite as @Serializable data classes in core-api/dto/   | ❌     |

## Not ported (dead/irrelevant)

| Source (Catfriend1)                                      | Reason                                                   |
|----------------------------------------------------------|----------------------------------------------------------|
| `activities/WebGuiActivity.java`                         | No embedded WebView UI                                   |
| `activities/WebViewActivity.java`                        | No embedded WebView UI                                   |
| `activities/PhotoShootActivity.java`                     | Camera feature out of scope                              |
| `activities/ThemedAppCompatActivity.java`                | AppCompat base class, not needed                         |
| `util/Languages.java`                                    | Android handles locale natively now                      |
| `util/extensions.kt`                                     | Will write own extensions as needed                      |
