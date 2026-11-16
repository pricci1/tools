# Gray Wake

A minimal, local-only Android app whose primary UI is one **Grayscale** Quick Settings tile.
No Tasker, root, accessibility service, network access, analytics, or third-party runtime libraries.

> **Validation status:** Android lint, the debug APK build, 25 domain tests and 10
> settings-adapter tests pass locally, along with syntax/structure checks.
> **No Android device tests have run.** The repository's root-level
> `.github/workflows/gray-wake-android.yml` builds and uploads a debug APK on push.

## Behavior

```text
Disabled → Enabled → Snooze until next wake → Snooze 5 min → Snooze 15 min → Snooze 30 min
         → Snooze 45 min → Snooze 60 min → Snooze 12 h → Disabled → …
```

| Selection | Display behavior | Monitoring |
| --- | --- | --- |
| Disabled | Turns Android color correction off immediately | Service stopped |
| Enabled | Applies grayscale immediately and again on each screen wake | Running |
| Snooze until next wake | Turns color correction off immediately; resumes Enabled when the screen next turns on | Running, no timer |
| Timed snooze | Turns color correction off immediately; resumes Enabled at expiry | Running |

Wake snooze lasts until the next **screen-on event**, not merely an unlock, clock change,
or service restart. It is saved across process recreation. Tap again before the next wake
to select Snooze 5 min. Each timed snooze tap starts a **new duration from that tap**,
not an extension of the previous one. Both wake and timed snoozes return to **Enabled**;
the next normal tap then selects Snooze until next wake. The tile displays the selected
preset and, for timed snoozes, remaining minutes where Android supports a subtitle.
Enabled is highlighted; snoozed uses a clock icon and is not highlighted.

While awake, a main-thread timer handles expiration. While the device is asleep, the app
does not wake the CPU just to change colors. The next screen-on event checks the stored
elapsed-time deadline and applies grayscale when due. This means an expired snooze may
remain stored until the next wake, but must not receive extra time just because the phone
slept. There can be normal system broadcast/display-update latency on wake.

If monitoring was stopped by Android, the tile shows **Tap to resume**; that recovery tap
resumes the existing selection rather than advancing it. Permission/write errors open the
setup page. These are recovery exceptions to the normal nine-state tap loop.

## Build

The project uses Java 17 source, native Android views, AGP **8.13.2**, and Gradle **8.13**.
Minimum Android **8.0 / API 26**; compile and target SDK **36**. API levels describe the
configured compatibility range, not devices already tested.

Install JDK 17 or 21 and Android SDK packages `platforms;android-36`, `build-tools;35.0.0`,
and `platform-tools`. Set `ANDROID_HOME` to the SDK location, or use Android Studio to
create `local.properties` with `sdk.dir=/absolute/path/to/your/android-sdk`.

```sh
./gradlew --no-daemon :app:lintDebug :app:coreTest :app:assembleDebug
```

Windows:

```powershell
.\gradlew.bat --no-daemon :app:lintDebug :app:coreTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

**First build needs Internet access.** Since a binary wrapper could not be downloaded in
this environment, the launchers first fetch the **official Gradle 8.13 wrapper JAR**, verify
its pinned SHA-256 checksum, and then launch it. The Gradle distribution checksum is also
pinned. Unix needs `curl` or `wget` and `sha256sum` or `shasum`; Windows uses PowerShell.
No arbitrary download is executed without verification. Run `./gradlew --version` (or
`gradlew.bat --version`) once before opening the project in Android Studio, so its wrapper
JAR is present. The downloaded JAR is ignored by git.

Alternatively, put this repository on GitHub and run the included **Android build**
workflow. It runs tests, Android lint, and `assembleDebug`, then uploads `gray-wake-debug`.
The workflow does not deploy an app or upload it to a store. CI debug signing keys may
change between clean runners; use your own stable signing key for long-term updates.

## Install and grant the one required privileged permission

Enable USB debugging and authorize your computer on your own Android phone, then run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant dev.graywake android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n dev.graywake/.MainActivity
```

Or, on macOS/Linux with ADB already installed:

```sh
./tools/install-debug.sh
```

That script builds, installs, grants the permission, and opens setup. Set `ANDROID_SERIAL`
when more than one device is connected. The app does not grant this permission to itself.
No root is required on standard Android builds that permit the ADB development grant;
some manufacturers impose additional USB-debugging/security restrictions.

In the app, tap **Add Quick Settings tile**. Android 13+ can display the system add-tile
prompt. On older versions, open Quick Settings → Edit and drag **Grayscale** into the
active tiles. The app starts **Disabled**. Tap the tile once to enable it.

Daily operation is entirely through ordinary tile taps. Long-pressing the tile opens the
small setup/recovery page. That page also has **Retry / resume**, a safety **Disable and
restore color** button, optional notification access, and a shortcut to app/battery settings.

If replacing an existing Tasker profile, disable that profile so it cannot override snooze.

## Permissions and background behavior

`WRITE_SECURE_SETTINGS` permits writing these Android secure settings:

```text
Enabled: accessibility_display_daltonizer = 0
         accessibility_display_daltonizer_enabled = 1
Off:     accessibility_display_daltonizer_enabled = 0
```

`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` support the live screen-on
receiver. Its low-importance notification is present while enabled or snoozed. Android
13+ notification permission (`POST_NOTIFICATIONS`) is **optional**: denying it does not
prevent the foreground service from starting, but Android controls where its status appears.
`RECEIVE_BOOT_COMPLETED` restores an already-active selection after reboot or app update.

There is **no Internet, wake-lock, exact-alarm, location, storage, overlay, or accessibility
service permission**. There is no polling loop or scheduled wakeup alarm. State is stored
locally in device-protected preferences and backups are disabled.

Some OEM battery managers can still kill foreground services. Allow unrestricted battery
usage in that app's system settings if needed. Explicit force-stop cannot be bypassed;
open the app and use **Retry / resume** afterward. Removing the tile does not disable an
already-active selection: use the notification's Disable action or the setup page.

## Important color-setting limitations

This app uses Android's **shared color-correction setting**, not a private display overlay.
It replaces the selected correction mode with monochromacy. **Snooze and Disable turn
correction off; they do not restore a previous color-vision correction mode.** Do not use
this unchanged implementation when preserving such a correction is essential.
Other features, including Digital Wellbeing/Bedtime grayscale, may affect color separately.

**Disable before uninstalling, clearing data, or force-stopping the app.** Those operations
do not automatically undo the system setting. To recover color manually through ADB:

```sh
adb shell settings put secure accessibility_display_daltonizer_enabled 0
```

Disable the app's automation first if it is still running, or it will reapply grayscale on
its next wake event. Other grayscale features may also need to be turned off separately.

## Tests

These checks need only a JDK and Python 3, not Android SDK or network access:

```sh
./tools/test-core.sh
./tools/test-settings.sh
python3 tools/check-project.py
python3 tools/test-bootstrap.py
```

The 25 core cases execute 15,240 assertions, including 5,000 seeded randomized transitions.
The 10 adapter cases execute 20 assertions using explicitly fake Android provider classes.
Fakes live under `tools/fakes` and are **not included in the app**. Adapter tests exercise
production setting-write/rollback logic, not real platform behavior.

`check-project.py` verifies resource references, manifest permissions/exported components,
XML and shell syntax, and parses all application Java sources. Syntax parsing **does not**
substitute for Android SDK compilation or lint. See `docs/TESTING.md` for the real-device
acceptance checklist and `docs/VALIDATION.md` for the exact validation scope.

## Layout and history

- `app/src/main/java/dev/graywake/core`: immutable state, deadlines, and transition engine.
- `MonitorService`, `GrayscaleSettings`, `StateStore`, `BootReceiver`: Android integration.
- `GrayTileService`, `MainActivity`: the tile and setup/recovery UI.
- `tools`: offline tests, verified wrapper bootstrap, and install helper.
- `.github/workflows/android.yml`: test, lint, and APK build workflow.

Changes were committed incrementally in a **local git repository**, not pushed to a remote.
The source ZIP is a snapshot. The companion `gray-wake.bundle` contains the full history:

```sh
git clone gray-wake.bundle gray-wake
cd gray-wake
git log --oneline
```

Architecture rationale and primary Android/Gradle documentation links are in
`docs/DECISIONS.md`.
