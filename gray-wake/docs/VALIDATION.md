# Validation performed in this delivery

## Wake snooze feature validation

- `tools/test-core.sh`: **25 cases, 15,240 assertions**, including the new wake snooze,
  persistence/recreation, the nine-state cycle, and timed snoozes across screen wakes.
- `tools/test-settings.sh`: **10 cases, 20 assertions** against a fake provider.
- `tools/check-project.py` and `tools/test-bootstrap.py`: passed.
- `./gradlew --no-daemon :app:lintDebug :app:coreTest :app:assembleDebug`: **BUILD SUCCESSFUL**
  with JDK 21, Android SDK 36 and build-tools 35.0.0. The debug APK signature verifies,
  and its packaged resources contain the new wake snooze labels and tap cycle.
- No connected Android device: native UI rendering and real screen-on broadcasts were
  not tested. The updated device checklist remains in `TESTING.md`.

The sections below record validation of the original source snapshot, before import.

## Passed

- Core domain: **24 cases, 15,204 assertions**. Compiled with `javac --release 17
  -Xlint:all -Werror` and executed with assertions enabled.
- Settings adapter: **10 cases, 20 assertions** against a fake provider. The actual
  `GrayscaleSettings.java` implementation is compiled into this test harness.
- All **14 application/test Java files** parsed successfully with the JDK compiler parser.
  This is syntax validation, not Android type-checking.
- All **8 Android XML files** parse; referenced string/drawable resources and manifest
  component source files exist.
- Manifest: only the five documented permissions; private monitor/boot receiver;
  SystemUI-protected tile; no manifest-only SCREEN_ON receiver; direct-boot monitoring.
- POSIX shell scripts pass `sh -n`.
- Wrapper bootstrap refuses an uncached binary in offline mode and refuses a binary
  whose checksum differs from the pinned official checksum.

The host provides OpenJDK 21. Tests use Java 17 source/API compatibility. Fake Android
classes are isolated under `tools/fakes`, outside all Android source sets.

## Not performed / blocked

- **Android Gradle build and Android lint:** no Android SDK or Gradle distribution installed;
  host build downloads fail DNS/network access. The wrapper's offline check also confirms
  the official binary is not cached.
- **APK generation:** no installable APK is included or claimed.
- **Device/emulator tests:** none run; no physical device or emulator is available.
- **GitHub Actions:** workflow authored, not pushed or executed.
- **Windows PowerShell launcher execution:** not run on this Linux host.
- **Mac-specific execution:** not run; bootstrap uses a portable mktemp template.

## Reproduce the completed checks

```sh
./tools/test-core.sh
./tools/test-settings.sh
python3 tools/check-project.py
python3 tools/test-bootstrap.py
```

## Required next validation

On a connected Android development machine, execute the build gate in `TESTING.md`,
install the resulting APK, grant the secure-settings permission, and run the acceptance
checklist. OEM background restrictions and color-correction behavior require device testing.
