# Device acceptance checklist (not yet executed)

Run on an emulator or personal test device after building and installing the APK. Suggested
coverage is API 26, 29, 33, 34, and 36, plus at least one intended physical/OEM device.
Use a screen without unrelated grayscale or accessibility color-correction automation.
Disable the old Tasker profile first.

## Inspect the actual Android settings

```sh
adb shell settings get secure accessibility_display_daltonizer
adb shell settings get secure accessibility_display_daltonizer_enabled
adb shell dumpsys activity services dev.graywake
adb logcat -s GrayWake
```

Mode `0` with enabled `1` represents this app's grayscale request. Enabled `0` means this
app's correction path is off; it does not prove that all other color transforms are off.
Inspect the actual display as well as settings and tile labels.

| Test | Expected result |
| --- | --- |
| Fresh install without ADB grant | Starts Disabled; opening setup does not alter color; tile opens setup rather than falsely claiming success |
| Grant permission and add tile | Tile initially says Disabled; no monitoring service is running |
| First tile tap | Enabled, correction mode 0 and enabled 1, quiet foreground notification |
| All nine steps and wrap | Enabled → Until next wake → 5 → 15 → 30 → 45 → 60 → 12 h → Disabled; subsequent tap enables again |
| Snooze until next wake | Correction enabled becomes 0 immediately; tile/notification say Until next wake without a countdown; screen off/on returns to Enabled and correction enabled 1 |
| Wake snooze without screen off/on | Unlock, clock changes, and Retry / resume do not end the snooze; time passing alone does not expire it |
| Tap again during wake snooze | Selects Snooze 5 min with a fresh five-minute deadline |
| Each timed snooze tap | Correction enabled becomes 0 immediately; preset duration starts from that tap, not from the first snooze |
| Rapid taps | No skipped/reordered persisted transitions; final state matches total accepted taps |
| Five-minute expiry with display awake | Returns to Enabled automatically, without another tap |
| Sleep across expiry | No app wake alarm; first subsequent screen-on event reconciles the deadline and reapplies grayscale |
| Screen wake while Enabled | If correction was manually turned off, screen off/on reapplies mode 0 and enabled 1 |
| Wake before snooze deadline | Remains snoozed with the original remaining duration |
| Notification permission denied on API 33+ | Tile/monitoring still work; platform task-manager visibility follows Android's rules |
| Disable from tile or safety button | Correction turns off; monitoring service and its notification stop |
| Remove tile | Does not silently disable active monitoring; notification/setup Disable remains available |
| Change wall clock forward/backward during a boot | Monotonic snooze remaining time does not change |
| Reboot during timed snooze | Deadline is recovered from wall time; after boot it is again monotonic and bounded to the preset |
| Process recreation during wake snooze | Preserves Until next wake; next observed screen-on event returns to Enabled |
| Reboot after snooze deadline | Returns to Enabled |
| Reboot while Disabled | Remains Disabled; no monitoring service started |
| Revoke secure-settings permission | No crash loop or silent successful transition; setup explains the missing grant |
| Re-grant after failure | Retry / resume clears the error and restores the selected mode |
| Force-stop while enabled | Android stops the service; display setting may remain. Reopen setup and Retry / resume to recover |
| Locked screen and first unlock after reboot | Monitor restores via direct boot; verify OEM-specific tile/lock-screen behavior |
| Uninstall cleanup | Disable first; uninstall should not leave this app's grayscale setting on |

To exercise wake enforcement without waiting for a timeout, while Enabled:

```sh
adb shell settings put secure accessibility_display_daltonizer_enabled 0
adb shell input keyevent KEYCODE_SLEEP
adb shell input keyevent KEYCODE_WAKEUP
adb shell settings get secure accessibility_display_daltonizer_enabled
```

Expected final value: `1` once the screen-on receiver has run. The user can perform the
same screen-off/on steps manually. The test deliberately changes a system display setting.

## Failure behavior to inspect

An unsuccessful secure-setting write must not advance the state. If setting the mode
succeeds but enabling correction fails, the adapter attempts to restore the old mode.
Disk persistence failure triggers best-effort display rollback and a visible error.
The app cannot undo writes after permission has been revoked or when Android prevents
its process from running. These conditions must be visible rather than described as success.

## Build gate

```sh
./gradlew --no-daemon :app:lintDebug :app:coreTest :app:assembleDebug
```

A successful local policy test is not a substitute for this gate. Record the Android build,
API level, manufacturer, and observed test outcomes before calling the app device-verified.
