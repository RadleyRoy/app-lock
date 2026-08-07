# AppLock

Lock any app on your phone behind your fingerprint or a PIN.

AppLock watches for a protected app coming to the foreground and puts a lock screen in front of
it before its content is ever visible. It uses the biometrics **already enrolled on your
device** — no separate enrolment, no biometric data ever leaves the phone or is stored by this
app.

Built for and tested on a **Samsung Galaxy S24+** (One UI, Android 15). Should work on any
device running Android 10 (API 29) or newer.

> **Not on Google Play, and not publishable there.** AppLock uses an `AccessibilityService` for
> something other than accessibility, which violates Play policy. This is a sideload-only
> personal project. See [Permissions](#permissions) for exactly what it asks for and why, and
> [Installing](#installing) for what Play Protect will say about it.

---

## Features

- **Per-app locking.** Pick exactly which apps are protected.
- **Fingerprint unlock**, using the biometric you already have set up.
- **PIN backup.** 4-digit PIN, stored only as a salted PBKDF2 hash.
- **Intruder capture.** After repeated wrong PINs, silently photographs whoever is holding the
  phone and logs which app they were trying to open.
- **Grace periods.** Choose how soon a just-unlocked app re-locks — immediately, after a
  timeout, or when the screen goes off.
- **Survives reboots** and keeps working if One UI disables the accessibility service.

## Why there is no face unlock

Short version: **Samsung does not allow it**, and no app can work around that.

Your phone's own lock screen runs face and fingerprint at the same time because it is
system-level and talks to the biometric hardware directly. Third-party apps go through
`BiometricPrompt`, and Samsung does not publish face recognition to it — on a Galaxy S24 the
prompt offers fingerprint and nothing else.

There is a related, separate limitation worth knowing: on Samsung, face is a **Class 2 (WEAK)**
biometric while the ultrasonic fingerprint is **Class 3 (STRONG)**, and Android refuses to offer
a WEAK biometric to an app that asked for `BIOMETRIC_STRONG`. AppLock therefore requests
`BIOMETRIC_WEAK`, which is necessary for face to be *possible* — it is just not sufficient,
because Samsung never exposes it. The request is kept as-is so that face works automatically on
any device that does.

One consequence: a WEAK biometric cannot release a Keystore-backed key, so AppLock does not use
a `CryptoObject`. That is fine here — the biometric gates a screen rather than decrypting
anything, and your PIN is never stored in a recoverable form regardless.

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

AppLock is dark-only by design — the palette is built around a near-black ground, and a
synthesised light theme would invert the one relationship the design depends on.

## Permissions

AppLock asks for a lot, and you should know why before granting any of it.

| Permission | Why it is needed |
|---|---|
| **Accessibility Service** | The only way to know *instantly* that a protected app came to the foreground. AppLock reads the package name of the focused window and nothing else — no content, no keystrokes. |
| **Display over other apps** | Two jobs: it paints the opaque shield that stops you glimpsing the protected app, and holding it is what exempts AppLock from Android's ban on starting an activity from the background. Without it, locking cannot work at all. |
| **Usage access** | Fallback detection, used only if One UI turns the accessibility service off. |
| **Camera** | Intruder photos only. Never opened unless the PIN has been entered wrongly N times. Android shows its green camera indicator when this happens — that is the OS, not something AppLock can or should suppress. |
| **Notifications** | For the persistent, silent notification the foreground watcher service is legally required to show. |
| **Run at startup** | So protection resumes after a reboot without you opening the app. |

AppLock has **no internet permission**. It cannot send anything anywhere.

It also does **not** request `QUERY_ALL_PACKAGES`. The app picker needs to see apps that have a
launcher icon, and a [`<queries>`](https://developer.android.com/guide/topics/manifest/queries-element)
element grants exactly that — asking for visibility of every package on the device to read a
launcher list was never justified.

### One UI battery settings

Samsung's battery manager will eventually kill the watcher service and silently stop
protection. Set AppLock to **Unrestricted** in `Settings → Apps → AppLock → Battery`. The in-app
setup wizard links you straight there.

## Installing

### Play Protect will warn you

An app that watches which app is in the foreground and draws over other apps looks, to a
heuristic scanner, a lot like spyware — and that is a reasonable thing for a scanner to think.
Expect a warning on install; choose **More details → Install anyway**.

Dropping `QUERY_ALL_PACKAGES` removes one of the strongest signals, but it will not make the
warning disappear entirely, and no honest change to this app would.

### "For your security, this setting is currently unavailable"

Separate from Play Protect. Android 13 and later refuse to let a **sideloaded** app enable an
accessibility service, greying the toggle out with that message. It is not a bug in AppLock.

Two ways past it:

- **Install with `adb install`.** This uses a session-based installer, which the restriction
  does not apply to. The simplest route, and the recommended one.
- **Or allow it manually:** `Settings → Apps → AppLock → ⋮ (top right) → Allow restricted
  settings`, then enable the accessibility service. The menu item only appears *after* you have
  tried to enable the service and been blocked.

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
app/src/main/java/com/radley/applock/
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
