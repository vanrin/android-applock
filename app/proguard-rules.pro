# BiometricPrompt uses reflection on some OEM builds; keep the androidx.biometric package intact.
-keep class androidx.biometric.** { *; }
