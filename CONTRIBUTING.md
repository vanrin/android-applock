# Contributing

Issues and pull requests are welcome.

## Building

Requirements: JDK 17+ and the Android SDK (API 36).

```bash
./gradlew :app:assembleDebug     # capturable, for development
./gradlew :app:assembleRelease   # minified; debug-signed unless keystore.properties exists
```

## Ground rules

- **Battery is the product.** The event path (`AppLockService.onAccessibilityEvent`)
  must stay allocation-light and must never touch disk, schedule timers, poll,
  or request window content. PRs that add polling, foreground services, or
  `canRetrieveWindowContent` will be declined.
- **Privacy is the product too.** No new permissions without a strong case;
  the `INTERNET` permission is off the table entirely.
- Keep the dependency footprint small — AndroidX + Material only.
- Match the existing code style; comments explain *why*, not *what*.

## Testing changes

Emulator setup that mirrors a real device (device PIN is required for the
credential fallback):

```bash
adb shell locksettings set-pin 1234
adb shell appops set io.github.vanrin.applock SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services \
    io.github.vanrin.applock/io.github.vanrin.applock.AppLockService
adb shell settings put secure accessibility_enabled 1
```

Before opening a PR, manually verify at minimum: lock triggers on a locked
app, unlock works, immediate re-lock works after leaving the app, and a fresh
`PACKAGE_ADDED` install gets auto-locked.
