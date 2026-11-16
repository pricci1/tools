# Design decisions

1. The tile cycles the requested eight states; there are no menus or long-press
   gestures in the normal workflow. Long press is reserved for setup/recovery.
2. One native foreground service dynamically registers SCREEN_ON while enabled or
   snoozing. A manifest-only SCREEN_ON receiver would not work.
3. Timers use monotonic elapsed time during a boot. Persisted wall time is only a
   reboot fallback. Expired snoozes return to Enabled before processing a new tap.
4. Do not acquire wake locks, poll, or request exact-alarm access. A Handler handles
   expiry while awake; SCREEN_ON reconciles elapsed time after deep sleep.
5. System color correction is shared with Accessibility settings. Enabled chooses
   monochromacy; snooze/disable turns correction off. Other color-correction modes
   are not restored. Explain this in setup and the README.
6. Disabled is the initial state. Opening the setup page must not change display
   settings while disabled. The app cannot grant its own privileged permission.
7. Local git history is the deliverable; no remote repository was specified.

Primary references:
- https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_ON
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/ui/views/quicksettings-tiles
- https://developer.android.com/develop/background-work/services/alarms
- https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/provider/Settings.java
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://gradle.org/release-checksums/

## Lifecycle and UI details

- The tile is passive, not an ACTIVE_TILE. Android binds it when Quick Settings is
  visible; a preference listener updates it while visible. Its service is never
  relied on to stay alive while the shade is closed.
- Tile taps enqueue explicit commands to the separate monitoring service. That
  service is the only state writer, serializing commands and timer/wake callbacks
  on the Android main thread.
- Disabled stops the monitor using start-ID-aware stop semantics, so an already
  queued later tap is not discarded.
- MainActivity does not automatically start or enable monitoring. It is a small
  setup and explicit-recovery page. Permission-denied taps go there.
- A paused tile resumes its selected state on its first recovery tap; normal tap
  cycling continues afterward. A failed service start is surfaced, not reported
  as successful background enforcement.
- State writes are synchronous and atomic at the SharedPreferences editor level.
  A failed save triggers best-effort rollback. System color correction itself is
  two separate settings; partial writes receive best-effort rollback as well.
- A boot counter distinguishes monotonic-clock epochs. Wall-clock fallback after
  reboot is capped by the selected duration. No implementation can know true
  elapsed time across a reboot plus arbitrary manual clock changes without an
  external trustworthy clock; this app deliberately has no network access.

Further primary references:
- https://developer.android.com/reference/android/os/Handler#postDelayed(java.lang.Runnable,long)
- https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime()
- https://developer.android.com/reference/android/provider/Settings.Global#BOOT_COUNT
- https://developer.android.com/privacy-and-security/direct-boot
- https://developer.android.com/develop/ui/views/notifications/notification-permission
- https://github.com/actions/checkout
- https://github.com/actions/setup-java
- https://github.com/actions/upload-artifact
- https://github.com/android-actions/setup-android
