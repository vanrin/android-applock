# Privacy

AppLock collects **nothing**. There is no analytics, no crash reporting, no
account, and no server.

Verifiable guarantees, straight from the manifest and service configuration:

- **No `INTERNET` permission.** The app is technically incapable of sending
  data anywhere, even if it wanted to.
- **No screen-content access.** The accessibility service declares
  `canRetrieveWindowContent="false"`; Android never hands it view hierarchies,
  text, or anything typed on screen. It receives only the *package name* of
  the app that just came to the foreground.
- **Biometrics never leave the device.** Authentication uses Android's
  `BiometricPrompt`; AppLock only learns "success" or "failure", never
  fingerprint data.
- **All settings stay local** in the app's private storage: the list of locked
  package names and a handful of preference flags. Nothing else is stored.
