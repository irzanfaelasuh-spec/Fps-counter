# FPS+ VECTOR

FPS+ VECTOR is a native Android floating performance HUD. It includes a nine-second animated startup, a permissions setup flow, a compact live dashboard, configurable overlay metrics, draggable positioning, resizing, and a GitHub Actions APK build.

## Included telemetry

- **FPS:** reads SurfaceFlinger TimeStats through the user's Shizuku-granted shell identity. It only reports a value when it can match the foreground app's layer and calculate a new frame sample. Unsupported firmware, missing layer data, or denied Shizuku access is displayed as `--` rather than a guessed number.
- **RAM:** system-wide used and total memory from `ActivityManager.MemoryInfo`.
- **Battery:** current charge percentage from the Android battery broadcast.
- **Temperature:** battery sensor temperature, when the device exposes it. This is not CPU or GPU temperature.

SurfaceFlinger frame statistics are device/firmware dependent. The app cannot guarantee accurate FPS access on every Android build. The monitoring service also adds a small amount of system work, especially at shorter sampling intervals.

## Permissions

1. Display over other apps — required to draw the floating HUD.
2. Shizuku access — required for the SurfaceFlinger FPS sampling path. Shizuku must already be installed and its service running.
3. Notifications — used for the persistent foreground-service notification on Android 13+.
4. Ignore battery optimizations — requested so Android battery management is less likely to suspend the long-running monitor. This is a user-controlled special access.

The app does not start Shizuku by itself. Start Shizuku using its own supported setup, then return to FPS+ VECTOR and grant access. FPS+ VECTOR uses Shizuku UserService rather than the deprecated direct process API.

## Build APK with GitHub Actions

1. Create a GitHub repository and upload the contents of this folder to its root.
2. Make sure the default branch is named `main` or `master`.
3. Open **Actions** and choose **Build FPS+ VECTOR APK**.
4. Run the workflow, or push a commit to the default branch.
5. Open the completed run and download the `FPS-Plus-Vector-debug` artifact. The APK is inside the downloaded ZIP.

The workflow runs a static project check and then builds `app-debug.apk` with Gradle 8.9, Android Gradle Plugin 8.7.3, and JDK 17.

## Local build

Open this folder as an Android Gradle project in Android Studio, allow Gradle to sync, then run the `assembleDebug` task.

## Notes

- Minimum Android version: Android 8.0 (API 26).
- Target SDK: Android 15 (API 35).
- The overlay runs as a foreground service with the Android `specialUse` foreground-service type because its purpose is a user-started, persistent floating performance HUD.
- The background-activity exemption is optional from a technical perspective, but this first-run setup asks for it to match the requested setup gate.
- The app does not claim to read CPU/GPU temperatures or per-process RAM. Those values require device-specific interfaces and may not be exposed by the firmware.
