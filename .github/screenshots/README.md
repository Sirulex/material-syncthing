# Screenshots

Add screenshots here and reference them from the root `README.md`.

## Naming Convention

| Filename | Content |
|---|---|
| `home_light.png` | Home overview tab (light mode) |
| `folders_light.png` | Folders list with filter chips (light mode) |
| `devices_light.png` | Devices list (light mode) |
| `settings_light.png` | Settings screen (light mode) |
| `home_dark.png` | Home overview tab (dark mode) |
| `folder_detail_dark.png` | Folder detail with actions (dark mode) |
| `insights_dark.png` | Insights / bandwidth chart (dark mode) |
| `add_device_dark.png` | Add device with QR + local ID (dark mode) |

## How to Capture

Use Android Studio's **Device Mirror** or `adb`:

```bash
# Light mode
adb shell cmd uimode night no
# ... capture screenshots ...

# Dark mode
adb shell cmd uimode night yes
# ... capture screenshots ...
```

Recommended: Pixel 8 Pro emulator at 1080x2400 for consistent aspect ratio.
