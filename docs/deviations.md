# Intentional deviations from Catfriend1

Documented here when ported code intentionally diverges from upstream logic.

## Planned deviations

- **Min API 28 (Android 9):** Catfriend1 supports API 21+. All pre-API-28 workarounds will be dropped during port.
- **Coroutines over threads:** SyncthingRunnable uses raw Thread + Process. NativeLauncher will use coroutines with Dispatchers.IO.
- **Flow over listener callbacks:** EventProcessor uses listener interfaces. EventStream will emit to SharedFlow.
- **StateFlow over Binder:** SyncthingService communicates state via AIDL-style binder. We use StateFlow exposed via AppContainer.
- **DataStore over SharedPreferences:** All preference access via DataStore<Preferences>.
- **No AIDL/Binder pattern:** Service state shared via AppContainer singleton, not bound service.

## Deviations recorded during porting

_(Entries added as each file is ported)_
