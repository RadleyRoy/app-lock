# Latch

Lock any app on your phone behind face recognition, your fingerprint, or a PIN.

Latch watches for a protected app coming to the foreground and puts a lock screen in front of
it before its content is ever visible. It uses the biometrics **already enrolled on your
device** — no separate enrolment, no biometric data ever leaves the phone or is stored by this
app.

Built for and tested on a **Samsung Galaxy S24+** (One UI, Android 15). Should work on any
device running Android 10 (API 29) or newer.

> **Not on Google Play, and not publishable there.** Latch needs `QUERY_ALL_PACKAGES` to list
> your installed apps, and it uses an `AccessibilityService` for something other than
> accessibility. Both violate Play policy. This is a sideload-only personal project. See
> [Permissions](#permissions) for exactly what it asks for and why.

---

## Features

- **Per-app locking.** Pick exactly which apps are protected.
- **Face + fingerprint.** Uses the biometrics you already have set up.
- **PIN backup.** 4-digit PIN, stored only as a salted PBKDF2 hash.
- **Intruder capture.** After repeated wrong PINs, silently photographs whoever is holding the
  phone and logs which app they were trying to open.
- **Grace periods.** Choose how soon a just-unlocked app re-locks — immediately, after a
  timeout, or when the screen goes off.
- **Survives reboots** and keeps working if One UI disables the accessibility service.

## Why face unlock behaves the way it does

On Samsung devices, face recognition is a **Class 2 (WEAK)** biometric; only the ultrasonic
fingerprint sensor is **Class 3 (STRONG)**. Android will not offer a WEAK biometric to an app
that asks for `BIOMETRIC_STRONG`, so Latch deliberately requests `BIOMETRIC_WEAK` — that is the
only way to get face unlock to appear at all.

The tradeoff is that a WEAK biometric cannot release a Keystore-backed key, so Latch does not
use a `CryptoObject`. This is the right call here: the biometric is an access gate, not a
decryption key, and your PIN is never stored in a recoverable form regardless.

If face unlock matters less to you than strength, this is a one-line change in
`BiometricAuthenticator.kt`.

## Colour

The palette is taken from a [Conceptzilla e-commerce
concept](https://dribbble.com/shots/26185810-E-Commerce-Mobile-App-Design):

| | Hex | Role |
|---|---|---|
| Ink | `#030303` | Background |
| Surface | `#141110` | Cards, keypad keys *(derived)* |
| Cocoa | `#56352C` | Elevated surfaces, gradient wash |
| **Clay** | `#965F4E` | **Accent** |
| Taupe | `#C6B9B2` | Highlight |
| Bone | `#F5F3F1` | Primary text |
| Ash | `#9A9593` | Secondary text |
| Slate | `#646363` | Outlines, disabled |
| Ember | `#B4503C` | Error *(derived)* |

Latch is dark-only by design — the palette is built around a near-black ground, and a
synthesised light theme would invert the one relationship the design depends on.

## Permissions

Latch asks for a lot, and you should know why before granting any of it.

| Permission | Why it is needed |
|---|---|
| **Accessibility Service** | The only way to know *instantly* that a protected app came to the foreground. Latch reads the package name of the focused window and nothing else — no content, no keystrokes. |
| **Display over other apps** | Two jobs: it paints the opaque shield that stops you glimpsing the protected app, and holding it is what exempts Latch from Android's ban on starting an activity from the background. Without it, locking cannot work at all. |
| **Usage access** | Fallback detection, used only if One UI turns the accessibility service off. |
| **Camera** | Intruder photos only. Never opened unless the PIN has been entered wrongly N times. Android shows its green camera indicator when this happens — that is the OS, not something Latch can or should suppress. |
| **Notifications** | For the persistent, silent notification the foreground watcher service is legally required to show. |
| **Query all packages** | To show you a list of installed apps to choose from. |
| **Run at startup** | So protection resumes after a reboot without you opening the app. |

Latch has **no internet permission**. It cannot send anything anywhere.

### One UI battery settings

Samsung's battery manager will eventually kill the watcher service and silently stop
protection. Set Latch to **Unrestricted** in `Settings → Apps → Latch → Battery`. The in-app
setup wizard links you straight there.

## Building

Requires **JDK 17** and the Android SDK (compileSdk 36). No Android Studio needed.

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Grab a signed APK from [Releases](../../releases) if you would rather not build it.

### Releases

Pushing a `v*` tag builds, tests, signs and publishes a release automatically. Signing uses a
keystore supplied through repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`); if those are absent the workflow falls back to the debug key so
the build still produces something installable.

## Project layout

```
app/src/main/java/com/radley/latch/
  data/        settings, PIN hashing, locked-app and intruder repositories
  lock/        detection services, the decision engine, session/grace handling
  security/    PBKDF2 hashing, intruder camera
  ui/          Compose screens — lock, app list, onboarding, settings, intruder log
art/           icon masters (original cyan + recoloured clay)
```

The lock engine (`LockGate`, `SessionManager`, `IntruderPolicy`, `ForegroundAppResolver`) takes
an injected clock and depends only on interfaces, so it is all pure-JVM testable — those tests
run in CI with no emulator.

## Licence

MIT. See [LICENSE](LICENSE).
